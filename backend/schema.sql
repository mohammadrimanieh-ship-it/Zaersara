-- Zaersara Mashhad v0.1 - Supabase/PostgreSQL
create extension if not exists btree_gist;

create table if not exists public.units (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  capacity int not null check (capacity > 0),
  kind text not null default 'suite',
  active boolean not null default true,
  sort_order int not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.persons (
  id uuid primary key default gen_random_uuid(),
  first_name text not null,
  last_name text not null,
  national_id varchar(10) not null unique check (national_id ~ '^[0-9]{10}$'),
  phone text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.reservations (
  id uuid primary key default gen_random_uuid(),
  title text not null default '',
  unit_id uuid not null references public.units(id),
  start_date date not null,
  end_date date not null,
  guest_count int not null check (guest_count > 0),
  reservation_type text not null default 'family' check (reservation_type in ('family','caravan')),
  leader_name text not null default '',
  leader_phone text not null default '',
  is_paid boolean not null default false,
  amount bigint not null default 0 check (amount >= 0),
  payment_status text not null default 'رایگان',
  status text not null default 'confirmed' check (status in ('temporary','confirmed','cancelled','checked_out')),
  notes text not null default '',
  created_by uuid default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (end_date > start_date)
);

-- Prevent two active reservations from overlapping on the same unit.
alter table public.reservations drop constraint if exists no_overlapping_active_reservations;
alter table public.reservations add constraint no_overlapping_active_reservations
exclude using gist (
  unit_id with =,
  daterange(start_date, end_date, '[)') with &&
) where (status <> 'cancelled');

create table if not exists public.reservation_guests (
  reservation_id uuid not null references public.reservations(id) on delete cascade,
  person_id uuid not null references public.persons(id),
  is_leader boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (reservation_id, person_id)
);

insert into public.units(name,capacity,kind,sort_order) values
 ('سوئیت ۱',5,'suite',1),('سوئیت ۲',5,'suite',2),('سوئیت ۳',5,'suite',3),
 ('سوئیت ۴',6,'suite',4),('سوئیت ۵',6,'suite',5),('سالن',14,'hall',6)
on conflict(name) do update set capacity=excluded.capacity,kind=excluded.kind,sort_order=excluded.sort_order;

alter table public.units enable row level security;
alter table public.persons enable row level security;
alter table public.reservations enable row level security;
alter table public.reservation_guests enable row level security;
drop policy if exists "auth read units" on public.units;
drop policy if exists "auth read persons" on public.persons;
drop policy if exists "auth write persons" on public.persons;
drop policy if exists "auth read reservations" on public.reservations;
drop policy if exists "auth write reservations" on public.reservations;
drop policy if exists "auth read reservation_guests" on public.reservation_guests;
drop policy if exists "auth write reservation_guests" on public.reservation_guests;

create policy "auth read units" on public.units for select to authenticated using (true);
create policy "auth read persons" on public.persons for select to authenticated using (true);
create policy "auth write persons" on public.persons for all to authenticated using (true) with check (true);
create policy "auth read reservations" on public.reservations for select to authenticated using (true);
create policy "auth write reservations" on public.reservations for all to authenticated using (true) with check (true);
create policy "auth read reservation_guests" on public.reservation_guests for select to authenticated using (true);
create policy "auth write reservation_guests" on public.reservation_guests for all to authenticated using (true) with check (true);

create or replace function public.create_reservation_atomic(
 p_title text,p_unit_id uuid,p_start_date date,p_end_date date,p_guest_count int,p_reservation_type text,
 p_leader_name text,p_leader_phone text,p_is_paid boolean,p_amount bigint,p_payment_status text,p_notes text,p_guests jsonb
) returns jsonb language plpgsql security invoker as $$
declare r_id uuid; g jsonb; p_id uuid;
begin
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if jsonb_array_length(p_guests) = 0 then raise exception 'حداقل یک زائر باید ثبت شود'; end if;
  if p_guest_count <> jsonb_array_length(p_guests) then raise exception 'تعداد نفرات با فهرست زائران برابر نیست'; end if;
  if p_guest_count > (select capacity from public.units where id=p_unit_id) then raise exception 'تعداد نفرات بیشتر از ظرفیت واحد است'; end if;
  insert into public.reservations(title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes)
  values(coalesce(p_title,''),p_unit_id,p_start_date,p_end_date,p_guest_count,p_reservation_type,coalesce(p_leader_name,''),coalesce(p_leader_phone,''),p_is_paid,p_amount,p_payment_status,coalesce(p_notes,'')) returning id into r_id;
  for g in select * from jsonb_array_elements(p_guests) loop
    if (g->>'national_id') !~ '^[0-9]{10}$' then raise exception 'کد ملی نامعتبر است'; end if;
    insert into public.persons(first_name,last_name,national_id)
    values(g->>'first_name',g->>'last_name',g->>'national_id')
    on conflict(national_id) do update set first_name=excluded.first_name,last_name=excluded.last_name,updated_at=now()
    returning id into p_id;
    insert into public.reservation_guests(reservation_id,person_id) values(r_id,p_id) on conflict do nothing;
  end loop;
  return jsonb_build_object('id',r_id);
end $$;

grant execute on function public.create_reservation_atomic(text,uuid,date,date,int,text,text,text,boolean,bigint,text,text,jsonb) to authenticated;

create or replace view public.person_visit_summary with (security_invoker=true) as
select p.id,p.national_id,trim(p.first_name||' '||p.last_name) full_name,count(rg.reservation_id)::int visit_count,
       max(r.end_date) last_departure
from public.persons p
left join public.reservation_guests rg on rg.person_id=p.id
left join public.reservations r on r.id=rg.reservation_id and r.status <> 'cancelled'
group by p.id,p.national_id,p.first_name,p.last_name;

grant select on public.person_visit_summary to authenticated;

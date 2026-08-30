-- Zaersara Mashhad 0.3.0 migration
-- Safe migration for an existing 0.2 database. Existing reservations are preserved.

-- 1) Unit grouping: suggestions are NEVER allowed to mix different groups.
alter table public.units add column if not exists unit_group text not null default 'original';
alter table public.units add column if not exists capacity_configured boolean not null default true;

update public.units
set unit_group='original', capacity_configured=true
where name in ('سوئیت ۱','سوئیت ۲','سوئیت ۳','سوئیت ۴','سوئیت ۵','سالن');

insert into public.units(name,capacity,kind,unit_group,capacity_configured,sort_order) values
 ('فاطمیه ۲ تخته',2,'room','fatemiyeh',true,101),
 ('فاطمیه ۳ تخته',3,'room','fatemiyeh',true,102),
 ('فاطمیه ۴ تخته',4,'room','fatemiyeh',true,103),
 ('آپارتمان طبقه دوم',0,'apartment','apartment',true,201),
 ('آپارتمان زیرزمین',0,'apartment','apartment',true,202)
on conflict(name) do update set
 capacity=case when excluded.unit_group in ('fatemiyeh','apartment') then excluded.capacity else public.units.capacity end,
 kind=excluded.kind,
 unit_group=excluded.unit_group,
 capacity_configured=true,
 sort_order=excluded.sort_order;

-- Allow authenticated app users to set/edit unit capacity.
drop policy if exists "auth write units" on public.units;
create policy "auth write units" on public.units for update to authenticated using (true) with check (true);

-- 2) National ID becomes optional. If provided, it must still be exactly 10 digits.
alter table public.persons alter column national_id drop not null;
alter table public.persons drop constraint if exists persons_national_id_check;
alter table public.persons drop constraint if exists persons_national_id_optional_check;
alter table public.persons add constraint persons_national_id_optional_check
check (national_id is null or national_id ~ '^[0-9]{10}$');

-- 3) Link rows belonging to one multi-unit/caravan booking.
alter table public.reservations add column if not exists booking_group_id uuid;
update public.reservations set booking_group_id = gen_random_uuid() where booking_group_id is null;
alter table public.reservations alter column booking_group_id set default gen_random_uuid();
alter table public.reservations alter column booking_group_id set not null;
create index if not exists reservations_booking_group_id_idx on public.reservations(booking_group_id);

-- 4) New atomic booking function: one unit for family reservations, one or more units for caravan reservations.
-- Every selected unit must belong to the SAME unit_group.
create or replace function public.create_booking_atomic(
 p_title text,
 p_start_date date,
 p_end_date date,
 p_reservation_type text,
 p_leader_name text,
 p_leader_phone text,
 p_is_paid boolean,
 p_amount bigint,
 p_payment_status text,
 p_notes text,
 p_units jsonb,
 p_guests jsonb
) returns jsonb language plpgsql security invoker as $$
declare
  booking_id uuid := gen_random_uuid();
  u jsonb;
  g jsonb;
  r_id uuid;
  p_id uuid;
  unit_uuid uuid;
  unit_cap int;
  unit_group_value text;
  expected_group text := null;
  alloc int;
  total_alloc int := 0;
  guest_index int := 0;
  i int;
  nid text;
  row_index int := 0;
begin
  if p_start_date < current_date then raise exception 'تاریخ ورود نمی‌تواند در گذشته باشد'; end if;
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if p_reservation_type not in ('family','caravan') then raise exception 'نوع رزرو نامعتبر است'; end if;
  if jsonb_array_length(p_units) = 0 then raise exception 'حداقل یک واحد باید انتخاب شود'; end if;
  if jsonb_array_length(p_guests) = 0 then raise exception 'حداقل یک زائر باید ثبت شود'; end if;
  if p_reservation_type = 'family' and jsonb_array_length(p_units) <> 1 then raise exception 'رزرو خانوادگی فقط می‌تواند یک واحد داشته باشد'; end if;
  if coalesce(p_amount,0) < 0 then raise exception 'مبلغ نامعتبر است'; end if;

  -- Validate capacities and prevent mixing unit groups.
  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;
    select capacity, unit_group into unit_cap, unit_group_value
      from public.units where id=unit_uuid and active=true and capacity_configured=true;
    if not found then raise exception 'واحد انتخاب‌شده فعال نیست'; end if;
    if alloc <= 0 then raise exception 'تعداد نفرات تخصیص داده‌شده باید بیشتر از صفر باشد'; end if;
    if unit_group_value <> 'apartment' and alloc > unit_cap then raise exception 'تعداد نفرات تخصیص داده‌شده از ظرفیت واحد بیشتر است'; end if;
    if expected_group is null then expected_group := unit_group_value;
    elsif expected_group <> unit_group_value then raise exception 'ترکیب واحدهای مجموعه‌های مختلف مجاز نیست';
    end if;
    total_alloc := total_alloc + alloc;
  end loop;

  if total_alloc <> jsonb_array_length(p_guests) then raise exception 'تعداد نفرات با ظرفیت تخصیص داده‌شده برابر نیست'; end if;

  -- Insert one reservation row per unit. The total amount is stored only on the first row
  -- so financial reports do not double-count a multi-unit booking.
  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;

    insert into public.reservations(
      booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,
      leader_name,leader_phone,is_paid,amount,payment_status,notes
    ) values (
      booking_id,coalesce(p_title,''),unit_uuid,p_start_date,p_end_date,alloc,p_reservation_type,
      coalesce(p_leader_name,''),coalesce(p_leader_phone,''),coalesce(p_is_paid,false),
      case when row_index=0 then coalesce(p_amount,0) else 0 end,
      case when coalesce(p_is_paid,false) then coalesce(p_payment_status,'بدهکار') else 'رایگان' end,
      coalesce(p_notes,'')
    ) returning id into r_id;

    for i in 1..alloc loop
      g := p_guests->guest_index;
      nid := nullif(coalesce(g->>'national_id',''),'');
      if nid is not null and nid !~ '^[0-9]{10}$' then raise exception 'کد ملی در صورت ورود باید ۱۰ رقمی باشد'; end if;
      if nullif(trim(coalesce(g->>'first_name','')),'') is null or nullif(trim(coalesce(g->>'last_name','')),'') is null then
        raise exception 'نام و نام خانوادگی زائر الزامی است';
      end if;

      if nid is null then
        insert into public.persons(first_name,last_name,national_id)
        values(trim(g->>'first_name'),trim(g->>'last_name'),null)
        returning id into p_id;
      else
        insert into public.persons(first_name,last_name,national_id)
        values(trim(g->>'first_name'),trim(g->>'last_name'),nid)
        on conflict(national_id) do update
          set first_name=excluded.first_name,last_name=excluded.last_name,updated_at=now()
        returning id into p_id;
      end if;

      insert into public.reservation_guests(reservation_id,person_id)
      values(r_id,p_id) on conflict do nothing;
      guest_index := guest_index + 1;
    end loop;
    row_index := row_index + 1;
  end loop;

  return jsonb_build_object('booking_group_id',booking_id,'reservation_rows',jsonb_array_length(p_units));
end $$;

grant execute on function public.create_booking_atomic(text,date,date,text,text,text,boolean,bigint,text,text,jsonb,jsonb) to authenticated;

-- Recreate visit summary to continue supporting optional national IDs.
create or replace view public.person_visit_summary with (security_invoker=true) as
select p.id,p.national_id,trim(p.first_name||' '||p.last_name) full_name,count(rg.reservation_id)::int visit_count,
       max(r.end_date) last_departure
from public.persons p
left join public.reservation_guests rg on rg.person_id=p.id
left join public.reservations r on r.id=rg.reservation_id and r.status <> 'cancelled'
group by p.id,p.national_id,p.first_name,p.last_name;

grant select on public.person_visit_summary to authenticated;

-- Zaersara Mashhad v0.8.0
-- Operational upgrade: apartment removal, checkout completion, per-reservation extra capacity,
-- rooming/mahram information, meals, payment/gift metadata and person notes/history.
-- IMPORTANT: per user request this migration permanently removes the two apartment units
-- and any reservation/person links that exist only through those apartment reservation rows.

begin;

-- 1) Completely remove apartment reservations from this app/database.
-- First detach reservation_guest links, then reservations, then apartment units.
delete from public.reservation_guests rg
using public.reservations r, public.units u
where rg.reservation_id = r.id
  and r.unit_id = u.id
  and (u.unit_group = 'apartment' or u.name in ('آپارتمان طبقه دوم','آپارتمان زیرزمین'));

delete from public.reservations r
using public.units u
where r.unit_id = u.id
  and (u.unit_group = 'apartment' or u.name in ('آپارتمان طبقه دوم','آپارتمان زیرزمین'));

delete from public.units
where unit_group = 'apartment'
   or name in ('آپارتمان طبقه دوم','آپارتمان زیرزمین');

-- 2) Operational reservation fields.
alter table public.reservations
  add column if not exists registered_at timestamptz not null default now(),
  add column if not exists extra_capacity integer not null default 0,
  add column if not exists room_gender text not null default 'family',
  add column if not exists mahram_notes text not null default '',
  add column if not exists service_type text not null default 'stay_no_food',
  add column if not exists breakfast_count integer not null default 0,
  add column if not exists lunch_count integer not null default 0,
  add column if not exists dinner_count integer not null default 0,
  add column if not exists payment_kind text not null default 'free',
  add column if not exists gift_description text not null default '';

alter table public.reservations drop constraint if exists reservations_extra_capacity_nonnegative;
alter table public.reservations add constraint reservations_extra_capacity_nonnegative check (extra_capacity >= 0);
alter table public.reservations drop constraint if exists reservations_meal_counts_nonnegative;
alter table public.reservations add constraint reservations_meal_counts_nonnegative check (breakfast_count >= 0 and lunch_count >= 0 and dinner_count >= 0);

-- 3) Individual notes / discipline history.
alter table public.persons
  add column if not exists personal_notes text not null default '',
  add column if not exists discipline_notes text not null default '';

create index if not exists reservations_registered_at_idx on public.reservations(registered_at);
create index if not exists reservations_service_type_idx on public.reservations(service_type);

-- 4) Checkout means the booking is completed and must no longer be counted as active.
create or replace function public.mark_booking_status(
  p_booking_group_id uuid,
  p_action text
)
returns jsonb
language plpgsql
security invoker
as $$
begin
  if p_action = 'check_in' then
    update public.reservations
       set check_in_at = coalesce(check_in_at, now()),
           check_out_at = null,
           status = 'active',
           updated_at = now()
     where booking_group_id = p_booking_group_id
       and status <> 'cancelled';
  elsif p_action = 'check_out' then
    update public.reservations
       set check_in_at = coalesce(check_in_at, now()),
           check_out_at = now(),
           status = 'completed',
           updated_at = now()
     where booking_group_id = p_booking_group_id
       and status <> 'cancelled';
  else
    raise exception 'عملیات ورود/خروج نامعتبر است';
  end if;

  if not found then raise exception 'رزرو پیدا نشد'; end if;
  return jsonb_build_object('ok', true, 'action', p_action);
end;
$$;
grant execute on function public.mark_booking_status(uuid,text) to authenticated;

-- 5) Replace booking RPC: no apartment exception; supports extra capacity, gender/mahram, meals, payment/gift.
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
)
returns jsonb
language plpgsql
security invoker
as $$
declare
  booking_id uuid := gen_random_uuid();
  u jsonb; g jsonb; r_id uuid; p_id uuid; unit_uuid uuid;
  unit_cap int; unit_group_value text; expected_group text := null;
  alloc int; extra_cap int; nid text; guest_phone text; row_index int := 0;
  family_name text; rg text; mn text; st text; pk text; gd text;
  bc int; lc int; dc int;
begin
  if p_start_date < current_date then raise exception 'تاریخ ورود نمی‌تواند در گذشته باشد'; end if;
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if p_reservation_type not in ('family','caravan') then raise exception 'نوع رزرو نامعتبر است'; end if;
  if p_units is null or jsonb_typeof(p_units)<>'array' or jsonb_array_length(p_units)=0 then raise exception 'حداقل یک واحد باید انتخاب شود'; end if;
  if p_reservation_type='family' and jsonb_array_length(p_units)<>1 then raise exception 'رزرو خانوادگی فقط می‌تواند یک واحد داشته باشد'; end if;
  if coalesce(p_amount,0)<0 then raise exception 'مبلغ نامعتبر است'; end if;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;
    extra_cap := greatest(coalesce(nullif(u->>'extra_capacity','')::int,0),0);
    family_name := trim(coalesce(u->>'family_last_name',''));
    select capacity,unit_group into unit_cap,unit_group_value from public.units where id=unit_uuid and active=true;
    if not found then raise exception 'واحد انتخاب‌شده فعال نیست'; end if;
    if unit_group_value='apartment' then raise exception 'آپارتمان‌ها از این سامانه حذف شده‌اند'; end if;
    if alloc<=0 then raise exception 'تعداد نفرات باید بیشتر از صفر باشد'; end if;
    if family_name='' then raise exception 'نام خانوادگی هر واحد الزامی است'; end if;
    if alloc > unit_cap + extra_cap then raise exception 'تعداد نفرات از ظرفیت مجاز این رزرو بیشتر است'; end if;
    if expected_group is null then expected_group:=unit_group_value; elsif expected_group<>unit_group_value then raise exception 'ترکیب واحدهای مجموعه‌های مختلف مجاز نیست'; end if;
    if exists (
      select 1 from public.reservations r
      where r.unit_id=unit_uuid and r.status not in ('cancelled','completed')
        and daterange(r.start_date,r.end_date,'[)') && daterange(p_start_date,p_end_date,'[)')
    ) then raise exception 'یکی از واحدهای انتخاب‌شده در این بازه رزرو است'; end if;
  end loop;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;
    extra_cap := greatest(coalesce(nullif(u->>'extra_capacity','')::int,0),0);
    family_name := trim(coalesce(u->>'family_last_name',''));
    rg := coalesce(nullif(trim(u->>'room_gender'),''),'family');
    mn := trim(coalesce(u->>'mahram_notes',''));
    st := coalesce(nullif(trim(u->>'service_type'),''),'stay_no_food');
    bc := greatest(coalesce(nullif(u->>'breakfast_count','')::int,0),0);
    lc := greatest(coalesce(nullif(u->>'lunch_count','')::int,0),0);
    dc := greatest(coalesce(nullif(u->>'dinner_count','')::int,0),0);
    pk := coalesce(nullif(trim(u->>'payment_kind'),''), case when coalesce(p_is_paid,false) then 'paid' else 'free' end);
    gd := trim(coalesce(u->>'gift_description',''));

    insert into public.reservations(
      booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,primary_last_name,
      leader_name,leader_phone,is_paid,amount,payment_status,notes,registered_at,extra_capacity,room_gender,
      mahram_notes,service_type,breakfast_count,lunch_count,dinner_count,payment_kind,gift_description,status
    ) values(
      booking_id,coalesce(p_title,''),unit_uuid,p_start_date,p_end_date,alloc,p_reservation_type,family_name,
      coalesce(p_leader_name,''),coalesce(p_leader_phone,''),coalesce(p_is_paid,false),
      case when row_index=0 then coalesce(p_amount,0) else 0 end,
      case when coalesce(p_is_paid,false) then coalesce(p_payment_status,'بدهکار') else case when pk='gift' then 'هدیه' else 'رایگان' end end,
      coalesce(p_notes,''),now(),extra_cap,rg,mn,st,bc,lc,dc,pk,gd,'active'
    ) returning id into r_id;
    row_index := row_index + 1;

    if u ? 'guests' and jsonb_typeof(u->'guests')='array' then
      for g in select * from jsonb_array_elements(u->'guests') loop
        nid := nullif(trim(coalesce(g->>'national_id','')), '');
        guest_phone := trim(coalesce(g->>'phone',''));
        if nid is not null and nid !~ '^[0-9]{10}$' then raise exception 'کد ملی در صورت ورود باید ۱۰ رقمی باشد'; end if;
        if guest_phone <> '' and guest_phone !~ '^[0-9]{7,11}$' then raise exception 'شماره موبایل نامعتبر است'; end if;
        if nullif(trim(coalesce(g->>'last_name','')),'') is null then continue; end if;
        if nid is null then
          insert into public.persons(first_name,last_name,national_id,phone)
          values(trim(coalesce(g->>'first_name','')),trim(g->>'last_name'),null,guest_phone) returning id into p_id;
        else
          insert into public.persons(first_name,last_name,national_id,phone)
          values(trim(coalesce(g->>'first_name','')),trim(g->>'last_name'),nid,guest_phone)
          on conflict(national_id) do update set first_name=excluded.first_name,last_name=excluded.last_name,
            phone=case when excluded.phone<>'' then excluded.phone else public.persons.phone end,updated_at=now()
          returning id into p_id;
        end if;
        insert into public.reservation_guests(reservation_id,person_id) values(r_id,p_id) on conflict do nothing;
      end loop;
    end if;
  end loop;

  return jsonb_build_object('booking_group_id',booking_id,'reservation_rows',jsonb_array_length(p_units));
end;
$$;
grant execute on function public.create_booking_atomic(text,date,date,text,text,text,boolean,bigint,text,text,jsonb,jsonb) to authenticated;

-- 6) Updating a reserved unit respects reservation-specific extra capacity.
create or replace function public.update_booking_unit(
 p_reservation_id uuid,
 p_unit_id uuid,
 p_guest_count int,
 p_family_last_name text
)
returns jsonb
language plpgsql
security invoker
as $$
declare
  r public.reservations%rowtype;
  cap int; grp text; target_grp text;
begin
  select * into r from public.reservations where id=p_reservation_id and status<>'cancelled' for update;
  if not found then raise exception 'رزرو پیدا نشد'; end if;
  if p_guest_count<=0 then raise exception 'تعداد نفرات نامعتبر است'; end if;
  if trim(coalesce(p_family_last_name,''))='' then raise exception 'نام خانوادگی الزامی است'; end if;
  select capacity,unit_group into cap,target_grp from public.units where id=p_unit_id and active=true;
  if not found then raise exception 'واحد انتخاب‌شده فعال نیست'; end if;
  if target_grp='apartment' then raise exception 'آپارتمان‌ها از این سامانه حذف شده‌اند'; end if;
  select unit_group into grp from public.units where id=r.unit_id;
  if grp<>target_grp then raise exception 'تغییر واحد فقط داخل همان مجموعه مجاز است'; end if;
  if p_guest_count > cap + coalesce(r.extra_capacity,0) then raise exception 'تعداد نفرات از ظرفیت مجاز این رزرو بیشتر است'; end if;
  if exists (
    select 1 from public.reservations x
    where x.unit_id=p_unit_id and x.id<>p_reservation_id and x.status not in ('cancelled','completed')
      and daterange(x.start_date,x.end_date,'[)') && daterange(r.start_date,r.end_date,'[)')
  ) then raise exception 'واحد جدید در این بازه رزرو است'; end if;
  update public.reservations set unit_id=p_unit_id, guest_count=p_guest_count,
      primary_last_name=trim(p_family_last_name), updated_at=now()
  where id=p_reservation_id;
  return jsonb_build_object('ok',true);
end;
$$;
grant execute on function public.update_booking_unit(uuid,uuid,int,text) to authenticated;


-- 6b) Caravan unit add keeps the booking's operational metadata (including Fatemiyeh caravans).
create or replace function public.add_booking_unit(
 p_booking_group_id uuid,
 p_unit_id uuid,
 p_guest_count int,
 p_family_last_name text
)
returns jsonb
language plpgsql
security invoker
as $$
declare
  base public.reservations%rowtype;
  cap int; target_grp text; base_grp text; new_id uuid;
begin
  select * into base from public.reservations where booking_group_id=p_booking_group_id and status<>'cancelled' order by created_at limit 1;
  if not found then raise exception 'رزرو پیدا نشد'; end if;
  if base.reservation_type<>'caravan' then raise exception 'افزودن واحد فقط برای رزرو کاروانی مجاز است'; end if;
  if p_guest_count<=0 or trim(coalesce(p_family_last_name,''))='' then raise exception 'اطلاعات واحد کامل نیست'; end if;
  select capacity,unit_group into cap,target_grp from public.units where id=p_unit_id and active=true;
  if not found or target_grp='apartment' then raise exception 'واحد انتخاب‌شده معتبر نیست'; end if;
  select unit_group into base_grp from public.units where id=base.unit_id;
  if target_grp<>base_grp then raise exception 'واحد جدید باید از همان مجموعه باشد'; end if;
  if p_guest_count>cap then raise exception 'برای ظرفیت اضافه، رزرو را از مسیر رزرو جدید با ظرفیت استثنایی ثبت کنید'; end if;
  if exists (select 1 from public.reservations x where x.unit_id=p_unit_id and x.status not in ('cancelled','completed') and daterange(x.start_date,x.end_date,'[)') && daterange(base.start_date,base.end_date,'[)')) then
    raise exception 'این واحد در بازه رزرو خالی نیست';
  end if;
  insert into public.reservations(
    booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,primary_last_name,
    leader_name,leader_phone,is_paid,amount,payment_status,notes,registered_at,extra_capacity,room_gender,
    mahram_notes,service_type,breakfast_count,lunch_count,dinner_count,payment_kind,gift_description,status
  ) values(
    base.booking_group_id,base.title,p_unit_id,base.start_date,base.end_date,p_guest_count,base.reservation_type,trim(p_family_last_name),
    base.leader_name,base.leader_phone,base.is_paid,0,base.payment_status,base.notes,now(),0,base.room_gender,
    base.mahram_notes,base.service_type,0,0,0,base.payment_kind,base.gift_description,'active'
  ) returning id into new_id;
  return jsonb_build_object('ok',true,'reservation_id',new_id);
end;
$$;
grant execute on function public.add_booking_unit(uuid,uuid,int,text) to authenticated;

-- 7) Person history: name search + individual notes and stay history summary.
create or replace function public.search_person_history(p_mode text, p_query text)
returns jsonb
language sql
security invoker
stable
as $$
with person_rows as (
  select p.id::text as id, p.first_name, p.last_name, coalesce(p.national_id,'') as national_id,
         coalesce(p.phone,'') as phone, count(distinct r.booking_group_id)::int as visit_count,
         max(r.end_date) as last_departure,
         coalesce(p.personal_notes,'') as personal_notes,
         coalesce(p.discipline_notes,'') as discipline_notes,
         coalesce(jsonb_agg(distinct jsonb_build_object(
           'start_date',r.start_date,'end_date',r.end_date,'unit_name',u.name,'family',r.primary_last_name
         )) filter (where r.id is not null), '[]'::jsonb) as stays
  from public.persons p
  left join public.reservation_guests rg on rg.person_id=p.id
  left join public.reservations r on r.id=rg.reservation_id and r.status<>'cancelled'
  left join public.units u on u.id=r.unit_id
  where (p_mode='national' and p.national_id=p_query)
     or (p_mode='phone' and p.phone=p_query)
     or (p_mode='last_name' and p.last_name ilike '%'||p_query||'%')
     or (p_mode='name' and (p.first_name ilike '%'||p_query||'%' or (p.first_name||' '||p.last_name) ilike '%'||p_query||'%'))
  group by p.id,p.first_name,p.last_name,p.national_id,p.phone,p.personal_notes,p.discipline_notes
)
select jsonb_build_object('items',coalesce(jsonb_agg(to_jsonb(x) order by x.visit_count desc,x.last_departure desc nulls last),'[]'::jsonb))
from (select * from person_rows limit 50) x;
$$;
grant execute on function public.search_person_history(text,text) to authenticated;

create or replace function public.update_person_notes(
  p_person_id uuid,
  p_personal_notes text,
  p_discipline_notes text
)
returns jsonb
language plpgsql
security invoker
as $$
begin
  update public.persons
     set personal_notes=coalesce(p_personal_notes,''), discipline_notes=coalesce(p_discipline_notes,''), updated_at=now()
   where id=p_person_id;
  if not found then raise exception 'فرد پیدا نشد'; end if;
  return jsonb_build_object('ok',true);
end;
$$;
grant execute on function public.update_person_notes(uuid,text,text) to authenticated;

commit;
notify pgrst, 'reload schema';

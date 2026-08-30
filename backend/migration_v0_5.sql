-- Zaersara Mashhad v0.5.0
-- Safe migration: keeps all existing reservations and people.
-- Adds one primary family name per reserved unit and editing/cancel/extension helpers.
-- Reservation overlap continues to use [start_date,end_date), so checkout day is free for a new arrival.

alter table public.reservations
  add column if not exists primary_last_name text not null default '';

create index if not exists reservations_booking_group_idx on public.reservations(booking_group_id);
create index if not exists reservations_primary_last_name_idx on public.reservations(primary_last_name);

-- For old rows, use the existing title as a best-effort display value only when the field is empty.
update public.reservations
set primary_last_name = trim(title)
where primary_last_name = '' and nullif(trim(title),'') is not null;

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
  alloc int; nid text; guest_phone text; row_index int := 0;
  family_name text;
begin
  if p_start_date < current_date then raise exception 'تاریخ ورود نمی‌تواند در گذشته باشد'; end if;
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if p_reservation_type not in ('family','caravan') then raise exception 'نوع رزرو نامعتبر است'; end if;
  if jsonb_array_length(p_units)=0 then raise exception 'حداقل یک واحد باید انتخاب شود'; end if;
  if p_reservation_type='family' and jsonb_array_length(p_units)<>1 then raise exception 'رزرو خانوادگی فقط می‌تواند یک واحد داشته باشد'; end if;
  if coalesce(p_amount,0)<0 then raise exception 'مبلغ نامعتبر است'; end if;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;
    family_name := trim(coalesce(u->>'family_last_name',''));
    select capacity,unit_group into unit_cap,unit_group_value from public.units where id=unit_uuid and active=true;
    if not found then raise exception 'واحد انتخاب‌شده فعال نیست'; end if;
    if alloc<=0 then raise exception 'تعداد نفرات باید بیشتر از صفر باشد'; end if;
    if family_name='' then raise exception 'نام خانوادگی هر واحد الزامی است'; end if;
    if unit_group_value<>'apartment' and alloc>unit_cap then raise exception 'تعداد نفرات از ظرفیت واحد بیشتر است'; end if;
    if expected_group is null then expected_group:=unit_group_value; elsif expected_group<>unit_group_value then raise exception 'ترکیب واحدهای مجموعه‌های مختلف مجاز نیست'; end if;
    if exists (
      select 1 from public.reservations r
      where r.unit_id=unit_uuid and r.status<>'cancelled'
        and daterange(r.start_date,r.end_date,'[)') && daterange(p_start_date,p_end_date,'[)')
    ) then raise exception 'یکی از واحدهای انتخاب‌شده در این بازه رزرو است'; end if;
  end loop;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid;
    alloc := (u->>'guest_count')::int;
    family_name := trim(coalesce(u->>'family_last_name',''));
    insert into public.reservations(
      booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,primary_last_name,
      leader_name,leader_phone,is_paid,amount,payment_status,notes
    ) values(
      booking_id,coalesce(p_title,''),unit_uuid,p_start_date,p_end_date,alloc,p_reservation_type,family_name,
      coalesce(p_leader_name,''),coalesce(p_leader_phone,''),coalesce(p_is_paid,false),
      case when row_index=0 then coalesce(p_amount,0) else 0 end,
      case when coalesce(p_is_paid,false) then coalesce(p_payment_status,'بدهکار') else 'رایگان' end,
      coalesce(p_notes,'')
    ) returning id into r_id;
    row_index := row_index + 1;
  end loop;

  -- Optional detailed people are still supported, but are no longer required for a reservation.
  if p_guests is not null and jsonb_typeof(p_guests)='array' then
    for g in select * from jsonb_array_elements(p_guests) loop
      nid := nullif(coalesce(g->>'national_id',''),'');
      guest_phone := trim(coalesce(g->>'phone',''));
      if nid is not null and nid !~ '^[0-9]{10}$' then raise exception 'کد ملی در صورت ورود باید ۱۰ رقمی باشد'; end if;
      if guest_phone <> '' and guest_phone !~ '^[0-9]{7,11}$' then raise exception 'شماره موبایل نامعتبر است'; end if;
      if nullif(trim(coalesce(g->>'last_name','')),'') is null then continue; end if;
      if nid is null then
        insert into public.persons(first_name,last_name,national_id,phone)
        values(trim(coalesce(g->>'first_name','')),trim(g->>'last_name'),null,guest_phone)
        returning id into p_id;
      else
        insert into public.persons(first_name,last_name,national_id,phone)
        values(trim(coalesce(g->>'first_name','')),trim(g->>'last_name'),nid,guest_phone)
        on conflict(national_id) do update set
          first_name=excluded.first_name,last_name=excluded.last_name,
          phone=case when excluded.phone<>'' then excluded.phone else public.persons.phone end,updated_at=now()
        returning id into p_id;
      end if;
      -- Attach optional person to the first active row of this booking.
      select id into r_id from public.reservations where booking_group_id=booking_id and status<>'cancelled' order by created_at limit 1;
      insert into public.reservation_guests(reservation_id,person_id) values(r_id,p_id) on conflict do nothing;
    end loop;
  end if;

  return jsonb_build_object('booking_group_id',booking_id,'reservation_rows',jsonb_array_length(p_units));
end;
$$;

grant execute on function public.create_booking_atomic(text,date,date,text,text,text,boolean,bigint,text,text,jsonb,jsonb) to authenticated;

create or replace function public.update_booking_meta(
 p_booking_group_id uuid,
 p_title text,
 p_start_date date,
 p_end_date date,
 p_leader_name text,
 p_leader_phone text,
 p_is_paid boolean,
 p_amount bigint,
 p_payment_status text,
 p_notes text
)
returns jsonb
language plpgsql
security invoker
as $$
begin
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if coalesce(p_amount,0)<0 then raise exception 'مبلغ نامعتبر است'; end if;
  if exists (
    select 1
    from public.reservations mine
    join public.reservations other on other.unit_id=mine.unit_id and other.id<>mine.id and other.status<>'cancelled'
    where mine.booking_group_id=p_booking_group_id and mine.status<>'cancelled'
      and daterange(other.start_date,other.end_date,'[)') && daterange(p_start_date,p_end_date,'[)')
  ) then raise exception 'در بازه جدید یکی از واحدها رزرو است'; end if;

  update public.reservations
  set title=coalesce(p_title,''), start_date=p_start_date, end_date=p_end_date,
      leader_name=coalesce(p_leader_name,''), leader_phone=coalesce(p_leader_phone,''),
      is_paid=coalesce(p_is_paid,false),
      amount=case when id=(select id from public.reservations where booking_group_id=p_booking_group_id and status<>'cancelled' order by created_at limit 1) then coalesce(p_amount,0) else 0 end,
      payment_status=case when coalesce(p_is_paid,false) then coalesce(p_payment_status,'بدهکار') else 'رایگان' end,
      notes=coalesce(p_notes,''), updated_at=now()
  where booking_group_id=p_booking_group_id and status<>'cancelled';

  if not found then raise exception 'رزرو پیدا نشد'; end if;
  return jsonb_build_object('ok',true);
end;
$$;

grant execute on function public.update_booking_meta(uuid,text,date,date,text,text,boolean,bigint,text,text) to authenticated;

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
  select unit_group into grp from public.units where id=r.unit_id;
  if grp<>target_grp then raise exception 'تغییر واحد فقط داخل همان مجموعه مجاز است'; end if;
  if target_grp<>'apartment' and p_guest_count>cap then raise exception 'تعداد نفرات از ظرفیت واحد بیشتر است'; end if;
  if exists (
    select 1 from public.reservations x
    where x.unit_id=p_unit_id and x.id<>p_reservation_id and x.status<>'cancelled'
      and daterange(x.start_date,x.end_date,'[)') && daterange(r.start_date,r.end_date,'[)')
  ) then raise exception 'واحد جدید در این بازه رزرو است'; end if;
  update public.reservations set unit_id=p_unit_id, guest_count=p_guest_count,
      primary_last_name=trim(p_family_last_name), updated_at=now()
  where id=p_reservation_id;
  return jsonb_build_object('ok',true);
end;
$$;

grant execute on function public.update_booking_unit(uuid,uuid,int,text) to authenticated;

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
  select unit_group into base_grp from public.units where id=base.unit_id;
  if target_grp<>base_grp then raise exception 'واحد جدید باید از همان مجموعه باشد'; end if;
  if target_grp<>'apartment' and p_guest_count>cap then raise exception 'تعداد نفرات از ظرفیت واحد بیشتر است'; end if;
  if exists (select 1 from public.reservations x where x.unit_id=p_unit_id and x.status<>'cancelled' and daterange(x.start_date,x.end_date,'[)') && daterange(base.start_date,base.end_date,'[)')) then
    raise exception 'این واحد در بازه رزرو خالی نیست';
  end if;
  insert into public.reservations(booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,primary_last_name,leader_name,leader_phone,is_paid,amount,payment_status,notes)
  values(base.booking_group_id,base.title,p_unit_id,base.start_date,base.end_date,p_guest_count,base.reservation_type,trim(p_family_last_name),base.leader_name,base.leader_phone,base.is_paid,0,base.payment_status,base.notes)
  returning id into new_id;
  return jsonb_build_object('ok',true,'reservation_id',new_id);
end;
$$;

grant execute on function public.add_booking_unit(uuid,uuid,int,text) to authenticated;

create or replace function public.remove_booking_unit(p_reservation_id uuid)
returns jsonb
language plpgsql
security invoker
as $$
declare gid uuid; active_count int;
begin
  select booking_group_id into gid from public.reservations where id=p_reservation_id and status<>'cancelled';
  if gid is null then raise exception 'رزرو پیدا نشد'; end if;
  select count(*) into active_count from public.reservations where booking_group_id=gid and status<>'cancelled';
  if active_count<=1 then raise exception 'آخرین واحد را حذف نکنید؛ برای حذف کامل از لغو رزرو استفاده کنید'; end if;
  update public.reservations set status='cancelled',updated_at=now() where id=p_reservation_id;
  return jsonb_build_object('ok',true);
end;
$$;

grant execute on function public.remove_booking_unit(uuid) to authenticated;

create or replace function public.cancel_booking(p_booking_group_id uuid)
returns jsonb
language plpgsql
security invoker
as $$
begin
  update public.reservations set status='cancelled',updated_at=now()
  where booking_group_id=p_booking_group_id and status<>'cancelled';
  if not found then raise exception 'رزرو پیدا نشد'; end if;
  return jsonb_build_object('ok',true);
end;
$$;

grant execute on function public.cancel_booking(uuid) to authenticated;

-- Search also covers the simplified reservations created in v0.5, where only family name is required.
create or replace function public.search_person_history(p_mode text, p_query text)
returns jsonb
language sql
security invoker
stable
as $$
with person_rows as (
  select p.id::text as id, p.first_name, p.last_name, coalesce(p.national_id,'') as national_id,
         coalesce(p.phone,'') as phone, count(rg.reservation_id)::int as visit_count,
         max(r.end_date) as last_departure
  from public.persons p
  left join public.reservation_guests rg on rg.person_id=p.id
  left join public.reservations r on r.id=rg.reservation_id and r.status<>'cancelled'
  where (p_mode='national' and p.national_id=p_query)
     or (p_mode='phone' and p.phone=p_query)
     or (p_mode='last_name' and p.last_name ilike '%'||p_query||'%')
  group by p.id,p.first_name,p.last_name,p.national_id,p.phone
), simplified_rows as (
  select ('family:'||lower(trim(r.primary_last_name))||':'||coalesce(nullif(r.leader_phone,''),'none')) as id,
         max(r.leader_name) as first_name,
         r.primary_last_name as last_name,
         ''::text as national_id,
         max(r.leader_phone) as phone,
         count(distinct r.booking_group_id)::int as visit_count,
         max(r.end_date) as last_departure
  from public.reservations r
  where r.status<>'cancelled' and trim(r.primary_last_name)<>''
    and (
      (p_mode='last_name' and r.primary_last_name ilike '%'||p_query||'%')
      or (p_mode='phone' and r.leader_phone=p_query)
    )
  group by r.primary_last_name, coalesce(nullif(r.leader_phone,''),'none')
), all_rows as (
  select * from person_rows
  union all
  select * from simplified_rows
)
select jsonb_build_object('items',coalesce(jsonb_agg(to_jsonb(x) order by x.visit_count desc,x.last_departure desc nulls last),'[]'::jsonb))
from (select * from all_rows limit 50) x;
$$;

grant execute on function public.search_person_history(text,text) to authenticated;

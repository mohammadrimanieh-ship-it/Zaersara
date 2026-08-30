-- Zaersara Mashhad v0.4.0
-- Safe migration: keeps all existing reservations and people.
-- Adds guest phone persistence + searchable visit history.

create index if not exists persons_phone_idx on public.persons(phone);
create index if not exists persons_last_name_idx on public.persons(last_name);

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
  alloc int; total_alloc int := 0; guest_index int := 0; i int; nid text; guest_phone text; row_index int := 0;
begin
  if p_start_date < current_date then raise exception 'تاریخ ورود نمی‌تواند در گذشته باشد'; end if;
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if p_reservation_type not in ('family','caravan') then raise exception 'نوع رزرو نامعتبر است'; end if;
  if jsonb_array_length(p_units)=0 then raise exception 'حداقل یک واحد باید انتخاب شود'; end if;
  if jsonb_array_length(p_guests)=0 then raise exception 'حداقل یک زائر باید ثبت شود'; end if;
  if p_reservation_type='family' and jsonb_array_length(p_units)<>1 then raise exception 'رزرو خانوادگی فقط می‌تواند یک واحد داشته باشد'; end if;
  if coalesce(p_amount,0)<0 then raise exception 'مبلغ نامعتبر است'; end if;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid; alloc := (u->>'guest_count')::int;
    select capacity,unit_group into unit_cap,unit_group_value from public.units where id=unit_uuid and active=true;
    if not found then raise exception 'واحد انتخاب‌شده فعال نیست'; end if;
    if alloc<=0 then raise exception 'تعداد نفرات تخصیص داده‌شده باید بیشتر از صفر باشد'; end if;
    if unit_group_value<>'apartment' and alloc>unit_cap then raise exception 'تعداد نفرات تخصیص داده‌شده از ظرفیت واحد بیشتر است'; end if;
    if expected_group is null then expected_group:=unit_group_value; elsif expected_group<>unit_group_value then raise exception 'ترکیب واحدهای مجموعه‌های مختلف مجاز نیست'; end if;
    total_alloc := total_alloc + alloc;
  end loop;
  if total_alloc<>jsonb_array_length(p_guests) then raise exception 'تعداد نفرات با تعداد تخصیص داده‌شده برابر نیست'; end if;

  for u in select * from jsonb_array_elements(p_units) loop
    unit_uuid := (u->>'unit_id')::uuid; alloc := (u->>'guest_count')::int;
    insert into public.reservations(booking_group_id,title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes)
    values(booking_id,coalesce(p_title,''),unit_uuid,p_start_date,p_end_date,alloc,p_reservation_type,coalesce(p_leader_name,''),coalesce(p_leader_phone,''),coalesce(p_is_paid,false),case when row_index=0 then coalesce(p_amount,0) else 0 end,case when coalesce(p_is_paid,false) then coalesce(p_payment_status,'بدهکار') else 'رایگان' end,coalesce(p_notes,''))
    returning id into r_id;

    for i in 1..alloc loop
      g := p_guests->guest_index;
      nid := nullif(coalesce(g->>'national_id',''),'');
      guest_phone := trim(coalesce(g->>'phone',''));
      if nid is not null and nid !~ '^[0-9]{10}$' then raise exception 'کد ملی در صورت ورود باید ۱۰ رقمی باشد'; end if;
      if guest_phone <> '' and guest_phone !~ '^[0-9]{7,11}$' then raise exception 'شماره موبایل نامعتبر است'; end if;
      if nullif(trim(coalesce(g->>'first_name','')),'') is null or nullif(trim(coalesce(g->>'last_name','')),'') is null then raise exception 'نام و نام خانوادگی زائر الزامی است'; end if;

      if nid is null then
        insert into public.persons(first_name,last_name,national_id,phone)
        values(trim(g->>'first_name'),trim(g->>'last_name'),null,guest_phone)
        returning id into p_id;
      else
        insert into public.persons(first_name,last_name,national_id,phone)
        values(trim(g->>'first_name'),trim(g->>'last_name'),nid,guest_phone)
        on conflict(national_id) do update set first_name=excluded.first_name,last_name=excluded.last_name,phone=case when excluded.phone<>'' then excluded.phone else public.persons.phone end,updated_at=now()
        returning id into p_id;
      end if;
      insert into public.reservation_guests(reservation_id,person_id) values(r_id,p_id) on conflict do nothing;
      guest_index := guest_index + 1;
    end loop;
    row_index := row_index + 1;
  end loop;
  return jsonb_build_object('booking_group_id',booking_id,'reservation_rows',jsonb_array_length(p_units));
end;
$$;

grant execute on function public.create_booking_atomic(text,date,date,text,text,text,boolean,bigint,text,text,jsonb,jsonb) to authenticated;

create or replace function public.search_person_history(p_mode text, p_query text)
returns jsonb
language sql
security invoker
stable
as $$
  select jsonb_build_object('items', coalesce(jsonb_agg(to_jsonb(x) order by x.visit_count desc, x.last_departure desc nulls last), '[]'::jsonb))
  from (
    select p.id, p.first_name, p.last_name, coalesce(p.national_id,'') as national_id, coalesce(p.phone,'') as phone,
           count(rg.reservation_id)::int as visit_count, max(r.end_date) as last_departure
    from public.persons p
    left join public.reservation_guests rg on rg.person_id=p.id
    left join public.reservations r on r.id=rg.reservation_id and r.status<>'cancelled'
    where
      (p_mode='national' and p.national_id=p_query)
      or (p_mode='phone' and p.phone=p_query)
      or (p_mode='last_name' and p.last_name ilike '%'||p_query||'%')
    group by p.id,p.first_name,p.last_name,p.national_id,p.phone
    limit 50
  ) x;
$$;

grant execute on function public.search_person_history(text,text) to authenticated;

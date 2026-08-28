-- Zaersara Mashhad 0.2.0 migration
-- Safe to run on the existing Supabase project. Existing reservations are preserved.

create or replace function public.create_reservation_atomic(
 p_title text,p_unit_id uuid,p_start_date date,p_end_date date,p_guest_count int,p_reservation_type text,
 p_leader_name text,p_leader_phone text,p_is_paid boolean,p_amount bigint,p_payment_status text,p_notes text,p_guests jsonb
) returns jsonb language plpgsql security invoker as $$
declare r_id uuid; g jsonb; p_id uuid;
begin
  if p_start_date < current_date then raise exception 'تاریخ ورود نمی‌تواند در گذشته باشد'; end if;
  if p_end_date <= p_start_date then raise exception 'تاریخ خروج باید بعد از تاریخ ورود باشد'; end if;
  if jsonb_array_length(p_guests) = 0 then raise exception 'حداقل یک زائر باید ثبت شود'; end if;
  if p_guest_count <> jsonb_array_length(p_guests) then raise exception 'تعداد نفرات با فهرست زائران برابر نیست'; end if;
  if p_guest_count > (select capacity from public.units where id=p_unit_id) then raise exception 'تعداد نفرات بیشتر از ظرفیت واحد است'; end if;

  insert into public.reservations(title,unit_id,start_date,end_date,guest_count,reservation_type,leader_name,leader_phone,is_paid,amount,payment_status,notes)
  values(coalesce(p_title,''),p_unit_id,p_start_date,p_end_date,p_guest_count,p_reservation_type,coalesce(p_leader_name,''),coalesce(p_leader_phone,''),p_is_paid,p_amount,p_payment_status,coalesce(p_notes,''))
  returning id into r_id;

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

-- Zaersara Mashhad v0.7.0 safe migration
-- Keeps all existing reservations, people and units.
-- Adds real check-in/check-out tracking and editable people per reserved unit.

begin;

alter table public.reservations
  add column if not exists check_in_at timestamptz,
  add column if not exists check_out_at timestamptz;

create index if not exists reservations_check_in_idx on public.reservations(check_in_at);
create index if not exists reservations_check_out_idx on public.reservations(check_out_at);

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
           updated_at = now()
     where booking_group_id = p_booking_group_id
       and status <> 'cancelled';
  elsif p_action = 'check_out' then
    update public.reservations
       set check_in_at = coalesce(check_in_at, now()),
           check_out_at = now(),
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

create or replace function public.get_reservation_guests(p_reservation_id uuid)
returns jsonb
language sql
security invoker
stable
as $$
  select jsonb_build_object(
    'items', coalesce(jsonb_agg(jsonb_build_object(
      'first_name', p.first_name,
      'last_name', p.last_name,
      'national_id', coalesce(p.national_id,''),
      'phone', coalesce(p.phone,'')
    ) order by rg.created_at), '[]'::jsonb)
  )
  from public.reservation_guests rg
  join public.persons p on p.id = rg.person_id
  where rg.reservation_id = p_reservation_id;
$$;

grant execute on function public.get_reservation_guests(uuid) to authenticated;

create or replace function public.set_reservation_guests(
  p_reservation_id uuid,
  p_guests jsonb
)
returns jsonb
language plpgsql
security invoker
as $$
declare
  g jsonb;
  p_id uuid;
  nid text;
  ph text;
  ln text;
begin
  if not exists(select 1 from public.reservations where id=p_reservation_id and status<>'cancelled') then
    raise exception 'رزرو پیدا نشد';
  end if;

  delete from public.reservation_guests where reservation_id=p_reservation_id;

  if p_guests is null or jsonb_typeof(p_guests) <> 'array' then
    return jsonb_build_object('ok',true,'count',0);
  end if;

  for g in select * from jsonb_array_elements(p_guests) loop
    ln := trim(coalesce(g->>'last_name',''));
    nid := nullif(trim(coalesce(g->>'national_id','')), '');
    ph := trim(coalesce(g->>'phone',''));

    if ln = '' then raise exception 'نام خانوادگی افراد ثبت‌شده الزامی است'; end if;
    if nid is not null and nid !~ '^[0-9]{10}$' then raise exception 'کد ملی در صورت ورود باید ۱۰ رقمی باشد'; end if;
    if ph <> '' and ph !~ '^[0-9]{7,11}$' then raise exception 'شماره موبایل نامعتبر است'; end if;

    if nid is not null then
      insert into public.persons(first_name,last_name,national_id,phone)
      values(trim(coalesce(g->>'first_name','')),ln,nid,ph)
      on conflict(national_id) do update set
        first_name=excluded.first_name,
        last_name=excluded.last_name,
        phone=case when excluded.phone<>'' then excluded.phone else public.persons.phone end,
        updated_at=now()
      returning id into p_id;
    else
      insert into public.persons(first_name,last_name,national_id,phone)
      values(trim(coalesce(g->>'first_name','')),ln,null,ph)
      returning id into p_id;
    end if;

    insert into public.reservation_guests(reservation_id,person_id)
    values(p_reservation_id,p_id)
    on conflict do nothing;
  end loop;

  return jsonb_build_object('ok',true,'count',jsonb_array_length(p_guests));
end;
$$;

grant execute on function public.set_reservation_guests(uuid,jsonb) to authenticated;

commit;
notify pgrst, 'reload schema';

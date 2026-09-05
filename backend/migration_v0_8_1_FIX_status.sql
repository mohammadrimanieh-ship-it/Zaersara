-- Zaersara Mashhad v0.8.1 hotfix
-- Fixes: new reservation rejected by legacy reservations_status_check
-- Safe: does not delete or rewrite existing reservations.

begin;

-- Remove the legacy check constraint that does not know the v0.8 statuses.
alter table public.reservations
  drop constraint if exists reservations_status_check;

-- v0.8 uses active/completed/cancelled.
-- Legacy values are kept acceptable so old rows/workflows are not broken.
alter table public.reservations
  add constraint reservations_status_check
  check (
    status in (
      'active',
      'completed',
      'cancelled',
      'pending',
      'reserved',
      'confirmed',
      'planned'
    )
  ) not valid;

-- Ask PostgREST to refresh its schema cache.
notify pgrst, 'reload schema';

commit;

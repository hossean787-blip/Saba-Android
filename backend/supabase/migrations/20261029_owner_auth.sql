-- Saba auth identity reservation: execute in a dedicated Supabase project.
-- This schema stores no plaintext national IDs or SMS codes.
create extension if not exists pgcrypto;

create table if not exists public.owner_identity_reservations (
  id uuid primary key default gen_random_uuid(),
  auth_user_id uuid unique references auth.users(id) on delete cascade,
  national_id_hmac text not null unique,
  mobile_hmac text not null unique,
  created_at timestamptz not null default now(),
  verified_at timestamptz
);
create index if not exists owner_identity_reservations_created_at_idx
  on public.owner_identity_reservations(created_at);
alter table public.owner_identity_reservations enable row level security;
-- Deliberately NO client-facing RLS policy. Only trusted backend using service role
-- can reserve or read identifiers. Never ship service_role key in Android APK.
revoke all on public.owner_identity_reservations from anon, authenticated;

create table if not exists public.sms_challenges (
  id uuid primary key default gen_random_uuid(),
  reservation_id uuid not null references public.owner_identity_reservations(id) on delete cascade,
  code_digest text not null,
  expires_at timestamptz not null,
  resend_after timestamptz not null,
  attempts int not null default 0 check (attempts between 0 and 5),
  consumed_at timestamptz,
  created_at timestamptz not null default now()
);
create index if not exists sms_challenges_reservation_id_idx on public.sms_challenges(reservation_id);
alter table public.sms_challenges enable row level security;
revoke all on public.sms_challenges from anon, authenticated;

comment on table public.owner_identity_reservations is
'Only server-side HMACs of normalized Iranian national ID and mobile; requires secure server-side pepper/HMAC key and transactional insertion.';

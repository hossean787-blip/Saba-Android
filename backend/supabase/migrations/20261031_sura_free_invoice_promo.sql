-- Deployed to Supabase project fiiitzfbdjzpmepzjsjw.
-- One promo redemption per verified auth account AND per Android app-scoped device ID.
-- Raw IP and Android ID are NEVER stored; Edge Function supplies keyed HMAC digests.
create table if not exists public.sura_promo_redemptions (
 id bigint generated always as identity primary key,
 account_id uuid not null unique references auth.users(id) on delete restrict,
 device_fingerprint text not null unique,
 ip_fingerprint text,
 credits_granted integer not null default 3 check (credits_granted = 3),
 credits_remaining integer not null default 3 check (credits_remaining between 0 and 3),
 redeemed_at timestamptz not null default now()
);
create index if not exists sura_promo_ip_idx on public.sura_promo_redemptions(ip_fingerprint);
alter table public.sura_promo_redemptions enable row level security;
revoke all on public.sura_promo_redemptions from anon, authenticated, public;
grant select, insert, update on public.sura_promo_redemptions to service_role;
grant usage, select on sequence public.sura_promo_redemptions_id_seq to service_role;
create or replace function public.sura_redeem_promo(p_account uuid, p_device text, p_ip text)
returns jsonb language plpgsql security invoker set search_path=public as $$
begin
 if p_account is null or p_device !~ '^[a-f0-9]{64}$' or
 (p_ip is not null and p_ip !~ '^[a-f0-9]{64}$') then
  raise exception 'invalid_claim' using errcode='22023';
 end if;
 insert into public.sura_promo_redemptions(account_id, device_fingerprint, ip_fingerprint)
 values(p_account,p_device,p_ip);
 return jsonb_build_object('granted',3,'remaining',3);
exception when unique_violation then
 return jsonb_build_object('granted',0,'error','already_redeemed');
end $$;
revoke all on function public.sura_redeem_promo(uuid,text,text) from public,anon,authenticated;
grant execute on function public.sura_redeem_promo(uuid,text,text) to service_role;

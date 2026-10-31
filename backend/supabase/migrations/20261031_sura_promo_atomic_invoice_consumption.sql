-- Applied to Supabase project. This migration contains no promotional secret.
create table if not exists public.sura_promo_invoice_uses (
 id bigint generated always as identity primary key,
 redemption_id bigint not null references public.sura_promo_redemptions(id) on delete restrict,
 account_id uuid not null references auth.users(id) on delete restrict,
 invoice_key uuid not null,
 used_at timestamptz not null default now(),
 unique(account_id,invoice_key)
);
create index if not exists sura_promo_use_redemption_idx on public.sura_promo_invoice_uses(redemption_id);
alter table public.sura_promo_invoice_uses enable row level security;
revoke all on public.sura_promo_invoice_uses from public,anon,authenticated;
grant select,insert on public.sura_promo_invoice_uses to service_role;
grant usage,select on sequence public.sura_promo_invoice_uses_id_seq to service_role;
create or replace function public.sura_consume_promo_credit(p_account uuid,p_invoice_key uuid)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare claim public.sura_promo_redemptions%rowtype; existing bigint;
begin
 if p_account is null or p_invoice_key is null then return jsonb_build_object('error','invalid_request'); end if;
 -- Serialize all credits and replays for this account before checking for duplicate claims.
 select * into claim from public.sura_promo_redemptions where account_id=p_account for update;
 if not found then return jsonb_build_object('error','no_promo'); end if;
 select id into existing from public.sura_promo_invoice_uses where account_id=p_account and invoice_key=p_invoice_key;
 if existing is not null then
  return jsonb_build_object('consumed',false,'already_consumed',true,'remaining',claim.credits_remaining);
 end if;
 if claim.credits_remaining<1 then return jsonb_build_object('error','no_credit'); end if;
 insert into public.sura_promo_invoice_uses(redemption_id,account_id,invoice_key)
 values(claim.id,p_account,p_invoice_key);
 update public.sura_promo_redemptions set credits_remaining=credits_remaining-1 where id=claim.id;
 return jsonb_build_object('consumed',true,'remaining',claim.credits_remaining-1);
end $$;
revoke all on function public.sura_consume_promo_credit(uuid,uuid) from public,anon,authenticated;
grant execute on function public.sura_consume_promo_credit(uuid,uuid) to service_role;
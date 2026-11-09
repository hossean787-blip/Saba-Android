-- SURA admin credit correction. Applied to Supabase project fiiitzfbdjzpmepzjsjw on 2026-11-09.
-- Server-only execution; non-destructive and auditable.
create table if not exists public.sura_admin_credit_adjustments (
 request_id uuid primary key,
 actor_user_id uuid not null references auth.users(id),
 owner_id uuid not null references auth.users(id),
 local_taxpayer_id bigint,
 before_credits bigint not null check(before_credits>=0),
 after_credits bigint not null check(after_credits>=0),
 delta bigint not null,
 reason text not null check(char_length(reason) between 5 and 500),
 created_at timestamptz not null default now(),
 constraint sura_admin_adjustments_delta check(delta=after_credits-before_credits),
 constraint sura_admin_adjustments_target check(local_taxpayer_id is null or local_taxpayer_id>0)
);
create index if not exists sura_admin_adjustments_created_idx on public.sura_admin_credit_adjustments(created_at desc);
create index if not exists sura_admin_adjustments_owner_idx on public.sura_admin_credit_adjustments(owner_id,created_at desc);
alter table public.sura_admin_credit_adjustments enable row level security;
revoke all on public.sura_admin_credit_adjustments from public,anon,authenticated;
grant select,insert on public.sura_admin_credit_adjustments to service_role;
create or replace function public.sura_admin_set_credit_balance(
 p_owner uuid,p_taxpayer bigint,p_new_balance bigint,p_expected_balance bigint,
 p_reason text,p_request uuid,p_actor uuid
) returns jsonb language plpgsql security invoker
set search_path=public,pg_temp
as $fn$
declare v_balance bigint;v_existing public.sura_admin_credit_adjustments%rowtype;
begin
 if p_owner is null or p_actor is null or p_request is null or
    p_new_balance is null or p_expected_balance is null or
    p_new_balance<0 or p_expected_balance<0 or
    p_new_balance>1000000000 or p_expected_balance>1000000000 or
    (p_taxpayer is not null and p_taxpayer<=0) or
    p_reason is null or char_length(trim(p_reason)) not between 5 and 500
 then return jsonb_build_object('error','invalid_request');end if;
 if not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin')
 then return jsonb_build_object('error','not_admin');end if;
 if p_taxpayer is not null and not exists(select 1 from public.sura_taxpayer_wallets
 where owner_id=p_owner and local_taxpayer_id=p_taxpayer)
 then return jsonb_build_object('error','taxpayer_wallet_not_found');end if;
 insert into public.sura_owner_wallets(owner_id,credits) values(p_owner,0) on conflict(owner_id) do nothing;
 select credits into v_balance from public.sura_owner_wallets where owner_id=p_owner for update;
 select * into v_existing from public.sura_admin_credit_adjustments where request_id=p_request;
 if found then
   if v_existing.owner_id<>p_owner or v_existing.actor_user_id<>p_actor or
      v_existing.local_taxpayer_id is distinct from p_taxpayer or
      v_existing.after_credits<>p_new_balance or v_existing.before_credits<>p_expected_balance or
      v_existing.reason<>trim(p_reason)
   then return jsonb_build_object('error','idempotency_conflict');end if;
   return jsonb_build_object('status','already_applied','balance',v_existing.after_credits,'receipt',p_request);
 end if;
 if p_taxpayer is not null then
   select credits into v_balance from public.sura_taxpayer_wallets
   where owner_id=p_owner and local_taxpayer_id=p_taxpayer for update;
   if not found then return jsonb_build_object('error','taxpayer_wallet_not_found');end if;
 end if;
 if v_balance is distinct from p_expected_balance then
   return jsonb_build_object('error','balance_changed','current_balance',v_balance);
 end if;
 if p_new_balance=p_expected_balance then return jsonb_build_object('error','no_change');end if;
 if p_taxpayer is null then
   update public.sura_owner_wallets set credits=p_new_balance,updated_at=now() where owner_id=p_owner;
 else
   update public.sura_taxpayer_wallets set credits=p_new_balance,updated_at=now()
   where owner_id=p_owner and local_taxpayer_id=p_taxpayer;
 end if;
 insert into public.sura_admin_credit_adjustments
   (request_id,actor_user_id,owner_id,local_taxpayer_id,before_credits,after_credits,delta,reason)
 values(p_request,p_actor,p_owner,p_taxpayer,p_expected_balance,p_new_balance,p_new_balance-p_expected_balance,trim(p_reason));
 return jsonb_build_object('status','applied','balance',p_new_balance,'receipt',p_request);
end;
$fn$;
revoke all on function public.sura_admin_set_credit_balance(uuid,bigint,bigint,bigint,text,uuid,uuid) from public,anon,authenticated;
grant execute on function public.sura_admin_set_credit_balance(uuid,bigint,bigint,bigint,text,uuid,uuid) to service_role;
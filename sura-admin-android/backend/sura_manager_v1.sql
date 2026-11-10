-- SURA Manager v1.0: privileged, append-only audit and exact financial summary.
-- Apply via Supabase migration to the existing SURA project, never from an APK.
create table if not exists public.sura_manager_role_audit (
 request_id uuid primary key,
 actor_id uuid not null references auth.users(id),
 target_id uuid not null references auth.users(id),
 role_before text,
 role_after text not null,
 reason text not null check(char_length(reason) between 5 and 500),
 created_at timestamptz not null default now(),
 constraint sura_manager_audit_roles_valid check(
   (role_before is null or role_before in ('admin','editor','viewer')) and
   role_after in ('admin','editor','viewer','none'))
);
create index if not exists sura_manager_role_audit_date_idx on public.sura_manager_role_audit(created_at desc);
create table if not exists public.sura_manager_price_audit (
 request_id uuid primary key,
 actor_id uuid not null references auth.users(id),
 old_price_rial bigint not null check(old_price_rial>0),
 new_price_rial bigint not null check(new_price_rial>0),
 reason text not null check(char_length(reason) between 5 and 500),
 created_at timestamptz not null default now()
);
create index if not exists sura_manager_price_audit_date_idx on public.sura_manager_price_audit(created_at desc);
alter table public.sura_manager_role_audit enable row level security;
alter table public.sura_manager_price_audit enable row level security;
revoke all on public.sura_manager_role_audit, public.sura_manager_price_audit from public, anon, authenticated;
grant select,insert on public.sura_manager_role_audit,public.sura_manager_price_audit to service_role;

create or replace function public.sura_manager_assign_role(
 p_actor uuid,p_target uuid,p_role text,p_expected text,p_reason text,p_request uuid
) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $body$
declare previous_role text; prior public.sura_manager_role_audit%rowtype;
begin
 if p_actor is null or p_target is null or p_request is null
    or p_role not in ('admin','editor','viewer','none')
    or p_reason is null or char_length(trim(p_reason)) not between 5 and 500
    or (p_expected is not null and p_expected not in ('admin','editor','viewer'))
 then return jsonb_build_object('error','invalid_role_request'); end if;
 perform pg_advisory_xact_lock(153813,942031);
 if not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin')
 then return jsonb_build_object('error','insufficient_role');end if;
 select * into prior from public.sura_manager_role_audit where request_id=p_request;
 if found then
   if prior.actor_id<>p_actor or prior.target_id<>p_target or prior.role_after<>p_role or prior.reason<>trim(p_reason)
   then return jsonb_build_object('error','idempotency_conflict');end if;
   return jsonb_build_object('status','already_applied','role',prior.role_after,'receipt',p_request);
 end if;
 select role into previous_role from public.sura_admin_roles where user_id=p_target for update;
 if previous_role is distinct from p_expected
 then return jsonb_build_object('error','role_changed','current_role',previous_role);end if;
 if previous_role is not distinct from nullif(p_role,'none')
 then return jsonb_build_object('error','no_change');end if;
 if p_target=p_actor and previous_role='admin' and p_role<>'admin'
 then return jsonb_build_object('error','self_demote_denied');end if;
 if previous_role='admin' and p_role<>'admin' and
   (select count(*) from public.sura_admin_roles where role='admin')<=1
 then return jsonb_build_object('error','last_admin_denied');end if;
 if p_role='none' then
   delete from public.sura_admin_roles where user_id=p_target;
 else
   insert into public.sura_admin_roles(user_id,role) values(p_target,p_role)
   on conflict(user_id) do update set role=excluded.role, updated_at=now();
 end if;
 insert into public.sura_manager_role_audit(request_id,actor_id,target_id,role_before,role_after,reason)
 values(p_request,p_actor,p_target,previous_role,p_role,trim(p_reason));
 return jsonb_build_object('status','applied','role',p_role,'receipt',p_request);
end $body$;

create or replace function public.sura_manager_set_price(
 p_actor uuid,p_expected bigint,p_new bigint,p_reason text,p_request uuid
) returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $body$
declare v_old bigint; prior public.sura_manager_price_audit%rowtype;
begin
 if p_actor is null or p_request is null or p_expected is null or p_new is null or
    p_expected<1000 or p_new<1000 or p_new>1000000000 or
    p_reason is null or char_length(trim(p_reason)) not between 5 and 500
 then return jsonb_build_object('error','invalid_price_request');end if;
 if not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin')
 then return jsonb_build_object('error','insufficient_role');end if;
 select price_per_invoice_rial into v_old from public.sura_credit_pricing where id=1 for update;
 select * into prior from public.sura_manager_price_audit where request_id=p_request;
 if found then
   if prior.actor_id<>p_actor or prior.old_price_rial<>p_expected or prior.new_price_rial<>p_new or prior.reason<>trim(p_reason)
   then return jsonb_build_object('error','idempotency_conflict');end if;
   return jsonb_build_object('status','already_applied','price',prior.new_price_rial,'receipt',p_request);
 end if;
 if v_old is null then return jsonb_build_object('error','not_found');end if;
 if v_old<>p_expected then return jsonb_build_object('error','price_changed','current_price',v_old);end if;
 if v_old=p_new then return jsonb_build_object('error','no_change');end if;
 update public.sura_credit_pricing set price_per_invoice_rial=p_new,currency='IRR',updated_at=now() where id=1;
 insert into public.sura_manager_price_audit(request_id,actor_id,old_price_rial,new_price_rial,reason)
 values(p_request,p_actor,p_expected,p_new,trim(p_reason));
 return jsonb_build_object('status','applied','price',p_new,'receipt',p_request);
end $body$;

create or replace function public.sura_manager_summary()
returns jsonb language sql stable security invoker set search_path=public,pg_temp as $body$
 select jsonb_build_object(
  'verified_revenue_rial',(select coalesce(sum(amount_rial),0)::text from public.sura_payment_orders where state='verified'),
  'verified_orders',(select count(*) from public.sura_payment_orders where state='verified'),
  'requested_orders',(select count(*) from public.sura_payment_orders where state in ('created','requested')),
  'failed_orders',(select count(*) from public.sura_payment_orders where state='failed'),
  'credits_sold',(select coalesce(sum(credit_units),0)::text from public.sura_payment_orders where state='verified'),
  'owner_wallets',(select count(*) from public.sura_owner_wallets),
  'taxpayer_wallets',(select count(*) from public.sura_taxpayer_wallets),
  'owner_credit_balance',(select coalesce(sum(credits),0)::text from public.sura_owner_wallets),
  'taxpayer_credit_balance',(select coalesce(sum(credits),0)::text from public.sura_taxpayer_wallets),
  'paid_invoice_uses',(select count(*) from public.sura_paid_invoice_uses),
  'free_invoice_uses',(select count(*) from public.sura_promo_invoice_uses),
  'promo_redemptions',(select count(*) from public.sura_promo_redemptions),
  'promo_remaining',(select coalesce(sum(credits_remaining),0)::text from public.sura_promo_redemptions),
  'manual_adjustments',(select count(*) from public.sura_admin_credit_adjustments),
  'base_price_rial',(select price_per_invoice_rial::text from public.sura_credit_pricing where id=1),
  'generated_at',now()
 );
$body$;
revoke all on function public.sura_manager_assign_role(uuid,uuid,text,text,text,uuid) from public,anon,authenticated;
revoke all on function public.sura_manager_set_price(uuid,bigint,bigint,text,uuid) from public,anon,authenticated;
revoke all on function public.sura_manager_summary() from public,anon,authenticated;
grant execute on function public.sura_manager_assign_role(uuid,uuid,text,text,text,uuid) to service_role;
grant execute on function public.sura_manager_set_price(uuid,bigint,bigint,text,uuid) to service_role;
grant execute on function public.sura_manager_summary() to service_role;

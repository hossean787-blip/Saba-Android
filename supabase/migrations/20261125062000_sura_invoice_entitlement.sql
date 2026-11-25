-- Invoice-only combined entitlement; permanent wallet transfers never see monthly quota.
create or replace function public.sura_invoice_entitlement_balance(p_owner uuid,p_taxpayer bigint)
returns bigint language sql stable security invoker set search_path='' as $$
 select coalesce((select credits from public.sura_taxpayer_wallets where owner_id=p_owner and local_taxpayer_id=p_taxpayer),0)+(public.sura_monthly_status(p_owner)->>'remaining')::bigint
$$;
create or replace function public.sura_consume_taxpayer_credit(p_owner uuid,p_taxpayer bigint,p_invoice_key uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare v_balance bigint; v_existing public.sura_paid_invoice_uses%rowtype;
begin
 if p_owner is null or p_taxpayer is null or p_taxpayer<=0 or p_invoice_key is null then return jsonb_build_object('error','invalid_request');end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190526));
 if exists(select 1 from public.sura_monthly_uses where owner_id=p_owner and invoice_key=p_invoice_key) then return '{"error":"idempotency_conflict"}';end if;
 select credits into v_balance from public.sura_taxpayer_wallets where owner_id=p_owner and local_taxpayer_id=p_taxpayer for update;
 select * into v_existing from public.sura_paid_invoice_uses where owner_id=p_owner and invoice_key=p_invoice_key;
 if found then
  if v_existing.local_taxpayer_id<>p_taxpayer then return '{"error":"idempotency_conflict"}';end if;
  return jsonb_build_object('consumed',false,'already_consumed',true,'remaining',coalesce(v_balance,0));
 end if;
 if coalesce(v_balance,0)<1 then return jsonb_build_object('error','no_credit','remaining',coalesce(v_balance,0));end if;
 update public.sura_taxpayer_wallets set credits=credits-1,updated_at=now() where owner_id=p_owner and local_taxpayer_id=p_taxpayer and credits>0;
 if not found then return '{"error":"no_credit"}';end if;
 insert into public.sura_paid_invoice_uses(owner_id,local_taxpayer_id,invoice_key) values(p_owner,p_taxpayer,p_invoice_key);
 return jsonb_build_object('consumed',true,'remaining',v_balance-1);
end $$;
create or replace function public.sura_consume_invoice_entitlement(p_owner uuid,p_taxpayer bigint,p_invoice_key uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare r jsonb;
begin
 if p_owner is null or p_taxpayer is null or p_taxpayer<=0 or p_invoice_key is null then return '{"error":"invalid_request"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190526));
 if exists(select 1 from public.sura_paid_invoice_uses where owner_id=p_owner and invoice_key=p_invoice_key) then
  r:=public.sura_consume_taxpayer_credit(p_owner,p_taxpayer,p_invoice_key);
 else
  r:=public.sura_consume_monthly(p_owner,p_taxpayer,p_invoice_key);
  if r->>'error'='no_monthly_credit' then r:=public.sura_consume_taxpayer_credit(p_owner,p_taxpayer,p_invoice_key);end if;
 end if;
 if r?'error' then return r;end if;
 return r||jsonb_build_object('remaining',public.sura_invoice_entitlement_balance(p_owner,p_taxpayer));
end $$;
-- Price confirmation is enforced in the same reservation transaction.
create or replace function public.sura_reserve_monthly_order(p_owner uuid,p_expected_total bigint)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare q jsonb;
begin
 if p_owner is null or p_expected_total is null or p_expected_total<=0 then return '{"error":"invalid_request"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190526));
 perform 1 from public.sura_monthly_plan where id=1 for share;
 q:=public.sura_monthly_quote();
 if not coalesce((q->>'enabled')::boolean,false) then return '{"error":"monthly_not_enabled"}';end if;
 if (q->>'total_rial')::bigint is distinct from p_expected_total then return '{"error":"price_changed"}';end if;
 if exists(select 1 from public.sura_payment_orders where owner_user_id=p_owner and state='requested') then return '{"error":"pending_order"}';end if;
 return public.sura_create_monthly_order(p_owner);
end $$;
revoke all on function public.sura_invoice_entitlement_balance(uuid,bigint),public.sura_consume_invoice_entitlement(uuid,bigint,uuid),public.sura_reserve_monthly_order(uuid,bigint) from public,anon,authenticated;
grant execute on function public.sura_invoice_entitlement_balance(uuid,bigint),public.sura_consume_invoice_entitlement(uuid,bigint,uuid),public.sura_reserve_monthly_order(uuid,bigint) to service_role;

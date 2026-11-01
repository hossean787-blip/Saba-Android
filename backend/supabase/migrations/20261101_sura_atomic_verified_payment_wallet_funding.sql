-- Only previously verified server-gateway payments may fund the owner wallet.
-- This replaces the earlier settlement transaction before any merchant is enabled.
create or replace function public.sura_settle_verified_payment(
 p_order_id uuid, p_authority text, p_ref_id text
) returns boolean language plpgsql security invoker
set search_path = public, pg_temp as $$
declare v public.sura_payment_orders%rowtype;
begin
 if p_order_id is null or p_authority is null or nullif(trim(p_ref_id),'') is null then
  raise exception 'Invalid settlement inputs';
 end if;
 select * into v from public.sura_payment_orders where id=p_order_id for update;
 if not found or v.authority is distinct from p_authority then
  raise exception 'Order authority mismatch';
 end if;
 if v.state='verified' then
  if v.ref_id is distinct from p_ref_id then raise exception 'Reference mismatch'; end if;
  return false;
 end if;
 if v.state<>'requested' then raise exception 'Order cannot be settled'; end if;
 insert into public.sura_owner_wallets(owner_id,credits) values(v.owner_user_id,0)
 on conflict(owner_id) do nothing;
 update public.sura_payment_orders
 set state='verified',ref_id=p_ref_id,verified_at=now() where id=p_order_id;
 insert into public.sura_credit_grants(order_id,owner_user_id,units)
 values(p_order_id,v.owner_user_id,v.credit_units);
 update public.sura_owner_wallets set credits=credits+v.credit_units,updated_at=now()
 where owner_id=v.owner_user_id;
 return true;
end $$;
revoke all on function public.sura_settle_verified_payment(uuid,text,text) from public,anon,authenticated;
grant execute on function public.sura_settle_verified_payment(uuid,text,text) to service_role;
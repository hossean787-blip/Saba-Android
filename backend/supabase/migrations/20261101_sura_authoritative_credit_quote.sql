create or replace function public.sura_quote_credits(p_quantity bigint)
returns jsonb language plpgsql security invoker set search_path=public as $$
declare v_price bigint;
begin
 if p_quantity is null or p_quantity<1 or p_quantity>999999 then
  return jsonb_build_object('error','invalid_quantity');
 end if;
 select price_per_invoice_rial into v_price from public.sura_credit_pricing where id=1 and currency='IRR';
 if v_price is null then return jsonb_build_object('error','price_unavailable'); end if;
 return jsonb_build_object('quantity',p_quantity,'unit_price_rial',v_price,'total_rial',v_price*p_quantity,'currency','IRR');
end $$;
revoke all on function public.sura_quote_credits(bigint) from public,anon,authenticated;
grant execute on function public.sura_quote_credits(bigint) to service_role;
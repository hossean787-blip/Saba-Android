-- LIVE DEPLOYED 2026-11-02 — server-side paid invoice credit consumption
create table if not exists public.sura_paid_invoice_uses (
 owner_id uuid not null,
 local_taxpayer_id bigint not null check (local_taxpayer_id > 0),
 invoice_key uuid not null,
 used_at timestamptz not null default now(),
 primary key(owner_id,invoice_key)
);
alter table public.sura_paid_invoice_uses enable row level security;
revoke all on public.sura_paid_invoice_uses from public,anon,authenticated;
create or replace function public.sura_consume_taxpayer_credit(
 p_owner uuid,p_taxpayer bigint,p_invoice_key uuid
) returns jsonb language plpgsql set search_path=public as $func$
declare v_balance bigint; v_existing public.sura_paid_invoice_uses%rowtype;
begin
 if p_owner is null or p_taxpayer is null or p_taxpayer<=0 or p_invoice_key is null then return jsonb_build_object('error','invalid_request'); end if;
 select credits into v_balance from public.sura_taxpayer_wallets where owner_id=p_owner and local_taxpayer_id=p_taxpayer for update;
 select * into v_existing from public.sura_paid_invoice_uses where owner_id=p_owner and invoice_key=p_invoice_key;
 if found then
  if v_existing.local_taxpayer_id<>p_taxpayer then return jsonb_build_object('error','idempotency_conflict'); end if;
  return jsonb_build_object('consumed',false,'already_consumed',true,'remaining',coalesce(v_balance,0));
 end if;
 if coalesce(v_balance,0)<1 then return jsonb_build_object('error','no_credit','remaining',coalesce(v_balance,0)); end if;
 update public.sura_taxpayer_wallets set credits=credits-1,updated_at=now()
 where owner_id=p_owner and local_taxpayer_id=p_taxpayer and credits>0;
 if not found then return jsonb_build_object('error','no_credit'); end if;
 insert into public.sura_paid_invoice_uses(owner_id,local_taxpayer_id,invoice_key) values(p_owner,p_taxpayer,p_invoice_key);
 return jsonb_build_object('consumed',true,'remaining',v_balance-1);
end $func$;
revoke execute on function public.sura_consume_taxpayer_credit(uuid,bigint,uuid) from public,anon,authenticated;
grant execute on function public.sura_consume_taxpayer_credit(uuid,bigint,uuid) to service_role;

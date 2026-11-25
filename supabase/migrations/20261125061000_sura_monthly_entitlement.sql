-- Monthly entitlement foundation. Public checkout remains disabled until price and gateway approval.
alter table public.sura_payment_orders add column if not exists purchase_kind text not null default 'single' check (purchase_kind in ('single','monthly'));
create table if not exists public.sura_monthly_plan (
 id integer primary key check(id=1), price_rial bigint check(price_rial between 1 and 10000000000),
 enabled boolean not null default false, check(not enabled or price_rial is not null)
);
insert into public.sura_monthly_plan(id) values(1) on conflict do nothing;
create table if not exists public.sura_monthly_periods (
 order_id uuid primary key references public.sura_payment_orders(id),
 owner_id uuid not null references auth.users(id),
 starts_at timestamptz not null, ends_at timestamptz not null,
 used integer not null default 0 check(used between 0 and 20),
 check(ends_at=starts_at+interval '720 hours')
);
create index if not exists sura_monthly_period_owner_idx on public.sura_monthly_periods(owner_id,ends_at);
create table if not exists public.sura_monthly_uses (
 owner_id uuid not null references auth.users(id), invoice_key uuid not null,
 local_taxpayer_id bigint not null check(local_taxpayer_id>0),
 order_id uuid not null references public.sura_monthly_periods(order_id),
 used_at timestamptz not null default now(),primary key(owner_id,invoice_key)
);
alter table public.sura_monthly_plan enable row level security;
alter table public.sura_monthly_periods enable row level security;
alter table public.sura_monthly_uses enable row level security;
revoke all on public.sura_monthly_plan,public.sura_monthly_periods,public.sura_monthly_uses from public,anon,authenticated;
grant select,insert,update on public.sura_monthly_plan,public.sura_monthly_periods,public.sura_monthly_uses to service_role;
create or replace function public.sura_monthly_quote() returns jsonb language sql stable security invoker set search_path='' as $$
 select jsonb_build_object('enabled',enabled,'quantity',20,'duration_days',30,'currency','IRR',
 'subtotal_rial',price_rial,'vat_rate_percent',10,'vat_rial',(price_rial*10+50)/100,
 'total_rial',price_rial+(price_rial*10+50)/100) from public.sura_monthly_plan where id=1
$$;
create or replace function public.sura_create_monthly_order(p_owner uuid) returns jsonb language plpgsql security invoker set search_path='' as $$
declare q jsonb; oid uuid;
begin
 if p_owner is null then return '{"error":"invalid_owner"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190526));
 q:=public.sura_monthly_quote();
 if not coalesce((q->>'enabled')::boolean,false) then return '{"error":"monthly_not_enabled"}';end if;
 if exists(select 1 from public.sura_payment_orders where owner_user_id=p_owner and purchase_kind='monthly' and state in ('created','requested')) then return '{"error":"pending_order"}';end if;
 insert into public.sura_payment_orders(owner_user_id,amount_rial,credit_units,state,purchase_kind)
 values(p_owner,(q->>'total_rial')::bigint,20,'created','monthly') returning id into oid;
 return jsonb_build_object('id',oid,'amount_rial',(q->>'total_rial')::bigint,'credit_units',20);
end $$;
-- Preserve single-credit settlement; monthly payment only opens a time-limited period.
create or replace function public.sura_settle_verified_payment(p_order_id uuid,p_authority text,p_ref_id text)
returns boolean language plpgsql security invoker set search_path='' as $$
declare v public.sura_payment_orders%rowtype; start_at timestamptz;
begin
 if p_order_id is null or p_authority is null or nullif(trim(p_ref_id),'') is null then raise exception 'Invalid settlement inputs';end if;
 select * into v from public.sura_payment_orders where id=p_order_id for update;
 if not found or v.authority is distinct from p_authority then raise exception 'Order authority mismatch';end if;
 if v.state='verified' then
  if v.ref_id is distinct from p_ref_id then raise exception 'Reference mismatch';end if;
  return false;
 end if;
 if v.state<>'requested' then raise exception 'Order cannot be settled';end if;
 if v.purchase_kind='monthly' then
  if v.credit_units<>20 then raise exception 'Invalid monthly quota';end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v.owner_user_id::text,190526));
  select greatest(now(),coalesce(max(ends_at),now())) into start_at from public.sura_monthly_periods where owner_id=v.owner_user_id;
  insert into public.sura_monthly_periods(order_id,owner_id,starts_at,ends_at) values(v.id,v.owner_user_id,start_at,start_at+interval '720 hours');
  update public.sura_payment_orders set state='verified',ref_id=p_ref_id,verified_at=now() where id=v.id;
  return true;
 end if;
 insert into public.sura_owner_wallets(owner_id,credits) values(v.owner_user_id,0) on conflict(owner_id) do nothing;
 update public.sura_payment_orders set state='verified',ref_id=p_ref_id,verified_at=now() where id=v.id;
 insert into public.sura_credit_grants(order_id,owner_user_id,units) values(v.id,v.owner_user_id,v.credit_units);
 update public.sura_owner_wallets set credits=credits+v.credit_units,updated_at=now() where owner_id=v.owner_user_id;
 return true;
end $$;
create or replace function public.sura_monthly_status(p_owner uuid) returns jsonb language sql stable security invoker set search_path='' as $$
 select coalesce((select jsonb_build_object('remaining',20-used,'starts_at',starts_at,'ends_at',ends_at)
 from public.sura_monthly_periods where owner_id=p_owner and starts_at<=now() and ends_at>now() order by starts_at limit 1),'{"remaining":0,"starts_at":null,"ends_at":null}'::jsonb)
$$;
create or replace function public.sura_consume_monthly(p_owner uuid,p_taxpayer bigint,p_invoice_key uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare v public.sura_monthly_periods%rowtype; prior public.sura_monthly_uses%rowtype;
begin
 if p_owner is null or p_taxpayer is null or p_taxpayer<=0 or p_invoice_key is null then return '{"error":"invalid_request"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190526));
 select * into prior from public.sura_monthly_uses where owner_id=p_owner and invoice_key=p_invoice_key;
 if found then
  if prior.local_taxpayer_id<>p_taxpayer then return '{"error":"idempotency_conflict"}';end if;
  return jsonb_build_object('consumed',false,'already_consumed',true,'remaining',(public.sura_monthly_status(p_owner)->>'remaining')::integer);
 end if;
 if exists(select 1 from public.sura_paid_invoice_uses where owner_id=p_owner and invoice_key=p_invoice_key) then return '{"error":"idempotency_conflict"}';end if;
 select * into v from public.sura_monthly_periods where owner_id=p_owner and starts_at<=now() and ends_at>now() and used<20 order by starts_at limit 1 for update;
 if not found then return '{"error":"no_monthly_credit","remaining":0}';end if;
 insert into public.sura_monthly_uses(owner_id,invoice_key,local_taxpayer_id,order_id) values(p_owner,p_invoice_key,p_taxpayer,v.order_id);
 update public.sura_monthly_periods set used=used+1 where order_id=v.order_id;
 return jsonb_build_object('consumed',true,'remaining',19-v.used);
end $$;
revoke all on function public.sura_monthly_quote(),public.sura_create_monthly_order(uuid),public.sura_monthly_status(uuid),public.sura_consume_monthly(uuid,bigint,uuid),public.sura_settle_verified_payment(uuid,text,text) from public,anon,authenticated;
grant execute on function public.sura_monthly_quote(),public.sura_create_monthly_order(uuid),public.sura_monthly_status(uuid),public.sura_consume_monthly(uuid,bigint,uuid),public.sura_settle_verified_payment(uuid,text,text) to service_role;

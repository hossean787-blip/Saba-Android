-- ZarinPal settlement ledger. Deploy only in the dedicated SURA Supabase project.
-- The client cannot add credit. Only a trusted server with service role
-- can create purchases and settle verified payments.
create table if not exists public.sura_payment_orders (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid not null references auth.users(id),
  gateway text not null default 'zarinpal' check (gateway = 'zarinpal'),
  authority text unique,
  amount_rial bigint not null check (amount_rial > 0),
  credit_units bigint not null check (credit_units > 0),
  state text not null default 'created'
    check (state in ('created','requested','verified','failed')),
  ref_id text unique,
  created_at timestamptz not null default now(),
  verified_at timestamptz,
  check (state <> 'verified' or (ref_id is not null and verified_at is not null))
);
create index if not exists sura_payment_orders_owner_idx
  on public.sura_payment_orders(owner_user_id, created_at desc);
create table if not exists public.sura_credit_grants (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.sura_payment_orders(id),
  owner_user_id uuid not null references auth.users(id),
  units bigint not null check (units > 0),
  created_at timestamptz not null default now()
);
alter table public.sura_payment_orders enable row level security;
alter table public.sura_credit_grants enable row level security;
revoke all on public.sura_payment_orders from anon, authenticated;
revoke all on public.sura_credit_grants from anon, authenticated;

-- One-transaction idempotent settlement invoked ONLY after server-side
-- verification of merchant_id, authority, original amount and order ownership.
create or replace function public.sura_settle_verified_payment(
  p_order_id uuid, p_authority text, p_ref_id text
) returns boolean language plpgsql security definer
set search_path = public, pg_temp as $$
declare v public.sura_payment_orders%rowtype;
begin
  select * into v from public.sura_payment_orders where id = p_order_id for update;
  if not found or v.authority is distinct from p_authority then
    raise exception 'Order authority mismatch';
  end if;
  if v.state = 'verified' then
    if v.ref_id is distinct from p_ref_id then raise exception 'Reference mismatch'; end if;
    return false;
  end if;
  if v.state <> 'requested' or p_ref_id is null or length(p_ref_id) < 1 then
    raise exception 'Order cannot be settled';
  end if;
  update public.sura_payment_orders
     set state = 'verified', ref_id = p_ref_id, verified_at = now()
   where id = p_order_id;
  insert into public.sura_credit_grants(order_id, owner_user_id, units)
  values (p_order_id, v.owner_user_id, v.credit_units);
  return true;
end $$;
revoke all on function public.sura_settle_verified_payment(uuid,text,text) from public, anon, authenticated;
-- Grant execute only to the server's service_role if supported by the environment.
grant execute on function public.sura_settle_verified_payment(uuid,text,text) to service_role;

-- Secret value is installed exclusively by a private SQL operation in Supabase.
-- Never place the promotional code or its hashed credential in this repository.
create table if not exists public.sura_promo_secrets (
 id integer primary key check (id = 1),
 code_hash text not null
);
alter table public.sura_promo_secrets enable row level security;
revoke all on public.sura_promo_secrets from public, anon, authenticated;
grant select on public.sura_promo_secrets to service_role;
create or replace function public.sura_validate_promo(p_code text)
returns boolean language sql security invoker set search_path = public, extensions as $$
 select exists (
   select 1 from public.sura_promo_secrets where id = 1
   and code_hash = extensions.crypt(p_code, code_hash)
 );
$$;
revoke all on function public.sura_validate_promo(text) from public, anon, authenticated;
grant execute on function public.sura_validate_promo(text) to service_role;

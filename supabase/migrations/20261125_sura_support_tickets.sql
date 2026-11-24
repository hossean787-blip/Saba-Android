-- Support tickets: no user-controlled owner, status or response.
create table public.sura_support_tickets (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  category text not null check (category in ('ارسال صورتحساب','ورود و حساب کاربری','اعتبار و پرداخت','مشکل فنی','حریم خصوصی و اطلاعات')),
  message text not null check (char_length(btrim(message)) between 10 and 2000),
  app_version text not null default '' check (char_length(app_version)<=40),
  status text not null default 'open' check (status in ('open','in_progress','answered','closed')),
  response text check (response is null or char_length(response)<=4000),
  created_at timestamptz not null default now()
);
alter table public.sura_support_tickets enable row level security;
revoke all on table public.sura_support_tickets from public, anon, authenticated;
grant select on table public.sura_support_tickets to authenticated;
grant insert(id,category,message,app_version) on table public.sura_support_tickets to authenticated;
grant select,insert,update,delete on table public.sura_support_tickets to service_role;
create policy sura_support_read_own on public.sura_support_tickets for select to authenticated
using ((select auth.uid())=owner_id);
create policy sura_support_insert_own on public.sura_support_tickets for insert to authenticated
with check ((select auth.uid())=owner_id and status='open' and response is null);
create index sura_support_owner_created_idx on public.sura_support_tickets(owner_id,created_at desc);
create function public.sura_support_check_rate() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
  if new.owner_id is null then raise exception 'Authentication required'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.owner_id::text,1542));
  if (select count(*) from public.sura_support_tickets
      where owner_id=new.owner_id and created_at>=now()-interval '24 hours')>=10 then
    raise exception 'Support request limit reached' using errcode='P0001';
  end if;
  return new;
end;
$$;
revoke all on function public.sura_support_check_rate() from public,anon,authenticated;
create trigger sura_support_rate before insert on public.sura_support_tickets
for each row execute function public.sura_support_check_rate();

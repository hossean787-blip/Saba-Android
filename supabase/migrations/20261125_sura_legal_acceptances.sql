-- New registrations only; existing accounts and logins are not modified.
create table public.sura_legal_acceptances (
 owner_id uuid not null references auth.users(id) on delete cascade,
 legal_version text not null,
 accepted_at timestamptz not null default clock_timestamp(),
 primary key(owner_id,legal_version)
);
alter table public.sura_legal_acceptances enable row level security;
revoke all on public.sura_legal_acceptances from public,anon,authenticated;
grant select on public.sura_legal_acceptances to authenticated,service_role;
create policy sura_legal_read_own on public.sura_legal_acceptances for select to authenticated
using ((select auth.uid())=owner_id);
create schema if not exists sura_private;
revoke all on schema sura_private from public,anon,authenticated;
create function sura_private.record_signup_consent() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.raw_user_meta_data->'sura_legal_accepted' is distinct from 'true'::jsonb
 or new.raw_user_meta_data->>'sura_legal_version' is distinct from '2026-11-25.1' then
   raise exception 'SURA legal consent required' using errcode='23514';
 end if;
 insert into public.sura_legal_acceptances(owner_id,legal_version) values(new.id,'2026-11-25.1');
 return new;
end;
$$;
revoke all on function sura_private.record_signup_consent() from public,anon,authenticated;
create trigger sura_signup_consent after insert on auth.users
for each row execute function sura_private.record_signup_consent();

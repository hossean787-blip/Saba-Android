create table public.sura_integrity_challenges (
  owner_id uuid primary key references auth.users(id) on delete cascade,
  session_hash text not null check (session_hash ~ '^[0-9a-f]{64}$'),
  nonce_hash text not null check (nonce_hash ~ '^[0-9a-f]{64}$'),
  issued_at timestamptz not null default clock_timestamp(),
  expires_at timestamptz not null,
  used_at timestamptz
);
alter table public.sura_integrity_challenges enable row level security;
revoke all on public.sura_integrity_challenges from public, anon, authenticated;
grant select, insert, update, delete on public.sura_integrity_challenges to service_role;
create function public.sura_integrity_issue(p_owner uuid,p_session text,p_nonce text)
returns boolean language sql security invoker set search_path = '' as $$
  with issued as (
    insert into public.sura_integrity_challenges(owner_id,session_hash,nonce_hash,issued_at,expires_at,used_at)
    values (p_owner,p_session,p_nonce,clock_timestamp(),clock_timestamp()+interval '3 minutes',null)
    on conflict(owner_id) do update set session_hash=excluded.session_hash,nonce_hash=excluded.nonce_hash,
      issued_at=excluded.issued_at,expires_at=excluded.expires_at,used_at=null
    where public.sura_integrity_challenges.issued_at < clock_timestamp()-interval '30 seconds'
    returning 1
  ) select exists(select 1 from issued);
$$;
create function public.sura_integrity_consume(p_owner uuid,p_session text,p_nonce text)
returns boolean language sql security invoker set search_path = '' as $$
  with consumed as (
    update public.sura_integrity_challenges set used_at=clock_timestamp()
    where owner_id=p_owner and session_hash=p_session and nonce_hash=p_nonce
      and used_at is null and expires_at > clock_timestamp()
    returning 1
  ) select exists(select 1 from consumed);
$$;
revoke all on function public.sura_integrity_issue(uuid,text,text) from public,anon,authenticated;
revoke all on function public.sura_integrity_consume(uuid,text,text) from public,anon,authenticated;
grant execute on function public.sura_integrity_issue(uuid,text,text) to service_role;
grant execute on function public.sura_integrity_consume(uuid,text,text) to service_role;

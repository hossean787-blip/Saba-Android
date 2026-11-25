-- Public nonpersonal reference data, immutable revisions. No rows until a verified source is supplied.
create table public.sura_starter_catalog(
 version bigint primary key check(version between 1 and 1000000000),
 source_hash text not null check(source_hash ~ '^[a-f0-9]{64}$'),
 as_of date not null,
 entries jsonb not null check(jsonb_typeof(entries)='array' and jsonb_array_length(entries) between 150 and 300),
 published_at timestamptz not null default now()
);
alter table public.sura_starter_catalog enable row level security;
revoke all on public.sura_starter_catalog from public,anon,authenticated;
grant select(version,source_hash,as_of,entries) on public.sura_starter_catalog to anon,authenticated;
create policy starter_read on public.sura_starter_catalog for select to anon,authenticated using(true);
grant select,insert on public.sura_starter_catalog to service_role;
create function public.sura_publish_starter(p_actor uuid,p_expected bigint,p_hash text,p_as_of date,p_entries jsonb)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare cur bigint; e jsonb; g text; n integer;
begin
 if p_actor is distinct from 'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
 if p_hash is null or p_hash !~ '^[a-f0-9]{64}$' or p_as_of is null or p_as_of>current_date or jsonb_typeof(p_entries) is distinct from 'array' then return '{"error":"invalid_source"}';end if;
 if jsonb_array_length(p_entries) not between 150 and 300 or pg_column_size(p_entries)>900000 then return '{"error":"invalid_count"}';end if;
 for e in select value from jsonb_array_elements(p_entries) loop
  if jsonb_typeof(e) is distinct from 'object' or
  coalesce(e->>'id','') !~ '^[0-9]{13}$' or coalesce(e->>'group','') not in ('accounting','computer','technical','auto','other') or
  coalesce(char_length(btrim(e->>'description')),0) not between 1 and 1000 or
  coalesce(e->>'kind','') not like '%عموم%' or char_length(e->>'kind')>100 or
  coalesce(char_length(btrim(e->>'source')),0) not between 1 and 100 or
  e->>'vat' is null or e->>'taxable' is null or e->>'expiration_date' is null or
  char_length(e->>'taxable')>100 or char_length(e->>'expiration_date')>50 or
  (e->>'vat'<>'' and (e->>'vat') !~ '^[0-9]{1,3}(\.[0-9]{1,4})?$') then return '{"error":"invalid_row"}';end if;
  if e->>'vat'<>'' and (e->>'vat')::numeric>100 then return '{"error":"invalid_vat"}';end if;
 end loop;
 if (select count(distinct x->>'id') from jsonb_array_elements(p_entries) x)<>jsonb_array_length(p_entries) then return '{"error":"duplicate_id"}';end if;
 for g,n in select * from (values ('accounting',25),('computer',30),('technical',30),('auto',40),('other',25)) v(g,n) loop
  if (select count(*) from jsonb_array_elements(p_entries) x where x->>'group'=g)<n then return '{"error":"group_shortfall"}';end if;
 end loop;
 perform pg_catalog.pg_advisory_xact_lock(501503);
 select coalesce(max(version),0) into cur from public.sura_starter_catalog;
 if cur is distinct from p_expected then return jsonb_build_object('error','version_conflict','version',cur);end if;
 insert into public.sura_starter_catalog(version,source_hash,as_of,entries) values(cur+1,p_hash,p_as_of,p_entries);
 return jsonb_build_object('status','published','version',cur+1);
end $$;
revoke all on function public.sura_publish_starter(uuid,bigint,text,date,jsonb) from public,anon,authenticated;
grant execute on function public.sura_publish_starter(uuid,bigint,text,date,jsonb) to service_role;

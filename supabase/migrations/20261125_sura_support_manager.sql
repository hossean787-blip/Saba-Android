-- Support operations: only authenticated server-verified superadmin through service role.
create table public.sura_support_manager_audit (
 request_id uuid primary key,
 actor_id uuid not null,
 ticket_id uuid not null references public.sura_support_tickets(id) on delete cascade,
 status_before text not null,
 status_after text not null,
 reply_body text not null default '',
 created_at timestamptz not null default now()
);
alter table public.sura_support_manager_audit enable row level security;
revoke all on public.sura_support_manager_audit from public,anon,authenticated;
grant select,insert on public.sura_support_manager_audit to service_role;
create function public.sura_support_manager_act(p_actor uuid,p_ticket uuid,p_request uuid,p_expected text,p_status text,p_reply text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare old_status text; prior public.sura_support_manager_audit%rowtype; msg text:=btrim(coalesce(p_reply,''));
begin
 if p_actor is distinct from 'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid or not exists
 (select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then
 return jsonb_build_object('error','forbidden'); end if;
 if p_request is null or p_ticket is null or p_expected is null or p_status is null or
 p_status not in ('open','in_progress','answered','closed') or p_expected not in ('open','in_progress','answered','closed') or
 char_length(msg)>4000 or msg ~* '-----BEGIN (RSA |EC |ENCRYPTED )?PRIVATE KEY-----' or
 (p_status=p_expected and msg='') or (p_expected='closed' and p_status='closed' and msg<>'') then
 return jsonb_build_object('error','invalid_action'); end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_request::text,1550));
 select * into prior from public.sura_support_manager_audit where request_id=p_request;
 if found then
  if prior.actor_id=p_actor and prior.ticket_id=p_ticket and prior.status_before=p_expected and prior.status_after=p_status and prior.reply_body=msg then
   return jsonb_build_object('status','applied','request_id',p_request,'replayed',true);
  end if;
  return jsonb_build_object('error','request_conflict');
 end if;
 select status into old_status from public.sura_support_tickets where id=p_ticket for update;
 if not found then return jsonb_build_object('error','ticket_not_found'); end if;
 if old_status<>p_expected then return jsonb_build_object('error','status_conflict'); end if;
 if msg<>'' then
  insert into public.sura_support_messages(id,ticket_id,sender,body) values(p_request,p_ticket,'support',msg);
 end if;
 update public.sura_support_tickets set status=p_status where id=p_ticket;
 insert into public.sura_support_manager_audit(request_id,actor_id,ticket_id,status_before,status_after,reply_body)
 values(p_request,p_actor,p_ticket,old_status,p_status,msg);
 return jsonb_build_object('status','applied','request_id',p_request,'replayed',false);
end $$;
revoke all on function public.sura_support_manager_act(uuid,uuid,uuid,text,text,text) from public,anon,authenticated;
grant execute on function public.sura_support_manager_act(uuid,uuid,uuid,text,text,text) to service_role;

-- List by latest conversation activity, including customer follow-ups on answered tickets.
create function public.sura_support_manager_list(p_status text,p_owner uuid,p_page integer)
returns jsonb language sql stable security invoker set search_path='' as $$
 select coalesce(jsonb_agg(to_jsonb(x) order by x.activity_at desc,x.id desc),'[]'::jsonb) from (
 select t.id,t.owner_id,t.category,t.status,t.message,t.created_at,
 coalesce(m.created_at,t.created_at) as activity_at,coalesce(m.sender,'customer') as last_sender
 from public.sura_support_tickets t left join lateral (
 select sender,created_at from public.sura_support_messages where ticket_id=t.id
 order by created_at desc,id desc limit 1) m on true
 where (p_status is null or t.status=p_status) and (p_owner is null or t.owner_id=p_owner)
 order by coalesce(m.created_at,t.created_at) desc,t.id desc limit 30 offset greatest(0,p_page-1)*30
 ) x;
$$;
revoke all on function public.sura_support_manager_list(text,uuid,integer) from public,anon,authenticated;
grant execute on function public.sura_support_manager_list(text,uuid,integer) to service_role;

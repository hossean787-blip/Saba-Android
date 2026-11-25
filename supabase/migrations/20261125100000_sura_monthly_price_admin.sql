-- Administrator controls monthly price separately from the release switch.
alter table public.sura_monthly_plan add column if not exists revision bigint not null default 0 check(revision>=0);
create table public.sura_monthly_price_audit(
 request_id uuid primary key, actor_id uuid not null references auth.users(id), old_price bigint, new_price bigint not null check(new_price between 1 and 10000000000),
 previous_revision bigint not null, new_revision bigint not null unique, reason text not null check(char_length(btrim(reason)) between 5 and 500), created_at timestamptz not null default now()
);
alter table public.sura_monthly_price_audit enable row level security;
revoke all on public.sura_monthly_price_audit from public,anon,authenticated;
grant select,insert on public.sura_monthly_price_audit to service_role;
create or replace function public.sura_manager_monthly_price(p_actor uuid,p_request uuid,p_expected bigint,p_price bigint,p_reason text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare v public.sura_monthly_plan%rowtype; a public.sura_monthly_price_audit%rowtype;
begin
 if p_actor is distinct from 'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
 if p_request is null or p_expected is null or p_expected<0 or p_price is null or p_price not between 1 and 10000000000 or p_reason is null or char_length(btrim(p_reason)) not between 5 and 500 then return '{"error":"invalid_request"}';end if;
 select * into v from public.sura_monthly_plan where id=1 for update;
 if not found then return '{"error":"plan_missing"}';end if;
 select * into a from public.sura_monthly_price_audit where request_id=p_request;
 if found then
 if a.actor_id is distinct from p_actor or a.previous_revision<>p_expected or a.new_price<>p_price or a.reason<>btrim(p_reason) then return '{"error":"idempotency_conflict"}';end if;
 return jsonb_build_object('receipt',a.request_id,'revision',a.new_revision,'price_rial',a.new_price,'already_saved',true);
 end if;
 if v.revision<>p_expected then return '{"error":"price_changed"}';end if;
 if v.price_rial is not distinct from p_price then return '{"error":"unchanged_price"}';end if;
 update public.sura_monthly_plan set price_rial=p_price,revision=v.revision+1 where id=1;
 insert into public.sura_monthly_price_audit(request_id,actor_id,old_price,new_price,previous_revision,new_revision,reason)
 values(p_request,p_actor,v.price_rial,p_price,v.revision,v.revision+1,btrim(p_reason));
 return jsonb_build_object('receipt',p_request,'revision',v.revision+1,'price_rial',p_price,'already_saved',false);
end $$;
revoke all on function public.sura_manager_monthly_price(uuid,uuid,bigint,bigint,text) from public,anon,authenticated;
grant execute on function public.sura_manager_monthly_price(uuid,uuid,bigint,bigint,text) to service_role;

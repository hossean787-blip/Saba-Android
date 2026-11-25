-- Transfer only the one reserved administrative identity; preserve owner records.
-- User created and confirmed the independent manager account.
do $cutover$
begin
  if not exists(select 1 from auth.users where id='b652c55e-27e3-4110-87d5-995f265d7d78'::uuid and lower(email)='hossean787+suramanager@gmail.com' and email_confirmed_at is not null) then raise exception 'Independent identity missing or not verified';end if;
  lock table public.sura_admin_roles in share row exclusive mode;
  if (select count(*) from public.sura_admin_roles where role='admin')<>1 or not exists(select 1 from public.sura_admin_roles where user_id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid and role='admin') or exists(select 1 from public.sura_admin_roles where user_id='b652c55e-27e3-4110-87d5-995f265d7d78'::uuid) then raise exception 'Unexpected role state';end if;
end $cutover$;
CREATE OR REPLACE FUNCTION public.sura_card_review(p_actor uuid, p_id uuid, p_decision text, p_bank_reference text, p_amount bigint, p_reason text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare r public.sura_card_requests%rowtype; start_at timestamptz;
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
 if p_decision is null or p_decision not in ('approved','rejected') or p_reason is null or char_length(btrim(p_reason)) not between 3 and 500 then return '{"error":"invalid_review"}';end if;
 select * into r from public.sura_card_requests where id=p_id for update;
 if not found then return '{"error":"not_found"}';end if;
 if r.state=p_decision and (p_decision='rejected' or (r.bank_reference=p_bank_reference and r.amount_rial=p_amount)) then return jsonb_build_object('state',r.state,'already_reviewed',true);end if;
 if r.state<>'pending' then return '{"error":"state_conflict"}';end if;
 if p_decision='rejected' then
 update public.sura_card_requests set state='rejected',reviewed_by=p_actor,review_reason=btrim(p_reason),reviewed_at=now() where id=p_id;
 return '{"state":"rejected"}';end if;
 if p_amount is distinct from r.amount_rial or p_bank_reference is null or p_bank_reference !~ '^[0-9]{4,40}$' then return '{"error":"bank_confirmation_required"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(r.owner_id::text,190526));
 -- All writes below share one transaction. Uniqueness failure rolls back the entire approval.
 update public.sura_card_requests set state='approved',bank_reference=p_bank_reference,reviewed_by=p_actor,review_reason=btrim(p_reason),reviewed_at=now() where id=p_id;
 insert into public.sura_payment_orders(id,owner_user_id,gateway,amount_rial,credit_units,state,ref_id,verified_at,purchase_kind)
 values(r.id,r.owner_id,'card_transfer',r.amount_rial,r.units,'verified','card:'||r.id::text,now(),r.kind);
 if r.kind='monthly' then
 select greatest(now(),coalesce(max(ends_at),now())) into start_at from public.sura_monthly_periods where owner_id=r.owner_id;
 insert into public.sura_monthly_periods(order_id,owner_id,starts_at,ends_at) values(r.id,r.owner_id,start_at,start_at+interval '720 hours');
 else
 insert into public.sura_owner_wallets(owner_id,credits) values(r.owner_id,0) on conflict(owner_id) do nothing;
 insert into public.sura_credit_grants(order_id,owner_user_id,units) values(r.id,r.owner_id,r.units);
 update public.sura_owner_wallets set credits=credits+r.units,updated_at=now() where owner_id=r.owner_id;
 end if;
 return '{"state":"approved"}';
exception when unique_violation then return '{"error":"duplicate_payment"}';
end $function$;
CREATE OR REPLACE FUNCTION public.sura_manager_monthly_price(p_actor uuid, p_request uuid, p_expected bigint, p_price bigint, p_reason text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare v public.sura_monthly_plan%rowtype; a public.sura_monthly_price_audit%rowtype;
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
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
end $function$;
CREATE OR REPLACE FUNCTION public.sura_publish_starter(p_actor uuid, p_expected bigint, p_hash text, p_as_of date, p_entries jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare cur bigint; e jsonb; g text; n integer;
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
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
  if ((e->>'kind') like '%خدمت%') is distinct from ((e->>'group') in ('accounting','technical','other')) then return '{"error":"wrong_kind"}';end if;
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
end $function$;
CREATE OR REPLACE FUNCTION public.sura_support_manager_act(p_actor uuid, p_ticket uuid, p_request uuid, p_expected text, p_status text, p_reply text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare old_status text; prior public.sura_support_manager_audit%rowtype; msg text:=btrim(coalesce(p_reply,''));
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists
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
end $function$;
insert into public.sura_admin_roles(user_id,role) values('b652c55e-27e3-4110-87d5-995f265d7d78'::uuid,'admin');
delete from public.sura_admin_roles where user_id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid and role='admin';
insert into public.sura_manager_role_audit(request_id,actor_id,target_id,role_before,role_after,reason) values
(gen_random_uuid(),'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid,'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid,null,'admin','Authorized separate manager identity'),
(gen_random_uuid(),'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid,'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid,'admin','none','Authorized separate manager identity');
do $verify$ begin if (select count(*) from public.sura_admin_roles where role='admin')<>1 or not exists(select 1 from public.sura_admin_roles where user_id='b652c55e-27e3-4110-87d5-995f265d7d78'::uuid and role='admin') or exists(select 1 from public.sura_admin_roles where user_id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid) then raise exception 'Role verification failed';end if; end $verify$;

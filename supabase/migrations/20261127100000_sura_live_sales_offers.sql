-- Live sales offers managed from SURA Manager; historical orders keep their price/units snapshot.
create table if not exists public.sura_sales_offers(
 id uuid primary key default gen_random_uuid(),
 title text not null check(char_length(btrim(title)) between 2 and 80),
 description text not null default '' check(char_length(description)<=240),
 credits bigint not null check(credits between 1 and 999999),
 price_rial bigint not null check(price_rial between 1 and 1000000000000),
 active boolean not null default false,
 featured boolean not null default false,
 sort_order integer not null default 100 check(sort_order between 0 and 10000),
 revision bigint not null default 1 check(revision>=1),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index if not exists sura_sales_offers_public_idx on public.sura_sales_offers(active,sort_order,created_at desc);
create index if not exists sura_card_requests_offer_id_idx on public.sura_card_requests(offer_id);
create index if not exists sura_sales_offer_audit_actor_idx on public.sura_sales_offer_audit(actor_id,created_at desc);
create index if not exists sura_sales_offer_audit_offer_idx on public.sura_sales_offer_audit(offer_id,created_at desc);
create table if not exists public.sura_sales_offer_audit(
 request_id uuid primary key,
 actor_id uuid not null references auth.users(id),
 offer_id uuid not null references public.sura_sales_offers(id),
 revision bigint not null,
 title text not null,
 description text not null,
 credits bigint not null,
 price_rial bigint not null,
 active boolean not null,
 featured boolean not null,
 sort_order integer not null,
 reason text not null check(char_length(btrim(reason)) between 5 and 500),
 created_at timestamptz not null default now()
);
alter table public.sura_sales_offers enable row level security;
alter table public.sura_sales_offer_audit enable row level security;
revoke all on public.sura_sales_offers,public.sura_sales_offer_audit from public,anon,authenticated;
grant select,insert,update on public.sura_sales_offers,public.sura_sales_offer_audit to service_role;

alter table public.sura_card_requests add column if not exists offer_id uuid references public.sura_sales_offers(id);
alter table public.sura_card_requests add column if not exists offer_title text;
alter table public.sura_card_requests drop constraint if exists sura_card_requests_kind_check;
alter table public.sura_card_requests add constraint sura_card_requests_kind_check check(kind in ('single','monthly','offer'));
alter table public.sura_card_requests add constraint sura_card_offer_snapshot_check check((kind='offer' and offer_id is not null and offer_title is not null) or (kind<>'offer' and offer_id is null and offer_title is null));
alter table public.sura_payment_orders drop constraint if exists sura_payment_orders_purchase_kind_check;
alter table public.sura_payment_orders add constraint sura_payment_orders_purchase_kind_check check(purchase_kind in ('single','monthly','offer'));

create or replace function public.sura_manager_save_sales_offer(
 p_actor uuid,p_request uuid,p_id uuid,p_expected bigint,p_title text,p_description text,
 p_credits bigint,p_price bigint,p_active boolean,p_featured boolean,p_sort integer,p_reason text
) returns jsonb language plpgsql security invoker set search_path='' as $$
declare v public.sura_sales_offers%rowtype; a public.sura_sales_offer_audit%rowtype; new_rev bigint;
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
 if p_request is null or p_id is null or p_expected is null or p_expected<0 or p_title is null or char_length(btrim(p_title)) not between 2 and 80 or p_description is null or char_length(p_description)>240 or p_credits is null or p_credits not between 1 and 999999 or p_price is null or p_price not between 1 and 1000000000000 or p_active is null or p_featured is null or p_sort is null or p_sort not between 0 and 10000 or p_reason is null or char_length(btrim(p_reason)) not between 5 and 500 then return '{"error":"invalid_request"}';end if;
 select * into a from public.sura_sales_offer_audit where request_id=p_request;
 if found then
  if a.actor_id is distinct from p_actor or a.offer_id is distinct from p_id or a.title is distinct from btrim(p_title) or a.description is distinct from btrim(p_description) or a.credits<>p_credits or a.price_rial<>p_price or a.active<>p_active or a.featured<>p_featured or a.sort_order<>p_sort or a.reason<>btrim(p_reason) then return '{"error":"idempotency_conflict"}';end if;
  return jsonb_build_object('receipt',a.request_id,'offer_id',a.offer_id,'revision',a.revision,'already_saved',true);
 end if;
 select * into v from public.sura_sales_offers where id=p_id for update;
 if not found then
  if p_expected<>0 then return '{"error":"offer_changed"}';end if;
  insert into public.sura_sales_offers(id,title,description,credits,price_rial,active,featured,sort_order,revision,updated_at)
  values(p_id,btrim(p_title),btrim(p_description),p_credits,p_price,p_active,p_featured,p_sort,1,now()) returning * into v;
  new_rev:=1;
 else
  if v.revision<>p_expected then return '{"error":"offer_changed"}';end if;
  update public.sura_sales_offers set title=btrim(p_title),description=btrim(p_description),credits=p_credits,price_rial=p_price,active=p_active,featured=p_featured,sort_order=p_sort,revision=v.revision+1,updated_at=now() where id=p_id returning * into v;
  new_rev:=v.revision;
 end if;
 insert into public.sura_sales_offer_audit(request_id,actor_id,offer_id,revision,title,description,credits,price_rial,active,featured,sort_order,reason)
 values(p_request,p_actor,p_id,new_rev,v.title,v.description,v.credits,v.price_rial,v.active,v.featured,v.sort_order,btrim(p_reason));
 return jsonb_build_object('receipt',p_request,'offer_id',p_id,'revision',new_rev,'already_saved',false);
end $$;

create or replace function public.sura_card_quote(p_kind text,p_units bigint,p_offer_id uuid)
returns jsonb language plpgsql stable security invoker set search_path='' as $$
declare c public.sura_card_config%rowtype; o public.sura_sales_offers%rowtype; q jsonb; subtotal bigint;
begin
 select * into c from public.sura_card_config where id=1;
 if p_kind='single' and p_units between 1 and 999999 and p_offer_id is null then q:=public.sura_quote_credits(p_units);
 elsif p_kind='monthly' and p_units=20 and p_offer_id is null then q:=public.sura_monthly_quote();
 elsif p_kind='offer' and p_offer_id is not null then
  select * into o from public.sura_sales_offers where id=p_offer_id and active=true;
  if not found or o.credits<>p_units then return '{"error":"offer_unavailable"}';end if;
  subtotal:=o.price_rial;
  q:=jsonb_build_object('subtotal_rial',subtotal,'vat_rial',(subtotal*10+50)/100,'total_rial',subtotal+(subtotal*10+50)/100,'currency','IRR','vat_rate_percent',10,'quantity',o.credits,'offer_title',o.title,'offer_description',o.description);
 else return '{"error":"invalid_plan"}';end if;
 if q is null or q->>'total_rial' is null then return '{"error":"price_unavailable"}';end if;
 return q||jsonb_build_object('enabled',coalesce(c.enabled,false) and (p_kind<>'monthly' or coalesce((q->>'enabled')::boolean,false)),'kind',p_kind);
end $$;

create or replace function public.sura_card_reserve(p_owner uuid,p_kind text,p_units bigint,p_total bigint,p_offer_id uuid)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare c public.sura_card_config%rowtype; q jsonb; r public.sura_card_requests%rowtype; title_snapshot text;
begin
 if p_owner is null or p_total is null or p_total<=0 then return '{"error":"invalid_request"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190527));
 select * into r from public.sura_card_requests where owner_id=p_owner and state in ('draft','pending');
 if found then return jsonb_build_object('id',r.id,'state',r.state,'kind',r.kind,'units',r.units,'amount_rial',r.amount_rial,'destination_card',r.destination_card,'holder',r.holder,'offer_title',r.offer_title);end if;
 select * into c from public.sura_card_config where id=1 for share;
 if not c.enabled then return '{"error":"card_not_enabled"}';end if;
 if p_kind='monthly' then perform 1 from public.sura_monthly_plan where id=1 for share;end if;
 q:=public.sura_card_quote(p_kind,p_units,p_offer_id);
 if q?'error' then return q;end if;
 if not coalesce((q->>'enabled')::boolean,false) then return '{"error":"plan_not_enabled"}';end if;
 if (q->>'total_rial')::bigint is distinct from p_total then return '{"error":"price_changed"}';end if;
 title_snapshot:=case when p_kind='offer' then q->>'offer_title' else null end;
 insert into public.sura_card_requests(owner_id,kind,units,amount_rial,destination_card,holder,offer_id,offer_title)
 values(p_owner,p_kind,p_units,p_total,c.destination_card,c.holder,p_offer_id,title_snapshot) returning * into r;
 return jsonb_build_object('id',r.id,'state',r.state,'kind',r.kind,'units',r.units,'amount_rial',r.amount_rial,'destination_card',r.destination_card,'holder',r.holder,'offer_title',r.offer_title);
end $$;

create or replace function public.sura_card_review(p_actor uuid,p_id uuid,p_decision text,p_bank_reference text,p_amount bigint,p_reason text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare r public.sura_card_requests%rowtype; start_at timestamptz;
begin
 if p_actor is distinct from 'b652c55e-27e3-4110-87d5-995f265d7d78'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
 if p_decision is null or p_decision not in ('approved','rejected') or p_reason is null or char_length(btrim(p_reason)) not between 3 and 500 then return '{"error":"invalid_review"}';end if;
 select * into r from public.sura_card_requests where id=p_id for update;
 if not found then return '{"error":"not_found"}';end if;
 if r.state=p_decision and (p_decision='rejected' or (r.bank_reference=p_bank_reference and r.amount_rial=p_amount)) then return jsonb_build_object('state',r.state,'already_reviewed',true);end if;
 if r.state<>'pending' then return '{"error":"state_conflict"}';end if;
 if p_decision='rejected' then update public.sura_card_requests set state='rejected',reviewed_by=p_actor,review_reason=btrim(p_reason),reviewed_at=now() where id=p_id;return '{"state":"rejected"}';end if;
 if p_amount is distinct from r.amount_rial or p_bank_reference is null or p_bank_reference !~ '^[0-9]{4,40}$' then return '{"error":"bank_confirmation_required"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(r.owner_id::text,190526));
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
end $$;

revoke all on function public.sura_manager_save_sales_offer(uuid,uuid,uuid,bigint,text,text,bigint,bigint,boolean,boolean,integer,text) from public,anon,authenticated;
grant execute on function public.sura_manager_save_sales_offer(uuid,uuid,uuid,bigint,text,text,bigint,bigint,boolean,boolean,integer,text) to service_role;
revoke all on function public.sura_card_quote(text,bigint,uuid),public.sura_card_reserve(uuid,text,bigint,bigint,uuid) from public,anon,authenticated;
grant execute on function public.sura_card_quote(text,bigint,uuid),public.sura_card_reserve(uuid,text,bigint,bigint,uuid) to service_role;

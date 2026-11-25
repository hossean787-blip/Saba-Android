-- Manual bank card transfer: no credit on receipt upload, only after administrator bank reconciliation.
create table public.sura_card_config(
 id integer primary key check(id=1), destination_card text, holder text, enabled boolean not null default false,
 check(destination_card is null or destination_card ~ '^[0-9]{16}$'),
 check(holder is null or char_length(btrim(holder)) between 2 and 100),
 check(not enabled or (destination_card is not null and holder is not null))
);
insert into public.sura_card_config(id) values(1);
create table public.sura_card_requests(
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references auth.users(id),
 kind text not null check(kind in ('single','monthly')), units bigint not null check(units between 1 and 999999),
 amount_rial bigint not null check(amount_rial>0), destination_card text not null, holder text not null,
 state text not null default 'draft' check(state in ('draft','pending','approved','rejected')),
 tracking text, transfer_date text, receipt_jpeg bytea, receipt_hash text,
 bank_reference text, reviewed_by uuid references auth.users(id), review_reason text, reviewed_at timestamptz,
 created_at timestamptz not null default now(), submitted_at timestamptz,
 check(kind<>'monthly' or units=20), check(receipt_jpeg is null or octet_length(receipt_jpeg) between 4 and 500000),
 check(state<>'approved' or (bank_reference is not null and reviewed_by is not null and reviewed_at is not null))
);
create unique index sura_card_one_open on public.sura_card_requests(owner_id) where state in ('draft','pending');
create unique index sura_card_verified_reference on public.sura_card_requests(destination_card,bank_reference) where state='approved';
create unique index sura_card_verified_image on public.sura_card_requests(receipt_hash) where state='approved';
create index sura_card_queue on public.sura_card_requests(state,created_at);
alter table public.sura_card_config enable row level security;
alter table public.sura_card_requests enable row level security;
revoke all on public.sura_card_config,public.sura_card_requests from public,anon,authenticated;
grant select,insert,update on public.sura_card_config,public.sura_card_requests to service_role;
alter table public.sura_payment_orders drop constraint sura_payment_orders_gateway_check;
alter table public.sura_payment_orders add constraint sura_payment_orders_gateway_check check(gateway in ('zarinpal','card_transfer'));
create or replace function public.sura_card_quote(p_kind text,p_units bigint) returns jsonb language plpgsql stable security invoker set search_path='' as $$
declare c public.sura_card_config%rowtype; q jsonb;
begin
 select * into c from public.sura_card_config where id=1;
 if p_kind='single' and p_units between 1 and 999999 then q:=public.sura_quote_credits(p_units);
 elsif p_kind='monthly' and p_units=20 then
 q:=public.sura_monthly_quote();
 if not coalesce((q->>'enabled')::boolean,false) then return '{"enabled":false,"error":"monthly_not_enabled"}';end if;
 else return '{"error":"invalid_plan"}';end if;
 if q is null or q->>'total_rial' is null then return '{"error":"price_unavailable"}';end if;
 return q||jsonb_build_object('enabled',c.enabled,'kind',p_kind);
end $$;
create or replace function public.sura_card_reserve(p_owner uuid,p_kind text,p_units bigint,p_total bigint) returns jsonb language plpgsql security invoker set search_path='' as $$
declare c public.sura_card_config%rowtype; q jsonb; r public.sura_card_requests%rowtype;
begin
 if p_owner is null or p_total is null or p_total<=0 then return '{"error":"invalid_request"}';end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190527));
 select * into r from public.sura_card_requests where owner_id=p_owner and state in ('draft','pending');
 if found then return jsonb_build_object('id',r.id,'state',r.state,'kind',r.kind,'units',r.units,'amount_rial',r.amount_rial,'destination_card',r.destination_card,'holder',r.holder);end if;
 select * into c from public.sura_card_config where id=1 for share;
 if not c.enabled then return '{"error":"card_not_enabled"}';end if;
 if p_kind='monthly' then perform 1 from public.sura_monthly_plan where id=1 for share;end if;
 q:=public.sura_card_quote(p_kind,p_units);
 if q?'error' then return q;end if;
 if (q->>'total_rial')::bigint is distinct from p_total then return '{"error":"price_changed"}';end if;
 insert into public.sura_card_requests(owner_id,kind,units,amount_rial,destination_card,holder)
 values(p_owner,p_kind,p_units,p_total,c.destination_card,c.holder) returning * into r;
 return jsonb_build_object('id',r.id,'state',r.state,'kind',r.kind,'units',r.units,'amount_rial',r.amount_rial,'destination_card',r.destination_card,'holder',r.holder);
end $$;
create or replace function public.sura_card_submit(p_owner uuid,p_id uuid,p_tracking text,p_date text,p_receipt text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare r public.sura_card_requests%rowtype; image_bytes bytea; h text;
begin
 if p_tracking is null or p_tracking !~ '^[0-9]{4,32}$' or p_date is null or p_date !~ '^[0-9]{4}/[0-9]{2}/[0-9]{2}$' or p_receipt is null or char_length(p_receipt)>666668 then return '{"error":"invalid_receipt"}';end if;
 begin image_bytes:=decode(p_receipt,'base64');exception when others then return '{"error":"invalid_receipt"}';end;
 if octet_length(image_bytes) not between 4 and 500000 or substring(image_bytes from 1 for 3)<>decode('ffd8ff','hex') or substring(image_bytes from octet_length(image_bytes)-1 for 2)<>decode('ffd9','hex') then return '{"error":"invalid_image"}';end if;
 h:=encode(sha256(image_bytes),'hex');
 select * into r from public.sura_card_requests where id=p_id and owner_id=p_owner for update;
 if not found then return '{"error":"not_found"}';end if;
 if r.state='pending' and r.receipt_hash=h and r.tracking=p_tracking and r.transfer_date=p_date then return jsonb_build_object('id',r.id,'state',r.state);end if;
 if r.state<>'draft' then return '{"error":"state_conflict"}';end if;
 update public.sura_card_requests set state='pending',tracking=p_tracking,transfer_date=p_date,receipt_jpeg=image_bytes,receipt_hash=h,submitted_at=now() where id=p_id;
 return jsonb_build_object('id',p_id,'state','pending');
end $$;
create or replace function public.sura_card_review(p_actor uuid,p_id uuid,p_decision text,p_bank_reference text,p_amount bigint,p_reason text) returns jsonb language plpgsql security invoker set search_path='' as $$
declare r public.sura_card_requests%rowtype; start_at timestamptz;
begin
 if p_actor is distinct from 'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid or not exists(select 1 from public.sura_admin_roles where user_id=p_actor and role='admin') then return '{"error":"forbidden"}';end if;
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
end $$;
revoke all on function public.sura_card_quote(text,bigint),public.sura_card_reserve(uuid,text,bigint,bigint),public.sura_card_submit(uuid,uuid,text,text,text),public.sura_card_review(uuid,uuid,text,text,bigint,text) from public,anon,authenticated;
grant execute on function public.sura_card_quote(text,bigint),public.sura_card_reserve(uuid,text,bigint,bigint),public.sura_card_submit(uuid,uuid,text,text,text),public.sura_card_review(uuid,uuid,text,text,bigint,text) to service_role;

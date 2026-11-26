-- Complete the audit snapshot and add covering indexes for live sales offer relations.
alter table public.sura_sales_offer_audit add column if not exists description text not null default '';
alter table public.sura_sales_offer_audit add column if not exists featured boolean not null default false;
alter table public.sura_sales_offer_audit add column if not exists sort_order integer not null default 100;
create index if not exists sura_card_requests_offer_id_idx on public.sura_card_requests(offer_id);
create index if not exists sura_sales_offer_audit_actor_idx on public.sura_sales_offer_audit(actor_id,created_at desc);
create index if not exists sura_sales_offer_audit_offer_idx on public.sura_sales_offer_audit(offer_id,created_at desc);
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

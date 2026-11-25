-- Cancel unpaid draft, retain audit trail; do not cancel a submitted receipt.
alter table public.sura_card_requests drop constraint sura_card_requests_state_check;
alter table public.sura_card_requests add constraint sura_card_requests_state_check check (state in ('draft','pending','approved','rejected','cancelled'));
alter table public.sura_card_requests add column if not exists cancelled_at timestamptz;
create or replace function public.sura_card_cancel(p_owner uuid,p_id uuid) returns jsonb
language plpgsql set search_path='' as $$
declare r public.sura_card_requests%rowtype;
begin
 if p_owner is null or p_id is null then return '{"error":"invalid_request"}'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_owner::text,190527));
 select * into r from public.sura_card_requests where id=p_id and owner_id=p_owner for update;
 if not found then return '{"error":"not_found"}';end if;
 if r.state='cancelled' then return jsonb_build_object('id',r.id,'state','cancelled');end if;
 if r.state<>'draft' or r.receipt_jpeg is not null or r.submitted_at is not null then return '{"error":"state_conflict"}';end if;
 update public.sura_card_requests set state='cancelled',cancelled_at=now() where id=r.id;
 return jsonb_build_object('id',r.id,'state','cancelled');
end $$;
revoke all on function public.sura_card_cancel(uuid,uuid) from public,anon,authenticated;
grant execute on function public.sura_card_cancel(uuid,uuid) to service_role;
-- A configured but disabled plan is quotable, never reservable.
create or replace function public.sura_card_quote(p_kind text,p_units bigint) returns jsonb
language plpgsql stable set search_path='' as $$
declare c public.sura_card_config%rowtype; q jsonb; plan_enabled boolean:=true;
begin
 select * into c from public.sura_card_config where id=1;
 if p_kind='single' and p_units between 1 and 999999 then q:=public.sura_quote_credits(p_units);
 elsif p_kind='monthly' and p_units=20 then
  q:=public.sura_monthly_quote();plan_enabled:=coalesce((q->>'enabled')::boolean,false);
 else return '{"error":"invalid_plan"}';end if;
 if q is null or q->>'total_rial' is null then return '{"error":"price_unavailable"}';end if;
 return q||jsonb_build_object('enabled',coalesce(c.enabled,false) and plan_enabled,'kind',p_kind);
end $$;

CREATE OR REPLACE FUNCTION public.sura_card_reserve(p_owner uuid, p_kind text, p_units bigint, p_total bigint)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
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
 if not coalesce((q->>'enabled')::boolean,false) then return '{"error":"monthly_not_enabled"}';end if;
 if (q->>'total_rial')::bigint is distinct from p_total then return '{"error":"price_changed"}';end if;
 insert into public.sura_card_requests(owner_id,kind,units,amount_rial,destination_card,holder)
 values(p_owner,p_kind,p_units,p_total,c.destination_card,c.holder) returning * into r;
 return jsonb_build_object('id',r.id,'state',r.state,'kind',r.kind,'units',r.units,'amount_rial',r.amount_rial,'destination_card',r.destination_card,'holder',r.holder);
end $function$;

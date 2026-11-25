-- Append-only conversations; no user may set sender, timestamps, ticket status or response.
create table public.sura_support_messages (
  id uuid primary key,
  ticket_id uuid not null references public.sura_support_tickets(id) on delete cascade,
  sender text not null default 'customer' check (sender in ('customer','support')),
  body text not null check (char_length(btrim(body)) between 1 and 4000),
  created_at timestamptz not null default now()
);
alter table public.sura_support_messages enable row level security;
revoke all on public.sura_support_messages from public,anon,authenticated;
grant select on public.sura_support_messages to authenticated;
grant insert(id,ticket_id,body) on public.sura_support_messages to authenticated;
grant select,insert on public.sura_support_messages to service_role;
create index sura_support_messages_ticket_time_idx on public.sura_support_messages(ticket_id,created_at,id);
create policy sura_support_message_read on public.sura_support_messages for select to authenticated
using (exists (select 1 from public.sura_support_tickets t where t.id=ticket_id and t.owner_id=(select auth.uid())));
create policy sura_support_message_write on public.sura_support_messages for insert to authenticated
with check (sender='customer' and char_length(btrim(body))<=2000 and
 exists (select 1 from public.sura_support_tickets t where t.id=ticket_id and t.owner_id=(select auth.uid()) and t.status<>'closed'));
create function public.sura_support_message_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
declare owner uuid;
begin
  if new.sender='customer' then
    select t.owner_id into owner from public.sura_support_tickets t where t.id=new.ticket_id and t.status<>'closed';
    if owner is null or (auth.uid() is not null and owner<>auth.uid()) then raise exception 'Ticket unavailable' using errcode='42501'; end if;
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner::text,1543));
    if (select count(*) from public.sura_support_messages m join public.sura_support_tickets t on t.id=m.ticket_id
      where t.owner_id=owner and m.sender='customer' and m.created_at>=now()-interval '24 hours')>=50 then
       raise exception 'Message limit reached' using errcode='P0001';
    end if;
  end if;
  return new;
end;
$$;
revoke all on function public.sura_support_message_guard() from public,anon,authenticated;
create trigger sura_support_message_guard before insert on public.sura_support_messages
for each row execute function public.sura_support_message_guard();

-- SURA Manager: designated verified superadmin account.
-- Successfully applied to project fiiitzfbdjzpmepzjsjw on 2026-11-12.
-- This migration does not modify balances, invoices, passwords, or customer data.
DO $guard$
BEGIN
 IF (SELECT count(*) FROM auth.users WHERE id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid
     AND lower(trim(email))='hossean787@gmail.com' AND email_confirmed_at IS NOT NULL) <> 1
 THEN RAISE EXCEPTION 'Designated verified superadmin account missing'; END IF;
 IF (SELECT count(*) FROM public.sura_admin_roles WHERE role='admin' AND user_id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid) <> 1
 THEN RAISE EXCEPTION 'Designated superadmin role missing'; END IF;
 IF EXISTS (SELECT 1 FROM public.sura_admin_roles WHERE role='admin' AND user_id<>'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid)
 THEN RAISE EXCEPTION 'Unexpected second admin; refusing unsafe migration'; END IF;
END $guard$;
ALTER TABLE public.sura_admin_roles ADD CONSTRAINT sura_only_designated_superadmin
 CHECK (role<>'admin' OR user_id='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc'::uuid);
CREATE UNIQUE INDEX IF NOT EXISTS sura_single_superadmin_role_idx
 ON public.sura_admin_roles(role) WHERE role='admin';

-- Applied to Supabase project fiiitzfbdjzpmepzjsjw on 2026-11-12.
-- Non-destructive performance fix. No customer/payment records are changed.
CREATE INDEX IF NOT EXISTS sura_manager_credit_actor_created_idx
  ON public.sura_admin_credit_adjustments(actor_user_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sura_manager_price_actor_created_idx
  ON public.sura_manager_price_audit(actor_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sura_manager_role_actor_created_idx
  ON public.sura_manager_role_audit(actor_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sura_manager_role_target_created_idx
  ON public.sura_manager_role_audit(target_id,created_at DESC);
-- Recovery: DROP INDEX CONCURRENTLY IF EXISTS public.sura_manager_*<exact index name>;

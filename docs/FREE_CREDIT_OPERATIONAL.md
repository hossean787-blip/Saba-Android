# SURA free-invoice promotion — operational handoff

Scope: Android SURA invoice project only, branch `main`, v0.17.0.

## Secret handling
- The former code was exposed in a historic Git commit and has been invalidated; history has **not** been rewritten.
- New random code generated in Supabase SQL, stored encrypted as a Supabase Vault secret named `sura_free_invoice_code`.
- The verification hash is in `public.sura_promo_secrets` with RLS and no direct end-user privileges.
- Previous interim public-table plaintext storage `public.sura_promo_owner_code` was removed after verifying the Vault secret matched the verifier.
- Never print the secret in app UI, documentation, repository, Android builds, or diagnostic logs.
- Project owner retrieves it from Supabase Dashboard → Database → Vault with administrator privileges. Do not expose it over the app API.
- A secret rotation must update both Vault and the hash verifier atomically.

## Backend
- Supabase project: `fiiitzfbdjzpmepzjsjw`.
- Edge Function `sura-free-credit`, JWT checked against Supabase Auth user endpoint.
- POST base path: one-time promo redemption, stored in `sura_promo_redemptions`.
- POST `/status`: authenticated account's remaining promotional credits.
- POST `/consume`: server-side idempotent consumption, keyed by account and invoice UUID.
- Consumption is atomic in Postgres function `public.sura_consume_promo_credit`, one credit per new invoice.
- Raw IP and Android ID are never stored; keyed HMAC fingerprints are stored instead. IP is not an exclusion criterion.
- Service-role secret remains server-only; never ship it to Android.
- Client-generated UUID is deterministic per taxpayer national ID and normalized invoice number. A distinct new number spends a distinct credit.
- The local invoice creation uses a server-confirmed path, records a Room quota-use audit entry, and edits do not spend again.
- The app stores only pending UUIDs in private preferences to retry a network-acknowledged spend after a local failure.
- Actual tax-authority submission remains a separate feature and is not implied by credit redemption.

## Tests performed
- Vault/code verifier match and old-code rejection verified by SQL.
- Private tables/RPC privileges and RLS verified by SQL.
- SQL transactional rollback test: first / second / third spends decrement 3→2→1→0, same UUID replay does not decrement, fourth distinct key rejected.
- Synthetic test rows rolled back, no production account credits modified.
- Android JVM/CI status must be checked on the latest `main` commit; manual phone and authenticated end-to-end trial remain required before commercial release.

## Known limitations
- Android ID is app-signing/user scoped and can change after factory reset; IP does not identify a device reliably.
- Client-local data is not a security boundary. Enforce tax-system submission and final credit checks independently server-side.
- Cross-service operations (server spend, then local Room draft) are not one distributed transaction. Pending-claim retry mitigates but does not eliminate all recovery cases (notably app uninstall before saving the local invoice).

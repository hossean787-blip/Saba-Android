# SURA — credit and invoice implementation checkpoint (2026-11-02)

## Scope
Only the existing Saba-Android project, main branch, and its linked Supabase backend. No cross-project assets.

## Implemented
- Existing 10% VAT backend quote remains authoritative: price 100000 IRR per invoice credit, VAT 10000 IRR, total 110000 IRR.
- Deployed additive `sura_paid_invoice_consumption_idempotent` migration on Supabase.
- Table `public.sura_paid_invoice_uses` records one immutable use per owner/invoice UUID with RLS enabled and revoked client access.
- RPC `sura_consume_taxpayer_credit` locks taxpayer wallet, ensures no negative balance, returns same debit for retry, and rejects spent balance. Only service_role can execute.
- Extended existing authenticated `sura-credit-transfer` Edge Function (version 3) with `operation=consume`; owner identity derived from verified token.
- Android credit client supports consume. InvoiceScreen loads verified taxpayer balance, consumes paid credit when first creating an invoice, retries with deterministic idempotency key, preserves a local ledger-use record, and does not charge on edits.
- Home new-invoice action no longer shows the obsolete disabled message.
- Purchase quote UI already displays 10% VAT; release signing workflow requires original signing material.

## Verified directly
- Database migration: success.
- Edge Function deploy: ACTIVE version 3.
- SQL transactional test: two distinct invoices consume exactly two credits; retry does not double-consume; third invoice after exhaustion is denied. Test transaction rolled back.
- Post-test database inspection: zero test debit rows and zero test taxpayer wallets (rollback succeeded).
- Authenticated real-payment and Android-device end-to-end tests: NOT run.

## Open gates (must not claim release-ready)
- Compile and JVM tests on main after latest changes: not confirmed in current session; inspect newest GitHub Actions run.
- Signed Release APK artifact for latest main commit: not confirmed.
- Android device installation, keystore upgrade compatibility, UI, biometrics: unverified.
- Real ZarinPal 110000 IRR transaction and settlement: requires actual authorized payment; no simulated success.
- Purchase invoice formal receipt, monthly subscription plan and full taxpayer submission remain incomplete/unverified.
- Reconciliation of server debit with local invoice save following app crash is idempotent on retry but lacks an automated recovery queue.
- Paid credit spending uses a client-issued invoice key; server cannot independently verify a device-local invoice exists. Do not describe it as fraud-proof licensing.

## Next action
Review newest GitHub Actions release build and APK signature for the latest commit, fix compile failures, then run a controlled device test of purchase→transfer→draft and payment webhook reconciliation.

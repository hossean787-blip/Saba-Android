# Pending Room tests and independent SQLite checks — 2026-10-29

## Executed outside Android
Five **independent Python sqlite3** checks were run successfully (5/5 PASS):
1. Composite customer/company foreign-key rejects cross-company invoices; distinct companies may use same invoice number.
2. Transaction rollback after an injected line insert failure preserves original invoice header and lines.
3. Transaction rollback after an injected quota-use failure preserves invoice status and quota.
4. Executes the **same SQL text** as the app's MIGRATION_1_2 for simulations table and indexes and verifies uniqueness and existing data; this is NOT a full Room migration schema validation.
5. Eight concurrent SQLite connections with BEGIN IMMEDIATE attempt finalization; exactly one records consumption and remaining quota is zero.

## Added to main
- `QuotaAtomicityInstrumentedTest` with 3 Android instrumented tests:
  - retry/idempotent finalization;
  - eight concurrent finalization calls;
  - SQL trigger-injected quota-use failure rolling back the invoice status and quota.

## Not executed
- Android Room instrumentation tests (no Android SDK/Gradle in the current runtime).
- Full Room migration validation against compiled schemas for v1 and v2.
- Device multi-company UI, key management, and taxpayer API.

## Caveats
SQLite simulation evidence is not equivalent to running the Kotlin repository. Android CI and APK build are outstanding. Before commercial release, run `connectedDebugAndroidTest` with real Android SDK and emulator/device, and verify Room schema migration with both historical schemas.

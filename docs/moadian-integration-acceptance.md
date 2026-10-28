# Moadian integration: implemented vs production-ready

## Implemented in this branch
- User-facing **offline simulation only** for finalized invoices; data persisted by company/invoice in Room v2.
- v1 -> v2 explicit Room migration with unique simulated-invoice index.
- Instrumentation smoke test for migration DDL and record preservation (requires Android device/emulator).
- Partial type-one sales invoice domain validation (not the complete published schema).
- Local SHA-256 fingerprint and request ledger/reconciliation modules.
- Generic JWS RS256 signing/verification and AES-256-GCM encryption utilities.
- Injectable access-token provider, guarded authenticated transport and HTTPS-only host restriction.
- Unit tests for contracts, crypto round trips, auth expiration and error handling.

## Not production ready
- Official schema/pattern validation and serialization, including all required header/body/payment fields.
- The government-specified JWS headers, JWT nonce exchange, server encryption key wrapping, packet envelopes, and official v2 endpoint semantics.
- Actual taxpayer authorization and key custody/integration.
- Authenticated real API send/inquiry and signed server response validation.
- Persisting transmission records and retries across crashes in an actual transactional workflow.
- Instrumentation test **execution** and full Room schema validation on a populated v1 database.
- Device installation, end-to-end operation, and compliance confirmation.

The provided upstream sources are kept in third_party/reference and are not compiled into Android.

Official specifications must be independently verified before activating real submission. Network code is not wired to the UI, which deliberately remains offline only. Never commit keys, credentials or real invoice data.

## Acceptance gates
1. Successful :app:testDebugUnitTest and :app:assembleDebug CI for the latest commit.
2. Successful on-device migration test and old-data preservation.
3. Exact current official schema validation with signed test vectors.
4. Safe keystore and token exchange; government server-information key validation.
5. End-to-end sandbox send and inquiry with authorized synthetic fixtures.
6. Explicit taxpayer approval before any real transmission.

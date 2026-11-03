# Android official submission and legacy recovery

Continue the authorized Android implementation without backend changes or live transactions.

1. Add local Room v9 outbox and import receipts. Freeze the exact tax ID, payload and request trace before POST; compare-and-set PREPARED to SENDING. An ambiguous attempt can only be queried, never blindly resubmitted.
2. Implement a constrained type-1/pattern-1/original/cash rial mapper with explicit issue timestamp and measurement unit, selected customer identifiers and independent totals. Reject unsupported or inconsistent input.
3. Extend selected-taxpayer JWS signing; fixed-host HTTPS v2 transport for invoice and inquiry, bounded responses and fresh challenge per operation. Validate acknowledgement identity and verify SUCCESS JWS against organization keys obtained over official HTTPS; bind tax ID, reference and fiscal memory.
4. Add an Android screen for prepare, explicit submission confirmation, durable history and inquiry after restart. No credits are consumed by tax submission.
5. Import eligible legacy records transactionally, preserving IDs and history, only where legacy UUID owner exactly matches authenticated UUID. Leave unknown/foreign records quarantined and source intact; never let user assertions replace ownership evidence. Do not copy obsolete cached credit balances or unscoped private keys.
6. Test mapper, state machine, ambiguous failures/restarts, signatures and identity mismatches, tenant isolation, idempotent/rollback legacy import, migrations and UI. Run unit tests/lint only. Commit, push and update existing draft PR; no merge/APK/live submission.

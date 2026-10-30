# SURA Home + Moadian execution checkpoint
Status: در حال اجرا
Branch: main; no branch created; no other project changed.
Inspected head: 9a666ba9a9d53974a710bd14a2c600292021ba4d.
Previous independently verified rollback point: 955132fc2dba7daf0ec47f6bbda3f4894090290d, CI 41012038427 succeeded (153 JVM tests in preceding revision; confirm exact current run logs before quoting).
Current main CI 41038768926 failed; investigating actual logs.
Inspection: HomeDashboard already exists; quota consumption has already been moved to first draft insertion and cancellation keeps ledger; no rewrite needed.
Found: Home request counter is remembered only inside InvoiceScreen, so removing/re-entering the screen can replay an old request; transfer UI generates a new operation ID for every retry; old drafts without ledger are blocked at finalization; tests still require review under new quota policy.
Next: diagnose latest CI, correct navigation request consumption and transfer retry identity, test quota policy and preserve existing data/ownership.
Files read: MainActivity.kt, ui/HomeDashboard.kt, ui/InvoiceScreen.kt, data/InvoiceRepository.kt, QuotaRepository.kt, QuotaDao.kt, previous checkpoint and workflow files.
No new tests executed for this combined mission yet. Device Keystore and instrumentation remain unverified.
Checkpoint cadence: immediately after safe small changes; reports only on new evidence. Keep work-in-progress off main when unsafe.

## Home fixes
Source baseline 9a666ba9. Current baseline CI: 153 tests / 134 passed / 19 failed, mostly obsolete quota fixtures. MainActivity atomic dashboard snapshot, retry-stable charge-transfer operation ID, explicit consumption acknowledgment for Home invoice request, charge shortcut inside settings. InvoiceScreen acknowledges parent request so switching taxpayer cannot replay it. Static call-site review done; runtime CI pending. Next: update quota fixtures without weakening assertions and add creation-policy regression tests.

## Quota test repair checkpoint
Updated tests to fund before creating new invoices, assert consumption at creation (not finalization), retain cancelled header/lines/ledger, reject unfunded creation, and inject quota-ledger failure during insertion to prove complete rollback. Added concurrent creation with one unit: only one can succeed; edit/finalize/retry cannot decrement again. Migration fixture explicitly constructs historical uncharged v1 data rather than running new policy against old fixtures. Files: InvoiceWorkflowTest, InvoiceScreenTest, DatabaseMigrationTest, MoadianPersistenceTest, TaxpayerSerialAllocatorTest. Static fixture review performed; CI required for execution. No test disabled or ignored. Next: actual CI and legacy-draft compatibility/device tests.

## Historical draft compatibility
Additive Room migration 5->6 records pre-policy DRAFT invoices lacking a quota-use ledger in a separate legacy exemption table. No old invoice/owner/quota is altered, no fake charge record is created, no retroactive debit. Such invoices may finalize without another charge; cancellation preserves them. New invoices still require charge-on-create. Migration regression test now finalizes an actual legacy fixture and asserts unchanged charge and absent consumption ledger. Files: SabaDatabase.kt, LegacyQuotaExemption.kt, InvoiceRepository.kt, DatabaseMigrationTest.kt. Static DDL/entity and migration-chain checks completed; execution pending CI. Rollback source: 955132fc (do not downgrade a migrated database).

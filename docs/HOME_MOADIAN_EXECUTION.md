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

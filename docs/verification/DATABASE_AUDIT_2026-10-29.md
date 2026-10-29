# Saba database audit — 2026-10-29

## Changes committed directly to main
- InvoiceRepository: missing customer/invoice/company/quota now yields IllegalArgumentException rather than IllegalStateException in the updated paths.
- Normalized blank invoice numbers are rejected before a transaction starts.
- Android instrumentation: added `sqliteFailureInsideDraftUpdateRollsBackHeaderAndLines` and `rejectsNumberThatNormalizesToWhitespace` to the existing tenant isolation suite.
- Instrumentation test catches Room-wrapped RuntimeException from injected trigger; an AssertionError from missing failure still propagates.

## Verified outside Android
PASS — standard Python SQLite in-memory transaction: intentionally rejected line insertion after header update and old-line deletion; transaction rollback preserved both invoice number and original line. This verifies SQLite's transaction behavior, **not** the Room integration.

## Not yet verified
- Android instrumentation suite: not run (Android SDK/Gradle unavailable).
- Kotlin/Android compilation: not run.
- Room schema migrations / database upgrades: not run.
- Quota finalization atomicity and concurrent transaction behavior on device: not run.
- Real app and device database state: not inspected.

## Outstanding risks
- Tests and app dependencies require full Android build and instrumentation.
- Validate migration 1 to 2 against exported schemas, SQLite foreign keys, indexes, and on-device test data.
- Protect tenant-scoped DAO calls beyond the repository layer; database ID checks do not replace actual account-level authorization.
- Financial and tax acceptance criteria require official specifications and dedicated tests.

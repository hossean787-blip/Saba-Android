# Authorized SURA app-only test build — 2026-11-25
Version 0.49.0 (49), full variant 0.50.0 (50). Unified checkout and error diagnostics. Maintain signing certificate, application ID, TLS and biometric safeguards. Verify existing full test gate, APK signature, ZIP integrity, merged permissions and SHA-256. No store publication, no payment, no disabling Play Protect. A recommendation to scan an unfamiliar sideloaded APK is not a malware verdict; Google alone controls the scan. No claim of Google certification. Previous CI 45599580851 failed in Robolectric/network tests; signed build must independently pass.
Retry after CI 45610216054: 459/460 passed; preserve pending-credit retry and show a safe explicit ambiguous-debit warning. Existing signing and full verification gates remain.
0.51.0 / 51: unified monthly invoice entitlement, cancel unpaid card draft, receipt preview and guided validation. Monthly sales must stay disabled until the signed upgrade and isolated entitlement/settlement verification succeed.
0.53.0 / 53: monthly subscription temporarily removed from checkout; retain existing historical orders, single credits, gift redemption, unpaid draft cancellation and receipt submission. Subscription sale remains disabled on server; no price or data deletion.
0.55.0 / 55: customer list UI follows Moadian: register/edit/deactivate in one row, normalized search, selected card, preserved historical customers, blocked new invoices for inactive buyers. Source tests added; signed build and UI tests required.
0.57.0 / 57 (reserved full variant 58): user-authorized signed app-only build. Direct fiscal-memory/private-key submission only, draft-to-send workflow, tax status and PDF, stage diagnostics. Source gate CI 45623584056: 468 passed, 0 failed. Retain original signer, package ID, full test and lint gates. No public release or real tax invoice submission. Build artifact for owner testing only.

CI dependency stabilization (same application version 0.57.0): stage pinned Robolectric SDK jars using Gradle dependency cache before running tests; retry preparation only; tests use local SDK jars, never download from Maven during test execution. Rebuild with all existing signing and test gates intact. No app behavior or signing identity changes.

Fix Kotlin DSL java extension shadowing: import ZipFile explicitly. Preserve dependency preparation, tests and signed build gates.

Confirmed Maven Central HTTP 429 in CI 45628748312 and release 45628747686. Restrict documented Google-hosted Central mirrors to the pinned Android test SDK artifact, use artifact-only resolution, and reuse prepared local jars across retry and test invocation. Fall back to EU mirror and then Central. Full test and signing gates unchanged.

Version 0.59.0: match approved landscape invoice sample, use bundled Vazirmatn and SURA logo, preserve exact financial amounts and official evidence, no QR. Update PDF Android regression tests and request signed build.

0.59.0 retry: preserve itemTitle through durable invoice creation payload, including recovery; retain optional backward-compatible decoding for old claims. Update starter fallback regression to verify reported VAT and unverified provenance instead of obsolete blank-rate expectation. All original tests remain enabled.

0.61.0: clarify direct inquiry SUCCESS vs reference identity verification; expose read-only reference recheck in details and PDF, suppress misleading V2 receipt evidence warnings from visible error text, preserve strict reference eligibility and prohibit resending. Regression test status and request eligibility. Request signed APK.

0.61.0: invoice events show customer names, invoice number and date in subtitle, and latest scoped official result. Distinguish SUCCESS from buyer approval and reference eligibility; remove obsolete local-only notice. Request rebuild.

0.63.0: card opens its own invoice; success/pending/unknown direct PDF preview; rejected or unsent/draft details with edit. Rejected original immutable, explicit new invoice only on user save; finalized unsent reopened transactionally without new debit, PREPARED may be discarded only before claim. Add routing and credit-preservation regression tests. Request signed build.

Preserve existing draft edit label for accessibility/regression; preflight failure is unsent, not an ambiguous inquiry. Successful event preview is read-only with PDF output; normal manual preview retains prior controls.

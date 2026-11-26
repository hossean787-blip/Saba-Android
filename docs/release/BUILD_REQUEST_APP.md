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

0.65.0: Rejected invoices reopen the same local invoice ID and reuse its recorded credit, retaining rejected request payload and error history in schema 16. New fiscal serial prevents reusing prior tax IDs. No retry of ambiguous or accepted requests. Preserve history in account archive, accept schema 15 backups, show bounded official error details in the editor. Add transaction and migration regression coverage. Request signed APK.

Split historical UI assertion: accepted invoice remains immutable, finalized unsent offers edit but not delete. Verify actual error description and missing-error fallback. Request signed rebuild.

0.67.0: preserve never-posted tax identity via RESERVED state; lock serial and issue date after allocation; rebuild payload with original issue timestamp and same tax ID/UID; guard concurrent stale send; fix Compose lint on rejection history. Existing definitive FAILED retry policy unchanged pending protocol verification. Request signed build.

0.69.0: unified PDF preview for all event cards, show status and error, conditional edit/send/inquiry, retain PDF-only successful view; return to preview after saving/editing/sending. Add action matrix regression coverage. Request signed APK.

0.71.0: compact taxpayer identity, four icon navigation tabs in one row, rounded search with clear action, unified lazy page scrolling for catalog/results/saved/manual Excel, preserving catalog sources and validation. Request signed build.

0.73.0: polish company invoice list with compact gradient credit panel, unified scrolling, icon refresh, full-width creation, rounded search with clear, count filters and colored official status cards. Preserve invoice detail navigation, receipt data and all edit/send/credit behavior. Request signed APK.

0.75.0: preserve spaces, Persian ZWNJ and hyphens through per-keystroke invoice supplementary JSON editing. Retain length and attachment guards; trim only at final persistence. Audit customer/taxpayer/identifier text entry; no change to numeric/identity validation. Add sequential typing and serialization regression tests. Request signed APK.

0.77.0: RSA 2048 export roundtrip verified by actual signing and semantic parameters; PEM picker compatibility, async file writing, visible progress and errors, volatile account-specific workshop state across recreation, clear after export/leave. Include previous authorized whitespace fix from parent commit. Request signed APK.

Direct-only production mode blocks all certificate-based readiness and historic V2 inquiry routes before network; legacy records remain intact and user is directed to official portal. Add guard regression tests. No changes to APK signing or secure TLS.

0.79.0: start correction from accepted original PDF/details; verify unbound legacy reference without POST, open existing referred document instead of duplicate, prefill buyer/lines/settlement and independent serial without debit until save. Preserve original. Simulated direct submission regression; update old unknown fixture to LEGACY for certificate-free inquiry. Real correction acceptance pending. Request signed APK.

0.79.0 rebuild: fix Kotlin local function declaration order; place exact «صورتحساب اصلاحی» action directly beneath PDF export. Preserve verified parent and no certificate route.

0.81.0: link original to correction in event/list/PDF/details; distinguish draft, pending, rejected and registered correction. Do not issue separate cancellation or assert buyer reaction/cancellation from posting success. Existing direct inquiry API does not provide verified buyer reaction; confirm legal status in portal. Add link regression tests. Signed build requested.

0.83.0: bind documented certificate-free SUCCESS inquiry without returned taxId using matching existing reference, fiscalId, UID, confirmation ID and immutable payload hash/header. Reject conflicting or missing evidence; no original resubmission. Signed build requested; live correction remains unverified.

0.85.0: enable correction picker for new invoice form even when no fully bound receipt exists; show same customer accepted originals and reuse read-only reference verification and existing credit-idempotent save. No tax POST during selection. Regression for owner/customer/pending isolation; signed build requested.

0.87.0: premium RTL support UI; brand header, segmented tabs, category selection, ticket cards, empty states, Persian dates, conversation bubbles and composer. Existing API, journal, pagination and validation unchanged. Signed APK requested; physical visual review pending.

0.89.0: company invoice list opens exactly the same direct PDF preview as event list for all statuses; preserve origin-specific back navigation and state-safe edit/send/correction. Align regression tests with new picker and shared preview; previous CI 490/492, two stale correction UI tests. Signed build requested.

0.91.0: real 0.89 device still blocked by mandatory echoed receipt fields. Authenticate and sign UID inquiry scoped to original fiscal memory; require SUCCESS, exact stored UID/reference, unchanged payload/taxId and no contradictory echoes. Optional echoes/confirmation ID may be absent. Distinguish evidence from v2 signature, preserve old receipts; add precise diagnostics and negative regressions. No new POST or debit during reconciliation. Source: technical self-TSP inquiry documentation https://rahrokh.com/technical-instructions-on-how-to-connect-to-modian-system-2/ . Live device response not inspected; real acceptance pending.

0.93.0: unify in-editor unsaved preview with actual A4 PDF renderer using in-memory draft, no save/debit/export/send; after save open same PDF preview as both lists. Keep editor state on back; tests check no credit or records and draft navigation. User 0.91 screenshot shows correction submitted/pending; final authority acceptance unverified. Signed build requested.

0.95.0: manual pending inquiry independently checks stored tracking number using signed self-TSP INQUIRY_BY_REFERENCE_NUMBER, validates matching UID/reference/fiscal/tax ID, records last inquiry time and status of each path. No invoice repost, debit or certificate. Source technical spec https://rahrokh.com/technical-instructions-on-how-to-connect-to-modian-system-2/ . Actual pending invoice requires on-device inquiry; live outcome not verified. Added gateway and service regression tests.

0.97.0: repair Lint ProduceStateDoesNotAssignValue without suppression by using remember + LaunchedEffect for draft preview seller. CI for 0.95 had 493/493 passing tests but Lint failed. Audit confirms mapper sends ins=2, irtaxid=original, new tax ID, matching type/pattern/buyer. Preserve immutable already-posted invoice. Add bounded taxResult/packetType to pending diagnostics and fallback rejection code, with parser test. Cross-reference request format agrees with technical sample. Actual delayed correction response not available; no claim of root cause or live acceptance. No resend, no debit, no credentials in diagnostics.

0.99.0: priority queue for new corrections only if fresh authenticated tax token advertises self-tsp.async.fast-enqueue; otherwise normal queue, no retry after POST. Shared serial allocation preserving legacy reservations and fiscal history, no renumbering. Tax ID/reference preflight, time-range error inquiry (max 7 days, exact UID), foreground auto-inquiry every minute while pending invoice visible. No background transmission, certificate or extra debit. Live fast permission/acceptance unverified. Source RC_TICS.IS_V01 pp16,28,64 and notice 31. Tests added/updated; signed build requested.

0.101.0: submission management button beneath preview send/inquiry action, management filter, guarded read-only diagnostics and current/archived status history, explicit local cancellation preserving identity/credit, rejected reopen and original-only cancellation draft after user attests legal prerequisites. Pending/unknown never deleted or resent. Cancellation of accepted correction chains remains unsupported; no fabricated approval. Build requested. Historical per-inquiry times absent before audit support.

0.101.0 final build request: durable audit timeline for future send claims/acks/inquiries/reference checks and local cancellations; Room migration 16->17, archive schema 15/16 backwards compatibility, restore historical audit without accepting old receipts. UI displays up to 100 recent events; old events cannot be reconstructed. Added archive, action-gate and service regression tests. No live financial posting performed.

0.101.0: fix compilation of management cancel callback by targeting concrete transactional InvoiceRepository rather than draft-gateway interface; re-request signed build.

0.101.0: update explicit Room migration test chains to include 16->17, ensuring legacy schema migration tests reach current version. Signed build requested for final commit.

0.103.0: repair stale never-prepared invoice serial on the same invoice before send confirmation; preserve original credit and lines; reject low manually entered numeric serial before debit; immutable pending/accepted/prepared records never renumbered. Added reproduction of old low draft and 2000000001 pending identity, credit and identity regression checks. No real tax submission. Signed APK requested.

0.105.0: restore separate display counters (original 1000000001+, referred 2000000001+); fiscal serial is allocated independently from durable fiscal history and kept immutable for retries. Existing draft numbers never changed. Migration from shared counters uses retained ranges and existing records. Added tests for principal after higher pending fiscal identity and independent numbering. No actual tax submission. Signed build requested.

0.107.0: dedicated cancellation action on PDF preview of eligible accepted original/correction; explicit confirmation, unchanged source record, active child and pending guards, linked cancellation status without assuming buyer approval, tests for accepted correction and blocked pending source. Cancellation shares existing referred visible range; official serial allocation, transport, signing, credit policy unchanged. No real submission. Signed build requested.

0.109.0: cancellation uses same existing permission-gated priority routing as correction; normal queue if fresh token lacks exact permission. Original unchanged; no extra POST, no automatic retry, no credit or numbering change. Added routing regression checks. Actual authority availability and processing duration are not guaranteed. Verified previous 0.107 CI and signed APK succeeded. Signed build requested.

0.111.0: fix shared Excel XML parser compatibility with Android while rejecting DTD/external entities, bound all expanded ZIP entries, preserve text and leading zeroes using standard XLSX styles, normalize numeric scientific notation, reject formula cells; retain selected Excel operation and taxpayer across document-picker recreation and accept generic MIME providers. Added regression tests. Actual handset/file-provider test remains required. Signed build requested.

0.113.0: initial three-sheet Excel invoice import, selected taxpayer only, existing/new customer resolution, preview and explicit credit authorization, original invoices only, durable per-invoice idempotency, no automatic tax submission. Added workbook round-trip tests. Signed build requested; handset and live-credit tests still required.

0.115.0: final batch Excel build request after adding Room tests for one customer across multiple invoices, duplicate replay, foreign taxpayer/missing buyer rejection and recovery after lost debit response. Back navigation is blocked during registration and credit/claim state is refreshed on return. One selected taxpayer per file, up to 100 originals/1000 data rows; registration is sequential and recoverable, not an atomic remote multi-invoice debit. Actual server debit and on-device picker not tested here. No changes to payment or tax transport. Signed APK requested for this exact revision.

0.117.0: CI for 0.115 ran 509 tests, 504 passed, five existing InvoiceScreenTest flows failed when locating invoice-details action after an extra header row was added. Restore original header height by placing manual and Excel entry side by side. No tests removed or weakened. Re-run full CI and request signed APK for this revision. No live debit or tax send performed.

0.119.0: premium dedicated draft-filter UI and dashboard navigation; accurate total, newest draft first, draft subject and tax amount, search-aware empty state, direct safe edit and PDF preview. Existing credit, official serial, transmission and Excel logic unchanged. Added draft dashboard navigation/identity/credit regression. Signed build requested.

0.121.0: draft/local/saved preview uses the exact exported A4 PDF bytes with 3x raster rendering, bounded pinch/double-tap/pan, zoom buttons and expanded viewer. All PDF painted digits normalized to Persian (Latin and Arabic-Indic), including identifiers in display only; existing bundled Vazirmatn used for both. Payload and storage unchanged. Added zoom bounds and digit regression tests. Signed build requested. Device visual and pinch validation remains required.

0.121.0 QA follow-up: exact identity at fit scale even after negative pan; Compose UI test exercises zoom controls and expanded view.

Stabilization pass: fix experimental transformable API compile error; cancellable off-main PDF generation/rendering with per-document/page fresh state; cache invoice list lookup/count/filter and draft total. Keep fiscal transport, storage, payments, and credit unchanged. Rebuild and run regression tests.


0.123.0 / 123 (full 124): SURA Security Shield, optimized R8 release minification, non-debuggable release, startup package/signer verification pinned to established signing certificate. No new key, no store publication; signed test APK only. Existing full test, lint and signer gates must pass. See SURA_SECURITY_SHIELD_2026-11-26.md.

0.125.0: app-wide visual system; existing Vazirmatn, shared semantic palette/typography/shapes; align navigation, dashboard, taxpayers, customers, invoice editor, drafts, identifiers, purchase, receipt, account, settings, support, about and account services. Core status colors preserved. PDF layout, financial logic, storage, network and signer integrity untouched. Added palette contrast regression. Prior checkpoint ed81bbba3e744200a350521157c642f0120d083c. All CI and signed release gates and physical-device visual checks pending.


0.127.0 / 127 (full 128): remote hardware attestation observation pilot only. No existing service enforcement. Existing test/lint/signing gates retained. Owner testing only.

0.129.0: copy finalized original/correction from invoice event cards, list and PDF preview into NEW original editor; same or another active customer, new date/serial/request, all lines and pricing retained; no reference, receipt, payment tracking, buyer branch, schedule or attachments copied. Original remains unchanged; one credit only on explicit new save, no automatic tax submission. Added end-to-end local UI regression. Signed build requested.

0.129.0 build prerequisite: fix pre-existing palette token/else whitespace syntax error in customer screen (previous CI 45800588919). No logic change. Re-run full build.

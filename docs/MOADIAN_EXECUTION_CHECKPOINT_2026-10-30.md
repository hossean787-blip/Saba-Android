# SURA Moadian implementation checkpoint — 2026-10-30

Status: IN PROGRESS. Not completed or tested.

Authorized scope: replace current Companies section with a secure full Moadian CRUD form, four types, validation, encrypted private keys, non-destructive removal, search, UI, tests, APK. Only this project.

Repository: hossean787-blip/Saba-Android
Branch: main (no branch created)
Last verified code-edit commit before this checkpoint: a96cbd1f7bfa58648fc31da6c1b413cfb900810e

Inspected:
- MainActivity.kt: existing Page.COMPANIES invokes CompaniesScreen(db, companies,...); dashboard, customer/invoice relationships depend on Company.id.
- data/SabaDatabase.kt: Room version 2, migration 1->2, CompanyDao insert/update/all/byId, no delete.
- data/Models.kt: companies table with purchaserId/name/nationalId/economicId/address/phone and unique nationalId. Customer and Invoice FK reference Company.id RESTRICT.
- Existing CompaniesScreen implementation was not located in this session; do not assume it supports new requirements.

Implemented:
- Renamed visible main navigation/dashboard labels from شرکت‌ها to مودیان and related selection/add labels in MainActivity.kt.
- Existing Room schema preserved; no existing data deleted.

Modified files: app/src/main/java/ir/saba/invoice/MainActivity.kt
Tests run: NONE. No build, device test, UI test or security test performed.
Errors found: existing schema does not represent all requested Moadian fields; no key storage or status. No verified current tax-id rules. No source location for CompaniesScreen yet.
Errors fixed: MainActivity visible section terminology only.
Remaining: locate CompaniesScreen source; implement actual form, confirmation dialogs, list, search and edit; design safe migration for separate details table linked to Company.id; encrypt key material via Android Keystore and avoid secrets in logs; implement soft deletion; verify official validation rules; test migration, CRUD, crypto, UI and APK.
Next step: inspect repository tree and existing CompaniesScreen implementation before modifying schema/UI; preserve stable Company IDs and all FK relationships. Do not claim completion before tests.

## Continuation checkpoint
Last code commit: 2dcd1f5e355c567d3d4d198598eeae9b70f08f23
Implemented (UNTESTED): app/src/main/java/ir/saba/invoice/ui/MoadianScreen.kt created and wired through MainActivity.kt. Existing Room CompanyDao used for create/edit; list/search/selection; confirmation before create/edit; deletion deliberately blocked to preserve related records; no destructive migration.
Files changed: app/src/main/java/ir/saba/invoice/ui/MoadianScreen.kt, app/src/main/java/ir/saba/invoice/MainActivity.kt, this checkpoint.
Tests executed: none. Build not run. Not production ready.
Critical remaining: new form is intentionally incomplete and must NOT be presented as fully compliant: 17 fields, dependent province/city, full type-specific validation, real key status, encryption with Android Keystore, key import, non-destructive deactivation and Room migration, official tax documentation verification, automated tests, APK.
Known issues to investigate: new screen Compose build correctness, screen vertical scrolling for editing, list layout on narrow devices; purchaserId fallback "owner" must be replaced by authenticated owner context before production.
Next step: inspect/build current code, fix compile and layout errors, implement details entity migration and secure key vault; do not silently overwrite historical records.

## Database phase — 2026-10-30
Status: IN PROGRESS / UNTESTED.
Commits: de97e6ea (MoadianDetails model), d74c30aa (Room version 3 and migration), 32c6cca3 (migration default correction).
Files: data/MoadianDetails.kt; data/SabaDatabase.kt.
Room details are stored separately from Company and its existing invoice/customer foreign keys. No existing rows are deleted. The new details table is NOT yet connected to the UI. No private key is stored.
Tests: NONE executed; migration integrity and generated Room schema validation are still unverified. Next: implement and wire complete fields and safe persistence, then build and run migration tests. Do not mark this as complete.

## Next checkpoint
Last code commits: f6921699 (editor scroll fix and truthful unknown taxpayer type), 2027522b (MoadianFieldValidation format checks).
Modified files: ui/MoadianScreen.kt; domain/MoadianFieldValidation.kt.
Tests: NOT RUN. No APK. Format validator is not yet connected to the form. Full 17-field UI and private-key storage remain blocked on implementation, not external permissions. Existing Room v3 migration has not been validated on device. Next: connect details DAO to form transactionally, preserve drafts on errors, implement safe key handling, run build/tests. Never claim complete.

## Form/Room integration checkpoint
Code commit e43d5166b1e3ed0767e7b38da0de7b3e77083b3b: ui/MoadianScreen.kt now loads/saves non-secret details in a Room transaction together with Company create/edit, restores fields on edit, displays stored taxpayer type and applies preliminary format checks. No private key material is accepted or persisted. No tests/build executed. Critical outstanding: missing logo picker, key vault/import and mandatory key, dependent province/county, type-specific official tax rules, inline help and error presentation, safe deactivation, full acceptance tests. Current partial registration must not be represented as compliant or production-ready. Last code commit e43d5166. Next action build and resolve compilation/migration failures before extending features.

## Resumed implementation — security foundation
Baseline f7ca32a had 137 executed tests: 136 passed, DatabaseMigrationTest failed because it registered only 1->2 against schema 3. fea86c1 corrects the fixture and full migration path; CI pending.
RSA parser commit ed5001b adds bounded PKCS8/PKCS1 parsing, minimum 2048-bit RSA and local signing/verification. This is not fiscal-memory matching.
This change adds Android Keystore AES-256-GCM encryption bound to Company.id via AAD; Room migration 3->4 adds nullable ciphertext; manual/file entry shares one draft value (64 KiB limit). Private key is never saveable state or logged. Key status remains NEEDS_MATCH, never VERIFIED.
Files: security/TaxpayerPrivateKey.kt, security/TaxpayerKeyVault.kt, data/MoadianDetails.kt, data/SabaDatabase.kt, ui/MoadianScreen.kt, security/TaxpayerPrivateKeyTest.kt, data/DatabaseMigrationTest.kt.
Fixed: new form incorrectly inherited selected taxpayer values; new duplicate check incorrectly excluded selection; edit preserves existing ciphertext/logo/inactive fields.
Tests: added parser rejection/self-check tests; CI result pending. Keystore hardware/device behavior not tested. No production readiness claim.
Remaining: dependent province/county, logo import, inline help/errors, official current rules verification (search found conflicting/older material; do not certify), archive behavior, persistent drafts/configuration handling, owner separation, serial allocation integration, complete UI/CRUD/security tests. Continue from this commit on main. No data deleted.

## Safe archive and form continuation
Previous security commit: 1bb1b44d0a1a7878088da21b689b787b840f6994. Build/tests pending in GitHub Actions.
Added reversible taxpayer deactivation; every row remains visible with inactive label; main selector excludes inactive taxpayers; historical Company/Customer/Invoice rows remain untouched. Saving an inactive taxpayer reactivates it.
Submit/confirmation controls moved outside scrollable fields. Added field-specific guide dialogs, standard Material action icons and sky-blue application palette.
Added MoadianPersistenceTest: reopen persistence, archive with invoice relations intact, isolation of details by Company.id, restoration, transaction rollback.
Files: ui/MoadianScreen.kt, MainActivity.kt, test/data/MoadianPersistenceTest.kt, this report.
Not yet verified: CI pending. Remaining acceptance gaps from previous checkpoint still apply; especially official rules, dependent selectors, inline errors/logo, authenticated owner partition and invoice sequence integration. Do not call this complete.

## Validation and CI correction
Last tested revision 5691a8a0: CI run 41010343379 executed 143 tests: 142 passed, 1 failed. Migration 1->4 and archive/persistence/rollback tests passed. RSA valid-key acceptance failed with ClassCastException confirmed by run 41010719384. Fixed assumption that RSA private keys expose CRT fields: read public exponent from bounded DER and use RSAPrivateKey interface; retain sign/verify self-test.
Added dependent searchable province/county selectors from attributed CC-BY-4.0 Open Admin Data snapshot (31/429; not a guarantee of latest divisions), bounded private app-storage logo import, inline errors and Persian/Arabic digit normalization. Added type-specific local form identity checks and transaction-scoped duplicate economic/fiscal identities.
Added cipher randomness/tamper/cross-taxpayer tests and format/location tests. CI pending for this change.
Official-rule verification: intamedia.ir fetch timed out on 2026-10-30. Older primary tax documents mirrored elsewhere support 11/14 economic and type-specific identities but current exceptions have NOT been certified. Form convention for natural persons intentionally follows requested economic-ID field semantics; do not reuse as legal national-code semantics in invoice payloads.
Remaining: certify current rules, device Android Keystore/import/UI tests, owner-account partition (legacy purchaserId owner), serial allocation integration, complete UI acceptance and visual review; newer location data coverage; orphan-logo cleanup. No production readiness claim.
Next: inspect CI, repair actual failures, retain evidence and APK only after successful checks.

## Verified checkpoint and sequence integration
Last green code/rollback point: 22f2b12403f47241926832db8160d5da0fc7e45b.
CI run 41011091311 / job 123916890812: BUILD SUCCESSFUL; 149 JVM tests passed, zero failures/errors/skips; 3 live authentication rejection smoke tests passed; lint/build succeeded.
This next change adds schema 5 nullable nextInvoiceSerial/nextReferenceSerial with additive migration 4->5; reservations are transactional, taxpayer scoped, persistent across draft deletion, and retain independent main/reference counters. Invoice form empty number requests automatic numbering for configured taxpayers; legacy companies still require a number. Inactive taxpayers cannot save new drafts.
Preserves counters on taxpayer edit. Fixes create retry after refresh failure by retaining the committed taxpayer identity; blocks saving after failed edit data load.
New tests: MoadianScreenTest for selection/new blank form and confirmed archival; TaxpayerSerialAllocatorTest for concurrency, isolation, no reuse after deletion and rollback.
Modified: data/MoadianDetails.kt, SabaDatabase.kt, TaxpayerSerialAllocator.kt, InvoiceRepository.kt; ui/MoadianScreen.kt, InvoiceScreen.kt; test/data/DatabaseMigrationTest.kt, TaxpayerSerialAllocatorTest.kt; test/MoadianScreenTest.kt.
Current new tests/changes pending CI. Remaining: official-current-rule certification, device Keystore/file provider validation, full visual/form acceptance; legacy owner context migration is not performed. No existing data deleted. Reference reservation service exists; reference-invoice workflow is outside the currently existing Invoice model.

## Verification checkpoint — 5e0db209
Commit 5e0db2095b71e52fff15e38f555e201355988caf passed CI run 41011489252 / job 123918154605, including migration through schema 5, UI selection/new-empty form and confirmed archive, serial concurrency/rollback/isolation/no-reuse checks.
2026-10-30T06:02:19.5913934Z JUNIT_SUMMARY {"tests": 153, "failures": 0, "errors": 0, "skipped": 0, "passed": 153}
2026-10-30T06:02:20.2636985Z Ran 3 tests in 0.587s
This follow-up freezes editable fields while confirmation/saving is active to prevent post-validation mutation. Uses original user-provided SURA_Complete_Design_Handoff.zip / 01_Brand_Assets/sura_icon_512.png as launcher and auth-header image. PNG 512x512 RGBA, archive-entry CRC verified; no PDF/HTML imported. Files: ui/MoadianScreen.kt, auth/AuthTheme.kt, AndroidManifest.xml, res/drawable-nodpi/sura_brand_icon.png.
Pending: final CI on this follow-up; official latest tax-rule exceptions and fiscal-ID alphabet must be verified against current official publication (site timeout), real device AndroidKeystore/file selection/install, exhaustive visual/form acceptance. Current key status intentionally NEEDS_MATCH; no online fiscal-memory key match performed. Legacy owner attribution must not be reassigned without evidence. Full mission is still IN PROGRESS; use final artifact only as a test build.

## Review of current main and race fix
Observed newer main than previous checkpoint: Room schema version 5, encrypted private key envelope, logo picker, province/county picker, field validation, soft deactivation, serial state and tests are present in source. This is code inspection only, NOT evidence of successful build or tests. Applied commit 427e70b6: province/county selection disabled while save confirmation or database operation is in progress, preventing form mutation after confirmation. No test or APK execution in this session. Next: inspect actual CI results and run Gradle build, migration tests and instrumentation before release. Do not infer pass from test file existence.

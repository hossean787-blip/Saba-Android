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

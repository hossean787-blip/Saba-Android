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

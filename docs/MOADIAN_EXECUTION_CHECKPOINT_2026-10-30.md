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

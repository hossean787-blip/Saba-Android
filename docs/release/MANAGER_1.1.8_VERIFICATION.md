# SURA Manager 1.1.8 — verification record

- Date: 2026-11-27
- Branch: `main`
- Release commit: `00f44ee36166deb8371115e6a8db46621accdd07`
- App ID: `ir.sabagostar.suramanager`
- Version: `1.1.8` (`versionCode=11`)
- APK artifact: GitHub Actions run `45971827009`, artifact `15185402585`
- APK SHA-256: `b3eed6c5f49fae4fb4aac0387ca56a5c49edc7dcc0151a2236a745eda63c7a1c`
- Signing certificate SHA-256: `55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b`
- Previous release 1.1.7 certificate fingerprint: identical; package ID unchanged and versionCode increased from 10 to 11.
- Supabase project: `saba-auth` (`fiiitzfbdjzpmepzjsjw`)
- Edge Function `sura-manager-api`: deployed version 9, ACTIVE, JWT verification enabled.

## Changes
- Applied measured Android system navigation-bar inset to the WebView bottom navigation.
- Added owner display name from Auth user metadata and email in the manager directory and account details.
- Added manager-only owner lookup by registered name or email for wallet credit grants.
- Added associated owner name/email columns to each linked taxpayer-wallet row.
- Regenerated WebView assets and compiled manager API during CI builds.

## Verification
- Manager source verification workflow: success (run `45971749312`).
- Static guards, generated JavaScript syntax check, manager API route tests (22 cases), manager support tests, XLSX writer test, filters test and gift-flow test: passed in source verification.
- Signed release workflow: success (run `45971827009`); signing prerequisites, asset compilation, manager guards, release build, signature/package/version/archive validation and artifact upload all succeeded.
- Local artifact inspection: ZIP/APK archive integrity passed; package metadata verified; signing certificate fingerprint matched 1.1.7.
- Packaged APK contains the Android navigation-inset CSS and owner lookup UI.
- Supabase function deployment verified by reading back version 9; deployed source includes `owner-search` and Auth metadata name extraction.

## Known limitation
The current server taxpayer-wallet table stores owner ID, local taxpayer ID and credits, but not a distinct legal taxpayer name/email. The taxpayer rows therefore display the linked owner's registered name/email. Separate legal taxpayer identities require adding and syncing those fields.

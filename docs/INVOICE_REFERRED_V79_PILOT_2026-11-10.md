# SURA Invoice — Referred sales invoice pilot (2026-11-10)

## Reference
Iranian Tax Administration / دستورالعمل صدور صورتحساب الکترونیکی, RC_IITP.IS_V7.9, تیر ۱۴۰۵.
Source: https://thdorsan.com/law-doc/صدور-صورتحساب-الکترونیکی-نسخه-rc-iitp-is-v7-9
Sections 5-2, 5-4, 8-6, 8-8, 9-3. Numeric subject codes 1/2/4; reference field `irtaxid` and 22-character tax ID.

## Implemented
- Original type-one pattern-one: header `ins=1`, no reference.
- Correction: `ins=2`, 22-character `irtaxid`; validated previously accepted original from the same taxpayer and buyer; unchanged item identities, unit codes and VAT rates; original issue date precedes the referred document.
- Partial return: `ins=4` and `irtaxid`, item reductions rather than returned quantity, no new item identities/unit prices/VAT changes; all-quantity return remains rejected because cancellation is required.
- Before preparing an official immutable tax payload the reference is rechecked in the same Room transaction. Invalid/missing/non-accepted reference blocks preparation before governmental HTTP and does not debit credit.
- Manual send still needs user confirmation, cryptographic taxpayer credentials, future-time gate and official inquiry. No synthetic acceptance is advertised.
- UI shows the actual subject and reference on the confirmation screen. PDF print layout for ordinary original invoices stays unchanged; referred labels no longer claim impossibility.

## Deliberately excluded
- Header-only cancellation, chain referrals to earlier corrections/returns, specialized invoice patterns and type two require separate validated flows.
- No server-driven future-send automation or government live acceptance testing. Pilot mode remains explicit in the app until an authorized live test returns a verified signed receipt.
- This commit is not a commercial-release assertion.

## QA
Read the CI and signed APK logs for this exact commit. Mock receipt fixtures in local tests are not evidence of production acceptance.

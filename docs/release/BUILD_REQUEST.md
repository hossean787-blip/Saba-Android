# Authorized SURA invoice APK release — 2026-11-27

Build current main as SURA invoice 0.148.0 (versionCode 148; applicationId ir.saba.invoice), preserving the established official signing identity and upgrade compatibility. This release adds authenticated, no-OTP mobile-number entry from Account > Personal Information; after successful registration, show a reminder to enter the mobile number there. Store the number in the signed-in user's central Supabase Auth user_metadata, not only on-device.

Run JVM regression tests and lint, assemble the signed Release APK, and verify package ID, version code/name, APK archive integrity, signature and certificate fingerprint. Upload APK, signature report, package metadata and SHA-256 checksum as a GitHub Actions artifact. Do not change package ID or signing credentials, publish to a store, perform live payments, grant credits, or submit tax invoices. If any gate or signing prerequisite fails, report the exact failure and never substitute a debug key.


Rebuild requested after mobile-profile integration and regression test additions.

Rebuild after latest QA correction on main.

# Authorized SURA APK build — 2026-11-27

Build the main SURA Android application version 0.144.0 from main. The invoice registration page must contain only new invoice creation and Excel import; saved invoices must appear only in the dedicated «فاکتورها» section. Keep advertising integrations absent and preserve the restored pre-PR19 single-account biometric login flow.

Run JVM tests, Android test compilation and lint before building the signed release APK. Verify package id, version code/name, APK integrity, official signing certificate fingerprint and permissions. Upload the verified APK and checksums as workflow artifacts. Use the existing official SURA release signing key only. Do not build or publish the manager app. Do not publish to any app store. Do not change signing credentials or grant credits.

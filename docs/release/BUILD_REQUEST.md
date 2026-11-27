# Authorized SURA invoice APK release — 2026-11-27

Build the current main-branch SURA invoice app version 0.147.0 (versionCode 147; preserve applicationId ir.saba.invoice). Include the merged live sales plans and server-quoted purchase flow from PR #24. Keep the existing official signing identity; do not create a second installable app, change package ID, change signing credentials, publish to an app store, or grant credits.

Run JVM tests and lint, assemble the signed Release APK, and verify package ID, version code/name, APK archive integrity, APK signature and certificate fingerprint. Upload the verified APK, signature report, package metadata and SHA-256 checksum as a GitHub Actions artifact. If required signing secrets are unavailable or the signing certificate cannot be verified, fail safely and report the exact blocker; never substitute a debug key.

## Final signed owner-test build — 0.147.0 (2026-11-27)

Build the exact current `main` revision as the standard SURA invoice variant: `applicationId=ir.saba.invoice`, `versionCode=147`, `versionName=0.147.0`. Preserve the established release signing certificate and upgrade compatibility. Run the complete regression, instrumentation compilation and lint gates defined by `.github/workflows/android-signed-release.yml`; assemble the minified signed release APK; verify package identity/version, signer SHA-256 fingerprint, merged permissions, APK signature and ZIP integrity; upload the APK and verification reports as workflow artifacts. Do not publish to a store, perform live payments, grant credits, or submit tax invoices. If any gate or signing prerequisite fails, stop and report the exact failure; never substitute a debug or new signing key.

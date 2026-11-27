# Authorized SURA invoice APK release — 2026-11-27

Build the current main-branch SURA invoice app version 0.147.0 (versionCode 147; preserve applicationId ir.saba.invoice). Include the merged live sales plans and server-quoted purchase flow from PR #24. Keep the existing official signing identity; do not create a second installable app, change package ID, change signing credentials, publish to an app store, or grant credits.

Run JVM tests and lint, assemble the signed Release APK, and verify package ID, version code/name, APK archive integrity, APK signature and certificate fingerprint. Upload the verified APK, signature report, package metadata and SHA-256 checksum as a GitHub Actions artifact. If required signing secrets are unavailable or the signing certificate cannot be verified, fail safely and report the exact blocker; never substitute a debug key.

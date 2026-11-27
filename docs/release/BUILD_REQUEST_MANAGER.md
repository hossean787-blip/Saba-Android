# Authorized SURA Manager APK release — 2026-11-27

Build the current main-branch SURA Manager app version 1.1.8 (versionCode 11; preserve applicationId ir.sabagostar.suramanager). Include the live sales-plan administration merged in PR #24. Keep the existing official SURA signing identity; do not create a duplicate app, change package ID, change signing credentials, publish to an app store, or grant credits.

Run static guards, backend route and manager tests, assemble the signed Release APK, run lint, and verify package ID, version code/name, APK archive integrity, APK signature and certificate fingerprint. Upload the verified APK, signature report, package metadata and SHA-256 checksum as a GitHub Actions artifact. If required signing secrets are unavailable or the signing certificate cannot be verified, fail safely and report the exact blocker; never substitute a debug key.

Retry note: the version assertion in static_guard.py was corrected to versionCode 10; run the complete release validation again against current main.

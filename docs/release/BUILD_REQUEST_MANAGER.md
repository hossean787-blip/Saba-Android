# Authorized SURA Manager APK release — 2026-11-27

Build current main as SURA Manager 1.1.9 (versionCode 12; applicationId ir.sabagostar.suramanager), preserving the established official SURA signing identity. Include the new protected «بانک اطلاعات» page, showing each user's name, email and saved mobile number. Mobile numbers are sourced from authenticated user metadata; do not infer or fabricate missing values.

Regenerate WebView assets and compiled manager API from sources. Run static guards, backend route and manager tests, assemble the signed Release APK, run lint, and verify package ID, version code/name, APK archive integrity, signature and certificate fingerprint. Upload APK, signature report, package metadata and SHA-256 checksum as a GitHub Actions artifact. Do not change package ID or signing credentials, publish to an app store, or grant credits. If any gate or signing prerequisite fails, report the exact failure and never substitute a debug key.


Rebuild requested after bank-directory mobile display and updated release guards.

Rebuild after latest QA correction on main.

Final rebuild trigger: includes all mobile profile lookup and directory QA changes on main.

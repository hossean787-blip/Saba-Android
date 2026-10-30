# SURA support screenshots — 2026-10-30

Scope: SURA Android main only; user reported that Android prevented screenshots of signup errors.

## Root cause
MainActivity set WindowManager.LayoutParams.FLAG_SECURE whenever the authentication session was null, which includes the entire login and signup screens. As a result, Android refused screenshots of registration error messages.

## Corrective change
- On foreground activity resume, clear FLAG_SECURE to allow normal screenshots on signup, login and other app screens.
- On activity pause, add FLAG_SECURE to protect the sensitive app snapshot when leaving the foreground.
- Password fields remain masked by default. Password values stay in non-saveable Compose state only; no passwords are logged or added to backups.
- Added Compose UI regression for screenshot flag on login and signup, and Robolectric lifecycle regression for foreground/background transitions.
- No database, auth server, identity data, schema or account permissions changed.

## QA status
Await GitHub Actions JVM / APK build evidence for the new commit. Manual device screenshot and app-switcher behavior not yet verified. This is a debug APK, not commercial approval.

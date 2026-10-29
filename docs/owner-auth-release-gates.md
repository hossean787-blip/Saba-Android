# Owner email/password authentication checkpoint — 2026-10-30

Scope: continue main from 2cc50665; no new branch. SMS remains outside this assignment.

## Implemented in this change (verification pending CI)
- Email/password registration and login through the existing deployed gateway v4.
- Removed national-ID/mobile collection from the active email/password form: gateway v4 does not persist those values. Legacy identity/OTP helpers remain inactive.
- Shared RTL navy/teal/gold authentication design; responsive bounded forms, safe system insets, field-specific validation, password visibility, loading/disabled states.
- In-memory ViewModel session across Activity recreation. Process death intentionally requires login again. No tokens or passwords in preferences, saved state, backups or logs.
- Explicit logout confirmation in settings, immediate local lock, backend logout with accessToken (previous client incorrectly sent refreshToken).
- Offline logout stays locked and explains that remote revocation was not confirmed. No offline revocation claim.
- Cancellation propagates; errors cannot display raw backend messages. HTTPS only, redirects disabled, invalid/empty session response rejected.
- FLAG_SECURE prevents screenshots/recents capture in the release APK and debug APK.

## Acceptance evidence
- Previous baseline 2cc50665: CI run 40951950965 succeeded (JVM suite, APK, instrumentation APK compilation, lint).
- Added gateway transport, validation, ViewModel and Compose tests. Their execution is pending the CI run for this change.
- CI writes exact counts from JUnit XML, preserves JSON summary and lint reports, runs 3 non-destructive live rejection-path smoke tests and uploads the debug APK.
- Live smoke does not create accounts or send email/SMS. It does not prove successful real email verification or login.
- Final installation, real owner signup/email confirmation/login/logout and visual inspection on the user's phone are pending user acceptance, as assigned by the user.
- No Android Emulator/device test execution claimed by this checkpoint.

## Recovery
The baseline commit 2cc50665d9b065e42cdd4add13632d014160350e is the Git recovery point.
This change does not deploy a backend or modify schema, billing, identity uniqueness rules or SMS settings.
This authentication checkpoint does not certify the whole app for commercial release.

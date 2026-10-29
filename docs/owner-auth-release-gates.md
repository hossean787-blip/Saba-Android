# Owner email/password authentication checkpoint — 2026-10-30

Scope: continue main from 2cc50665; no new branch. SMS remains outside this assignment.

## Implemented
- Email/password registration and login through the existing deployed gateway v4.
- Removed national-ID/mobile collection from the active email/password form: gateway v4 does not persist those values. Legacy identity/OTP helpers remain inactive.
- Shared RTL navy/teal/gold authentication design; responsive bounded forms, safe system insets, field-specific validation, password visibility, loading/disabled states.
- Bundled Vazirmatn Regular/Bold, downloaded from its official repository with OFL license. No runtime font download.
- In-memory ViewModel session across Activity recreation. Process death intentionally requires login again. No tokens or passwords in preferences, saved state, backups or logs.
- Explicit logout confirmation in settings, immediate local lock, backend logout with accessToken (previous client incorrectly sent refreshToken).
- Offline logout stays locked and explains that remote revocation was not confirmed. No offline revocation claim.
- Cancellation propagates; errors cannot display raw backend messages. HTTPS only, redirects disabled, invalid/empty session response rejected.
- FLAG_SECURE prevents screenshots/recents capture in the release APK and debug APK.

## Acceptance evidence
- Previous baseline 2cc50665: CI run 40951950965 succeeded (JVM suite, APK, instrumentation APK compilation, lint).
- Commit 1ebd60cee56ebfc62d4256084f7ad980f6b30128: CI run 40953263515 passed 136 JVM/Compose tests (0 failures, 0 errors, 0 skipped), 3 live rejection-path smoke tests, APK build, instrumentation APK compilation and lint. Full job logs: job 123726654024.
- The font integration is validated by the subsequent main CI run; its JUnit summary, lint reports and APK are uploaded as artifacts. Require a successful result on the final main SHA before delivery.
- CI writes exact counts from JUnit XML, preserves JSON summary and lint reports, runs 3 non-destructive live rejection-path smoke tests and uploads the debug APK.
- Live smoke does not create accounts or send email/SMS. It does not prove successful real email verification or login.
- Final installation, real owner signup/email confirmation/login/logout and visual inspection on the user's phone are pending user acceptance, as assigned by the user.
- No Android Emulator/device test execution claimed by this checkpoint.

## Recovery
The baseline commit 2cc50665d9b065e42cdd4add13632d014160350e is the Git recovery point.
This change does not deploy a backend or modify schema, billing, identity uniqueness rules or SMS settings.
This authentication checkpoint does not certify the whole app for commercial release.

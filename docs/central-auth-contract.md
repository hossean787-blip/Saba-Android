# Current Saba authentication contract — 2026-10-30

Active scope: owner registration and login with email and a persistent user-chosen password. SMS/OTP is disabled in the active Android UI.

## Existing deployed gateway v4
Base: https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/saba-auth-gateway

- POST /v1/auth/registration/start: firstName, lastName, email, password. Returns accessToken + refreshToken when a session is issued, otherwise HTTP 202 with status=email_confirmation_required.
- POST /v1/auth/login/password: identifier (email), password. Returns accessToken + refreshToken on success.
- POST /v1/auth/logout: accessToken. The gateway calls Supabase Auth logout with Bearer accessToken. A refreshToken in this request is incorrect.
- Non-2xx responses are failures. A successful response with empty/missing tokens is never accepted as an authenticated session.
- The gateway uses its server-side Supabase anon key; no service-role key, SMS key or password storage is added to Android.

## Android behavior
- Registration requires first/last names (1–100 trimmed characters), valid email, matching nonblank passwords of 8–128 characters.
- The active form does not collect national ID/mobile because this gateway does not persist or enforce their uniqueness. Existing inactive OTP helpers are outside this delivery.
- Network requests are HTTPS only, no redirects, 15-second connect/read timeouts, no raw backend errors shown to the user.
- Session lives only in a ViewModel: retained during Activity recreation, cleared on process termination. There is no disk session restoration or remember-me claim.
- Password fields use remember rather than rememberSaveable; passwords are cleared after submission. Session.toString is redacted. Screenshots/recents capture is disabled.
- Logout confirmation locks local access immediately, then calls remote logout. Offline failure leaves the app locked and explicitly reports remote revocation as unconfirmed.
- Back or navigation cannot launch a second overlapping auth request.
- Owner signup/email confirmation/real login/visual acceptance on the phone remain user acceptance tests. Technical contract tests must not be represented as those manual tests.

## Verification
Run existing main CI: JVM/Compose regression tests, APK and instrumentation APK compilation, lint, three non-destructive live rejection-path checks. Exact JUnit counts are in saba-test-and-lint-reports/auth-verification/junit-summary.json; no accounts, email or SMS are created by the smoke script.

## Deferred requirements
SMS, national-ID uniqueness, mobile verification/change, persistent encrypted session restore and app-wide owner data isolation require separate implementation/acceptance. This checkpoint certifies only the assigned email/password authentication work, subject to the stated manual acceptance tests.

# SURA registration error handling checkpoint

Scope: Android owner signup UI + safe HTTP error classification only. Branch: main.

## Root cause confirmed in source
The original button handler invoked onSubmit then immediately cleared both password fields before the asynchronous gateway response. A remote rejection therefore left the form invalid, giving the impression of a password confirmation mismatch, even when the two input values had matched.

## Change
- Keep non-saveable password and repeat-password values after rejected requests; screen disposal on successful signup clears them.
- Show explicit "دو رمز عبور مطابقت دارند." feedback when the field values match and local length checks pass.
- Preserve existing strict byte-for-byte password comparison and current length policy; never silently trim, normalize or log passwords.
- Distinguish a registration HTTP 400/409/422 from a local mismatch, without reading/printing raw server response bodies.
- UI test: matching passwords persist after simulated backend rejection.
- UI test: different passwords display a mismatch and disable submission.
- HTTP client test: server rejection message remains safe and does not falsely attribute the error to confirmation.

## Verification
Changes require final main CI and APK artifact to prove compilation and tests. Real email signup and device keyboard behavior require manual acceptance; no such success is claimed.

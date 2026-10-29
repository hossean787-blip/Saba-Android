# Saba owner authentication acceptance and release gate

Status: NOT production-ready. SMS template 553755 is awaiting provider approval.

## Verified environment
- Supabase project: fiiitzfbdjzpmepzjsjw
- Edge Function: saba-auth-gateway (deployed v3; readiness is a presence check only).
- Tables: owner_identity_reservations, sms_challenges; RLS enabled and no client policies intentionally.
- Android registration UI and validation exist; MainActivity still has offline demo access, NOT authenticated login.

## Mandatory implementation sequence
1. Confirm provider's official pattern SMS endpoint, request schema, API-key authentication, IP allowlist and template approval. Do not send until approved.
2. Implement an authenticated or abuse-resistant server-side registration/start flow; server-side Iranian national ID checksum, HMAC uniqueness and atomic reservation; use approved secret storage only.
3. Implement OTP challenge: CSPRNG 6 digits, hashed code, 120-second server expiry, max 5 attempts, resend throttle, single-use consume, anti-enumeration and abuse rate limits.
4. Activate account only after SMS verification; never store raw passwords or national IDs; integrate Supabase Auth for real sessions.
5. Implement password and OTP login; secure token persistence, logout, and no demo bypass in production.
6. Connect Android screen to backend with loading/error states and clear offline demo separation.

## Acceptance tests (must actually execute before release)
- National ID invalid checksum, duplicate national ID and mobile; concurrency two identical registrations.
- SMS provider failure: no account activation; no false success message.
- OTP wrong, expired, consumed, repeated, brute-force, resend too early.
- No secret in APK, source, logs or network response.
- Successful real registration, login, logout and session restore on emulator/device.
- Run JVM and instrumentation suites; inspect logs and attach APK artifact to CI.

## Blocking facts
- Provider template 553755 pending approval.
- Secret existence/validity not independently verified.
- No live SMS sent or real account created.
- GitHub CI and instrumentation need fresh verified successful runs.

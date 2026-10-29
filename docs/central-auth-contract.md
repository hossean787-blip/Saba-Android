# Saba central authentication contract (implementation blueprint)

The Android app MUST NOT contain Melipayamak credentials, a national-ID registry, or a self-issued authentication token. Registration and uniqueness checks are enforced by a central HTTPS backend.

## Required backend persistence
- `accounts`: id (UUID PK), first_name, last_name, national_id_ciphertext, national_id_hmac (UNIQUE, NOT NULL), mobile_e164 (UNIQUE), email (NOT NULL), password_hash (Argon2id), verified_at, created_at, disabled_at.
- `otp_challenges`: id, account_pending_id, purpose, mobile_e164, code_hash, expires_at, attempts, consumed_at, resend_available_at.
- `sessions`: id, account_id, refresh_token_hash, revoked_at, expires_at.
- Secrets: national-ID HMAC key, encryption key, password pepper (if used), Melipayamak API credentials stored in server secret management; never in Git or APK.
- Require encryption at rest and least-privilege access to sensitive fields. Normalize Persian/Arabic digits server-side.

## Endpoints
- `POST /v1/auth/registration/start`: validate all required fields (including email); check valid national-ID checksum and unique HMAC; atomically reserve pending identity and mobile; hash password; request OTP through server-side Melipayamak adapter; return challenge ID, masked mobile, server-issued expiration.
- `POST /v1/auth/registration/verify`: challenge ID + six-digit code. Validate hash with constant-time comparison, 120-second expiry, attempt limit and single-use consumption; atomically activate account.
- `POST /v1/auth/login/password`: mobile OR national ID + password; return short-lived access token and rotating refresh token.
- `POST /v1/auth/login/otp/start` and `POST /v1/auth/login/otp/verify`: send and verify login OTP to existing verified mobile.
- `POST /v1/auth/owner/mobile/change/start` and `POST /v1/auth/owner/mobile/change/verify`: require authenticated owner and recent step-up authentication, verify NEW number before changing it.
- `POST /v1/auth/logout`: revoke refresh token.

## Security and abuse controls
- Always return generic errors for account existence checks; don't disclose whether a national ID is registered.
- Atomic UNIQUE constraints on national_id_hmac and mobile_e164; handle conflicts as safe generic errors.
- Rate-limit by account, mobile, IP and device; cap OTP verification attempts; avoid user enumeration.
- Expiration, resend and throttling are SERVER enforced; client countdown is only a visual hint.
- Audit changes without logging national IDs, passwords, OTPs, tokens or provider secrets.
- Use HTTPS with certificate validation; no local offline bypass for production.
- Send SMS using the current Melipayamak official API from the backend only. Confirm actual endpoint, authentication and template format against the provider's current documentation before deploying; no provider endpoint is assumed here.

## Acceptance criteria
1. Two simultaneous registrations with the same national ID result in exactly one activated owner.
2. Expired/reused/wrong OTPs never activate accounts.
3. No SMS provider secret or national-ID lookup table appears in APK or source.
4. Login method can be selected between password and SMS OTP.
5. Owner mobile changes require step-up authentication and proof of new number.
6. Integration tests use a fake SMS adapter; production delivery requires valid provider credentials and reachable backend.

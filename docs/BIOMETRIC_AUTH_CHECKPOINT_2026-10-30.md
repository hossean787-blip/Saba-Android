# SURA biometric sign-in checkpoint — 2026-10-30

Scope: Android owner login only, branch main.

## User flow
- Successful ordinary email/password login offers an explicit "enable fingerprint" choice once, if a strong biometric is enrolled.
- Device system BiometricPrompt must authorize the Keystore AES-GCM Cipher before email/password can be encrypted and stored.
- Later launches show a fingerprint button; the protected credential decrypt requires another system prompt, then calls the SAME server password-login API.
- No backend authentication bypass; failed password at backend (HTTP 401/403) invalidates local biometric enrollment.
- Password fallback is always available. The owner can disable biometric sign-in from Account > Settings.
- Never save credentials unencrypted, in logcat, Activity saved state, backups or source control. No new cloud permissions.
- Android API 28+ only; older devices continue password-only. Weak biometrics are not accepted for Android Keystore operations.

## QA
Regression tests cover candidate lifecycle, denial handling, login button and credential format.
Keystore hardware/biometric dialog needs real-device manual acceptance. Until verified, do not claim fingerprint successfully works on all Android models.

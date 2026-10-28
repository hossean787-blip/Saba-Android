# Moadian local request preparation

This change introduces a deterministic SHA-256 payload fingerprint, a caller-supplied or random request ID, and unit tests. It does **not** encode the official Moadian JSON schema or call any endpoint.

## Safety boundaries

- Persist request ID, payload hash, ownership and submission state transactionally before sending.
- Never retry a possibly submitted request blindly; reconcile its status first.
- Do not log invoice payloads, taxpayer identifiers, signatures, or private keys.
- Validate official invoice schema and crypto protocol with current tax authority documentation before a live connection.
- Use Android Keystore or appropriately supported key providers, and keep cryptographic operations isolated behind SigningPort.
- Third-party reference sources under third_party/reference are intentionally not compiled into the Android app.

## Tests

SubmissionPreparationTest provides four JUnit tests for known SHA-256, defensive copying, and input rejection.
No execution of these tests or APK build is claimed until CI provides evidence.

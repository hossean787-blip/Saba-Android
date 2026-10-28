# RS256/JWS foundation (non-production)

This module adds a generic independently implemented RS256 compact-JWS signing primitive with two JVM tests. It is not proof of compatibility with the Iranian tax service. The actual endpoint's signed-message profile, token exchange, certificate identifiers, private-key storage format, payload schema, and response authentication must be confirmed from current official documentation.

No endpoint calls, real taxpayer payloads, keys, or credentials are included. Keep private keys inside an approved secure provider; do not store them in app resources or GitHub. The test generates a disposable RSA keypair in memory.

Acceptance criteria before production: current official specifications, test vectors, negative tests, pinning/trust decisions, payload validation, secure storage, network retries/status reconciliation, and authorized sandbox tests.

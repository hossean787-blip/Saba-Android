# Next stage — Saba-Android

## New native integration boundary
- `TaxpayerGateway` is an independent Kotlin interface. It does not make network requests or implement the Iranian taxpayer API.
- `OfflineTaxpayerGateway` explicitly returns Unavailable; it must never be used to mark invoices as submitted.
- No third-party source, cryptographic algorithm or licensed library is copied into the application.

## Relevant upstream references
- https://github.com/android/architecture-samples — Android testable architecture.
- https://github.com/android/compose-samples — UI patterns, adaptation required.
- https://github.com/raheemadamboev/room-sqlcipher — study encrypted Room; validate dependency license, Android compatibility and key lifecycle.
- https://github.com/arjavand/moadian — Python taxpayer protocol reference, not Android-native source.
- https://github.com/Msameim181/iranian-taxpayer-system-java-microservice — Java API/JWS/JWE reference; audit updated official protocol before porting.
- https://github.com/Jooyeshgar/FreeAmir — Iranian accounting workflows; assess GPL obligations; do not copy commercial-incompatible code.

## Before any production integration
1. Fetch official latest Iranian taxpayer API specifications and validate exact invoice schema and protocol versions.
2. Specify certificate and private-key lifecycle, threat model, signing and encryption tests. Never commit secrets.
3. Define canonical tenant-scoped Room schema, transactional draft recovery and database migration plan.
4. Replace offline gateway with an authenticated implementation and mocked integration tests.
5. Run Gradle unit/instrumented tests, Android lint, dependency audit, and device installation.
6. Only merge after CI passes and code review.

Status: source files committed, execution of tests and real taxpayer communication NOT VERIFIED.

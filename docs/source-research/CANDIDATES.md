# Saba Android — upstream source audit (2026-10-29)

This catalog is project-specific; no assets from unrelated user projects were used.

| Upstream | Purpose | Language/stack | License status | Integration decision |
|---|---|---|---|---|
| https://github.com/android/architecture-samples | architecture, separation of layers | Kotlin | check pinned upstream license | Reference only; do not bulk-copy |
| https://github.com/android/compose-samples | native Compose screen patterns | Kotlin | check example-specific headers | Reference only |
| https://github.com/android/nowinandroid | app architecture / tests | Kotlin | check pinned upstream license | Reference only |
| https://github.com/raheemadamboev/room-sqlcipher | encrypted Room example | Kotlin | Apache-2.0 declared | Security design reference; audit dependencies |
| https://github.com/Desquared/kmp-encrypted-room-database | SQLCipher + Room | Kotlin KMP | Apache-2.0 declared | Not directly importable without KMP |
| https://github.com/arjavand/moadian | Iranian taxpayer API | Python | review LICENSE before redistribution | Protocol reference, not Android dependency |
| https://github.com/Msameim181/iranian-taxpayer-system-java-microservice | JWS/JWE taxpayer SDK | Java 11+ / server | Apache-2.0 declared | Audit cryptography and latest official spec; not copy backend secrets into app |
| https://github.com/Jooyeshgar/FreeAmir | Iran accounting feature reference | varies | confirm GPL obligations | Reference only; no direct copy into commercial app |
| https://github.com/invoice-x/invoice2data | invoice extraction | Python | verify upstream | Optional external import architecture, not native Android |

## Rules

- No wholesale copying from public repositories. Verify the exact file and dependency licenses before importing.
- GPL/AGPL components require legal compatibility review before code reuse or linking in a proprietary distributed product.
- Vendor-specific binaries, private keys, certificates, secrets, real taxpayer data, and unofficial signing shortcuts are excluded.
- Official up-to-date TPIS documentation controls API behavior, certificate handling, invoice schema, and tax-ID algorithm.
- No claim of live integration, production readiness, build success, or legal/regulatory compliance without reproducible tests.

## Current baseline from GitHub main

- Android app namespace: `ir.saba.invoice`; Kotlin/JVM 17; compileSdk/targetSdk 35; minSdk 23.
- Compose, Room 2.6.1, Kotlin coroutines present.
- README explicitly states APK and real taxpayer integration are **not verified**.

## Prioritized missing verification

1. Check current domain entities, DAOs, migrations, multi-company boundaries and draft persistence.
2. Unit-test exact monetary arithmetic and invoice line validation.
3. Add official taxpayer API protocol client under a dedicated interface, with fake transport tests; exclude real credentials.
4. Implement key management, signing, cryptographic payloads and certificate lifecycle according to current specifications.
5. Build signed/unsigned debug APK via CI and execute emulator/device tests.
6. Verify licensing, dependency vulnerabilities and RTL/Persian UX.

This document is an integration audit, not a claim that upstream projects have been copied or executed.

# Saba database isolation and taxpayer integration gate — 2026-10-29

## Code changes
- `InvoiceRepository.linesForInvoice(companyId, invoiceId)` now enforces tenant ownership using `detailsForCompany`.
- Four Android Room instrumentation tests added:
  1. Cross-company customer rejected with no partial invoice insert.
  2. Two companies can use identical invoice numbers while lines remain tenant-scoped.
  3. Invalid draft update preserves previous invoice and line data.
  4. Negative quantity / over-discount rejected with no invoice insert.
- Existing saveDraft and finalizeDraft already call Room `withTransaction`, so their atomicity is intended; full real-device verification is outstanding.

## Status
- Code review: completed for changed files.
- Android instrumentation: NOT RUN; unavailable Android SDK and Gradle.
- APK build: NOT RUN.
- Taxpayer live connectivity: NOT IMPLEMENTED or tested; existing `OfflineTaxpayerGateway` reports unavailable.
- No secrets, taxpayer records, or production endpoints have been committed.

## Taxpayer integration safety requirements
1. Obtain current official Organization of Iranian Tax Administration (INTA) technical specifications for direct connection, JSON schema, key exchange, headers, endpoints, response codes, tax ID numbering and error handling.
2. Implement actual signing/encryption using officially specified algorithms, using per-company keys from Android Keystore or protected storage with key lifecycle and recovery; review security on rooted/compromised devices.
3. Implement an idempotent async submission queue with confirmed acknowledgements and status polling. Never mark a locally prepared invoice as successfully sent.
4. Add unit tests using signed official example vectors and mock network transports; verify clock drift, retries, duplicate submissions and tenant-specific keys.
5. Require deployment credentials and controlled sandbox verification before enabling production mode.

Reference docs discovered: historic technical connection document RC_TICS.IS_V01 (1401), invoice instructions RC_IITP.IS_V6.7 (1402). Neither is adequate proof of current 1405 protocol compatibility. Verify current official releases before implementing live endpoints.

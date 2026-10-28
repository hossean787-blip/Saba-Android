# Moadian integration — staged, not production ready

This branch adds an independent Kotlin core for line-level rial calculations and submission state validation.
It does NOT send, sign, encrypt, or verify Iranian tax invoices. No existing app workflow has been modified.

Sources assessed: moadian-main, moadian2-main, moadian-full-main, oe_moadian-19.0, TaxCollector-master.
No third-party source has been copied. GPL-3.0 source must not be silently folded into proprietary app distribution.
Asterisk is excluded as unrelated.

Next: verify latest official tax schema and endpoints; add isolated models and signing adapter with secure key management;
add test fixtures and negative tests; wire an explicit confirmation flow and receipts; test CI build and authorized
sandbox before any live submission. Never commit taxpayer keys, credentials, or production data.
Current limitations: no live authenticated tax connection, no Android device or APK build verification.

# Invoice official status screen integrity — 2026-11-10

## Verified code changes
- The invoice detail screen loads the durable official outbox record for the displayed invoice under the selected owner/company.
- No statement of government acceptance is derived from a local invoice FINALIZED status, a tracking number or a bare POST acknowledgement.
- PREPARED, SENDING, UNKNOWN, SUBMITTED, ACCEPTED, REJECTED receive distinct Persian labels; acceptance requires a saved signed receipt.
- Official reference and safe error message are visible in the invoice details. A prepared/sent document links to status tracking, not an unconditional first-time submission label.
- A pure regression test verifies false-acceptance protection.
- Normal version 0.39.0 (code 39), full bank version 0.40.0 (code 40).

## Release gate
CI, signed APK and emulator evidence for this exact commit must be recorded before marking tested.
Any actual tax-server acceptance remains unverified without credentials and an explicitly approved genuine invoice.

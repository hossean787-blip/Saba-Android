# SURA credit system — financial contract (draft)

## Unit and lifecycle
- 1 credit permits exactly 1 newly created invoice. Editing, retrying a submission, reopening or viewing the same invoice MUST NOT consume a second credit.
- A credit is a non-monetary unit. Server owns all balances; app never increases balances independently.
- Never turn legacy local demo quota into verified, paid credit.
- Quota is tied to authenticated owner and taxpayer. Client-supplied owner IDs are never trusted.

## Ledger and endpoints
Server-side PostgreSQL ledger is mandatory before real transactions.
- `POST /credit/orders`: choose server-defined package ID; authenticated owner; immutable price in IRR; pending order with expiration.
- `POST /credit/payments/verify`: server independently verifies provider result (not client redirect), checks amount, merchant and unique authority/reference, grants owner wallet credits exactly once in one DB transaction.
- `GET /credit/balances`: returns owner wallet and authorized taxpayer balances plus ledger revision.
- `POST /credit/transfers`: UUID idempotency key, taxpayer ID, positive integer credits, source=owner wallet. Lock source and destination, check ownership and balance, debit/credit atomically, record double-entry ledger rows. Return receipt.
- `POST /credit/invoices/reserve`: deterministic idempotency key based on invoice ID. Atomic check-and-deduct for a new invoice only. Replays return the existing reservation; no duplicate deduction.
- `POST /credit/invoices/release`: privileged reversal only if business policy requires cancellation, preserving audit trail.

## Mandatory invariants
1. No wallet or taxpayer balance is negative.
2. Every internal transfer conserves total credits.
3. Only verified, unique settled payments mint credits.
4. The same payment, transfer or invoice request cannot mint/deduct twice.
5. Prices are obtained from backend. UI sample prices are never authoritative.
6. Only owners/admins with scoped authorization transfer credits; don't accept owner identity from client input.
7. Each change carries user, timestamp, operation UUID, cause, old/new balance, and source reference; no ledger deletion.
8. Financial writes require transaction-level locking / serializable guarantees; race tests are compulsory.
9. Provider credentials reside only on server, not in APK or repository. Production must use signed HTTPS endpoints and validated auth tokens.
10. Support unknown payment outcome and reconciliation queue; never assume success solely from a callback.

## Rollout gate
Current Compose screen is intentionally read-only for financial actions. Enable only after
backend deployment, verified merchant credentials, price configuration, real-provider
sandbox/production verification as applicable, server tests including race/idempotency,
and Android integration tests. Do not publish pricing or claim commercial readiness
until tested against actual provider and recovery scenarios.

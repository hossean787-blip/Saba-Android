# SURA — Production ZarinPal integration contract

**Status (2026-11-12):** Edge Function `sura-zarinpal` version 4 deployed ACTIVE in the SURA `saba-auth` Supabase project. Android integration is implemented; live merchant-secret readiness and a real paid transaction remain UNVERIFIED.

## Server-only responsibilities

1. Authenticated owner requests one of the server-defined price packages. The client may supply the package ID, never the payable amount or credit quantity.
2. Backend calculates price in **rial**, writes an immutable `sura_payment_orders` record and calls ZarinPal v4 `POST https://api.zarinpal.com/pg/v4/payment/request.json` with `merchant_id`, `amount`, `callback_url`, and an order-specific description.
3. Backend saves unique `authority` and state `requested` before sending the payment URL `https://www.zarinpal.com/pg/StartPay/{authority}` to the Android client.
4. ZarinPal returns the browser to the HTTPS server callback. Any Status query parameter is UNTRUSTED. Backend reloads its original order by unique authority and invokes ZarinPal v4 `POST https://api.zarinpal.com/pg/v4/payment/verify.json` with the stored amount and merchant_id.
5. After gateway code 100 (first success) or code 101 (gateway already verified that same authority, including interrupted-settlement recovery), backend uses **one atomic database transaction** to mark the order verified and add one `sura_credit_grants` record. Duplicate callbacks do not grant credit twice; a mismatching ref_id is rejected.
6. A wallet balance is derived from server-verified credit grants minus server-owned transfers and uses. **Never** from Android Room `QuotaWallet`, local preferences, callback query parameters, or a JavaScript payment response.
7. Authenticated owner can query the definitive order state from backend. The `status` route independently re-verifies a pending order with ZarinPal before returning the server state, even when the browser callback never arrived. The mobile UI may display “پرداخت در انتظار تأیید” until confirmation.

## Configuration required to go live

* Active ZarinPal merchant ID, held as a server-side secret `ZARINPAL_MERCHANT_ID`.
* Public HTTPS endpoint for callback; the validated URL is fixed by the server, not accepted from Android.
* Dedicated Supabase project with service-role secret accessible **only** server-side; execute migration `20261031_zarinpal_payments.sql`.
* A production-authenticated owner identity and approved price table.
* Server-sourced wallet and transfer endpoint, with ownership controls and replay protection.
* E2E payment verification in production for a small authorized transaction, refund/reconciliation process, and live logging.

## Release gate

The 0.39.0 app uses server-side checkout; it must never issue credits from the browser callback, local Room wallet, or unsigned Android results. The current server route is deployed and the quote RPC was read-only tested on 2026-11-12: quantity=1, price=100000 IRR, VAT=10000 IRR, payable=110000 IRR. Three protocol guard tests and Deno type checking passed in GitHub Actions run 43173690991. The payment ledger contained zero orders and zero grants at that inspection.

**Still not verified:** whether `ZARINPAL_MERCHANT_ID` is set and valid on the live project; real gateway handshake/payment/verification, user device checkout, and invoice receipt details. No production transaction was initiated during these checks.

**Security and merchant setup:** add merchant UUID only as Supabase Edge Function secret `ZARINPAL_MERCHANT_ID`. The Android app must not embed that ID or a service-role key. Enable real charging only after a merchant-authorized small payment has completed server verification and one-credit idempotent settlement tests.

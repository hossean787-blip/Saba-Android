# SURA — Production ZarinPal integration contract

**Status:** backend ledger implemented in migration; gateway connection and deployment NOT ACTIVE.

## Server-only responsibilities

1. Authenticated owner requests one of the server-defined price packages. The client may supply the package ID, never the payable amount or credit quantity.
2. Backend calculates price in **rial**, writes an immutable `sura_payment_orders` record and calls ZarinPal v4 `POST https://payment.zarinpal.com/pg/v4/payment/request.json` with `merchant_id`, `amount`, `callback_url`, and an order-specific description.
3. Backend saves unique `authority` and state `requested` before sending the payment URL `https://www.zarinpal.com/pg/StartPay/{authority}` to the Android client.
4. ZarinPal returns the browser to the HTTPS server callback. Any Status query parameter is UNTRUSTED. Backend reloads its original order by unique authority and invokes ZarinPal v4 `POST https://payment.zarinpal.com/pg/v4/payment/verify.json` with the stored amount and merchant_id.
5. After gateway code 100 (first success) or verified, matching prior settlement (101), backend uses **one atomic database transaction** to mark the order verified and add one `sura_credit_grants` record. Duplicate callbacks do not grant credit twice; a mismatching ref_id is rejected.
6. A wallet balance is derived from server-verified credit grants minus server-owned transfers and uses. **Never** from Android Room `QuotaWallet`, local preferences, callback query parameters, or a JavaScript payment response.
7. Authenticated owner can query the definitive order state from backend. The mobile UI may display “پرداخت در انتظار تأیید” until it is confirmed by the backend.

## Configuration required to go live

* Active ZarinPal merchant ID, held as a server-side secret `ZARINPAL_MERCHANT_ID`.
* Public HTTPS endpoint for callback; the validated URL is fixed by the server, not accepted from Android.
* Dedicated Supabase project with service-role secret accessible **only** server-side; execute migration `20261031_zarinpal_payments.sql`.
* A production-authenticated owner identity and approved price table.
* Server-sourced wallet and transfer endpoint, with ownership controls and replay protection.
* E2E payment verification in production for a small authorized transaction, refund/reconciliation process, and live logging.

## Release gate

The 0.11.0 app has intentionally disabled issuance and local simulated wallet funding. Do not replace these guards with direct Android-to-ZarinPal merchant requests. Production charging must remain disabled until server endpoints are connected, independently audited, and tested with the real merchant credentials. A release binary alone does not constitute a live payment integration.

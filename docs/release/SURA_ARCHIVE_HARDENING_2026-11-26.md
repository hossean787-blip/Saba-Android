# SURA archive validation checkpoint — 2026-11-26
Baseline 6cc53e0606f2f9de269c42829fef5e1600227a51. Scope: account archive, no live data or payment, no store publication.
- Existing AES-256-GCM/PBKDF2 archive envelope, same-owner check, Keystore protection, non-destructive merge and live-credit read are preserved.
- Validate portable metadata before remote credit read, taxpayer-ID reservation or database merge. Reject duplicate identifier keys (including across batches), missing journals, invalid optional history/status object types, excessive history.
- Regression: malformed encrypted portable files must not query credit, reserve IDs or create rows; wrong password must fail before credit lookup. Existing regression covers foreign account, altered ciphertext, 80-to-60 credit, replay, key re-encryption and preserved newer records.
- Not a full atomic filesystem/Room/Keystore transaction: device storage failure after Room commit can still require re-import; account database is not fully encrypted at rest. Physical-device Keystore/restore, live-credit reconciliation, security audit and attestation remain release gates. CI result pending; no claim of test pass.

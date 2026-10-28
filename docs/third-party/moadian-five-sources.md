# Five Moadian source libraries — source audit and transfer manifest

This directory is an **isolated source-reference manifest**. The original ZIP binaries are not embedded in this commit; upload of the binaries requires an authenticated file-byte transfer path. No original source is in the Android app compilation path.

| Archive | Files | License found | SHA-256 |
|---|---:|---|---|
| moadian-main.zip | 16 | GPL-3.0 | `2c649248e82e41bc576f9fdc20efdfdaec78db0018718958ca1bd9c61947ccd3` |
| moadian2-main.zip | 9 | GPL-3.0 | `c1e303577a35062111d03c3f1e8b1f95827858d8e7cb4faeebc30b0c1fa60167` |
| moadian-full-main.zip | 13 | GPL-3.0 | `d8ce14a84b8fde9c9e32fbe0e9ca2af80e22c0b7b14efded277da9463ea49516` |
| oe_moadian-19.0.zip | 27 | Not identified in archive | `91657e05a142a703d560c473b4d7a7617b372f9d147b9010cec1f51d98e3b582` |
| TaxCollector-master.zip | 85 | Not identified in archive | `0858934adb20d3b699041f3f6b97ec7220cb321483fc7d2871541f48b5344652` |

The separate Asterisk telephony archive is excluded.

## Integration boundary
- Use these sources for requirements investigation and independently written Android implementations.
- Do not import GPL-3.0 implementation code into proprietary Android runtime without evaluating distribution obligations.
- No license file in an archive does not mean public domain or unrestricted use.
- Do not execute third-party scripts or expose keys while importing.
- Keep all originals out of `app/src/main` and Gradle build inputs until compatible licensing, security review, and functional tests are complete.
- Production API schema, signature, encryption, tax-ID check digit, retry idempotency and status reconciliation remain to be verified against official documentation.

## Transfer progress (2026-10-28)

Reference-source text files for all five source trees have been staged under `third_party/reference/` on the integration branch from the authors' upstream GitHub repositories. The only untransferred archive member is the Odoo PNG icon (`oe_moadian-19.0/static/description/icon.png`), which is a non-executable graphic asset. Original ZIP archives are not themselves committed. Five representative uploaded-ZIP blobs were compared against their staged Git blob SHA-1 identifiers and matched. This is not a byte-for-byte verification of every file.

**Do not add the reference directories to Android Gradle source sets or ship this tree inside the APK.** The two orally permitted libraries have user-reported commercial-use permission; redistributing source and sublicensing permissions remain undocumented. For the three Python libraries, the LICENSE files in the uploaded ZIPs contain GPL version 3 text, regardless of inconsistent MIT classifier metadata in some pyproject files.

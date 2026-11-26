# SURA Security Shield — checkpoint 2026-11-26

Scope: official invoice Android application only. No manager-app changes, no live tax
submission, no changes to payment or database contracts, no store publication.

Baseline main: f2ac162b85a4dd1293993af944474024139dcc26
Existing controls observed: release signing secrets; CI verifies the established public
certificate fingerprint (successful run 45793002873); TLS only; Android backup disabled;
taxpayer key vault uses Android Keystore AES-GCM; credit client delegates to authenticated
server. These observations are source inspection, not a new runtime test.

Changes: enable optimized R8 minification for release, keep resource shrinking disabled
to avoid catalog regressions; disable release debugging; check installed current signer
and package before application content is initialized (API 23–27 legacy signatures,
28+ signingInfo), allow developer debug builds; retain original app ID and established
certificate, increment version 123/124; add positive/negative digest tests. No new
dependencies. Preserve signing identity, local database and taxpayer keys.

Security limit: local verification can be removed by a skilled reverse engineer.
It does not prove app authenticity to the server. Do not trust package-name or
fingerprint headers. Real remote integrity enforcement needs a supported attestation
provider and fresh server-issued challenge, replay protection, staged enforcement and
testing across Bazaar/sideloaded Android devices. Do not block legitimate customers based
on an unverified client claim. Existing server credit rules remain unchanged.

Repository is public as of inspection. Owner must change visibility separately in
Settings > General > Danger Zone, after confirming implications; existing copies cannot
be revoked. Never publish mapping.txt or signing secrets. Retain mapping privately for
crash diagnosis. No new signing key or certificate rotation.

Acceptance gates pending until verified: all existing debug tests and lint; R8 release
compilation and lint; signed APK certificate and package/version; signed update over
previous app with data retained; normal startup on API 23 and 28+; re-signed/renamed APK
must show blocked screen; verify taxpayer/customer/invoice/PDF/Excel/credit/payment
regression on minified release without actual financial transaction. Prior signed run
45796462283 failed at testDebugUnitTest; do not bypass failures. A source commit is not
a completed release. CI build request is for owner testing only.

# SURA 0.139 re-signed APK rejection evidence
2026-11-26. Exact official APK from signed run 45844007954 (source 0cf2ecc381c997c847818a00f7647b8291578269). Test workflow/script revision e50d1fdf3d582c5e11fcb8b02cb3bc552d1ac01b. Verified GitHub Actions run https://github.com/hossean787-blip/Saba-Android/actions/runs/45852769643 completed SUCCESS.
API 29 job 140023169084 and API 36 job 140023168758 both completed SUCCESS. Direct log inspection for both:
- official_login=PASS
- wrong_signer_upgrade=REJECTED (INSTALL_FAILED_UPDATE_INCOMPATIBLE)
- fresh_resigned_start=BLOCKED
- remote_attestation=NOT_TESTED
Both jobs successfully uploaded test evidence only. Disposable test signing key and re-signed APK not published. No production code, accounts or fiscal data changed.
Acceptance: local rejection of re-signed unmodified APK and genuine launch verified on two emulators. This does not prove resistance against a patched local guard, nor server attestation on actual hardware. Physical compatible handset positive/repeated/negative tests remain necessary; server enforcement is not enabled. Do not claim full commercial security acceptance from this gate.

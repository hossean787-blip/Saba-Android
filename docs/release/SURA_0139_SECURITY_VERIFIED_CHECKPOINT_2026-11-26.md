# SURA 0.139.0 security verification checkpoint — 2026-11-26

Scope: main app security and authenticity stabilization; no financial requests, no database or manager changes, no public publication, no authorization/enforcement changes.
Verified source: 0cf2ecc381c997c847818a00f7647b8291578269 (version 139).
Evidence inspected directly from GitHub Actions jobs and logs:
- [Signed release](https://github.com/hossean787-blip/Saba-Android/actions/runs/45844007954): success; JUNIT_SUMMARY 527 tests, 527 passed, 0 failures/errors/skips; debug regression/lint, release build and signature gate successful. Job 139992804098 signer SHA-256 matches established pin 55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b.
- [CI](https://github.com/hossean787-blip/Saba-Android/actions/runs/45844008101): success; test/lint and live authentication rejection-path steps succeeded.
- [Security and recovery](https://github.com/hossean787-blip/Saba-Android/actions/runs/45844007918): API 29 and 36 both successful, three instrumentation tests executed per emulator. These cover synthetic restore, local Keystore, and local proof of possession, not remote hardware trust.
- Signed release artifact 15130253468, present and unexpired at inspection, archive 2516968 bytes, GitHub-reported archive digest sha256:87b98bcece2270c415ecf3f2ee701b7ce9c06175d9eed0cf8037cd2ab7ed40d2. Not independently downloaded or hashed in this inspection.

Source review: R8 release, non-debuggable release, pinned signer and package, session-isolated positive observation cache, monotonic 30-second limit, challenge consumption and server-side certificate verification. Client observations never authorize credit or other services. A local modified program may bypass its own checks. Repository remains public; no visibility change authorized.

Remaining release gate: actual signed APK on physical compatible device; fresh remote proof succeeds, immediate repeated check displays recent positive observation, after 30 seconds fresh check succeeds or returns an accurate inconclusive state; test offline and rejected proof separately. A legitimate unsupported Android device must not lose access. Test real re-signed rejection using synthetic data in isolated environment, not real credentials. Remote enforcement remains deliberately disabled pending dedicated security audit and compatibility rollout; do not treat observation as authentication or claim absolute reverse-engineering protection. Database encryption is separate and not addressed here.

Status: automated gates for exact version completed and tested; full real-device authenticity/production acceptance blocked by lack of physical-device testing access. No new application code required to record this checkpoint. Preserve concurrent work on other tasks.

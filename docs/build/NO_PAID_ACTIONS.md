# Build without paid GitHub Actions

Status 2026-10-29: a complete JDK 17, Android SDK and Gradle toolchain were provisioned in the current execution environment. The previous main snapshot passed 96 JVM/Robolectric tests and Debug/Android-test APK builds plus lint. Current repair verification is recorded separately; this is not a claim of successful GitHub-hosted execution.

## Free self-managed Linux workflow
1. Obtain Ubuntu/Linux workstation or a permitted free computer environment with sufficient RAM, storage and network access.
2. Install JDK 17 and official Android SDK command-line tools from Google, accept licenses, and install `platforms;android-35` and a compatible build-tools version.
3. Use the repository's official Gradle 8.9 wrapper; it verifies the distribution checksum. A separate Gradle installation is unnecessary.
4. Clone this *private* repo through your own authenticated GitHub access without publishing your credentials.
5. Run `bash scripts/build-offline-ci.sh`. It verifies prerequisites, resolves runtime dependencies, runs unit tests, builds APK and runs Android lint.
6. Verify artifact at `app/build/outputs/apk/debug/app-debug.apk`; then install on a test device and run instrumentation tests.

**No paid plan is required by this script**, but the Linux host, downloads, compute time, or third-party cloud provider may incur costs. Do not use unfamiliar cloud services with private source or secrets.

## Current limitations
- Real-device installation and real Moadian communication have not been verified. Local build success does not resolve the GitHub account billing block; see `CI_STATUS_FA.md`.
- This workflow is an alternate build path, not proof the project compiles.
- Do not paste secrets or signing credentials into GitHub or build logs.
- GitHub Actions billing cannot be corrected by source-code changes.

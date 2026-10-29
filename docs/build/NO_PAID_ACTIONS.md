# Build without paid GitHub Actions

Status 2026-10-29: the connected execution environment has Java and Git but **no Android SDK or Gradle**. APK creation could not be performed here.

## Free self-managed Linux workflow
1. Obtain Ubuntu/Linux workstation or a permitted free computer environment with sufficient RAM, storage and network access.
2. Install JDK 17 and official Android SDK command-line tools from Google, accept licenses, and install `platforms;android-35` and a compatible build-tools version.
3. Install Gradle 8.9, or generate and commit an official Gradle wrapper using a trusted Gradle installation.
4. Clone this *private* repo through your own authenticated GitHub access without publishing your credentials.
5. Run `bash scripts/build-offline-ci.sh`. It verifies prerequisites, resolves runtime dependencies, runs unit tests, builds APK and runs Android lint.
6. Verify artifact at `app/build/outputs/apk/debug/app-debug.apk`; then install on a test device and run instrumentation tests.

**No paid plan is required by this script**, but the Linux host, downloads, compute time, or third-party cloud provider may incur costs. Do not use unfamiliar cloud services with private source or secrets.

## Current limitations
- No Gradle resolution, build, lint, device installation or real Moadian communication has been verified.
- This workflow is an alternate build path, not proof the project compiles.
- Do not paste secrets or signing credentials into GitHub or build logs.
- GitHub Actions billing cannot be corrected by source-code changes.

#!/usr/bin/env bash
# Independent build on a Linux machine; does NOT invoke GitHub Actions.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null; then echo "BLOCKED: JDK 17 required"; exit 2; fi
JAVA_MAJOR="$(java -version 2>&1 | head -1)"
echo "Java: $JAVA_MAJOR"
if ! java -version 2>&1 | grep -Eq '"17[."]'; then echo "BLOCKED: switch to JDK 17"; exit 2; fi
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "BLOCKED: install Android SDK command-line tools and export ANDROID_HOME"; exit 2
fi
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [[ ! -d "$SDK/platforms/android-35" ]]; then
  echo "BLOCKED: Android SDK platform 35 is not installed"; exit 2
fi
if [[ ! -d "$SDK/build-tools" ]]; then
  echo "BLOCKED: Android SDK build-tools are not installed"; exit 2
fi
if [[ -f ./gradlew ]]; then
  chmod +x ./gradlew
  GRADLE=(./gradlew)
elif command -v gradle >/dev/null; then
  GRADLE=(gradle)
else
  echo "BLOCKED: Gradle 8.9 needed (or add the official Gradle wrapper)"; exit 2
fi
"${GRADLE[@]}" --version
"${GRADLE[@]}" --no-daemon :app:dependencies --configuration debugRuntimeClasspath
"${GRADLE[@]}" --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
echo "PASS: check app/build/outputs/apk/debug for APK"

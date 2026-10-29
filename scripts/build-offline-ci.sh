#!/usr/bin/env bash
# Independent build on a Linux machine; does NOT invoke GitHub Actions.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null; then echo "BLOCKED: JDK 17 required"; exit 2; fi
JAVA_MAJOR="$(java -version 2>&1 | sed -n '/version "/p')"
echo "Java: $JAVA_MAJOR"
if ! java -version 2>&1 | grep -Eq '"17[."]'; then echo "BLOCKED: switch to JDK 17"; exit 2; fi
for tool in javac jlink; do
  if ! command -v "$tool" >/dev/null; then echo "BLOCKED: full JDK 17 required; missing $tool"; exit 2; fi
done
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "BLOCKED: install Android SDK command-line tools and export ANDROID_HOME"; exit 2
fi
SDK="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [[ ! -f "$SDK/platforms/android-35/android.jar" ]]; then
  echo "BLOCKED: Android SDK platform 35 is not installed"; exit 2
fi
if [[ ! -f "$SDK/build-tools/34.0.0/source.properties" ]]; then
  echo "BLOCKED: Android SDK build-tools are not installed"; exit 2
fi
if [[ ! -f ./gradlew || ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "BLOCKED: official Gradle wrapper is missing; restore it from the repository"; exit 2
fi
GRADLE=(bash ./gradlew)
"${GRADLE[@]}" --version
"${GRADLE[@]}" --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
echo "PASS: check app/build/outputs/apk/debug for APK"

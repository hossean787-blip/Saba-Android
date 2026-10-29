#!/usr/bin/env bash
# Codespaces: JDK 17 + Android SDK + bounded Gradle JVM + unit tests + debug APK.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [[ -x "$HOME/.sdkman/candidates/java/17.0.18-tem/bin/java" ]]; then
  export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.18-tem"
elif [[ -d "$HOME/.sdkman/candidates/java" ]]; then
  for candidate in "$HOME"/.sdkman/candidates/java/17*; do
    if [[ -x "$candidate/bin/javac" ]]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "=== Java ==="
java -version
if ! java -version 2>&1 | grep -Eq '"17([."]|\+)'; then
  echo "BLOCKED: JDK 17 must be active. Check JAVA_HOME and SDKMAN." >&2
  exit 2
fi
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
  echo "BLOCKED: Android SDK platform 35 not found under $ANDROID_HOME" >&2
  exit 2
fi
if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "BLOCKED: gradle-wrapper.jar missing. Restore the official Gradle wrapper." >&2
  exit 2
fi
chmod +x ./gradlew
echo "=== Gradle ==="
./gradlew --version
echo "=== Tests + APK ==="
./gradlew --stop || true
./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.java.home="$JAVA_HOME" \
  -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8" \
  -Dkotlin.compiler.execution.strategy=in-process \
  :app:testDebugUnitTest :app:assembleDebug
echo "=== APK paths ==="
find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print
echo "SUCCESS: JVM unit tests and debug APK build finished."
echo "NOTE: Android instrumented Room tests still require an emulator or device."

#!/usr/bin/env bash
# Codespaces: JDK 17 + Android SDK + bounded Gradle JVM + unit tests + debug APK.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# SDKMAN can be installed outside HOME in GitHub Codespaces.
if [[ -n "${SDKMAN_DIR:-}" && -f "$SDKMAN_DIR/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$SDKMAN_DIR/bin/sdkman-init.sh"
elif [[ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]]; then
  # shellcheck disable=SC1091
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi
if declare -F sdk >/dev/null 2>&1; then
  sdk use java 17.0.18-tem >/dev/null 2>&1 || true
fi

# Prefer an explicitly installed JDK 17, not the default JDK 25.
for candidate in \
  "${JAVA_HOME:-}" \
  "$HOME/.sdkman/candidates/java/17.0.18-tem" \
  "${SDKMAN_DIR:-/nonexistent}/candidates/java/17.0.18-tem" \
  "/usr/local/sdkman/candidates/java/17.0.18-tem"; do
  [[ -n "$candidate" && -x "$candidate/bin/javac" ]] || continue
  if "$candidate/bin/java" -version 2>&1 | grep -Eq '"17([."]|\\+)'; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    break
  fi
done
# When 'sdk use' already adjusted PATH, derive JAVA_HOME from the active JDK.
if ! [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]] || \
   ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -Eq '"17([."]|\\+)'; then
  active_java="$(readlink -f "$(command -v java)")"
  candidate="$(dirname "$(dirname "$active_java")")"
  if [[ -x "$candidate/bin/javac" ]] && "$candidate/bin/java" -version 2>&1 | grep -Eq '"17([."]|\\+)'; then
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
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

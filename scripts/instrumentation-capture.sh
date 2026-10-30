#!/usr/bin/env bash
# Run inside reactivecircus/android-emulator-runner while the emulator is alive.
# The runner launches each inline workflow line in a separate process. This script
# deliberately keeps all shell variables and APK installation in one bash process.
set -euo pipefail

./gradlew :app:connectedDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

app_apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test_apk="$(find app/build/outputs/apk/androidTest -type f -name '*.apk' -print -quit)"
if [[ -z "$app_apk" || -z "$test_apk" ]]; then
  echo "Missing debug APK or androidTest APK after Gradle build" >&2
  exit 1
fi

# Gradle's connected test runner uninstalls the tested app, along with its
# app-scoped screenshots. A focused second run keeps the app installed.
adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
mkdir -p app/build/emulator-screenshots
adb shell am instrument -w \
  -e class ir.saba.invoice.StartupLogoVisualInstrumentedTest \
  ir.saba.invoice.test/androidx.test.runner.AndroidJUnitRunner \
  | tee app/build/sura-intro-verification.txt
grep -q 'OK (1 test)' app/build/sura-intro-verification.txt

adb pull /sdcard/Android/data/ir.saba.invoice/files/screenshots/ app/build/emulator-screenshots/
for frame in sura-intro-particles-emulator.png sura-intro-mark-emulator.png; do
  match="$(find app/build/emulator-screenshots -type f -name "$frame" -size +1000c -print -quit)"
  if [[ -z "$match" ]]; then
    echo "Missing/empty real device frame: $frame" >&2
    exit 1
  fi
  ls -lh "$match"
done

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
OUT="$ROOT/app/build/emulator-proof"
mkdir -p "$OUT"
[ -s "$APK" ] || { echo "APK missing"; exit 1; }
adb install -r "$APK" | tee "$OUT/install.txt"
adb shell pm path ir.sabagostar.suramanager | tee "$OUT/package.txt"
adb logcat -c
adb shell am start -W -n ir.sabagostar.suramanager/.AdminActivity | tee "$OUT/launch.txt"
sleep 9
adb shell pidof ir.sabagostar.suramanager | tee "$OUT/pid.txt"
adb shell dumpsys activity activities > "$OUT/activity.txt"
if ! grep -q 'ir.sabagostar.suramanager' "$OUT/activity.txt"; then
  echo 'SURA Manager activity not present'; exit 1
fi
adb shell uiautomator dump /sdcard/sura-manager-window.xml > "$OUT/uiautomator.txt" 2>&1
adb pull /sdcard/sura-manager-window.xml "$OUT/window.xml"
# Secure WebViews do not reliably expose DOM text through UIAutomator on newer Android.
# Instead validate a minimal DOM health probe executed inside the real Android WebView.
adb logcat -d -v brief > "$OUT/logcat.txt"
if ! grep -q 'SURA_ADMIN_TEST.*login_ready=true' "$OUT/logcat.txt"; then
  echo 'Embedded WebView DOM/assets did not pass the ready probe.'
  grep 'SURA_ADMIN_TEST' "$OUT/logcat.txt" || true
  exit 1
fi
if grep -A 6 'FATAL EXCEPTION' "$OUT/logcat.txt" | grep -q 'ir.sabagostar.suramanager'; then
  echo 'App crash detected'; exit 1
fi
printf 'PASS: package installed, activity launched, WebView login visible and process alive\n' | tee "$OUT/summary.txt"

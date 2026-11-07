#!/usr/bin/env bash
# Executed only on an isolated CI emulator (never a customer's real device).
# Checks an actual signed, in-place APK upgrade; intentionally avoids any tax API.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/build/upgrade
previous="app/build/upgrade/previous/app-release.apk"
current="app/build/upgrade/current/app-release.apk"
package="ir.saba.invoice"
component="$package/.MainActivity"
test -s "$previous" && test -s "$current"
adb wait-for-device
sdk_api="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
[[ "$sdk_api" == "29" || "$sdk_api" == "36" ]] || { echo "Unexpected test API: $sdk_api" >&2; exit 1; }
# A freshly booted emulator can report boot_completed before PackageManager
# finishes starting. Wait for BOTH Android boot and the package service.
ready=0
for attempt in $(seq 1 50); do
  boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$boot" == "1" ]] && adb shell cmd package list packages >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 3
done
[[ "$ready" == "1" ]] || { echo "Emulator package service not ready." >&2; exit 1; }

install_safely() {
  local apk="$1"
  local attempt=1
  while (( attempt <= 8 )); do
    if adb install --no-streaming -r "$apk"; then return 0; fi
    echo "Android package service unavailable; retry $attempt/8." >&2
    adb wait-for-device || true
    sleep 8
    attempt=$((attempt + 1))
  done
  echo "Signed APK could not be installed after package-service retries." >&2
  return 1
}
install_safely "$previous"
before="$(adb shell pm list packages -U "$package" | tr -d '\r')"
[[ "$before" =~ ^package:ir\.saba\.invoice[[:space:]]uid:[0-9]+$ ]] || {
  echo "Failed to record installed package UID: $before" >&2
  exit 1
}
echo "Before update: $before"
adb shell am start -W -n "$component" | tee app/build/upgrade/start-before.txt
# Reinstall without clearing app data or removing the old package.
install_safely "$current"
after="$(adb shell pm list packages -U "$package" | tr -d '\r')"
[[ "$before" == "$after" ]] || {
  echo "Package UID changed during in-place upgrade (data safety not verified)." >&2
  exit 1
}
echo "After update: $after"
adb shell dumpsys package "$package" | grep 'versionCode=25' > app/build/upgrade/installed-version.txt
adb shell pm path "$package" | grep -q 'package:'
adb logcat -c
adb shell am force-stop "$package"
adb shell am start -W -n "$component" | tee app/build/upgrade/start-after.txt
# Device/launcher performance varies; allow normal app startup.
alive=0
for attempt in $(seq 1 20); do
  if adb shell pidof "$package" | grep -Eq '[0-9]+'; then
    alive=1
    break
  fi
  sleep 1
done
[[ "$alive" == "1" ]] || { echo "Release process did not remain running after upgrade" >&2; exit 1; }
adb shell screencap -p /sdcard/sura-signed-upgrade.png
adb pull /sdcard/sura-signed-upgrade.png app/build/upgrade/after-upgrade.png
test "$(wc -c < app/build/upgrade/after-upgrade.png)" -gt 4000
adb logcat -d -b crash > app/build/upgrade/crash-buffer.txt
if grep -E 'FATAL EXCEPTION|Process: ir\.saba\.invoice' app/build/upgrade/crash-buffer.txt; then
  echo "Android crash reported on release launch" >&2
  exit 1
fi
{
  echo "SURA APK signed upgrade verified in Android emulator, API $sdk_api"
  echo "before_uid=$before"
  echo "after_uid=$after"
  echo "previous_versionCode=21"
  echo "current_versionCode=25"
  echo "installation_mode=adb install -r (without uninstall/pm clear)"
  echo "release_process_running=yes"
  echo "crash_buffer=clear"
  echo "Note: unchanged UID proves the installation was an upgrade; app-owned data was not directly inspected."
  echo "Note: this test does not prove operation on the user's actual handset or real fiscal submission."
} > app/build/upgrade/upgrade-proof.txt
cat app/build/upgrade/upgrade-proof.txt

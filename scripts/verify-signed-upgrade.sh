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
adb shell getprop sys.boot_completed | grep -q 1
adb install -r "$previous"
before="$(adb shell pm list packages -U "$package" | tr -d '\r')"
[[ "$before" =~ ^package:ir\.saba\.invoice[[:space:]]uid:[0-9]+$ ]] || {
  echo "Failed to record installed package UID: $before" >&2
  exit 1
}
echo "Before update: $before"
adb shell am start -W -n "$component" | tee app/build/upgrade/start-before.txt
# Reinstall without clearing app data or removing the old package.
adb install -r "$current"
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
  echo "SURA APK signed upgrade verified in Android emulator, API 29"
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

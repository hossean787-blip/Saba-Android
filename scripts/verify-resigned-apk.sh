#!/usr/bin/env bash
set -euo pipefail
pkg=ir.saba.invoice
dir="$RUNNER_TEMP/sura-authenticity"
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$sdk" == 29 || "$sdk" == 36 ]]
# Fresh disposable emulator only. No account sign-in or financial request.
for i in $(seq 1 50); do
  if [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]] && adb shell cmd package list packages >/dev/null 2>&1; then break; fi
  sleep 3
done
if adb shell pm path "$pkg" | grep -q '^package:'; then echo "Unexpected existing app on emulator"; exit 1; fi
adb install --no-streaming "$dir/app-release.apk"
adb shell am start -W -n "$pkg/.MainActivity" | tee evidence/official-start.txt
# Require actual positive login UI, not just process existence.
wait_ui() {
  local expected="$1" name="$2"
  for i in $(seq 1 20); do
    adb shell rm -f /sdcard/sura-gate.xml >/dev/null 2>&1 || true
    adb shell uiautomator dump /sdcard/sura-gate.xml >/dev/null 2>&1 || true
    if adb pull /sdcard/sura-gate.xml "evidence/$name.xml" >/dev/null 2>&1 && grep -Fq "$expected" "evidence/$name.xml"; then
      return 0
    fi
    sleep 2
  done
  echo "Expected UI not found: $name" >&2
  return 1
}
wait_ui 'ورود با رمز ثابت' official-login
if grep -Fq 'نسخه نامعتبر سورا' evidence/official-login.xml; then exit 1; fi
# Android must reject an in-place update signed by another certificate.
if adb install --no-streaming -r "$dir/resigned.apk" > evidence/update-rejection.txt 2>&1; then
  echo "Unexpected re-signed update success"; exit 1
fi
grep -F 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' evidence/update-rejection.txt
# Uninstall only the synthetic clean test installation, then try fresh install.
adb uninstall "$pkg"
adb install --no-streaming "$dir/resigned.apk"
adb shell am start -W -n "$pkg/.MainActivity" | tee evidence/resigned-start.txt
wait_ui 'نسخه نامعتبر سورا' resigned-block
grep -F 'امضای این نسخه با نسخه رسمی سورا مطابقت ندارد' evidence/resigned-block.xml
if grep -Fq 'ورود با رمز ثابت' evidence/resigned-block.xml; then exit 1; fi
printf 'api=%s\nofficial_login=PASS\nwrong_signer_upgrade=REJECTED\nfresh_resigned_start=BLOCKED\nremote_attestation=NOT_TESTED\n' "$sdk" | tee evidence/result.txt

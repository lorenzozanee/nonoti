#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <emulator-serial>" >&2
  exit 2
fi

device_serial="$1"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"

cd "$project_dir"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

adb -s "$device_serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$device_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$device_serial" shell cmd notification disallow_dnd com.example.nonoti || true
adb -s "$device_serial" shell cmd notification allow_listener \
  com.example.nonoti/com.example.nonoti.notifications.NonotiNotificationListenerService
adb -s "$device_serial" shell cmd notification allow_dnd com.example.nonoti
adb -s "$device_serial" shell pm grant com.example.nonoti android.permission.POST_NOTIFICATIONS
adb -s "$device_serial" shell pm grant com.example.nonoti.test android.permission.POST_NOTIFICATIONS
adb -s "$device_serial" shell appops set com.example.nonoti SCHEDULE_EXACT_ALARM allow
result_file="$(mktemp)"
trap 'rm -f "$result_file"' EXIT
adb -s "$device_serial" shell am instrument -w -r \
  com.example.nonoti.test/com.example.nonoti.NonotiTestRunner | tee "$result_file"
grep -Eq '^OK \([0-9]+ tests\)$' "$result_file"

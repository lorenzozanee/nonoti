#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <emulator-serial>" >&2
  exit 2
fi

device_serial="$1"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
app_package="com.example.nonoti"
test_package="$app_package.test"
listener_component="$app_package/$app_package.notifications.NonotiNotificationListenerService"
control_component="$app_package/$app_package.debug.DebugTestControlReceiver"
publisher_component="$test_package/$app_package.ExternalNotificationPublisherReceiver"
notification_id="$((RANDOM + 40000))"

broadcast_control() {
  adb -s "$device_serial" shell am broadcast --receiver-foreground \
    -a "$app_package.debug.$1" -n "$control_component"
}

restore_device() {
  adb -s "$device_serial" shell dumpsys deviceidle unforce >/dev/null 2>&1 || true
  adb -s "$device_serial" shell dumpsys battery reset >/dev/null 2>&1 || true
  adb -s "$device_serial" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb -s "$device_serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb -s "$device_serial" shell cmd notification allow_listener "$listener_component" 0 >/dev/null 2>&1 || true
  adb -s "$device_serial" shell cmd notification allow_dnd "$app_package" 0 >/dev/null 2>&1 || true
  broadcast_control RESET >/dev/null 2>&1 || true
}

trap restore_device EXIT

cd "$project_dir"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest >/dev/null
adb -s "$device_serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
adb -s "$device_serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null
adb -s "$device_serial" shell cmd notification allow_listener "$listener_component" 0
adb -s "$device_serial" shell cmd notification allow_dnd "$app_package" 0
adb -s "$device_serial" shell pm grant "$app_package" android.permission.POST_NOTIFICATIONS
adb -s "$device_serial" shell pm grant "$test_package" android.permission.POST_NOTIFICATIONS
adb -s "$device_serial" shell appops set "$app_package" SCHEDULE_EXACT_ALARM allow
broadcast_control RESET >/dev/null
broadcast_control START | grep -q 'start=Started'

adb -s "$device_serial" shell input keyevent KEYCODE_SLEEP
adb -s "$device_serial" shell dumpsys battery unplug >/dev/null
adb -s "$device_serial" shell dumpsys deviceidle force-idle >/dev/null
idle_state="$(adb -s "$device_serial" shell dumpsys deviceidle get deep | tr -d '\r')"
[[ "$idle_state" == "IDLE" ]]

adb -s "$device_serial" shell am broadcast --receiver-foreground \
  -a "$test_package.POST_NOTIFICATION" -n "$publisher_component" \
  --ei notification_id "$notification_id" \
  --es notification_title "Doze test" \
  --es notification_text "Controlled notification" >/dev/null
sleep 3

adb -s "$device_serial" shell dumpsys deviceidle unforce >/dev/null
adb -s "$device_serial" shell dumpsys battery reset >/dev/null
adb -s "$device_serial" shell input keyevent KEYCODE_WAKEUP
adb -s "$device_serial" shell wm dismiss-keyguard

state=""
for _ in {1..15}; do
  state="$(broadcast_control QUERY 2>/dev/null | sed -n 's/.*data="\([^"]*\)".*/\1/p' | tail -n 1)"
  if [[ "$state" == *"persisted=Focusing"* && "$state" == *"runtime=Focusing"* &&
        "$state" == *"zen=true"* && "$state" == *"notifications=1"* ]]; then
    echo "Doze capture passed: $state"
    exit 0
  fi
  sleep 1
done

echo "Doze capture failed; last state: $state" >&2
exit 1

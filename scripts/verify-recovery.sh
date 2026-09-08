#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <emulator-serial>" >&2
  exit 2
fi

device_serial="$1"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
app_package="com.example.nonoti"
listener_component="$app_package/$app_package.notifications.NonotiNotificationListenerService"
control_component="$app_package/$app_package.debug.DebugTestControlReceiver"

broadcast() {
  local action="$1"
  adb -s "$device_serial" shell am broadcast --receiver-foreground \
    -a "$app_package.debug.$action" -n "$control_component"
}

query_state() {
  broadcast QUERY 2>/dev/null | sed -n 's/.*data="\([^"]*\)".*/\1/p' | tail -n 1
}

wait_for_state() {
  local description="$1"
  shift
  local state=""
  for _ in {1..15}; do
    state="$(query_state || true)"
    local matches=true
    for expected in "$@"; do
      if [[ "$state" != *"$expected"* ]]; then
        matches=false
        break
      fi
    done
    if [[ "$matches" == true ]]; then
      echo "$description: $state"
      return 0
    fi
    sleep 1
  done
  echo "$description failed; last state: $state" >&2
  return 1
}

assert_not_focusing() {
  local description="$1"
  local expected_capability="$2"
  local state=""
  for _ in {1..15}; do
    state="$(query_state || true)"
    if [[ "$state" == *"$expected_capability"* &&
          "$state" == *"zen=false"* &&
          "$state" != *"persisted=Focusing"* &&
          "$state" != *"runtime=Focusing"* ]]; then
      echo "$description: $state"
      return 0
    fi
    sleep 1
  done
  echo "$description failed; last state: $state" >&2
  return 1
}

restore_device() {
  adb -s "$device_serial" shell pm grant "$app_package" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  adb -s "$device_serial" shell appops set "$app_package" SCHEDULE_EXACT_ALARM allow >/dev/null 2>&1 || true
  adb -s "$device_serial" shell cmd notification allow_listener "$listener_component" 0 >/dev/null 2>&1 || true
  adb -s "$device_serial" shell cmd notification allow_dnd "$app_package" 0 >/dev/null 2>&1 || true
  broadcast RESET >/dev/null 2>&1 || true
}

trap restore_device EXIT

cd "$project_dir"
./gradlew :app:assembleDebug
adb -s "$device_serial" install -r app/build/outputs/apk/debug/app-debug.apk
restore_device
wait_for_state "ready" "post=true" "listener=true" "policy=true" "exact=true" "zen=false"

broadcast START | grep -q 'start=Started'
before_state="$(query_state)"
before_pid="$(sed -n 's/.*pid=\([0-9]*\).*/\1/p' <<<"$before_state")"
adb -s "$device_serial" shell am crash --user 0 "$app_package" >/dev/null 2>&1 || true
api_level="$(adb -s "$device_serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if (( api_level >= 35 )); then
  wait_for_state "process recovery" "persisted=Focusing" "runtime=Focusing" "zen=true"
  after_state="$(query_state)"
  after_pid="$(sed -n 's/.*pid=\([0-9]*\).*/\1/p' <<<"$after_state")"
  [[ -n "$before_pid" && -n "$after_pid" && "$before_pid" != "$after_pid" ]]
else
  assert_not_focusing "conservative process recovery" "zen=false"
fi

broadcast RESET >/dev/null
broadcast START | grep -q 'start=Started'
adb -s "$device_serial" shell pm revoke "$app_package" android.permission.POST_NOTIFICATIONS
assert_not_focusing "notification permission fail-open" "post=false"
adb -s "$device_serial" shell pm grant "$app_package" android.permission.POST_NOTIFICATIONS

broadcast RESET >/dev/null
broadcast START | grep -q 'start=Started'
adb -s "$device_serial" shell appops set "$app_package" SCHEDULE_EXACT_ALARM deny
assert_not_focusing "exact alarm fail-open" "exact=false"
adb -s "$device_serial" shell appops set "$app_package" SCHEDULE_EXACT_ALARM allow

broadcast RESET >/dev/null
broadcast START | grep -q 'start=Started'
adb -s "$device_serial" logcat -c
adb -s "$device_serial" shell cmd notification disallow_listener "$listener_component" 0
wait_for_state "notification access fail-open" "persisted=Unsupported" "runtime=Unsupported" "zen=false" "listener=false"
if adb -s "$device_serial" logcat -d -v brief AndroidRuntime:E '*:S' | grep -q 'FATAL EXCEPTION'; then
  echo "notification access revocation crashed the app" >&2
  exit 1
fi

echo "Recovery checks passed on API $api_level"

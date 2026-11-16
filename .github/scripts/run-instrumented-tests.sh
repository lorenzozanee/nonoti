#!/usr/bin/env bash
set -euo pipefail

if bash scripts/verify-emulator.sh emulator-5554 2>&1 | tee instrumentation-output.txt; then
    exit 0
else
    status=$?
    adb -s emulator-5554 logcat -d > logcat.log 2>&1 || printf '%s\n' 'Could not capture emulator logcat.' >&2
    exit "$status"
fi

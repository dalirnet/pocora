#!/bin/bash
#
# Build the debug apps, then install and open one or both.
#
# Usage:  sh run.sh <child|parent|both> [serial]
#
# Without a serial, each app goes to its own virtual phone (see phones.sh).
# With a serial, the app goes to that device, such as a real phone.
# Requires: adb

. "$(dirname "$0")/common.sh"

TARGET=${1:-}
SERIAL=${2:-}
case "$TARGET" in
    child | parent) RUN_ROLES=$TARGET ;;
    both) RUN_ROLES=$ROLES ;;
    *) fail "usage: $0 <child|parent|both> [serial]" ;;
esac

need adb
bash scripts/compile.sh debug

for role in $RUN_ROLES; do
    serial=${SERIAL:-$(phone_serial "$role")}
    adb devices | grep -q "^$serial[[:space:]]*device" || fail "$serial is not connected. Start the virtual phones with: make phones"

    apk=$(find "app/build/outputs/apk/$role/debug" -name '*.apk' | head -1)
    adb -s "$serial" install -r -t "$apk" >/dev/null
    adb -s "$serial" shell am start -n "$APPLICATION_ID_PREFIX.$role/$MAIN_ACTIVITY" >/dev/null
    echo "Done: $role app opened on $serial"
done

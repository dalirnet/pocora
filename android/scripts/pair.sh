#!/bin/bash
#
# Pair the two virtual phones, in place of scanning the code.
#
# Usage:  sh pair.sh
#
# A virtual phone has no camera to point at the other one, and the two are not on one network.
# This script does both jobs by hand:
#   - it reads the pairing code from the parent phone's log and hands it to the child app
#   - it forwards the parent app's port to this computer, which is the child phone's gateway
#
# First open the parent app, add a child and stay on the pairing code. Then run this and tap Accept.
# It works with debug builds only: a release build takes a code from the camera and nowhere else.
# With only the parent phone running, it pairs the two apps installed on that one phone.
# Requires: adb, the virtual phones running (make phones) with the apps installed (make run-both)

. "$(dirname "$0")/common.sh"

need adb

connected() {
    adb devices | grep -q "^$1[[:space:]]*device"
}

child=$(phone_serial child)
parent=$(phone_serial parent)
connected "$parent" || fail "$parent is not connected. Start the virtual phones with: make phones"
# With the child phone off, both apps are on the parent phone and meet over loopback.
connected "$child" || child=$parent

code=$(adb -s "$parent" logcat -d -s Endpoint:D | grep -o 'pocora:1:[^[:space:]]*' | tail -1 || true)
[ -n "$code" ] || fail "no pairing code yet. On the parent phone, add a child and stay on the pairing code."

[ "$child" = "$parent" ] || adb -s "$parent" forward "tcp:$PARENT_APP_PORT" "tcp:$PARENT_APP_PORT" >/dev/null
adb -s "$child" shell am start -S -n "$APPLICATION_ID_PREFIX.child/$MAIN_ACTIVITY" --es pairing_code "$code" >/dev/null

echo "Done: the child phone is asking to pair. Tap Accept on the parent phone."

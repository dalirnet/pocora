#!/bin/bash
#
# Build both apps, child and parent.
#
# Usage:  sh compile.sh [debug|release]

. "$(dirname "$0")/common.sh"

CONFIG=${1:-debug}
case "$CONFIG" in
    debug) TASK=assembleDebug ;;
    release) TASK=assembleRelease ;;
    *) fail "usage: $0 [debug|release]" ;;
esac

echo "Building $CONFIG..."
$GRADLE "$TASK" || fail "build failed."

for role in $ROLES; do
    apk=$(find "app/build/outputs/apk/$role/$CONFIG" -name '*.apk' 2>/dev/null | head -1)
    [ -n "$apk" ] || fail "no $role APK was produced."
    echo "Done: $apk"
done

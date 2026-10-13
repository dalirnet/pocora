#!/bin/bash
#
# Build both apps, child and parent. Release builds each app's Myket build too.
#
# Usage:  sh compile.sh [debug|release]

. "$(dirname "$0")/common.sh"

CONFIG=${1:-debug}
case "$CONFIG" in
    debug) TASKS=assembleDebug BUILD_TYPES=debug ;;
    release) TASKS="assembleRelease assembleMyket" BUILD_TYPES="release myket" ;;
    *) fail "usage: $0 [debug|release]" ;;
esac

echo "Building $CONFIG..."
# shellcheck disable=SC2086
$GRADLE $TASKS || fail "build failed."

for role in $ROLES; do
    for type in $BUILD_TYPES; do
        apk=$(find "app/build/outputs/apk/$role/$type" -name '*.apk' 2>/dev/null | head -1)
        [ -n "$apk" ] || fail "no $role $type APK was produced."
        echo "Done: $apk"
    done
done

#!/bin/bash
#
# Check that this machine can build and run the project.
#
# Usage:  sh check.sh

. "$(dirname "$0")/common.sh"

# --- Required ---

echo "Required"

need java
JAVA_VERSION=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)
[ "$JAVA_VERSION" -ge 17 ] 2>/dev/null || fail "JDK 17 or later required (found $JAVA_VERSION)."
echo "  java          $JAVA_VERSION"

find_sdk
echo "  android sdk   $SDK"

chmod +x gradlew
echo "  gradle        $(./gradlew --version | sed -n 's/^Gradle //p')"

# --- Optional ---

echo "Optional"

report() {
    if command -v "$1" >/dev/null 2>&1; then
        echo "  $1 found"
    else
        echo "  $1 missing, needed for: $2"
    fi
}
report ktlint "make format"
report jq "make format"
report xmllint "make format"
report adb "make run"
report avdmanager "make phones"

if [ -x "$SDK/emulator/emulator" ]; then
    echo "  emulator found"
else
    echo "  emulator missing, install with: sdkmanager \"emulator\""
fi

if [ -f "$(image_folder)/system.img" ]; then
    echo "  system image found"
else
    echo "  system image missing, install with: make image"
fi

# --- Dependencies ---

echo "Resolving dependencies..."
$GRADLE dependencies >/dev/null

echo "Ready"

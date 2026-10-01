#!/bin/bash
#
# Shared by every script in this folder. Sourced, never run.
#
# It moves to the project root, so paths in the scripts are relative to android/.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# --- Constants ---

# Storing the configuration cache makes Gradle prepare the Java compiler,
# which fails on the newest JDKs. The project has no Java, so skip the cache.
GRADLE="./gradlew --no-configuration-cache -q"

ROLES="child parent"
APPLICATION_ID_PREFIX="ir.pocora"
MAIN_ACTIVITY="ir.pocora.ui.MainActivity"

IMAGE_PACKAGE="system-images;android-35;google_apis;arm64-v8a"
PHONE_DEVICE="pixel_6"
PHONE_MEMORY_MEGABYTES=1536
PHONE_CORES=2
CHILD_PHONE_PORT=5554
PARENT_PHONE_PORT=5556

# The port the parent app listens on, as in Protocol.kt.
PARENT_APP_PORT=47601

# --- Helpers ---

fail() {
    echo "Error: $*" >&2
    exit 1
}

# need <command>...  Stop unless every command is installed.
need() {
    local command
    for command in "$@"; do
        command -v "$command" >/dev/null 2>&1 || fail "$command required."
    done
}

# find_sdk  Set SDK to the Android SDK folder.
# local.properties comes first, because that is the SDK Gradle builds with.
# The emulator, the system image and the virtual phones must live in the same one.
find_sdk() {
    SDK=""
    if [ -f local.properties ]; then
        SDK=$(grep '^sdk.dir=' local.properties | cut -d= -f2)
    fi
    [ -n "$SDK" ] || SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    [ -n "$SDK" ] && [ -d "$SDK" ] || fail "Android SDK not found. Add sdk.dir to local.properties or set ANDROID_HOME."
    export ANDROID_HOME="$SDK"
    export ANDROID_SDK_ROOT="$SDK"
}

# image_folder  Print where the system image lives inside the SDK.
image_folder() {
    echo "$SDK/$(echo "$IMAGE_PACKAGE" | tr ';' '/')"
}

# phone_name <role>  Print the virtual phone's name.
phone_name() {
    echo "pocora-$1"
}

# phone_serial <role>  Print the virtual phone's adb serial.
phone_serial() {
    case "$1" in
        child) echo "emulator-$CHILD_PHONE_PORT" ;;
        parent) echo "emulator-$PARENT_PHONE_PORT" ;;
        *) fail "unknown role: $1" ;;
    esac
}

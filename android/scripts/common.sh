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

# One virtual phone holds both apps by default, as when a parent tries Pocora first: less memory, and pairing
# runs through the app's own same-phone flow. TWO_PHONES=1 gives each app its own phone.
TWO_PHONES=${TWO_PHONES:-}
# Alone, the phone gets more of the computer: two apps and their services on 1.5 GB starve each other.
if [ -z "$TWO_PHONES" ]; then
    PHONE_MEMORY_MEGABYTES=3072
    PHONE_CORES=4
fi

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

# phone_roles  Print what each virtual phone is for: "both", or "child parent" with TWO_PHONES.
phone_roles() {
    if [ -n "$TWO_PHONES" ]; then echo "$ROLES"; else echo "both"; fi
}

# phone_name <role>  Print the virtual phone's name: pocora for both apps, pocora-<role> for one.
phone_name() {
    case "$1" in
        both) echo "pocora" ;;
        *) echo "pocora-$1" ;;
    esac
}

# phone_serial <role>  Print the adb serial of the virtual phone that role's app runs on.
phone_serial() {
    case "$1" in
        child | parent | both) ;;
        *) fail "unknown role: $1" ;;
    esac
    if [ -z "$TWO_PHONES" ] || [ "$1" = "child" ] || [ "$1" = "both" ]; then
        echo "emulator-$CHILD_PHONE_PORT"
    else
        echo "emulator-$PARENT_PHONE_PORT"
    fi
}

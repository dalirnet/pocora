#!/bin/bash
#
# The virtual phone holding both apps, or with TWO_PHONES=1 two phones: one for the child app, one for the parent app.
#
# Usage:  sh phones.sh [start|stop|create]
#
#   start    create them if needed, boot them one after the other and wait until each is ready
#   stop     shut them down
#   create   only create them
#
# Requires: avdmanager, adb, the emulator (sdkmanager "emulator") and the system image (make image)

. "$(dirname "$0")/common.sh"

BOOT_TIMEOUT_SECONDS=300
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

# avdmanager creates a phone without a keyboard device. The emulator's own Home, Back and volume
# buttons, and typing on the computer's keyboard, all go through that device, so it is switched on.
PHONE_SETTINGS="hw.keyboard=yes"

find_sdk
EMULATOR="$SDK/emulator/emulator"

# --- Steps ---

create() {
    need avdmanager
    [ -f "$(image_folder)/system.img" ] || fail "the system image is not installed. Install it with: make image"

    local role name
    for role in $(phone_roles); do
        name=$(phone_name "$role")
        if avdmanager list avd 2>/dev/null | grep -q "Name: $name\$"; then
            echo "  exists   $name"
        else
            echo "no" | avdmanager create avd --name "$name" --package "$IMAGE_PACKAGE" --device "$PHONE_DEVICE" >/dev/null
            echo "  created  $name"
        fi
        configure "$name"
    done
}

# configure <name>  Apply PHONE_SETTINGS to the phone's config.ini, replacing any value already there.
# It takes effect at the phone's next start.
configure() {
    local config="$AVD_HOME/$1.avd/config.ini" setting key
    [ -f "$config" ] || fail "$config not found"
    for setting in $PHONE_SETTINGS; do
        key=${setting%%=*}
        grep -v "^$key=" "$config" >"$config.tmp"
        echo "$setting" >>"$config.tmp"
        mv "$config.tmp" "$config"
    done
}

# system_proxy  This computer's HTTP proxy, as "http://host:port", when one is on in its network settings.
# The phones go online through it, so sites blocked here load there too. PROXY=http://host:port names another one.
system_proxy() {
    if [ -n "${PROXY:-}" ]; then
        echo "$PROXY"
        return
    fi
    command -v scutil >/dev/null 2>&1 || return 0
    scutil --proxy | awk '
        /HTTPEnable/ { on = $3 }
        /HTTPProxy/ { host = $3 }
        /HTTPPort/ { port = $3 }
        END { if (on == 1 && host != "" && port != "") print "http://" host ":" port }'
}

# Two phones may share the computer's memory, so each one is kept small:
# less memory, two cores, no sound, and the computer's own graphics card.
# Without -gpu host the emulator falls back to software graphics when memory
# is short, stalls, and is then killed by its own hang detection.
boot() {
    local role=$1 name serial
    name=$(phone_name "$role")
    serial=$(phone_serial "$role")
    PHONE_PID=""

    if adb devices | grep -q "^$serial"; then
        echo "  running  $name ($serial)"
        return
    fi
    [ -x "$EMULATOR" ] || fail "the emulator is not installed. Install it with: sdkmanager \"emulator\""
    local proxy
    proxy=$(system_proxy)
    # shellcheck disable=SC2086
    nohup "$EMULATOR" -avd "$name" -port "${serial#emulator-}" \
        -memory "$PHONE_MEMORY_MEGABYTES" -cores "$PHONE_CORES" -gpu host \
        -no-audio -no-snapshot -no-boot-anim ${proxy:+-http-proxy "$proxy"} >"/tmp/$name.log" 2>&1 &
    PHONE_PID=$!
    echo "  starting $name ($serial)${proxy:+ through $proxy}"
}

wait_ready() {
    local name serial waited=0
    name=$(phone_name "$1")
    serial=$(phone_serial "$1")

    until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        if [ -n "$PHONE_PID" ] && ! kill -0 "$PHONE_PID" 2>/dev/null; then
            fail "$name stopped while booting. See /tmp/$name.log"
        fi
        sleep 3
        waited=$((waited + 3))
        [ "$waited" -lt "$BOOT_TIMEOUT_SECONDS" ] || fail "$serial did not boot. See /tmp/$name.log"
    done
    echo "  ready    $serial"
}

stop() {
    local role serial
    for role in $(phone_roles); do
        serial=$(phone_serial "$role")
        if adb devices | grep -q "^$serial"; then
            adb -s "$serial" emu kill >/dev/null
            echo "  stopped  $serial"
        fi
    done
}

# --- Main ---

case "${1:-start}" in
    create)
        create
        ;;
    start)
        need adb
        create
        # One after the other: booting both at once is what runs out of memory.
        for role in $(phone_roles); do
            boot "$role"
            wait_ready "$role"
        done
        ;;
    stop)
        need adb
        stop
        ;;
    *)
        fail "usage: $0 [start|stop|create]"
        ;;
esac

echo "Done"

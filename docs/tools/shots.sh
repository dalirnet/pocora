#!/bin/bash
#
# Take the landing page's screenshots from the virtual phones, into assets/shots/, with more screens along the way
# for whatever needs one later.
#
# Usage:  sh tools/shots.sh
# Requires: adb, cwebp and curl. The two virtual phones are started here when they are not running, as
# cd android && TWO_PHONES=1 make phones does, and the apps are built here. The phones go online through this
# computer's proxy when it has one, which YouTube needs where it is blocked.
#
# The scene: the parent phone holds Pocora Parent and, for the other child, Pocora Child too. The child phone is the
# child the page follows. It is paired and set up first, and YouTube runs on it for some screen time while the other
# child is paired and set up, so the clock is not idle. Both phones are set to 19:00, so the schedule and its
# timeline show an evening with internet on, and the status bar is Android's demo one, saying the same.
#
# Persian is typed through the ADB Keyboard (github.com/senzhk/ADBKeyBoard), fetched once: adb alone cannot type it.
# Reading the screen costs a few seconds each time, so taps go by place on a Pixel 6 screen, which the virtual phones
# are, wherever a thing has a fixed place: the bottom button, the keypad, the name field. The rest go by their text,
# read once and tapped as soon as it shows. Animations are off while this runs, so screens settle at once.
#
# Every shot is named app-screen-detail: parent-home-child, child-setup-done. "other" is the child on the parent phone.
#
# USAGE_MINUTES=5 sh tools/shots.sh    leaves YouTube open longer, for more screen time on the Home screens

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# --- The scene ---

# The child the page follows, on the child phone; the other child, as the child app on the parent phone.
CHILD_NAME="پارسا"
CHILD_AGE="۱۴"
OTHER_NAME="پویا"
OTHER_AGE="۱۱"
PASSWORD="۱ ۲ ۳ ۴"
USAGE_MINUTES=${USAGE_MINUTES:-1}
USAGE_APP="com.google.android.youtube"
# The hour the shots are taken at, on the phones' clocks and in the status bar.
SCENE_TIME="1900"

PARENT="emulator-5556"
CHILD="emulator-5554"
ANDROID="../android"
APKS="$ANDROID/app/build/outputs/apk"
KEYBOARD_URL="https://github.com/senzhk/ADBKeyBoard/raw/master/ADBKeyboard.apk"
KEYBOARD_APK="${TMPDIR:-/tmp}/ADBKeyboard.apk"
KEYBOARD_IME="com.android.adbkeyboard/.AdbIME"
SHOTS="assets/shots"
WORK=$(mktemp -d)
# The run going on, so a new one can stop it first: two at once fight over the phones.
PID_FILE="${TMPDIR:-/tmp}/pocora-shots.pid"

# Places on a Pixel 6 screen, 1080 x 2400.
BOTTOM_BUTTON="540 2236"
NAME_FIELD="540 966"

# --- Helpers ---

fail() {
    echo "Error: $*" >&2
    exit 1
}

need() {
    local command
    for command in "$@"; do
        command -v "$command" >/dev/null 2>&1 || fail "$command required."
    done
}

step() {
    echo "  $*"
}

# --- The screen ---

# on <serial> <command...>  A shell command on one phone.
on() {
    local serial=$1
    shift
    adb -s "$serial" shell "$@"
}

# texts <serial>  Every text on the screen, one per line as "x y text", from the centre of its bounds.
# The last dump is removed first, so a failed one reads as an empty screen and not as the screen before.
texts() {
    on "$1" "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1 || true
    { on "$1" cat /sdcard/ui.xml 2>/dev/null || true; } | python3 -c '
import re, sys
for node in re.finditer(r"<node[^>]*>", sys.stdin.read()):
    text = re.search(r"text=\"([^\"]*)\"", node.group(0)).group(1)
    bounds = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", node.group(0))
    if text and bounds:
        x1, y1, x2, y2 = map(int, bounds.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2, text)
'
}

# tap <serial> <x> <y>
tap() {
    # shellcheck disable=SC2086
    on "$1" input tap $2 $3
}

# tapLine <serial> <line>  Taps where a line of texts points.
tapLine() {
    tap "$1" "$(echo "$2" | cut -d' ' -f1)" "$(echo "$2" | cut -d' ' -f2)"
}

# pick <texts> <text>  The line that is exactly the text, or else the first that contains it, or nothing.
# Nothing is not a failure here: the callers wait for the text to come.
pick() {
    local line
    line=$(echo "$1" | grep -E -- "^[0-9]+ [0-9]+ $2\$" | head -1 || true)
    [ -n "$line" ] || line=$(echo "$1" | grep -F -- "$2" | head -1 || true)
    echo "$line"
}

# await <serial> <text> [seconds]  Reads the screen until the text is on it, and prints its line.
# A hung System UI is waited out on the way. Fails when the text never comes, which stops the script unless the
# caller has a fallback.
await() {
    local seen line waited=0
    while true; do
        seen=$(texts "$1")
        line=$(pick "$seen" "$2")
        [ -z "$line" ] || break
        line=$(pick "$seen" "Wait")
        [ -z "$line" ] || tapLine "$1" "$line"
        sleep 1
        waited=$((waited + 1))
        [ "$waited" -lt "${3:-30}" ] || {
            echo "Error: did not see \"$2\" on $1" >&2
            return 1
        }
    done
    echo "$line"
}

# tapWhen <serial> <text> [seconds]  Waits until the text is on screen, then taps it.
tapWhen() {
    local line
    line=$(await "$@") || return 1
    tapLine "$1" "$line"
}

# tapScrolled <serial> <text>  Scrolls down until the text is on screen, then taps it: for a list longer than the screen.
tapScrolled() {
    local line tries
    for tries in 1 2 3 4 5 6; do
        line=$(pick "$(texts "$1")" "$2")
        if [ -n "$line" ]; then
            tapLine "$1" "$line"
            return
        fi
        on "$1" input swipe 540 1800 540 700 300
        sleep 1
    done
    fail "did not find \"$2\" on $1 scrolling down"
}

# waitText <serial> <text> [seconds]  Waits until the text is on screen.
waitText() {
    await "$@" >/dev/null
}

# --- Typing ---

# typeText <serial> <text>  Types through the ADB Keyboard, which must be the keyboard in use.
typeText() {
    on "$1" am broadcast -a ADB_INPUT_B64 --es msg "$(printf '%s' "$2" | base64)" >/dev/null
}

keyboardOn() {
    on "$1" ime enable "$KEYBOARD_IME" >/dev/null
    on "$1" ime set "$KEYBOARD_IME" >/dev/null
}

keyboardOff() {
    on "$1" ime reset >/dev/null 2>&1 || true
}

# --- The phones ---

# animations <serial> <0|1>  Off while this runs, so every screen settles at once; back on at the end.
animations() {
    local setting
    for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
        on "$1" settings put global "$setting" "$2"
    done
}

# demo <serial>  The status bar as the shots want it.
demo() {
    on "$1" settings put global sysui_demo_allowed 1
    local command
    for command in \
        "enter" \
        "clock -e hhmm $SCENE_TIME" \
        "battery -e level 100 -e plugged false" \
        "network -e wifi show -e level 4 -e fully true" \
        "network -e mobile hide" \
        "notifications -e visible false" \
        "status -e volume hide -e bluetooth hide -e location hide -e alarm hide -e sync hide -e mute hide"; do
        # shellcheck disable=SC2086
        on "$1" am broadcast -a com.android.systemui.demo -e command $command >/dev/null
    done
}

demoOff() {
    on "$1" am broadcast -a com.android.systemui.demo -e command exit >/dev/null
}

# clock <serial>  The phone's clock set to today at SCENE_TIME, which needs root: the virtual phones give it.
clock() {
    local tries
    adb -s "$1" root >/dev/null 2>&1 || true
    adb -s "$1" wait-for-device
    for tries in 1 2 3; do
        on "$1" settings put global auto_time 0
        on "$1" date "$(date +%m%d)$SCENE_TIME$(date +%Y)" >/dev/null
        sleep 1
        on "$1" date | grep -q " ${SCENE_TIME:0:2}:" && return
    done
    fail "could not set the clock on $1"
}

# clockBack <serial>  The phone's own time again.
clockBack() {
    on "$1" settings put global auto_time 1
}

# --- Around the apps ---

# shot <serial> <name>  The screen, as the page wants it: 540 x 1200 WebP.
shot() {
    sleep 1
    adb -s "$1" exec-out screencap -p >"$WORK/$2.png"
    cwebp -quiet -q 82 -resize 540 1200 "$WORK/$2.png" -o "$SHOTS/$2.webp"
    echo "  shot     $2"
}

open() {
    on "$1" am start -n "$2/ir.pocora.ui.MainActivity" >/dev/null 2>&1
    sleep 2
}

back() {
    on "$1" input keyevent KEYCODE_BACK
    sleep 1
}

# home  Back until the parent's Home, with its Add circle, is on screen.
home() {
    local tries
    for tries in 1 2 3 4; do
        texts "$PARENT" | grep -qE "^[0-9]+ [0-9]+ افزودن$" && return
        back "$PARENT"
    done
    fail "could not get back to Home on $PARENT"
}

# --- Steps ---

# prepare <serial>  The phone as the scene needs it: the apps fresh from the build, with the child's permissions
# given, no animations, the clock at the scene's hour, and the demo status bar.
prepare() {
    local serial=$1
    on "$serial" pm clear ir.pocora.child >/dev/null
    on "$serial" pm clear ir.pocora.parent >/dev/null 2>&1 || true
    adb -s "$serial" install -r -t -d "$(find "$APKS/child/debug" -name '*.apk' | head -1)" >/dev/null
    [ "$serial" = "$CHILD" ] ||
        adb -s "$serial" install -r -t -d "$(find "$APKS/parent/debug" -name '*.apk' | head -1)" >/dev/null
    adb -s "$serial" install -r "$KEYBOARD_APK" >/dev/null
    # The child's setup steps that adb can grant. The VPN and its always-on switch are done on screen.
    local role
    for role in child parent; do
        on "$serial" pm grant "ir.pocora.$role" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    done
    on "$serial" appops set ir.pocora.child android:get_usage_stats allow
    on "$serial" dpm set-active-admin ir.pocora.child/ir.pocora.service.AdminReceiver >/dev/null
    on "$serial" dumpsys deviceidle whitelist +ir.pocora.child >/dev/null
    animations "$serial" 0
    clock "$serial"
    demo "$serial"
    echo "  prepared $serial"
}

finish() {
    local serial
    rm -f "$PID_FILE"
    for serial in "$PARENT" "$CHILD"; do
        animations "$serial" 1
        clockBack "$serial"
        demoOff "$serial"
    done
    rm -rf "$WORK"
}

# The parent app's keypad, by place: 1 2 3 on the first row, 0 under the 8.
keypad() {
    local digit
    step "typing the password"
    for digit in $PASSWORD; do
        case "$digit" in
            ۱) tap "$PARENT" 288 1474 ;; ۲) tap "$PARENT" 541 1474 ;; ۳) tap "$PARENT" 793 1474 ;;
            ۴) tap "$PARENT" 288 1674 ;; ۵) tap "$PARENT" 541 1674 ;; ۶) tap "$PARENT" 793 1674 ;;
            ۷) tap "$PARENT" 288 1874 ;; ۸) tap "$PARENT" 541 1874 ;; ۹) tap "$PARENT" 793 1874 ;;
            ۰) tap "$PARENT" 541 2074 ;;
        esac
        sleep 0.3
    done
    sleep 2
}

# unlock  Past the parent app's lock, when there is one: the app locks when it leaves the screen, and it may not
# have. On first run it asks for the new password once more, and says so.
unlock() {
    waitText "$PARENT" "رمز" 5 2>/dev/null || return 0
    keypad
    if texts "$PARENT" | grep -q "یک بار دیگر"; then
        keypad
    fi
}

# addChild <name> <age> <shot>  From the parent's Welcome or Home to the pairing code of a new child.
addChild() {
    step "adding a child: $3"
    # Start, on Welcome; on Home, the Add circle in the children's row.
    if texts "$PARENT" | grep -qE "^[0-9]+ [0-9]+ شروع$"; then
        # shellcheck disable=SC2086
        tap "$PARENT" $BOTTOM_BUTTON
    else
        tapWhen "$PARENT" "افزودن"
    fi
    sleep 1
    # The keyboard needs a moment to take over, and the field a moment to take the keyboard.
    keyboardOn "$PARENT"
    sleep 1.5
    # shellcheck disable=SC2086
    tap "$PARENT" $NAME_FIELD
    sleep 1.5
    typeText "$PARENT" "$1"
    sleep 1
    # The ages scroll sideways; the older ones start off screen.
    tapWhen "$PARENT" "$2" 3 2>/dev/null || {
        on "$PARENT" input swipe 300 1334 900 1334 300
        sleep 0.5
        tapWhen "$PARENT" "$2"
    }
    keyboardOff "$PARENT"
    sleep 0.5
    shot "$PARENT" "$3"
    # shellcheck disable=SC2086
    tap "$PARENT" $BOTTOM_BUTTON
    waitText "$PARENT" "منتظر اتصال"
    step "the pairing code is up"
}

# setupChild <serial>  Through the child app's setup: the VPN's own dialog, then its settings for Always-on.
# It ends on "all done", whose one button, Done, is the bottom one: the caller taps it by its text.
setupChild() {
    local serial=$1 round seen line
    for round in 1 2 3 4 5 6; do
        seen=$(texts "$serial")
        if echo "$seen" | grep -q "همه‌چیز آماده است"; then
            step "the child app is set up"
            return
        fi
        if ! echo "$seen" | grep -q "آماده‌سازی"; then
            # The app is not on screen: brought back, it returns to its setup.
            open "$serial" ir.pocora.child
            seen=$(texts "$serial")
        fi
        step "setup step $round"
        # The step's own button: the last text on screen, above "later" where the step may wait. The VPN step
        # cannot wait and has no "later", so its button sits where "later" would.
        line=$(echo "$seen" | grep -v "بعداً" | tail -1)
        tapLine "$serial" "$line"
        sleep 2
        seen=$(texts "$serial")
        line=$(pick "$seen" "OK")
        if [ -n "$line" ]; then
            # Android asking whether the VPN may run.
            tapLine "$serial" "$line"
            sleep 1
        elif ! echo "$seen" | grep -q "آماده‌سازی"; then
            # Android's settings opened for the step. Opening them is the step.
            back "$serial"
        fi
    done
    fail "the child app's setup did not finish on $serial"
}

# --- Main ---

need adb cwebp curl python3
[ -f "$ANDROID/scripts/compile.sh" ] || fail "the android folder is not beside docs."
on "$CHILD" pm list packages | grep -q "^package:$USAGE_APP$" || fail "$USAGE_APP is not on $CHILD, and the shots want its screen time."
[ -f "$KEYBOARD_APK" ] || curl -sL -o "$KEYBOARD_APK" "$KEYBOARD_URL" || fail "could not fetch the ADB Keyboard."
mkdir -p "$SHOTS"
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Stopping the run still going..."
    pkill -P "$(cat "$PID_FILE")" 2>/dev/null || true
    kill "$(cat "$PID_FILE")" 2>/dev/null || true
    # Its last words put the phones back, which takes a moment, and must not land on top of this run.
    waited=0
    while kill -0 "$(cat "$PID_FILE")" 2>/dev/null && [ "$waited" -lt 20 ]; do
        sleep 1
        waited=$((waited + 1))
    done
fi
echo $$ >"$PID_FILE"
trap finish EXIT

echo "The phones..."
# Started when they are not running; a running one is left as it is.
(cd "$ANDROID" && TWO_PHONES=1 bash scripts/phones.sh start | grep -E "starting|ready|running" | sed 's/^/ /') ||
    fail "the phones did not start. See: cd android && TWO_PHONES=1 make phones"
for serial in "$PARENT" "$CHILD"; do
    adb devices | grep -q "^$serial[[:space:]]*device" || fail "$serial is not connected."
done

echo "Building the apps..."
(cd "$ANDROID" && bash scripts/compile.sh debug >/dev/null) || fail "the build failed. Run it alone to see why: cd android && make build"
echo "  built    child and parent"

echo "Preparing the phones..."
prepare "$PARENT"
prepare "$CHILD"

echo "The child, on the child phone..."
open "$CHILD" ir.pocora.child
waitText "$CHILD" "اسکن کد"
shot "$CHILD" child-welcome-scan
open "$PARENT" ir.pocora.parent
waitText "$PARENT" "رمز"
shot "$PARENT" parent-lock-new
unlock
waitText "$PARENT" "شروع"
shot "$PARENT" parent-welcome-start
addChild "$CHILD_NAME" "$CHILD_AGE" parent-add-child
shot "$PARENT" parent-pair-child
step "handing the code to the child phone"
(cd "$ANDROID" && TWO_PHONES=1 bash scripts/pair.sh >/dev/null)
waitText "$CHILD" "کد اسکن شد"
shot "$CHILD" child-pair-waiting
waitText "$PARENT" "قبول"
shot "$PARENT" parent-pair-accept
step "accepting the child"
tapWhen "$PARENT" "قبول"
sleep 4
waitText "$CHILD" "آماده‌سازی" 60
shot "$CHILD" child-setup-step
setupChild "$CHILD"
shot "$CHILD" child-setup-done
tapWhen "$CHILD" "تمام"
waitText "$CHILD" "زمان اینترنت"

echo "Screen time: $USAGE_APP on the child phone, while the other child is set up..."
on "$CHILD" monkey -p "$USAGE_APP" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
usageStart=$(date +%s)
# Its first-run prompts, waved away, so the time is spent on videos: notifications, Google's consent, sign-in.
for round in 1 2 3 4; do
    sleep 6
    seen=$(texts "$CHILD")
    for prompt in "Don’t allow" "Reject all" "No thanks" "Skip"; do
        line=$(pick "$seen" "$prompt")
        [ -z "$line" ] || {
            tapLine "$CHILD" "$line"
            step "waved away: $prompt"
            break
        }
    done
done

echo "The other child, as the child app on the parent phone..."
open "$PARENT" ir.pocora.parent
unlock
addChild "$OTHER_NAME" "$OTHER_AGE" parent-add-other
shot "$PARENT" parent-pair-other
step "pairing the child app on this phone"
tapWhen "$PARENT" "پوکورا فرزند روی همین موبایل است؟"
sleep 6
open "$PARENT" ir.pocora.child
waitText "$PARENT" "آماده‌سازی"
setupChild "$PARENT"
tapWhen "$PARENT" "تمام"
waitText "$PARENT" "زمان اینترنت"
shot "$PARENT" child-home-other

# The rest of the screen time, if the other child took less than that.
left=$((usageStart + USAGE_MINUTES * 60 - $(date +%s)))
if [ "$left" -gt 0 ]; then
    step "waiting $left s more for the screen time"
    sleep "$left"
fi
on "$CHILD" input keyevent KEYCODE_HOME
sleep 1

echo "The parent app..."
open "$PARENT" ir.pocora.parent
unlock
step "home, the other child"
tapWhen "$PARENT" "$OTHER_NAME"
sleep 2
shot "$PARENT" parent-home-other
step "home, the child"
tapWhen "$PARENT" "$CHILD_NAME"
sleep 2
shot "$PARENT" parent-home-child
step "internet times"
tapWhen "$PARENT" "زمان اینترنت"
sleep 1
shot "$PARENT" parent-times-child
home
step "apps"
tapWhen "$PARENT" "برنامه‌ها"
sleep 1
shot "$PARENT" parent-apps-groups
# The groups beyond the first few are behind "show all", at the end of the list.
tapScrolled "$PARENT" "نمایش همه" 2>/dev/null || true
tapScrolled "$PARENT" "ویدیو"
sleep 1
shot "$PARENT" parent-apps-video
tapWhen "$PARENT" "YouTube"
sleep 1
shot "$PARENT" parent-apps-youtube
back "$PARENT"
back "$PARENT"
tapWhen "$PARENT" "تغییر"
sleep 1
shot "$PARENT" parent-apps-lists
home
step "data"
tapWhen "$PARENT" "حجم مجاز"
sleep 1
shot "$PARENT" parent-data-quota
home
step "events and settings"
tapWhen "$PARENT" "رویدادها"
sleep 1
shot "$PARENT" parent-events-list
tapWhen "$PARENT" "تنظیمات"
sleep 1
shot "$PARENT" parent-settings-main
step "the lock"
on "$PARENT" am force-stop ir.pocora.parent
open "$PARENT" ir.pocora.parent
waitText "$PARENT" "رمز"
shot "$PARENT" parent-lock-enter

echo "The child app, on the child phone..."
open "$CHILD" ir.pocora.child
waitText "$CHILD" "زمان اینترنت"
sleep 1
shot "$CHILD" child-home-main
step "the child's screens"
tapWhen "$CHILD" "زمان اینترنت"
sleep 1
shot "$CHILD" child-times-schedule
back "$CHILD"
tapWhen "$CHILD" "گزارش مصرف"
sleep 1
shot "$CHILD" child-usage-report
back "$CHILD"
tapWhen "$CHILD" "والدین چه می‌بینند"
sleep 1
shot "$CHILD" child-parent-view
back "$CHILD"
tapWhen "$CHILD" "رویدادها"
sleep 1
shot "$CHILD" child-events-list
tapWhen "$CHILD" "تنظیمات"
sleep 1
shot "$CHILD" child-settings-main

echo "Done: $SHOTS"

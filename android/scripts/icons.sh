#!/bin/bash
#
# Write app/src/main/java/ir/pocora/ui/AppIcons.kt, the app's icons as ImageVectors, from the Solar icon set
# (Bold style).
#
# Usage:  sh icons.sh
# Requires: curl, jq, and the internet, to reach the Iconify API
#
# Solar is by 480 Design, under CC BY 4.0: https://icon-sets.iconify.design/solar/
# Add or change an icon in ICONS, then run `make icons`.

. "$(dirname "$0")/common.sh"

need curl jq

OUTPUT=app/src/main/java/ir/pocora/ui/AppIcons.kt
STYLE=bold

# Each icon the app uses: its name in AppIcons, and its name in Solar.
ICONS="
AccessTime clock-circle
Add add
AdminPanelSettings shield-user
AllInclusive infinity
Apps widget
Autorenew refresh-circle
Backspace backspace
BarChart chart-2
BatteryChargingFull battery-charge
BeachAccess umbrella
Build sledgehammer
Cake balloon
CalendarMonth calendar
Call phone
Chat chat-round-dots
Check check
CheckCircle check-circle
ChildCare smile-circle
CloudOff cloud-cross
ContentCopy copy
ContentCut scissors
DataSaverOn pie-chart-3
DataUsage pie-chart-2
Delete trash-bin-trash
DeleteSweep trash-bin-minimalistic-2
Download download-minimalistic
EmojiPeople user-hands
Event calendar-mark
FamilyRestroom users-group-rounded
Groups users-group-two-rounded
HighlightOff close-circle
Home home-2
HourglassEmpty hourglass-line
HourglassTop hourglass
Language global
LinkOff link-broken
Lock lock-keyhole
MenuBook book-bookmark
MoreTime alarm-add
MusicNote music-note
Notifications bell
Person user
PersonRemove user-minus
PhoneAndroid smartphone
PhonelinkRing smartphone-vibration
PlayArrow play
PlayCircle play-circle
PowerSettingsNew power
QrCodeScanner qr-code
Remove minus
RestartAlt restart
School square-academic-cap
Settings settings
SportsEsports gamepad
Storefront shop
SwapHoriz transfer-horizontal
Timer stopwatch
Tune tuning-2
VideogameAssetOff forbidden-circle
Visibility eye
VisibilityOff eye-closed
VpnKey key
Warning danger-triangle
WarningAmber danger-triangle
WbSunny sun
Wifi wi-fi
WifiFind magnifer
WifiOff wi-fi-off
"

# Icons that point a way, so they turn around in Persian.
MIRRORED="Backspace"

icons_json=$(echo "$ICONS" | jq -R 'select(length > 0) | split(" ") | {key: .[0], value: .[1]}' | jq -s 'from_entries')
names=$(echo "$icons_json" | jq -r --arg style "$STYLE" '[.[] + "-" + $style] | unique | join(",")')

data=$(curl -sf "https://api.iconify.design/solar.json?icons=$names") || fail "could not reach the Iconify API."

missing=$(echo "$data" | jq -r '(.not_found // []) | join(", ")')
[ -z "$missing" ] || fail "not in Solar: $missing"

# Bold Solar icons are plain filled paths.
shapes=$(echo "$data" | jq '[.icons[].body | select(test("<(circle|rect|ellipse|line|polyline|polygon)\\b"))] | length')
[ "$shapes" -eq 0 ] || fail "an icon uses a shape other than path"

kotlin=$(
    echo "$data" | jq -r \
        --argjson icons "$icons_json" \
        --arg style "$STYLE" \
        --arg mirrored "$MIRRORED" '
    . as $data
    | def body($name):
        if ($data.aliases // {})[$name] then body($data.aliases[$name].parent) else $data.icons[$name].body end;
    def parts($svg):
        [$svg | scan("<path([^>]*)/?>") | .[0]]
        | map({d: (capture("\\bd=\"(?<d>[^\"]+)\"").d), evenOdd: test("fill-rule=\"evenodd\"")});
    ($mirrored | split(" ")) as $mirror
    | $icons | to_entries | sort_by(.key)
    | map(
        . as {key: $name, value: $solar}
        | [
            "    // solar:\($solar)-\($style)",
            "    val \($name): ImageVector by lazy {",
            "        icon(",
            "            \"\($name)\",",
            (parts(body("\($solar)-\($style)"))[]
                | "            Part(",
                  "                \"\(.d)\",",
                  "                evenOdd = \(.evenOdd),",
                  "            ),"),
            (if $mirror | index($name) then "            autoMirror = true," else empty end),
            "        )",
            "    }",
            ""
        ]
        | join("\n")
    )
    | join("\n")
    '
)

cat >"$OUTPUT" <<EOF
// Path data runs long, and is written by a script, not read.
@file:Suppress("ktlint:standard:max-line-length")

package ir.pocora.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Written by scripts/icons.sh, \`make icons\`. Do not edit by hand.
// The Solar icon set, Bold style, by 480 Design, under CC BY 4.0: https://icon-sets.iconify.design/solar/
object AppIcons {
$kotlin

    private class Part(
        val d: String,
        val evenOdd: Boolean,
    )

    private const val SIZE = 24f

    // Drawn black; Icon tints it to the colour it is given.
    private fun icon(
        name: String,
        vararg parts: Part,
        autoMirror: Boolean = false,
    ): ImageVector =
        ImageVector
            .Builder(name, SIZE.dp, SIZE.dp, SIZE, SIZE, autoMirror = autoMirror)
            .apply {
                for (part in parts) {
                    addPath(
                        pathData = PathParser().parsePathString(part.d).toNodes(),
                        pathFillType = if (part.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = SolidColor(Color.Black),
                    )
                }
            }.build()
}
EOF

echo "Done: $OUTPUT ($(echo "$icons_json" | jq length) icons)"

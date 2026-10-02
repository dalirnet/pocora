#!/usr/bin/env python3
#
# Write the app's icons, drawn from the Solar icon set (Bold style), as one Kotlin file of ImageVectors.
#
# Usage:  python3 icons.py <output file>
#
# Solar is by 480 Design, under CC BY 4.0: https://icon-sets.iconify.design/solar/
# The icons come from the Iconify API. Add or change an icon here, then run `make icons`.

import json
import re
import subprocess
import sys

STYLE = "bold"

# Each icon the app uses: its name in AppIcons, and its name in Solar.
ICONS = {
    "AccessTime": "clock-circle",
    "Add": "add",
    "AdminPanelSettings": "shield-user",
    "AllInclusive": "infinity",
    "Apps": "widget",
    "Autorenew": "refresh-circle",
    "Backspace": "backspace",
    "BarChart": "chart-2",
    "BatteryChargingFull": "battery-charge",
    "BeachAccess": "umbrella",
    "Build": "sledgehammer",
    "Cake": "balloon",
    "CalendarMonth": "calendar",
    "Call": "phone",
    "Chat": "chat-round-dots",
    "Check": "check",
    "CheckCircle": "check-circle",
    "ChildCare": "smile-circle",
    "CloudOff": "cloud-cross",
    "ContentCopy": "copy",
    "ContentCut": "scissors",
    "DataSaverOn": "pie-chart-3",
    "DataUsage": "pie-chart-2",
    "Delete": "trash-bin-trash",
    "DeleteSweep": "trash-bin-minimalistic-2",
    "Download": "download-minimalistic",
    "EmojiPeople": "user-hands",
    "Event": "calendar-mark",
    "FamilyRestroom": "users-group-rounded",
    "Groups": "users-group-two-rounded",
    "HighlightOff": "close-circle",
    "Home": "home-2",
    "HourglassEmpty": "hourglass-line",
    "HourglassTop": "hourglass",
    "Language": "global",
    "LinkOff": "link-broken",
    "Lock": "lock-keyhole",
    "MenuBook": "book-bookmark",
    "MoreTime": "alarm-add",
    "MusicNote": "music-note",
    "Notifications": "bell",
    "Person": "user",
    "PersonRemove": "user-minus",
    "PhoneAndroid": "smartphone",
    "PhonelinkRing": "smartphone-vibration",
    "PlayArrow": "play",
    "PlayCircle": "play-circle",
    "PowerSettingsNew": "power",
    "QrCodeScanner": "qr-code",
    "Remove": "minus",
    "RestartAlt": "restart",
    "School": "square-academic-cap",
    "Settings": "settings",
    "SportsEsports": "gamepad",
    "Storefront": "shop",
    "SwapHoriz": "transfer-horizontal",
    "Timer": "stopwatch",
    "Tune": "tuning-2",
    "VideogameAssetOff": "forbidden-circle",
    "Visibility": "eye",
    "VisibilityOff": "eye-closed",
    "VpnKey": "key",
    "Warning": "danger-triangle",
    "WarningAmber": "danger-triangle",
    "WbSunny": "sun",
    "Wifi": "wi-fi",
    "WifiFind": "magnifer",
    "WifiOff": "wi-fi-off",
}

# Icons that point a way, so they turn around in Persian.
MIRRORED = {"Backspace"}


def fetch(names):
    # Iconify turns away Python's own user agent, so curl does the fetching.
    url = "https://api.iconify.design/solar.json?icons=" + ",".join(names)
    data = json.loads(subprocess.check_output(["curl", "-sf", url]))
    missing = data.get("not_found") or []
    if missing:
        sys.exit("Error: not in Solar: " + ", ".join(missing))
    return data


def body(data, name):
    aliases = data.get("aliases", {})
    while name in aliases:
        name = aliases[name]["parent"]
    return data["icons"][name]["body"]


def paths(svg):
    """Each path's data and whether it fills even-odd. Bold Solar icons are plain filled paths."""
    found = []
    for attributes in re.findall(r"<path([^>]*)/?>", svg):
        d = re.search(r'\bd="([^"]+)"', attributes).group(1)
        even_odd = 'fill-rule="evenodd"' in attributes
        found.append((d, even_odd))
    if re.search(r"<(circle|rect|ellipse|line|polyline|polygon)\b", svg):
        sys.exit("Error: an icon uses a shape other than path")
    return found


def kotlin(data):
    lines = [
        "// Path data runs long, and is written by a script, not read.",
        '@file:Suppress("ktlint:standard:max-line-length")',
        "",
        "package ir.pocora.ui",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.PathFillType",
        "import androidx.compose.ui.graphics.SolidColor",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "import androidx.compose.ui.graphics.vector.PathParser",
        "import androidx.compose.ui.unit.dp",
        "",
        "// Written by scripts/icons.py, `make icons`. Do not edit by hand.",
        "// The Solar icon set, Bold style, by 480 Design, under CC BY 4.0: https://icon-sets.iconify.design/solar/",
        "object AppIcons {",
    ]
    for name, solar in sorted(ICONS.items()):
        parts = []
        for d, even_odd in paths(body(data, f"{solar}-{STYLE}")):
            parts += [
                "            Part(",
                f'                "{d}",',
                f'                evenOdd = {"true" if even_odd else "false"},',
                "            ),",
            ]
        lines.append(f"    // solar:{solar}-{STYLE}")
        lines.append(f"    val {name}: ImageVector by lazy {{")
        lines.append("        icon(")
        lines.append(f'            "{name}",')
        lines.extend(parts)
        if name in MIRRORED:
            lines.append("            autoMirror = true,")
        lines.append("        )")
        lines.append("    }")
        lines.append("")
    lines += [
        "    private class Part(",
        "        val d: String,",
        "        val evenOdd: Boolean,",
        "    )",
        "",
        "    private const val SIZE = 24f",
        "",
        "    // Drawn black; Icon tints it to the colour it is given.",
        "    private fun icon(",
        "        name: String,",
        "        vararg parts: Part,",
        "        autoMirror: Boolean = false,",
        "    ): ImageVector =",
        "        ImageVector",
        "            .Builder(name, SIZE.dp, SIZE.dp, SIZE, SIZE, autoMirror = autoMirror)",
        "            .apply {",
        "                for (part in parts) {",
        "                    addPath(",
        "                        pathData = PathParser().parsePathString(part.d).toNodes(),",
        "                        pathFillType = if (part.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,",
        "                        fill = SolidColor(Color.Black),",
        "                    )",
        "                }",
        "            }.build()",
        "}",
        "",
    ]
    return "\n".join(lines)


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: icons.py <output file>")
    names = sorted({f"{solar}-{STYLE}" for solar in ICONS.values()})
    data = fetch(names)
    with open(sys.argv[1], "w", encoding="utf-8") as output:
        output.write(kotlin(data))
    print(f"Done: {sys.argv[1]} ({len(ICONS)} icons)")


if __name__ == "__main__":
    main()

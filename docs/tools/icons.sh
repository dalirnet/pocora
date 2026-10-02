#!/bin/bash
#
# Write assets/icons/sprite.svg, the landing page's icon sprite, from the Solar icon set (Bold style).
#
# Usage:  sh tools/icons.sh
# Requires: curl, jq, and the internet, to reach the Iconify API
#
# Solar is by 480 Design, under CC BY 4.0: https://icon-sets.iconify.design/solar/
# The page uses an icon as <svg><use href="assets/icons/sprite.svg#name"/></svg>. Add a name here, then run this again.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../assets/icons"

for command in curl jq; do
    command -v "$command" >/dev/null 2>&1 || { echo "Error: $command required." >&2; exit 1; }
done

ICONS="
alarm-add
danger-triangle
download-minimalistic
eye
eye-closed
home-2
lock-keyhole
pie-chart-2
qr-code
question-circle
shield-user
smartphone
square-academic-cap
sun
users-group-rounded
wallet-money
wi-fi
widget
"

icons_json=$(echo "$ICONS" | jq -R 'select(length > 0)' | jq -s .)
names=$(echo "$icons_json" | jq -r 'map(. + "-bold") | join(",")')

data=$(curl -sf "https://api.iconify.design/solar.json?icons=$names") || {
    echo "Error: could not reach the Iconify API." >&2
    exit 1
}

missing=$(echo "$data" | jq -r '(.not_found // []) | join(", ")')
[ -z "$missing" ] || { echo "Error: not in Solar: $missing" >&2; exit 1; }

symbols=$(
    echo "$data" | jq -r --argjson icons "$icons_json" '
    . as $data
    | def body($name):
        if ($data.aliases // {})[$name] then body($data.aliases[$name].parent) else $data.icons[$name].body end;
    $icons[] | "<symbol id=\"\(.)\" viewBox=\"0 0 24 24\">\(body("\(.)-bold"))</symbol>"
    '
)

# Pocora's own mark, the shield-P from the app icon without its colour, kept in assets/icons/mark.svg.
mark=$(jq -Rrs '
    (capture("viewBox=\"(?<v>[^\"]+)\"").v) as $viewBox
    | (capture("<svg[^>]*>(?<inner>.*)</svg>"; "s").inner) as $inner
    | "<symbol id=\"mark\" viewBox=\"\($viewBox)\">\($inner)</symbol>"
' mark.svg)

cat >sprite.svg <<EOF
<svg xmlns="http://www.w3.org/2000/svg">
<!-- Written by tools/icons.sh. The Solar icon set, Bold style, by 480 Design, CC BY 4.0. -->
$symbols
$mark
</svg>
EOF

echo "Done: assets/icons/sprite.svg ($(echo "$icons_json" | jq length) icons)"

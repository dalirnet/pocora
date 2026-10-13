#!/bin/bash
#
# Write the app store screenshots: for each app a few of its screens in a phone, under a title and a line saying
# what it shows.
#
# Usage:  sh tools/store.sh
# Writes: ../assets/store/child/shots/1.png ...   1080 x 1920 (9:16), as the stores ask
#         ../assets/store/parent/shots/1.png ...
# Requires: the headless Chromium that Playwright installs, or Google Chrome (CHROME=/path/to/chrome names one), and
#           the shots (sh tools/shots.sh).
#
# Each picture is a page, written here for the run, on the landing page's stylesheet, fonts and shots, with the app's
# own colour behind it. The browser draws it at twice its size, so the text is sharp.
#
# Three each, the stores' least. Each app opens on its Home screen, then the screens that matter most to it.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

DOCS="$(pwd)"
STORE="$DOCS/../assets/store"
# The page's size; the picture is twice it.
WIDTH=540
HEIGHT=960
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

fail() {
    echo "Error: $*" >&2
    exit 1
}

[ -f assets/shots/parent-home-child.webp ] || fail "the shots are missing. Take them with: sh tools/shots.sh"

# browser  The first Chromium-based browser found that draws pages without a window.
browser() {
    [ -z "${CHROME:-}" ] || { echo "$CHROME"; return; }
    local found
    found=$(find ~/Library/Caches/ms-playwright -name chrome-headless-shell -type f 2>/dev/null | sort | tail -1)
    [ -z "$found" ] || { echo "$found"; return; }
    for found in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium"; do
        [ -x "$found" ] && { echo "$found"; return; }
    done
    fail "no browser found for the screenshots. Name one with CHROME=/path/to/chrome"
}

BROWSER=$(browser)

# Each app's screens, in order: role | shot | title | line
SCREENS="
child | child-home-main | اینترنت امروز، یک‌جا | زمان باقی‌مانده و مصرف امروز
child | child-setup-step | راه‌اندازی قدم‌به‌قدم | پوکورا خودش راه را نشان می‌دهد
child | child-settings-main | اتصال به والد دیگر | پدر و مادر، هر دو در کنار فرزند
parent | parent-home-child | هر فرزند، قوانین خودش | وقت بیشتر یا قطع، با یک لمس
parent | parent-times-child | الگوهای آمادهٔ زمان‌بندی | مدرسه، امتحان، رمضان و تابستان
parent | parent-apps-lists | برنامه‌های مجاز هر فرزند | فهرست آماده، مناسب سن فرزند
"

# shot  Write one picture: role, shot name, its number, its title, its line.
shot() {
    local role=$1 name=$2 number=$3 title=$4 line=$5
    local page="$WORK/$role-$number.html" out="$STORE/$role/shots/$number.png"
    local fill dots
    # Each picture its own shade of the app's colour, turned its own way, so the row does not read as one strip. The
    # dots too: their grid turned, and fading toward another corner. dots is: turn, fade direction.
    case $number in
        1) fill="160deg, var(--$role-light), var(--$role-deep)" dots="0deg 180deg" ;;
        2) fill="225deg, var(--$role-deep), var(--$role-light)" dots="30deg 135deg" ;;
        *) fill="115deg, var(--$role), var(--$role-deep) 80%" dots="-30deg 225deg" ;;
    esac
    set -- $dots
    cat >"$page" <<EOF
<!doctype html>
<html lang="fa" dir="rtl">
    <head>
        <meta charset="utf-8" />
        <link rel="stylesheet" href="file://$DOCS/assets/css/style.css" />
        <style>
            html,
            body {
                width: ${WIDTH}px;
                height: ${HEIGHT}px;
                margin: 0;
                overflow: hidden;
            }

            body {
                position: relative;
                display: flex;
                flex-direction: column;
                align-items: center;
                color: #fff;
                background: linear-gradient($fill);
            }

            /* The hero's dots, fading away; a wrapper fades them, the layer inside turns them. */
            .dots {
                position: absolute;
                inset: 0;
                overflow: hidden;
                -webkit-mask-image: linear-gradient($2, #000, transparent 55%);
                mask-image: linear-gradient($2, #000, transparent 55%);
            }

            .dots::before {
                content: "";
                position: absolute;
                inset: -50%;
                transform: rotate($1);
                background-image: radial-gradient(rgba(255, 255, 255, 0.22) 1.4px, transparent 1.6px);
                background-size: 22px 22px;
            }


            h1 {
                position: relative;
                z-index: 1;
                margin: 40px 0 0;
                font-size: 31px;
                line-height: var(--leading-title);
                white-space: nowrap;
            }

            p {
                position: relative;
                z-index: 1;
                margin: 6px 0 0;
                font-size: 17px;
                white-space: nowrap;
                opacity: 0.9;
            }

            .phone {
                --phone: 356px;
                position: absolute;
                z-index: 1;
                top: 140px;
                left: 50%;
                transform: translateX(-50%);
                box-shadow:
                    inset 0 0 0 1px rgba(255, 255, 255, 0.16),
                    inset 0 0 0 3px #08080b;
            }

            .phone img {
                display: block;
                width: 100%;
                height: auto;
            }
        </style>
    </head>
    <body>
        <div class="dots"></div>
        <h1>$title</h1>
        <p>$line</p>
        <figure class="phone $role">
            <img src="file://$DOCS/assets/shots/$name.webp" alt="" width="540" height="1200" />
        </figure>
    </body>
</html>
EOF
    # Virtual time lets the fonts and pictures arrive before the shot, without waiting on a clock.
    "$BROWSER" --headless --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=4000 \
        --force-device-scale-factor=2 --window-size="$WIDTH,$HEIGHT" --screenshot="$out" "file://$page" \
        >/dev/null 2>&1
    [ -s "$out" ] || fail "the browser wrote nothing for $out"
    echo "$role/shots/$number.png  $name"
}

for role in child parent; do
    rm -rf "$STORE/$role/shots"
    mkdir -p "$STORE/$role/shots"
done

number=0
previous=""
while IFS='|' read -r role name title line; do
    role=$(echo "$role" | xargs)
    [ -n "$role" ] || continue
    [ "$role" = "$previous" ] || number=0
    previous=$role
    number=$((number + 1))
    shot "$role" "$(echo "$name" | xargs)" "$number" "$(echo "$title" | xargs)" "$(echo "$line" | xargs)"
done <<<"$SCREENS"

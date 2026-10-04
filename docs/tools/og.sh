#!/bin/bash
#
# Write assets/og.png, the picture a shared link shows: the page's hero, framed at 1200 x 630.
#
# Usage:  sh tools/og.sh
# Requires: a browser that can take a screenshot without a window: the headless Chromium that Playwright installs,
#           Google Chrome, or Firefox. CHROME=/path/to/chrome names one. And the shots (sh tools/shots.sh).
#
# The picture is a page, written here for the run: the hero's own markup on the site's stylesheet, fonts and shots,
# with the mark and sized for the frame. The browser opens it at the picture's size and saves what it draws, so the
# preview looks like the page because it is the page.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

DOCS="$(pwd)"
OUT="$DOCS/assets/og.png"
WIDTH=1200
HEIGHT=630
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

fail() {
    echo "Error: $*" >&2
    exit 1
}

[ -f assets/shots/parent-home-child.webp ] || fail "the shots are missing. Take them with: sh tools/shots.sh"

# browser  The first browser found that draws pages without a window.
browser() {
    [ -z "${CHROME:-}" ] || { echo "$CHROME"; return; }
    local found
    found=$(find ~/Library/Caches/ms-playwright -name chrome-headless-shell -type f 2>/dev/null | sort | tail -1)
    [ -z "$found" ] || { echo "$found"; return; }
    for found in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium" \
        "/Applications/Firefox.app/Contents/MacOS/firefox" \
        "/Applications/Firefox Developer Edition.app/Contents/MacOS/firefox"; do
        [ -x "$found" ] && { echo "$found"; return; }
    done
    fail "no browser found for the screenshot. Name one with CHROME=/path/to/chrome"
}

# The app's mark, drawn white on its tile.
MARK_BOX=$(sed -n 's/.*viewBox="\([^"]*\)".*/\1/p' assets/icons/mark.svg)
MARK=$(sed -e 's/^<svg[^>]*>//' -e 's|</svg>[[:space:]]*$||' assets/icons/mark.svg)

cat >"$WORK/og.html" <<EOF
<!doctype html>
<html lang="fa" dir="rtl">
    <head>
        <meta charset="utf-8" />
        <title>پوکورا</title>
        <link rel="stylesheet" href="file://$DOCS/assets/css/style.css" />
        <style>
            html,
            body {
                width: ${WIDTH}px;
                height: ${HEIGHT}px;
                margin: 0;
                overflow: hidden;
            }

            .hero {
                position: relative;
                width: ${WIDTH}px;
                height: ${HEIGHT}px;
                padding: 0;
                box-sizing: border-box;
                /* The hero's own shade, warm at the top and paper at the foot, with the apps' colours glowing through. */
                background: linear-gradient(160deg, #fff1e8, #fffaf5 45%, #f5f3ee);
            }

            .hero::before {
                content: "";
                position: absolute;
                inset: 0;
                background-image: radial-gradient(rgba(123, 97, 255, 0.12) 1.4px, transparent 1.6px);
                background-size: 28px 28px;
                -webkit-mask-image: linear-gradient(100deg, #000 30%, transparent 70%);
                mask-image: linear-gradient(100deg, #000 30%, transparent 70%);
            }

            .glow.hero-end {
                top: -180px;
                inset-inline-end: -140px;
                opacity: 0.5;
            }

            .glow.hero-start {
                bottom: -220px;
                inset-inline-start: 34%;
                opacity: 0.45;
            }

            .glow.parent {
                background: #d8ccff;
                top: -60px;
                inset-inline-start: 56%;
                width: 420px;
                height: 420px;
                opacity: 0.55;
            }

            .glow.child {
                background: #bfe9fb;
                bottom: -140px;
                inset-inline-start: 72%;
                width: 380px;
                height: 380px;
                opacity: 0.5;
            }

            .hero-grid {
                height: 100%;
                max-width: none;
                padding: 0 52px 0 36px;
                grid-template-columns: 1fr 560px;
                gap: 40px;
                align-items: center;
            }

            .brand {
                display: flex;
                align-items: center;
                gap: 16px;
                margin-bottom: 44px;
            }

            .brand .tile {
                width: 60px;
                height: 60px;
                border-radius: 17px;
                background: linear-gradient(135deg, #ff8a5c, #e8552a);
                box-shadow: 0 10px 24px rgba(255, 107, 61, 0.3);
            }

            .brand .tile svg {
                width: 32px;
                height: 40px;
                fill: #fff;
            }

            .brand b {
                font-size: 36px;
                color: var(--text);
            }

            h1 {
                margin: 0 0 14px;
                font-size: 66px;
                line-height: 1.3;
            }

            .lead {
                margin: 0 0 40px;
                font-size: 24px;
            }

            .pill {
                font-size: 19px;
                padding: 6px 6px 6px 18px;
            }

            .pill b {
                padding: 3px 14px;
                font-size: 15px;
            }

            .stage {
                position: relative;
                height: ${HEIGHT}px;
                --phone: 246px;
                --phone-pair: 196px;
            }

            .hero .phone.front {
                transform: translate(70px, 8px);
            }

            .hero .phone.behind {
                transform: translate(-112px, 44px);
            }

            .phone figcaption {
                display: none;
            }
        </style>
    </head>
    <body>
        <header class="hero tint">
            <div class="glow hero-end"></div>
            <div class="glow hero-start"></div>
            <div class="glow parent"></div>
            <div class="glow child"></div>
            <div class="wrap hero-grid">
                <div>
                    <div class="brand">
                        <span class="tile"><svg viewBox="$MARK_BOX">$MARK</svg></span>
                        <b>پوکورا</b>
                    </div>
                    <h1>اینترنت فرزندان،<br /><span class="gradient">به‌اندازه و به‌موقع</span></h1>
                    <p class="lead">پوکورا به زندگی دیجیتال فرزندان سر و سامان می‌دهد</p>
                    <span class="pill"><b>رایگان</b>ساخته‌شده برای خانواده‌های ایرانی</span>
                </div>
                <div class="stage">
                    <figure class="phone child behind">
                        <img src="file://$DOCS/assets/shots/child-home-main.webp" alt="" width="540" height="1200" />
                    </figure>
                    <figure class="phone parent front">
                        <img src="file://$DOCS/assets/shots/parent-home-child.webp" alt="" width="540" height="1200" />
                    </figure>
                </div>
            </div>
        </header>
    </body>
</html>
EOF

BROWSER=$(browser)
case "$BROWSER" in
    *irefox*)
        "$BROWSER" --headless --screenshot "$OUT" --window-size="$WIDTH,$HEIGHT" "file://$WORK/og.html" >/dev/null 2>&1
        ;;
    *)
        # Virtual time lets the fonts and pictures arrive before the shot, without waiting on a clock.
        "$BROWSER" --headless --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=4000 \
            --window-size="$WIDTH,$HEIGHT" --screenshot="$OUT" "file://$WORK/og.html" >/dev/null 2>&1
        ;;
esac
[ -s "$OUT" ] || fail "the browser wrote nothing. Try it by hand: $BROWSER --headless --screenshot=... file://$WORK/og.html"
echo "Done: assets/og.png, by $(basename "$BROWSER")"

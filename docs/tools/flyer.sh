#!/bin/bash
#
# Write the A5 flyer, a page to print in colour: the question, three pains and Pocora's answer to each, the two phones,
# and a QR code to the download.
#
# Usage:  sh tools/flyer.sh
# Writes: assets/flyer/pocora-a5.pdf  148 x 210 mm
#         assets/flyer/pocora-a5.png  a preview of the page, 300 dpi
# Requires: the headless Chromium that Playwright installs, or Google Chrome (CHROME=/path/to/chrome names one),
#           Python 3 with pip for the QR code (the segno package is fetched once), and the shots (sh tools/shots.sh).
#
# The flyer is a page, written here for the run, built from the landing page's own parts on its stylesheet, fonts and
# shots: the hero, the family section's rows, the download section's app heads. The browser prints it at the paper's
# size, so the flyer looks like the site because it is the site.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

DOCS="$(pwd)"
OUT="$DOCS/assets/flyer"
URL="https://pocora.ir/#download"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

fail() {
    echo "Error: $*" >&2
    exit 1
}

[ -f assets/shots/parent-home-child.webp ] || fail "the shots are missing. Take them with: sh tools/shots.sh"

# browser  The first browser found that prints pages without a window.
browser() {
    [ -z "${CHROME:-}" ] || { echo "$CHROME"; return; }
    local found
    found=$(find ~/Library/Caches/ms-playwright -name chrome-headless-shell -type f 2>/dev/null | sort | tail -1)
    [ -z "$found" ] || { echo "$found"; return; }
    for found in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
        "/Applications/Chromium.app/Contents/MacOS/Chromium"; do
        [ -x "$found" ] && { echo "$found"; return; }
    done
    fail "no browser found to print the page. Name one with CHROME=/path/to/chrome"
}

# The QR code, as an SVG path to draw inline. It needs the segno package; when Python does not have it, the script
# fetches it once into a cache of its own, so nothing is installed into the system Python.
QR_CACHE="$HOME/Library/Caches/pocora/python"
export PYTHONPATH="$QR_CACHE${PYTHONPATH:+:$PYTHONPATH}"
if ! python3 -c "import segno" 2>/dev/null; then
    echo "Fetching segno for the QR code into $QR_CACHE"
    pip3 install --quiet --target "$QR_CACHE" segno >/dev/null 2>&1 || fail "could not fetch segno. Install it by hand: pip3 install segno"
fi
cat >"$WORK/qr.py" <<'EOF'
import sys, segno
q = segno.make(sys.argv[1], error="q")
rows = list(q.matrix)
n = len(rows)
path = "".join(f"M{x} {y}h1v1h-1z" for y, row in enumerate(rows) for x, bit in enumerate(row) if bit)
print(f'<svg viewBox="0 0 {n} {n}" shape-rendering="crispEdges"><path d="{path}"/></svg>')
EOF
QR=$(python3 "$WORK/qr.py" "$URL") || fail "the QR code could not be drawn"

# The app's mark and the icon sprite, inlined so the page needs no other file for them.
MARK_BOX=$(sed -n 's/.*viewBox="\([^"]*\)".*/\1/p' assets/icons/mark.svg)
MARK=$(sed -e 's/^<svg[^>]*>//' -e 's|</svg>[[:space:]]*$||' assets/icons/mark.svg)
SPRITE=$(sed -e '1d' -e '/^<!--/d' assets/icons/sprite.svg)

# page  Writes the flyer at the given width and height to $WORK/<name>.html.
page() {
    local name=$1 width=$2 height=$3
    cat >"$WORK/$name.html" <<EOF
<!doctype html>
<html lang="fa" dir="rtl">
    <head>
        <meta charset="utf-8" />
        <title>پوکورا</title>
        <link rel="stylesheet" href="file://$DOCS/assets/css/style.css" />
        <style>
            /* The site's scale, shrunk for A5: one site pixel is about 0.19 mm. The paper is 559 CSS px wide,
               so the site's phone breakpoints apply; the rules below put the desktop layout back where it matters. */
            :root {
                --w: $width;
                --h: $height;
                --side: 7mm;
                --red: #e01e2a;
                --green-ink: #12a150;
                --orange-ink: #f0862a;
                --text-xs: 7pt;
                --text-sm: 7.5pt;
                --text-base: 8.5pt;
                --text-lg: 10pt;
                --display: 24pt;
                --s1: 0.8mm;
                --s2: 1.5mm;
                --s3: 2.3mm;
                --s4: 3mm;
                --s5: 3.8mm;
                --r-sm: 2mm;
                --r-md: 2.8mm;
                --r-lg: 4mm;
                --r-pill: 999px;
                --tile: 7mm;
                --shadow-sm: none;
                --shadow-md: none;
                --shadow-lg: none;
            }

            @page {
                size: var(--w) var(--h);
                margin: 0;
            }

            html,
            body {
                width: var(--w);
                height: var(--h);
                margin: 0;
                overflow: hidden;
                -webkit-print-color-adjust: exact;
                print-color-adjust: exact;
            }

            body {
                font-size: var(--text-base);
                line-height: 1.7;
            }

            /* The paper: the hero's warm light at the top, the two apps' colours breathing through lower down. */
            .sheet {
                display: flex;
                flex-direction: column;
                justify-content: space-between;
                gap: 5mm;
                width: var(--w);
                height: var(--h);
                padding: var(--side);
                box-sizing: border-box;
                overflow: hidden;
                background:
                    radial-gradient(95mm 75mm at 100% 0%, rgba(255, 176, 138, 0.5), transparent 70%),
                    radial-gradient(80mm 90mm at 0% 18%, rgba(255, 221, 180, 0.45), transparent 70%),
                    radial-gradient(90mm 80mm at 6% 58%, rgba(155, 135, 255, 0.14), transparent 70%),
                    radial-gradient(85mm 70mm at 100% 70%, rgba(61, 214, 245, 0.13), transparent 70%),
                    radial-gradient(110mm 60mm at 50% 104%, rgba(255, 196, 160, 0.28), transparent 70%),
                    linear-gradient(180deg, #fff8f2, #fbf9fe 55%, #f7f5fb);
            }

            /* 1. The hero: the logo, the pill, the question and the promise, and the two phones. */
            .hero-grid {
                grid-template-columns: 1fr 62mm;
                gap: 5mm;
                align-items: center;
                margin-top: 2mm;
            }

            .logo {
                margin-bottom: 4mm;
                font-size: 13pt;
                color: var(--text);
            }

            .logo .mark svg {
                width: 6mm;
                height: 7mm;
            }

            .pill b {
                background: var(--green);
            }

            .hero-grid h1 {
                margin: var(--s4) 0 var(--s2);
                letter-spacing: 0;
            }

            /* The key words in one solid orange, which a press prints as one ink mix. */
            .hero-grid .gradient {
                background: none;
                color: var(--orange-ink);
                -webkit-text-fill-color: var(--orange-ink);
            }

            .hero-grid .lead {
                margin-bottom: 0;
            }

            .stage {
                min-height: 0;
                height: 74mm;
                --phone: 32mm;
                --phone-pair: 27mm;
            }

            .phone {
                padding: 1.2mm;
                border-radius: 5mm;
                box-shadow:
                    inset 0 0 0 0.25mm rgba(255, 255, 255, 0.16),
                    inset 0 0 0 0.6mm #08080b;
            }

            .phone img {
                display: block;
                width: 100%;
                height: auto;
                border-radius: 3.8mm;
            }

            .phone::before {
                top: 2.5mm;
                width: 1.6mm;
                height: 1.6mm;
                box-shadow: 0 0 0 0.35mm rgba(0, 0, 0, 0.5);
            }

            .phone::after {
                right: -0.6mm;
                width: 0.6mm;
                border-radius: 0 0.4mm 0.4mm 0;
            }

            .stage .phone.front {
                position: absolute;
                z-index: 2;
                top: 0;
                inset-inline-start: 3mm;
                transform: none;
            }

            .stage .phone.behind {
                position: absolute;
                top: 8mm;
                inset-inline-end: 4mm;
                width: var(--phone-pair);
                transform: none;
            }

            /* 2. The pains and the answers: two columns of the family section's rows, under a coloured heading. */
            .sides {
                grid-template-columns: 1fr 1fr;
                gap: 3.5mm;
            }

            /* The site's cards, stripped to bare columns; .side.child is named too, to outrank the site's own tint. */
            .side,
            .side.child {
                gap: var(--s4);
                padding: 0;
                background: none;
                border: 0;
            }

            .side-head {
                padding: 0 1mm;
            }

            .side-head b {
                display: inline-flex;
                align-items: center;
                gap: 2.2mm;
                font-size: 12pt;
                line-height: 1.4;
                color: var(--red);
            }

            .side-head b::before {
                content: "";
                flex: none;
                width: 2.8mm;
                height: 2.8mm;
                border-radius: 50%;
                background: currentColor;
            }

            .side.child .side-head b {
                color: var(--green-ink);
            }

            .rows {
                gap: var(--s3);
            }

            .row {
                min-height: 0;
                padding: var(--s3);
                background: var(--card);
                border: 1px solid var(--line);
            }

            .row .tile {
                border-radius: 50%;
                font-size: 3.8mm;
                background: var(--red);
            }

            .side.child .row .tile {
                background: var(--green-ink);
            }

            .row-body b {
                display: block;
                margin: 0;
                font-size: var(--text-sm);
            }

            /* 3. The foot, in ink, running off the page: install, the two apps, the QR code. */
            .foot {
                display: grid;
                grid-template-columns: 1fr auto;
                gap: 6mm;
                align-items: center;
                /* 1 mm past the page on three sides, so no rounding leaves a light hairline at the edge. */
                margin: 0 calc(-1 * var(--side) - 1mm) calc(-1 * var(--side) - 1mm);
                padding: 6mm calc(var(--side) + 1mm) 8mm;
                color: #fff;
                background: var(--text);
            }

            /* The words start level with the top of the QR code; the tags end level with its foot. */
            .foot-text {
                align-self: start;
                margin-top: 1mm;
            }

            .foot h2 {
                margin: 0 0 1.5mm;
                font-size: 17pt;
                line-height: 1.3;
                color: #fff;
            }

            .foot .lead {
                margin: 0 0 7mm;
                font-size: var(--text-base);
                color: rgba(255, 255, 255, 0.72);
            }

            /* The two apps as tags, each as wide as the wider one and no wider. */
            .apps {
                display: grid;
                grid-auto-flow: column;
                grid-auto-columns: 1fr;
                grid-template-columns: none;
                justify-content: start;
                width: max-content;
                gap: 2.5mm;
            }

            .foot .app-card {
                gap: var(--s2);
                padding: 2mm 3.5mm 2mm var(--s3);
                border-radius: var(--r-lg);
                background: rgba(255, 255, 255, 0.08);
                border-color: rgba(255, 255, 255, 0.16);
            }

            .foot .app-head {
                gap: var(--s2);
            }

            /* The app's mark on a rounded square as tall as its two lines of text. */
            .foot .app-head .tile {
                width: 8.4mm;
                height: 8.4mm;
                border-radius: 2.4mm;
                font-size: 4.8mm;
                background: var(--parent);
            }

            .foot .app-card.child .app-head .tile {
                background: var(--child-deep);
            }

            .foot .app-head b {
                font-size: var(--text-sm);
                color: #fff;
            }

            .foot .app-head small {
                font-size: 6.5pt;
                color: var(--parent-light);
            }

            .foot .app-card.child .app-head small {
                color: var(--child-light);
            }

            .qr {
                display: grid;
                justify-items: center;
                gap: 1.2mm;
            }

            .qr .code {
                width: 33mm;
                height: 33mm;
                padding: 2mm;
                border-radius: 3mm;
                background: #fff;
                box-sizing: border-box;
            }

            .qr svg {
                display: block;
                width: 100%;
                height: 100%;
                fill: var(--text);
            }

            /* The address in a code face: 17 glyphs at 0.6 em span the code's 29 mm, the tile less its quiet zone. */
            .qr b {
                display: block;
                width: 33mm;
                font-family: "Fira Code", "SF Mono", Menlo, monospace;
                font-size: 8pt;
                font-weight: 600;
                text-align: center;
                white-space: nowrap;
                direction: ltr;
                color: #fff;
            }
        </style>
    </head>
    <body>
        <svg width="0" height="0" style="position: absolute">$SPRITE</svg>
        <div class="sheet">
            <header class="hero-grid">
                <div>
                    <span class="logo"><span class="mark"><svg viewBox="$MARK_BOX">$MARK</svg></span>پوکورا</span>
                    <span class="pill"><b>رایگان</b>ساخته‌شده برای خانواده‌های ایرانی</span>
                    <h1>فرزندتان همیشه<br /><span class="gradient">سرگرم موبایل</span> است؟</h1>
                    <p class="lead">با پوکورا، فرزندان به‌اندازه و به‌موقع اینترنت دارند</p>
                </div>
                <div class="stage">
                    <figure class="phone child behind">
                        <img src="file://$DOCS/assets/shots/child-home-main.webp" alt="" width="540" height="1200" />
                    </figure>
                    <figure class="phone parent front">
                        <img src="file://$DOCS/assets/shots/parent-home-child.webp" alt="" width="540" height="1200" />
                    </figure>
                </div>
            </header>

            <div class="sides">
                <div class="side">
                    <div class="side-head"><b>برایتان آشناست؟</b></div>
                    <div class="rows">
                        <div class="row">
                            <span class="tile"><svg><use href="#smartphone" /></svg></span>
                            <div class="row-body"><b>تا دیروقت با موبایل بیدار است</b><small>هر شب همین ماجرا تکرار می‌شود</small></div>
                        </div>
                        <div class="row">
                            <span class="tile"><svg><use href="#hourglass" /></svg></span>
                            <div class="row-body"><b>حجم اینترنت زود تمام می‌شود</b><small>هر بار باید دوباره حجم خرید</small></div>
                        </div>
                        <div class="row">
                            <span class="tile"><svg><use href="#question-circle" /></svg></span>
                            <div class="row-body"><b>معلوم نیست وقت او صرف چه می‌شود</b><small>نه برنامه مشخص است و نه مدت آن</small></div>
                        </div>
                    </div>
                </div>
                <div class="side child">
                    <div class="side-head"><b>پوکورا راه‌حل شماست!</b></div>
                    <div class="rows">
                        <div class="row">
                            <span class="tile"><svg><use href="#clock-circle" /></svg></span>
                            <div class="row-body"><b>اینترنت فقط در ساعت مقرر وصل است</b><small>زمان آن را شما مشخص می‌کنید</small></div>
                        </div>
                        <div class="row">
                            <span class="tile"><svg><use href="#pie-chart-2" /></svg></span>
                            <div class="row-body"><b>حجم اینترنت به‌اندازه مصرف می‌شود</b><small>هر ساعت سقف مشخصی از حجم دارد</small></div>
                        </div>
                        <div class="row">
                            <span class="tile"><svg><use href="#eye" /></svg></span>
                            <div class="row-body"><b>تمام مصرف فرزند پیش چشم شماست</b><small>زمان و حجم هر برنامه مشخص است</small></div>
                        </div>
                    </div>
                </div>
            </div>

            <footer class="foot">
                <div class="foot-text">
                    <h2>همین امروز نصب کنید</h2>
                    <p class="lead">دو برنامه، رایگان و بدون تبلیغ، برای اندروید</p>
                    <div class="apps">
                        <div class="app-card parent">
                            <div class="app-head">
                                <span class="tile"><svg><use href="#mark" /></svg></span>
                                <div><b>پوکورا والدین</b><small>تنظیم زمان و حجم اینترنت</small></div>
                            </div>
                        </div>
                        <div class="app-card child">
                            <div class="app-head">
                                <span class="tile"><svg><use href="#mark" /></svg></span>
                                <div><b>پوکورا فرزند</b><small>اجرای قوانین و گزارش مصرف</small></div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="qr">
                    <span class="code">$QR</span>
                    <b>https://pocora.ir</b>
                </div>
            </footer>
        </div>
    </body>
</html>
EOF
}

BROWSER=$(browser)
mkdir -p "$OUT"

# The PDF, at the paper's size.
page print 148mm 210mm
"$BROWSER" --headless --allow-file-access-from-files --virtual-time-budget=4000 --no-pdf-header-footer \
    --print-to-pdf="$OUT/pocora-a5.pdf" "file://$WORK/print.html" >/dev/null 2>&1
[ -s "$OUT/pocora-a5.pdf" ] || fail "the browser wrote nothing. Try it by hand: $BROWSER --headless --print-to-pdf=... file://$WORK/print.html"

# The preview, at 300 dpi. The paper is 559.4 x 793.7 CSS px; the picture takes the page at whole pixels, or the last
# column and row would fall outside the sheet and show as a light hairline.
page preview 560px 794px
"$BROWSER" --headless --hide-scrollbars --allow-file-access-from-files --virtual-time-budget=4000 \
    --force-device-scale-factor=3.125 --window-size=560,794 --screenshot="$OUT/pocora-a5.png" "file://$WORK/preview.html" >/dev/null 2>&1
[ -s "$OUT/pocora-a5.png" ] || fail "the browser wrote no preview"

echo "Done: assets/flyer/pocora-a5.pdf and pocora-a5.png, by $(basename "$BROWSER")"

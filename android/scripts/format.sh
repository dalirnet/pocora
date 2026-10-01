#!/bin/bash
#
# Format the sources: Kotlin, JSON and XML.
#
# Usage:  sh format.sh
# Requires: ktlint, jq, xmllint

. "$(dirname "$0")/common.sh"

need ktlint jq xmllint

# --- Kotlin ---

ktlint -F "app/src/**/*.kt"

# --- JSON ---

find app/src -name '*.json' | while read -r file; do
    jq --indent 4 . "$file" >"$file.tmp" && mv "$file.tmp" "$file"
done

# --- XML ---

export XMLLINT_INDENT="    "
find app/src/main/res app/manifests app/src/main/AndroidManifest.xml -name '*.xml' | while read -r file; do
    xmllint --format "$file" -o "$file"
done

echo "Done"

#!/bin/bash
#
# Remove build output and this project's Gradle and Kotlin caches.
#
# Usage:  sh clean.sh

. "$(dirname "$0")/common.sh"

for path in build app/build .gradle .kotlin; do
    if [ -e "$path" ]; then
        rm -rf "$path"
        echo "  removed $path"
    fi
done

echo "Done"

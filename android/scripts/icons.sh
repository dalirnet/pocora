#!/bin/bash
#
# Write app/src/main/java/ir/pocora/ui/AppIcons.kt from the Solar icon set.
#
# Usage:  sh icons.sh
# Requires: python3, curl, and the internet, to reach the Iconify API

. "$(dirname "$0")/common.sh"

need python3 curl

python3 scripts/icons.py app/src/main/java/ir/pocora/ui/AppIcons.kt

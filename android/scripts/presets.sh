#!/bin/bash
#
# Write app/src/main/assets/presets.json from ../preset/, the design source.
#
# Usage:  sh presets.sh
# Requires: python3

. "$(dirname "$0")/common.sh"

need python3

python3 scripts/presets.py ../preset app/src/main/assets/presets.json

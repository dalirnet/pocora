#!/bin/bash
#
# Run the unit tests of both apps.
#
# Usage:  sh test.sh

. "$(dirname "$0")/common.sh"

echo "Testing..."
$GRADLE testChildDebugUnitTest testParentDebugUnitTest || fail "tests failed."

for role in $ROLES; do
    results="app/build/test-results/test$(echo "$role" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')DebugUnitTest"
    count=$(cat "$results"/*.xml 2>/dev/null | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*' | awk '{sum += $1} END {print sum + 0}')
    echo "Done: $role, $count tests passed"
done

#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
GATE_ROOT="$REPOSITORY_ROOT/spikes/android-gate0"
UI_ROOT="$REPOSITORY_ROOT/spikes/android-ui"
GRADLE_WRAPPER="$GATE_ROOT/gradlew"
UI_MANIFEST="$UI_ROOT/app/src/main/AndroidManifest.xml"
UI_SOURCE="$UI_ROOT/app/src/main"

fail() {
    echo "Android pre-device verification failed: $1" >&2
    exit 1
}

[[ ! -e "$REPOSITORY_ROOT/android-app" ]] \
    || fail "android-app/ exists before the physical Gate 0 decision is PASS"
[[ -x "$GRADLE_WRAPPER" ]] || fail "the pinned Gate 0 Gradle wrapper is unavailable"
[[ -f "$UI_MANIFEST" ]] || fail "the permission-free UI prototype manifest is missing"

if command -v rg >/dev/null 2>&1; then
    rg --quiet '<uses-permission|<service' "$UI_MANIFEST" \
        && fail "the UI prototype must not declare a permission or service"
    rg --quiet \
        'AccessibilityService|TYPE_ACCESSIBILITY_OVERLAY|NfcAdapter|UsageStatsManager|DevicePolicyManager|VpnService' \
        "$UI_SOURCE" \
        && fail "the UI prototype crossed into a restricted platform capability"
else
    grep -E -q '<uses-permission|<service' "$UI_MANIFEST" \
        && fail "the UI prototype must not declare a permission or service"
    grep -E -R -q \
        'AccessibilityService|TYPE_ACCESSIBILITY_OVERLAY|NfcAdapter|UsageStatsManager|DevicePolicyManager|VpnService' \
        "$UI_SOURCE" \
        && fail "the UI prototype crossed into a restricted platform capability"
fi

bash "$GATE_ROOT/scripts/verify-static-policy.sh"

"$GRADLE_WRAPPER" --no-daemon -p "$GATE_ROOT" \
    testDebugUnitTest \
    lintDebug \
    assembleDebug \
    assembleDebugAndroidTest

"$GRADLE_WRAPPER" --no-daemon -p "$UI_ROOT" \
    testDebugUnitTest \
    lintDebug \
    assembleDebug

echo "Android pre-device baseline verified; production remains HOLD pending physical and store gates."

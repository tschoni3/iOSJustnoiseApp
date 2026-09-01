#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
GATE_ROOT="$REPOSITORY_ROOT/spikes/android-gate0"
UI_ROOT="$REPOSITORY_ROOT/spikes/android-ui"
INTEGRATED_ROOT="$REPOSITORY_ROOT/spikes/android-integrated"
GRADLE_WRAPPER="$GATE_ROOT/gradlew"
UI_MANIFEST="$UI_ROOT/app/src/main/AndroidManifest.xml"
UI_SOURCE="$UI_ROOT/app/src/main"
INTEGRATED_MANIFEST="$INTEGRATED_ROOT/app/src/main/AndroidManifest.xml"
INTEGRATED_ACTIVITY="$INTEGRATED_ROOT/app/src/main/java/com/justnoise/integrated/MainActivity.kt"

fail() {
    echo "Android pre-device verification failed: $1" >&2
    exit 1
}

[[ ! -e "$REPOSITORY_ROOT/android-app" ]] \
    || fail "android-app/ exists before the physical Gate 0 decision is PASS"
[[ -x "$GRADLE_WRAPPER" ]] || fail "the pinned Gate 0 Gradle wrapper is unavailable"
[[ -f "$UI_MANIFEST" ]] || fail "the permission-free UI prototype manifest is missing"
[[ -f "$INTEGRATED_MANIFEST" ]] || fail "the integrated Android development manifest is missing"

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

if command -v rg >/dev/null 2>&1; then
    rg --quiet 'android.nfc.action.NDEF_DISCOVERED' "$INTEGRATED_MANIFEST" \
        || fail "the integrated development app is missing direct NDEF dispatch"
    rg --quiet 'Justnoise Android Dev app blocking' "$INTEGRATED_ROOT/app/src/main/res" \
        || fail "the integrated Accessibility service has no unambiguous user-facing label"
    rg --quiet 'enableReaderMode' "$INTEGRATED_ACTIVITY" \
        || fail "the polished integrated Zap surface is not wired to a real NFC reader"
    rg --quiet 'ProcessDirectNfcMutationGate.tryClaim' "$INTEGRATED_ACTIVITY" \
        || fail "the integrated foreground reader does not reserve the shared mutation gate"
else
    grep -q 'android.nfc.action.NDEF_DISCOVERED' "$INTEGRATED_MANIFEST" \
        || fail "the integrated development app is missing direct NDEF dispatch"
    grep -R -q 'Justnoise Android Dev app blocking' "$INTEGRATED_ROOT/app/src/main/res" \
        || fail "the integrated Accessibility service has no unambiguous user-facing label"
    grep -q 'enableReaderMode' "$INTEGRATED_ACTIVITY" \
        || fail "the polished integrated Zap surface is not wired to a real NFC reader"
    grep -q 'ProcessDirectNfcMutationGate.tryClaim' "$INTEGRATED_ACTIVITY" \
        || fail "the integrated foreground reader does not reserve the shared mutation gate"
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

"$GRADLE_WRAPPER" --no-daemon -p "$INTEGRATED_ROOT" \
    lintDebug \
    assembleDebug

echo "Android spikes and integrated development APK verified; production remains HOLD pending physical and store gates."

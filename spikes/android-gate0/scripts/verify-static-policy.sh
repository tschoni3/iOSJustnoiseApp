#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPIKE_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
REPOSITORY_ROOT="$(cd "$SPIKE_ROOT/../.." && pwd)"
MANIFEST="$SPIKE_ROOT/app/src/main/AndroidManifest.xml"
ACCESSIBILITY_METADATA="$SPIKE_ROOT/app/src/main/res/xml/gate0_accessibility_service.xml"
SERVICE_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/restriction/Gate0AccessibilityService.kt"
MAIN_SOURCE="$SPIKE_ROOT/app/src/main/java"
MATRIX="$SPIKE_ROOT/evidence/DEVICE_MATRIX.md"

fail() {
    echo "Android Gate 0 static verification failed: $1" >&2
    exit 1
}

require_literal() {
    local value="$1"
    local file="$2"
    rg --fixed-strings --quiet "$value" "$file" || fail "missing '$value' in $file"
}

reject_literal() {
    local value="$1"
    local path="$2"
    if rg --fixed-strings --quiet "$value" "$path"; then
        fail "prohibited '$value' found in $path"
    fi
}

require_literal 'id("com.android.application") version "9.3.0"' "$SPIKE_ROOT/build.gradle.kts"
require_literal 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip' "$SPIKE_ROOT/gradle/wrapper/gradle-wrapper.properties"
require_literal 'distributionSha256Sum=940e623ea98e40ea9ad398770a6ebb91a61c0869d394dda81aa86b0f4f0025e7' "$SPIKE_ROOT/gradle/wrapper/gradle-wrapper.properties"
require_literal 'compileSdk = 36' "$SPIKE_ROOT/app/build.gradle.kts"
require_literal 'targetSdk = 36' "$SPIKE_ROOT/app/build.gradle.kts"
require_literal 'minSdk = 31' "$SPIKE_ROOT/app/build.gradle.kts"
require_literal 'include(":app")' "$SPIKE_ROOT/settings.gradle.kts"
reject_literal 'include(":app",' "$SPIKE_ROOT/settings.gradle.kts"

require_literal 'android.permission.NFC' "$MANIFEST"
require_literal 'android.intent.category.LAUNCHER' "$MANIFEST"
require_literal 'android.intent.category.HOME' "$MANIFEST"
require_literal 'android:allowBackup="false"' "$MANIFEST"
require_literal 'android:fullBackupContent="false"' "$MANIFEST"
require_literal 'android:dataExtractionRules="@xml/data_extraction_rules"' "$MANIFEST"
for permission in \
    android.permission.INTERNET \
    android.permission.QUERY_ALL_PACKAGES \
    android.permission.SYSTEM_ALERT_WINDOW \
    android.permission.PACKAGE_USAGE_STATS; do
    reject_literal "$permission" "$MANIFEST"
done

require_literal 'android:accessibilityEventTypes="typeWindowStateChanged"' "$ACCESSIBILITY_METADATA"
require_literal 'android:canRetrieveWindowContent="false"' "$ACCESSIBILITY_METADATA"
require_literal 'android:isAccessibilityTool="false"' "$ACCESSIBILITY_METADATA"
require_literal 'android:notificationTimeout="0"' "$ACCESSIBILITY_METADATA"
reject_literal 'android:accessibilityFlags' "$ACCESSIBILITY_METADATA"

require_literal 'event.packageName' "$SERVICE_SOURCE"
for content_api in \
    event.source \
    event.text \
    rootInActiveWindow \
    findAccessibilityNodeInfos \
    FLAG_RETRIEVE_INTERACTIVE_WINDOWS \
    AccessibilityNodeInfo; do
    reject_literal "$content_api" "$SERVICE_SOURCE"
done

for disallowed_runtime in \
    org.jetbrains.kotlin.android \
    compose \
    retrofit \
    okhttp \
    firebase \
    java.net; do
    reject_literal "$disallowed_runtime" "$SPIKE_ROOT/app/build.gradle.kts"
done

reject_literal 'android.util.Log' "$MAIN_SOURCE"
reject_literal 'println(' "$MAIN_SOURCE"
reject_literal 'tag.id' "$MAIN_SOURCE"
reject_literal 'tag.getId' "$MAIN_SOURCE"
require_literal '"com.android.vending"' "$MAIN_SOURCE/com/justnoise/gate0/platform/discovery/ProtectedPackageResolver.kt"
require_literal '"com.sec.android.app.samsungapps"' "$MAIN_SOURCE/com/justnoise/gate0/platform/discovery/ProtectedPackageResolver.kt"
require_literal '<string name="shield_open_justnoise">Keep going</string>' "$SPIKE_ROOT/app/src/main/res/values/strings.xml"
require_literal '<cloud-backup>' "$SPIKE_ROOT/app/src/main/res/xml/data_extraction_rules.xml"
require_literal '<device-transfer>' "$SPIKE_ROOT/app/src/main/res/xml/data_extraction_rules.xml"

matrix_rows="$(rg --count '^\| G0-[0-9]{2} .*\| BLOCKED \| BLOCKED \|' "$MATRIX")"
[[ "$matrix_rows" == "18" ]] || fail "device matrix must contain 18 explicitly BLOCKED rows"

bash "$REPOSITORY_ROOT/scripts/verify-product-contracts.sh"
echo "Android Gate 0 static policy boundary verified; physical evidence remains BLOCKED."

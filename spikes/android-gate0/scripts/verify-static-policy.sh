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
WRAPPER_JAR="$SPIKE_ROOT/gradle/wrapper/gradle-wrapper.jar"
OFFICIAL_WRAPPER_JAR_SHA256="497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

fail() {
    echo "Android Gate 0 static verification failed: $1" >&2
    exit 1
}

contains_literal() {
    local value="$1"
    local path="$2"

    if command -v rg >/dev/null 2>&1; then
        rg --fixed-strings --quiet -- "$value" "$path"
    else
        grep -F -R -q -- "$value" "$path"
    fi
}

require_literal() {
    local value="$1"
    local file="$2"
    contains_literal "$value" "$file" || fail "missing '$value' in $file"
}

reject_literal() {
    local value="$1"
    local path="$2"
    if contains_literal "$value" "$path"; then
        fail "prohibited '$value' found in $path"
    fi
}

sha256_file() {
    local file="$1"

    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{ print $1 }'
    else
        sha256sum "$file" | awk '{ print $1 }'
    fi
}

require_literal 'id("com.android.application") version "9.3.0"' "$SPIKE_ROOT/build.gradle.kts"
require_literal 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip' "$SPIKE_ROOT/gradle/wrapper/gradle-wrapper.properties"
require_literal 'distributionSha256Sum=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746' "$SPIKE_ROOT/gradle/wrapper/gradle-wrapper.properties"
[[ -x "$SPIKE_ROOT/gradlew" ]] || fail "Gradle wrapper launcher must be executable"
[[ -f "$WRAPPER_JAR" ]] || fail "Gradle wrapper JAR is missing"
wrapper_jar_sha256="$(sha256_file "$WRAPPER_JAR")"
[[ "$wrapper_jar_sha256" == "$OFFICIAL_WRAPPER_JAR_SHA256" ]] \
    || fail "Gradle wrapper JAR checksum does not match the official Gradle 9.5.0 checksum"
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

if command -v rg >/dev/null 2>&1; then
    matrix_rows="$(rg --count '^\| G0-[0-9]{2} .*\| BLOCKED \| BLOCKED \|' "$MATRIX" || true)"
else
    matrix_rows="$(grep -E -c '^\| G0-[0-9]{2} .*\| BLOCKED \| BLOCKED \|' "$MATRIX" || true)"
fi
[[ "$matrix_rows" == "18" ]] || fail "device matrix must contain 18 explicitly BLOCKED rows"

bash "$REPOSITORY_ROOT/scripts/verify-product-contracts.sh"
echo "Android Gate 0 static policy boundary verified; physical evidence remains BLOCKED."

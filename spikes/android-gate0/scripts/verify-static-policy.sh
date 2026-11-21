#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPIKE_ROOT="$(cd "$SCRIPT_DIRECTORY/.." && pwd)"
REPOSITORY_ROOT="$(cd "$SPIKE_ROOT/../.." && pwd)"
MANIFEST="$SPIKE_ROOT/app/src/main/AndroidManifest.xml"
INTEGRATED_MANIFEST="$REPOSITORY_ROOT/spikes/android-integrated/app/src/main/AndroidManifest.xml"
ACCESSIBILITY_METADATA="$SPIKE_ROOT/app/src/main/res/xml/gate0_accessibility_service.xml"
SERVICE_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/restriction/Gate0AccessibilityService.kt"
OVERLAY_EVENT_RULES_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/restriction/OverlayEventRules.kt"
NDEF_READER_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/nfc/NdefTagReader.kt"
NDEF_PARSER_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/nfc/NdefTextPayloadParser.kt"
SCAN_COORDINATOR_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/nfc/OneShotScanCoordinator.kt"
ACTIVITY_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/app/MainActivity.kt"
DIRECT_ACTIVITY_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/app/DirectNfcActivity.kt"
DIRECT_RULES_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/nfc/DirectNfcDispatchRules.kt"
DIRECT_MUTATION_GATE_SOURCE="$SPIKE_ROOT/app/src/main/java/com/justnoise/gate0/platform/nfc/DirectNfcMutationGate.kt"
DATA_INVENTORY="$SPIKE_ROOT/evidence/DATA_INVENTORY.md"
DECISION_RECORD="$SPIKE_ROOT/evidence/DECISION_RECORD.md"
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
require_literal 'android.nfc.action.NDEF_DISCOVERED' "$MANIFEST"
require_literal 'android:mimeType="text/plain"' "$MANIFEST"
require_literal 'android:allowBackup="false"' "$MANIFEST"
require_literal 'android:fullBackupContent="false"' "$MANIFEST"
require_literal 'android:dataExtractionRules="@xml/data_extraction_rules"' "$MANIFEST"
for permission in \
    android.permission.INTERNET \
    android.permission.USE_EXACT_ALARM \
    android.permission.QUERY_ALL_PACKAGES \
    android.permission.SYSTEM_ALERT_WINDOW \
    android.permission.POST_PROMOTED_NOTIFICATIONS \
    android.permission.FOREGROUND_SERVICE \
    android.permission.PACKAGE_USAGE_STATS; do
    reject_literal "$permission" "$MANIFEST"
done

python3 - "$MANIFEST" "$INTEGRATED_MANIFEST" <<'PY'
from pathlib import Path
import io
import sys
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
permissions = {
    "android.permission.NFC",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",
}
actions = {
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.TIME_SET",
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.MY_PACKAGE_REPLACED",
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
}

for filename in sys.argv[1:]:
    def require(condition, message):
        if not condition:
            raise SystemExit(f"Android source policy boundary failed: {filename}: {message}")

    contents = Path(filename).read_text()
    require("<!DOCTYPE" not in contents and "<!ENTITY" not in contents, "Unexpected XML declarations")
    root = ET.fromstring(contents)
    require(root.tag == "manifest", "Expected a manifest root")
    requested_nodes = [node for node in root if node.tag.startswith("uses-permission")]
    require(all(node.tag == "uses-permission" and set(node.attrib) == {android + "name"}
                and len(node) == 0 for node in requested_nodes), "Unexpected permission shape")
    requested = [node.get(android + "name") for node in requested_nodes]
    require(len(requested) == len(permissions) and set(requested) == permissions,
            "Permissions must be exactly NFC, optional notifications and the three explicit scheduling permissions")
    applications = root.findall("application")
    require(len(applications) == 1, "Expected one application")
    application = applications[0]
    receivers = application.findall("receiver")
    require(len(receivers) == 2, "Expected only schedule and notification dismissal receivers in source")
    schedule_receivers = [node for node in receivers if node.get(android + "name") in {
        ".platform.schedule.ScheduleAlarmReceiver",
        "com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver",
    }]
    require(len(schedule_receivers) == 1, "Expected exactly one reviewed schedule receiver")
    receiver = schedule_receivers[0]
    require(set(receiver.attrib) <= {android + name for name in ("name", "exported", "directBootAware", "enabled")},
            "Unexpected receiver attribute or process override")
    require(receiver.get(android + "exported") == "false", "Schedule receiver must explicitly be non-exported")
    require(receiver.get(android + "directBootAware", "false") == "false"
            and application.get(android + "directBootAware", "false") == "false", "Direct-boot scheduling is prohibited")
    require(receiver.get(android + "enabled", "true") == "true", "Schedule receiver is disabled")
    require(len(receiver) == 1 and receiver[0].tag == "intent-filter" and not receiver[0].attrib,
            "Expected one unqualified schedule intent filter")
    entries = list(receiver[0])
    require(all(node.tag == "action" and set(node.attrib) == {android + "name"}
                and len(node) == 0 for node in entries), "Only named system actions belong in the schedule filter")
    received_actions = [node.get(android + "name") for node in entries]
    require(len(received_actions) == len(actions) and set(received_actions) == actions,
            "Schedule receiver actions differ from the exact allowlist")

    dismissals = [node for node in receivers if node.get(android + "name") ==
                  "com.justnoise.gate0.platform.notification.SessionNotificationDismissReceiver"]
    require(len(dismissals) == 1, "Expected exactly one reviewed notification dismissal receiver")
    dismissal = dismissals[0]
    require(set(dismissal.attrib) <= {android + name for name in ("name", "exported", "enabled")},
            "Unexpected notification dismissal attribute, permission or execution override")
    require(dismissal.get(android + "exported") == "false",
            "Notification dismissal receiver must explicitly be non-exported")
    require(dismissal.get(android + "enabled", "true") == "true", "Notification dismissal receiver is disabled")
    require(len(dismissal) == 0, "Notification dismissal receiver must not have filters or other children")

    # The compatibility experiment is a debug source-set overlay, never a new
    # shipping permission or component. Release package checks remain unchanged.
    sources = Path(filename).parent.parent
    debug_manifest = sources / "debug" / "AndroidManifest.xml"
    require(debug_manifest.is_file(), f"Missing debug experiment overlay: {debug_manifest}")
    debug_contents = debug_manifest.read_text()
    require("<!DOCTYPE" not in debug_contents and "<!ENTITY" not in debug_contents,
            "Unexpected debug overlay XML declarations")
    debug = ET.fromstring(debug_contents)
    namespaces = [binding for _, binding in ET.iterparse(io.StringIO(debug_contents), events=("start-ns",))]
    require(namespaces == [("android", android[1:-1])], "Debug overlay must declare only the Android namespace")
    require(debug.tag == "manifest" and not debug.attrib and len(debug) == 1,
            "Debug overlay must contain only its one reviewed permission and no manifest overrides")
    permission = debug[0]
    promotion_permission = "android.permission.POST_PROMOTED_NOTIFICATIONS"
    require(permission.tag == "uses-permission" and permission.attrib == {android + "name": promotion_permission}
            and len(permission) == 0, "Debug overlay is not the exact promoted-notification permission")
    release_manifest = sources / "release" / "AndroidManifest.xml"
    if release_manifest.exists():
        release_contents = release_manifest.read_text()
        require("<!DOCTYPE" not in release_contents and "<!ENTITY" not in release_contents,
                "Unexpected release overlay XML declarations")
        release = ET.fromstring(release_contents)
        require(release.tag == "manifest", "Expected release manifest overlay")
        require(not any(node.tag.startswith("uses-permission") and
                        node.get(android + "name") == promotion_permission for node in release),
                "Release overlay must not request promoted notifications")
PY

require_literal 'android:accessibilityEventTypes="typeWindowStateChanged"' "$ACCESSIBILITY_METADATA"
require_literal 'android:canRetrieveWindowContent="false"' "$ACCESSIBILITY_METADATA"
require_literal 'android:isAccessibilityTool="false"' "$ACCESSIBILITY_METADATA"
require_literal 'android:notificationTimeout="0"' "$ACCESSIBILITY_METADATA"
reject_literal 'android:accessibilityFlags' "$ACCESSIBILITY_METADATA"

require_literal 'event?.packageName' "$SERVICE_SOURCE"
require_literal 'OverlayEventDecision.IGNORE' "$SERVICE_SOURCE"
require_literal 'overlayController.isShowing' "$SERVICE_SOURCE"
require_literal 'foregroundPackage == justnoisePackage' "$OVERLAY_EVENT_RULES_SOURCE"
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
require_literal '<string name="shield_open_justnoise">Be Right Back</string>' "$SPIKE_ROOT/app/src/main/res/values/strings.xml"
require_literal 'android:clickable="true"' "$SPIKE_ROOT/app/src/main/res/layout/overlay_blocked.xml"
require_literal '<cloud-backup>' "$SPIKE_ROOT/app/src/main/res/xml/data_extraction_rules.xml"
require_literal '<device-transfer>' "$SPIKE_ROOT/app/src/main/res/xml/data_extraction_rules.xml"

require_literal 'selectSingleCanonicalStrictUtf8Record' "$NDEF_PARSER_SOURCE"
require_literal 'AMBIGUOUS_TEXT_RECORDS' "$NDEF_PARSER_SOURCE"
require_literal 'if (textRecordIndexes.size != 1)' "$NDEF_READER_SOURCE"
require_literal 'payload = if (index == candidateIndex) record.payload.copyOf() else byteArrayOf()' "$NDEF_READER_SOURCE"
require_literal 'transientFields.forEach { fields -> fields.payload.fill(0) }' "$NDEF_READER_SOURCE"
require_literal 'catch (_: TagLostException)' "$NDEF_READER_SOURCE"
require_literal 'catch (_: FormatException)' "$NDEF_READER_SOURCE"
require_literal 'class OneShotScanCoordinator' "$SCAN_COORDINATOR_SOURCE"
require_literal 'fun isClaimActive' "$SCAN_COORDINATOR_SOURCE"
require_literal 'fun claim(token: Token<P>)' "$SCAN_COORDINATOR_SOURCE"
require_literal '{ tag -> onTagDiscovered(token, tag) }' "$ACTIVITY_SOURCE"
require_literal 'scanCoordinator.claim(token)' "$ACTIVITY_SOURCE"
require_literal 'completeOneShotScan(token, result)' "$ACTIVITY_SOURCE"
require_literal 'Reserve this physical interaction before restoring normal NFC dispatch' "$ACTIVITY_SOURCE"
require_literal 'ProcessDirectNfcMutationGate.tryClaim' "$ACTIVITY_SOURCE"
require_literal 'candidate.liveNfcTag()' "$DIRECT_ACTIVITY_SOURCE"
require_literal 'NdefTagReader.readSingleStrictTextRecord' "$DIRECT_ACTIVITY_SOURCE"
require_literal 'DirectNfcDispatchRules.accepts' "$DIRECT_ACTIVITY_SOURCE"
require_literal 'ProcessDirectNfcMutationGate.tryClaim' "$DIRECT_ACTIVITY_SOURCE"
reject_literal 'EXTRA_NDEF_MESSAGES' "$DIRECT_ACTIVITY_SOURCE"
require_literal 'hasLiveTag' "$DIRECT_RULES_SOURCE"
require_literal 'AtomicLong' "$DIRECT_MUTATION_GATE_SOURCE"
require_literal 'multiple Text candidates are rejected as ambiguous' "$DATA_INVENTORY"
require_literal 'lifecycle cancellation invalidates a late result' "$DATA_INVENTORY"
require_literal 'copied `EXTRA_NDEF_MESSAGES` are never read or trusted' "$DATA_INVENTORY"
require_literal 'All 19 required A16 cells remain `BLOCKED`' "$DECISION_RECORD"
require_literal 'Galaxy A16 family' "$MATRIX"
require_literal '`SM-A165F`' "$MATRIX"
require_literal 'A16 is required; A17 is deferred/unverified and excluded from gate arithmetic' "$MATRIX"
require_literal 'Required cells: 19.' "$MATRIX"
require_literal 'Passing cells: 0.' "$MATRIX"
require_literal 'Blocked/failing cells: 19.' "$MATRIX"

if command -v rg >/dev/null 2>&1; then
    matrix_rows="$(rg --count '^\| G0-[0-9]{2} \| [^|]*\| BLOCKED \| (PASS|FAIL|BLOCKED) \|' "$MATRIX" || true)"
else
    matrix_rows="$(grep -E -c '^\| G0-[0-9]{2} \| [^|]*\| BLOCKED \| (PASS|FAIL|BLOCKED) \|' "$MATRIX" || true)"
fi
[[ "$matrix_rows" == "19" ]] || fail "device matrix must contain 19 explicitly BLOCKED required A16 rows"

bash "$REPOSITORY_ROOT/scripts/verify-product-contracts.sh"
echo "Android Gate 0 static policy boundary verified; physical evidence remains BLOCKED."

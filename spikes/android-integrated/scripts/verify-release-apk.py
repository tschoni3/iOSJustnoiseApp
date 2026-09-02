#!/usr/bin/env python3
"""Check a built release APK's narrow manifest boundary, not signing or readiness."""

import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


ANDROID = "http://schemas.android.com/apk/res/android:"
SERVICE = "com.justnoise.gate0.platform.restriction.Gate0AccessibilityService"
RECEIVER_PERMISSION = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
SCHEDULE_RECEIVER = "com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver"
PROFILE_RECEIVER = "androidx.profileinstaller.ProfileInstallReceiver"
PLATFORM_PERMISSIONS = {
    "android.permission.NFC",
    "android.permission.SCHEDULE_EXACT_ALARM",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.WAKE_LOCK",
}
SCHEDULE_ACTIONS = {
    "android.intent.action.BOOT_COMPLETED",
    "android.intent.action.TIME_SET",
    "android.intent.action.TIMEZONE_CHANGED",
    "android.intent.action.MY_PACKAGE_REPLACED",
    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
}


class BoundaryError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise BoundaryError(message)


class Node:
    def __init__(self, name):
        self.name = name
        self.attrs = {}
        self.children = []


def xml_tree(dump):
    """Read only aapt2's E/A/N text records; aapt2 decodes the binary XML."""
    roots, stack = [], []
    for line in dump.splitlines():
        if not line.strip():
            continue
        record = re.fullmatch(r"( *)([EAN]): (.+)", line)
        require(record is not None, "Malformed aapt2 XML dump")
        indent, kind, value = len(record[1]), record[2], record[3]
        if kind == "N":
            continue
        if kind == "E":
            element = re.fullmatch(r"([\w:.-]+) \(line=\d+\)", value)
            require(element is not None, "Malformed element in aapt2 XML dump")
            while stack and stack[-1][0] >= indent:
                stack.pop()
            node = Node(element[1])
            (stack[-1][1].children if stack else roots).append(node)
            stack.append((indent, node))
        else:
            require(stack and indent == stack[-1][0] + 2,
                    "Attribute has no unambiguous owning element")
            attribute = re.fullmatch(r"([^\s=(]+)(?:\(0x[0-9a-fA-F]+\))?=(.+)", value)
            require(attribute is not None, "Malformed attribute in aapt2 XML dump")
            name = attribute[1].replace(ANDROID, "android:", 1)
            require(name not in stack[-1][1].attrs, "Duplicate manifest attribute")
            stack[-1][1].attrs[name] = attribute[2]
    require(len(roots) == 1, "Expected one nonempty XML root")
    return roots[0]


def text_attr(node, name):
    raw = node.attrs.get(name, "")
    try:
        value, end = json.JSONDecoder().raw_decode(raw)
    except ValueError as error:
        raise BoundaryError(f"Missing or invalid {name}") from error
    require(isinstance(value, str) and
            (not raw[end:] or re.fullmatch(r' \(Raw: ".*"\)', raw[end:])),
            f"Invalid string attribute {name}")
    return value


def one(nodes, description):
    require(len(nodes) == 1, f"Expected exactly one {description}")
    return nodes[0]


def receiver_boundary(app):
    receivers = [node for node in app.children if node.name == "receiver"]
    names = [text_attr(node, "android:name") for node in receivers]
    require(len(names) == len(set(names)) and
            set(names) <= {SCHEDULE_RECEIVER, PROFILE_RECEIVER},
            "Unexpected or duplicate release receiver")
    receiver = one([node for node in receivers
                    if text_attr(node, "android:name") == SCHEDULE_RECEIVER],
                   "schedule receiver")
    require(set(receiver.attrs) <= {"android:name", "android:exported", "android:directBootAware", "android:enabled"},
            "Unexpected schedule receiver attribute or process override")
    require(receiver.attrs.get("android:exported") == "false",
            "Schedule receiver must explicitly be non-exported")
    require(receiver.attrs.get("android:directBootAware", "false") == "false" and
            app.attrs.get("android:directBootAware", "false") == "false",
            "Schedule delivery must not run before first unlock")
    require(receiver.attrs.get("android:enabled", "true") == "true",
            "Schedule receiver must be enabled")
    intent_filter = one(receiver.children, "schedule receiver intent filter")
    require(intent_filter.name == "intent-filter" and not intent_filter.attrs,
            "Unexpected schedule receiver intent filter")
    actions = []
    for node in intent_filter.children:
        require(node.name == "action" and set(node.attrs) == {"android:name"} and not node.children,
                "Schedule filter must contain only named system actions")
        actions.append(text_attr(node, "android:name"))
    require(len(actions) == len(SCHEDULE_ACTIONS) and set(actions) == SCHEDULE_ACTIONS,
            "Schedule receiver system actions differ from the exact allowlist")

    for node in receivers:
        if text_attr(node, "android:name") == PROFILE_RECEIVER:
            require(text_attr(node, "android:permission") == "android.permission.DUMP",
                    "Profile installer receiver must remain DUMP-protected")
            require(node.attrs.get("android:exported") == "true" and
                    node.attrs.get("android:directBootAware", "false") == "false",
                    "Profile installer receiver boundary changed")


def manifest_boundary(dump, expected_id):
    root = xml_tree(dump)
    require(root.name == "manifest", "Expected a manifest XML root")
    require(text_attr(root, "package") == expected_id, "Packaged application ID differs")
    app = one([n for n in root.children if n.name == "application"], "application")
    require(app.attrs.get("android:debuggable", "false") == "false",
            "Release APK is debuggable or has an unknown debuggable value")
    for node in app.children:
        if node.name in ("activity", "activity-alias"):
            require(text_attr(node, "android:name") != "androidx.compose.ui.tooling.PreviewActivity",
                    "Compose PreviewActivity must not be packaged in release")
            require('"androidx.compose.ui.tooling.PreviewActivity"' not in
                    node.attrs.get("android:targetActivity", ""),
                    "Release activity alias targets Compose PreviewActivity")

    expected_permission = expected_id + RECEIVER_PERMISSION
    permission_nodes = [n for n in root.children if n.name.startswith("uses-permission")]
    require(all(n.name == "uses-permission" and set(n.attrs) == {"android:name"} and
                not n.children for n in permission_nodes),
            "Unexpected requested-permission tag or attribute")
    requested = [text_attr(n, "android:name") for n in permission_nodes]
    expected_permissions = PLATFORM_PERMISSIONS | {expected_permission}
    require(len(requested) == len(expected_permissions) and set(requested) == expected_permissions,
            "Requested permissions must be exactly NFC, explicit scheduling permissions and the app-scoped receiver permission")
    declared = one([n for n in root.children if n.name == "permission"],
                   "app-scoped permission declaration")
    require(text_attr(declared, "android:name") == expected_permission,
            "Unexpected permission declaration")
    require(declared.attrs.get("android:protectionLevel") == "0x00000002",
            "App-scoped receiver permission must be signature-only")

    receiver_boundary(app)

    service = one([n for n in app.children if n.name == "service"], "Accessibility service")
    require(text_attr(service, "android:name") == SERVICE, "Unexpected release service")
    require(text_attr(service, "android:permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "Accessibility service binding is not protected")
    metadata = one([n for n in service.children if n.name == "meta-data" and
                    text_attr(n, "android:name") == "android.accessibilityservice"],
                   "Accessibility metadata reference")
    reference = metadata.attrs.get("android:resource", "")
    require(re.fullmatch(r"@0x[0-9a-fA-F]{8}", reference), "Invalid Accessibility resource reference")
    return reference[1:].lower()


def accessibility_path(resources, resource_id):
    lines, matches = resources.splitlines(), []
    for index, line in enumerate(lines):
        entry = re.fullmatch(r"( *)resource (0x[0-9a-fA-F]{8}) ([\w/.-]+)", line)
        if entry is None or entry[2].lower() != resource_id:
            continue
        require(entry[3] == "xml/gate0_accessibility_service", "Service points to unexpected XML resource")
        values = []
        for following in lines[index + 1:]:
            if not following.strip():
                continue
            if len(following) - len(following.lstrip()) <= len(entry[1]):
                break
            values.append(following.strip())
        value = one(values, "default Accessibility XML resource configuration")
        file_match = re.fullmatch(r"\(\) \(file\) (\S+) type=XML", value)
        require(file_match is not None, "Unresolved or qualified Accessibility resource mapping")
        path = PurePosixPath(file_match[1])
        require(path.parts[0] == "res" and ".." not in path.parts and path.suffix == ".xml",
                "Invalid packaged Accessibility XML path")
        matches.append(str(path))
    return one(matches, "resolved Accessibility XML resource")


def accessibility_boundary(dump):
    root = xml_tree(dump)
    require(root.name == "accessibility-service" and not root.children,
            "Invalid Accessibility service XML")
    expected = {
        "android:accessibilityEventTypes": "0x00000020",
        "android:accessibilityFeedbackType": "0x00000010",
        "android:notificationTimeout": "0",
        "android:canRetrieveWindowContent": "false",
        "android:isAccessibilityTool": "false",
    }
    require(set(root.attrs) <= set(expected) | {"android:description"},
            "Unexpected Accessibility attribute or retrieval flags")
    for name, value in expected.items():
        require(root.attrs.get(name) == value, f"Accessibility boundary changed: {name}")


def release_apk(metadata_path, expected_id):
    data = json.loads(metadata_path.read_text())
    require(isinstance(data, dict) and data.get("variantName") == "release" and
            data.get("artifactType", {}).get("type") == "APK", "Expected release APK output metadata")
    require(data.get("applicationId") == expected_id, "Output metadata application ID differs")
    element = one(data.get("elements", []), "release APK output")
    filename = element.get("outputFile", "")
    require(isinstance(filename, str) and filename.endswith(".apk") and
            Path(filename).name == filename, "Invalid release APK filename")
    apk = (metadata_path.parent / filename).resolve()
    require(apk.parent == metadata_path.parent.resolve() and apk.is_file(), "Release APK is missing or outside its output directory")
    return apk


def main(args):
    require(len(args) == 2, "Usage: verify-release-apk.py <output-metadata.json> <expected-application-id>")
    metadata_path, expected_id = Path(args[0]), args[1]
    require(re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", expected_id),
            "Invalid expected application ID")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(sdk, "Set ANDROID_HOME or ANDROID_SDK_ROOT; no SDK downloads are performed")
    aapt2 = Path(sdk) / "build-tools/36.0.0/aapt2"
    require(aapt2.is_file() and os.access(aapt2, os.X_OK), "SDK build-tools 36.0.0 aapt2 is unavailable")
    apk = release_apk(metadata_path, expected_id)

    def dump(kind, *options):
        return subprocess.run([str(aapt2), "dump", kind, str(apk), *options],
                              check=True, capture_output=True, text=True).stdout

    resource_id = manifest_boundary(dump("xmltree", "--file", "AndroidManifest.xml"), expected_id)
    path = accessibility_path(dump("resources"), resource_id)
    accessibility_boundary(dump("xmltree", "--file", path))
    print(f"Release APK boundary verified for {expected_id}; signing, device safety and Play readiness are not asserted.")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (BoundaryError, OSError, ValueError, TypeError, AttributeError, subprocess.CalledProcessError) as error:
        print(f"Release APK boundary verification failed: {error}", file=sys.stderr)
        sys.exit(1)

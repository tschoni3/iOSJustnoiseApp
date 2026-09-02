"""Pure regressions for the focused aapt2 release-boundary checks."""

import json
from pathlib import Path
import re
import runpy
import tempfile
import types
import unittest


check = types.SimpleNamespace(**runpy.run_path(str(Path(__file__).with_name("verify-release-apk.py"))))
PACKAGE = "com.justnoise.androiddev"
SCHEDULE_RECEIVER_XML = """        E: receiver (line=7)
          A: android:name="com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver"
          A: android:exported=false
          A: android:directBootAware=false
            E: intent-filter (line=7)
                E: action (line=7)
                  A: android:name="android.intent.action.BOOT_COMPLETED"
                E: action (line=7)
                  A: android:name="android.intent.action.TIME_SET"
                E: action (line=7)
                  A: android:name="android.intent.action.TIMEZONE_CHANGED"
                E: action (line=7)
                  A: android:name="android.intent.action.MY_PACKAGE_REPLACED"
                E: action (line=7)
                  A: android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
"""
MANIFEST = f"""E: manifest (line=1)
  A: package="com.justnoise.androiddev"
    E: uses-permission (line=2)
      A: android:name="android.permission.NFC"
    E: uses-permission (line=2)
      A: android:name="android.permission.SCHEDULE_EXACT_ALARM"
    E: uses-permission (line=2)
      A: android:name="android.permission.RECEIVE_BOOT_COMPLETED"
    E: uses-permission (line=2)
      A: android:name="android.permission.WAKE_LOCK"
    E: uses-permission (line=3)
      A: android:name="com.justnoise.androiddev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    E: permission (line=4)
      A: android:name="com.justnoise.androiddev.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
      A: android:protectionLevel(0x01010009)=0x00000002
    E: application (line=5)
      A: android:debuggable(0x0101000f)=false
        E: activity (line=6)
          A: android:name="com.justnoise.integrated.MainActivity"
          A: android:exported=true
{SCHEDULE_RECEIVER_XML}        E: receiver (line=7)
          A: android:name="androidx.profileinstaller.ProfileInstallReceiver"
          A: android:permission="android.permission.DUMP"
          A: android:exported=true
        E: service (line=8)
          A: android:name="com.justnoise.gate0.platform.restriction.Gate0AccessibilityService"
          A: android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            E: meta-data (line=9)
              A: android:name="android.accessibilityservice"
              A: android:resource(0x01010025)=@0x7f0b0001
"""
ACCESSIBILITY = """E: accessibility-service (line=1)
  A: android:accessibilityEventTypes(0x01010380)=0x00000020
  A: android:accessibilityFeedbackType(0x01010382)=0x00000010
  A: android:notificationTimeout(0x01010383)=0
  A: android:canRetrieveWindowContent(0x01010385)=false
  A: android:isAccessibilityTool(0x01010641)=false
"""
RESOURCES = """Package name=com.justnoise.androiddev id=7f
  type xml id=0b entryCount=2
    resource 0x7f0b0000 xml/data_extraction_rules
      () (file) res/4j.xml type=XML
    resource 0x7f0b0001 xml/gate0_accessibility_service
      () (file) res/renamed.xml type=XML
"""


class ReleaseBoundaryTests(unittest.TestCase):
    def test_narrow_release_and_optimized_resource_path(self):
        resource_id = check.manifest_boundary(MANIFEST, PACKAGE)
        self.assertEqual(check.accessibility_path(RESOURCES, resource_id), "res/renamed.xml")
        check.accessibility_boundary(ACCESSIBILITY)

    def test_absent_debuggable_is_false(self):
        check.manifest_boundary(MANIFEST.replace("      A: android:debuggable(0x0101000f)=false\n", ""), PACKAGE)

    def test_android_namespace_and_raw_string_format(self):
        dump = MANIFEST.replace("android:", check.ANDROID).replace(
            'package="com.justnoise.androiddev"',
            'package="com.justnoise.androiddev" (Raw: "com.justnoise.androiddev")',
        )
        check.manifest_boundary(dump, PACKAGE)

    def test_invalid_release_manifests_are_rejected(self):
        changes = {
            "debuggable": MANIFEST.replace("debuggable(0x0101000f)=false", "debuggable(0x0101000f)=true"),
            "internet": MANIFEST.replace("android.permission.NFC", "android.permission.INTERNET"),
            "extra_permission": MANIFEST.replace("    E: application", '    E: uses-permission-sdk-23 (line=4)\n      A: android:name="android.permission.INTERNET"\n    E: application'),
            "preview": MANIFEST.replace("com.justnoise.integrated.MainActivity", "androidx.compose.ui.tooling.PreviewActivity"),
            "receiver_permission": MANIFEST.replace("protectionLevel(0x01010009)=0x00000002", "protectionLevel(0x01010009)=0x00000000"),
            "unprotected_service": MANIFEST.replace("android.permission.BIND_ACCESSIBILITY_SERVICE", "android.permission.DUMP"),
            "missing_metadata": MANIFEST.replace('android:name="android.accessibilityservice"', 'android:name="unrelated"'),
        }
        for label, dump in changes.items():
            with self.subTest(label=label), self.assertRaises(check.BoundaryError):
                check.manifest_boundary(dump, PACKAGE)

    def test_wrong_application_id(self):
        with self.assertRaises(check.BoundaryError):
            check.manifest_boundary(MANIFEST, "com.justnoise.other")

    def test_permissions_are_an_exact_allowlist(self):
        for permission in ["android.permission.INTERNET", "android.permission.USE_EXACT_ALARM",
                           "android.permission.QUERY_ALL_PACKAGES", "android.permission.SYSTEM_ALERT_WINDOW",
                           "android.permission.PACKAGE_USAGE_STATS", "android.permission.POST_NOTIFICATIONS"]:
            extra = f'    E: uses-permission (line=99)\n      A: android:name="{permission}"\n'
            with self.subTest(permission=permission), self.assertRaises(check.BoundaryError):
                check.manifest_boundary(MANIFEST.replace("    E: application", extra + "    E: application"), PACKAGE)
        for permission in check.PLATFORM_PERMISSIONS | {PACKAGE + check.RECEIVER_PERMISSION}:
            pattern = rf'    E: uses-permission \(line=\d+\)\n      A: android:name="{re.escape(permission)}"\n'
            declaration = re.search(pattern, MANIFEST).group()
            for mutation in ["", declaration * 2, declaration + '      A: android:maxSdkVersion=31\n']:
                with self.subTest(permission=permission, mutation=mutation), self.assertRaises(check.BoundaryError):
                    check.manifest_boundary(MANIFEST.replace(declaration, mutation), PACKAGE)

    def test_schedule_receiver_export_and_filter_changes_are_rejected(self):
        changes = {
            "missing": "",
            "duplicate": SCHEDULE_RECEIVER_XML * 2,
            "exported": SCHEDULE_RECEIVER_XML.replace("android:exported=false", "android:exported=true"),
            "missing_exported": SCHEDULE_RECEIVER_XML.replace("          A: android:exported=false\n", ""),
            "direct_boot": SCHEDULE_RECEIVER_XML.replace("android:directBootAware=false", "android:directBootAware=true"),
            "separate_process": SCHEDULE_RECEIVER_XML.replace("          A: android:exported=false", '          A: android:process=":schedule"\n          A: android:exported=false'),
            "other_receiver": SCHEDULE_RECEIVER_XML.replace("com.justnoise.gate0.platform.schedule.ScheduleAlarmReceiver", "com.justnoise.UnexpectedReceiver"),
            "unexpected_action": SCHEDULE_RECEIVER_XML.replace("android.intent.action.TIME_SET", "android.intent.action.PACKAGE_REPLACED"),
            "duplicate_action": SCHEDULE_RECEIVER_XML.replace("android.intent.action.TIME_SET", "android.intent.action.BOOT_COMPLETED"),
            "private_alarm_filter": SCHEDULE_RECEIVER_XML.replace("android.intent.action.TIME_SET", "com.justnoise.gate0.action.SCHEDULE_ALARM"),
            "category": SCHEDULE_RECEIVER_XML.replace("E: action", "E: category", 1),
            "extra_filter": SCHEDULE_RECEIVER_XML + "            E: intent-filter (line=99)\n",
        }
        for label, receiver in changes.items():
            with self.subTest(label=label), self.assertRaises(check.BoundaryError):
                check.manifest_boundary(MANIFEST.replace(SCHEDULE_RECEIVER_XML, receiver), PACKAGE)
        extra = '        E: receiver (line=99)\n          A: android:name="com.justnoise.OtherReceiver"\n          A: android:exported=false\n'
        with self.assertRaises(check.BoundaryError):
            check.manifest_boundary(MANIFEST.replace(SCHEDULE_RECEIVER_XML, SCHEDULE_RECEIVER_XML + extra), PACKAGE)

    def test_existing_profile_receiver_cannot_lose_its_protection(self):
        unprotected = MANIFEST.replace("android.permission.DUMP", "android.permission.NFC")
        with self.assertRaises(check.BoundaryError):
            check.manifest_boundary(unprotected, PACKAGE)
        exported_by_default = MANIFEST.replace(
            '          A: android:permission="android.permission.DUMP"\n          A: android:exported=true\n',
            '          A: android:permission="android.permission.DUMP"\n',
        )
        with self.assertRaises(check.BoundaryError):
            check.manifest_boundary(exported_by_default, PACKAGE)

    def test_application_id_uses_android_ascii_rules(self):
        with self.assertRaisesRegex(check.BoundaryError, "Invalid expected application ID"):
            check.main(["unused.json", "com.justnoisé.app"])

    def test_release_output_metadata_identity_and_path(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "signed-name.apk"
            apk.write_bytes(b"path-resolution fixture, not a real APK")
            metadata = root / "output-metadata.json"
            valid = {"variantName": "release", "artifactType": {"type": "APK"},
                     "applicationId": PACKAGE, "elements": [{"outputFile": apk.name}]}
            metadata.write_text(json.dumps(valid))
            self.assertEqual(check.release_apk(metadata, PACKAGE), apk.resolve())
            changes = [
                {**valid, "variantName": "debug"},
                {**valid, "applicationId": "com.justnoise.other"},
                {**valid, "elements": []},
                {**valid, "elements": valid["elements"] * 2},
                {**valid, "elements": [{"outputFile": "../outside.apk"}]},
                {**valid, "elements": [{"outputFile": "missing.apk"}]},
            ]
            for data in changes:
                with self.subTest(data=data), self.assertRaises(check.BoundaryError):
                    metadata.write_text(json.dumps(data))
                    check.release_apk(metadata, PACKAGE)

    def test_broadened_or_missing_accessibility_fields_are_rejected(self):
        changes = [
            ACCESSIBILITY.replace("0x00000020", "0xffffffff"),
            ACCESSIBILITY.replace("canRetrieveWindowContent(0x01010385)=false", "canRetrieveWindowContent(0x01010385)=true"),
            ACCESSIBILITY.replace("isAccessibilityTool(0x01010641)=false", "isAccessibilityTool(0x01010641)=true"),
            ACCESSIBILITY.replace("notificationTimeout(0x01010383)=0", "notificationTimeout(0x01010383)=100"),
            ACCESSIBILITY + "  A: android:accessibilityFlags(0x01010384)=0x00000040\n",
            ACCESSIBILITY + "  A: android:canPerformGestures=true\n",
            ACCESSIBILITY.replace("  A: android:isAccessibilityTool(0x01010641)=false\n", ""),
        ]
        for dump in changes:
            with self.subTest(dump=dump), self.assertRaises(check.BoundaryError):
                check.accessibility_boundary(dump)

    def test_broken_or_ambiguous_resource_mapping_is_rejected(self):
        changes = ["", RESOURCES.replace("0x7f0b0001", "0x7f0b0099"),
                   RESOURCES.replace("xml/gate0_accessibility_service", "xml/other"),
                   RESOURCES.replace("res/renamed.xml", "../outside.xml"),
                   RESOURCES + "      (v31) (file) res/other.xml type=XML\n"]
        for dump in changes:
            with self.subTest(dump=dump), self.assertRaises(check.BoundaryError):
                check.accessibility_path(dump, "0x7f0b0001")

    def test_empty_malformed_or_duplicate_xml_is_rejected(self):
        for dump in ["", "garbage", "N: android=namespace (line=1)",
                     MANIFEST + "E: manifest (line=1)\n",
                     MANIFEST.replace('  A: package="com.justnoise.androiddev"',
                                      '  A: package="com.justnoise.androiddev"\n  A: package="other"')]:
            with self.subTest(dump=dump), self.assertRaises(check.BoundaryError):
                check.xml_tree(dump)


if __name__ == "__main__":
    unittest.main()

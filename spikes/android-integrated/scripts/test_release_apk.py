"""Pure regressions for the focused aapt2 release-boundary checks."""

import json
from pathlib import Path
import runpy
import tempfile
import types
import unittest


check = types.SimpleNamespace(**runpy.run_path(str(Path(__file__).with_name("verify-release-apk.py"))))
PACKAGE = "com.justnoise.androiddev"
MANIFEST = """E: manifest (line=1)
  A: package="com.justnoise.androiddev"
    E: uses-permission (line=2)
      A: android:name="android.permission.NFC"
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
        E: receiver (line=7)
          A: android:name="androidx.profileinstaller.ProfileInstallReceiver"
          A: android:permission="android.permission.DUMP"
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

"""Pure fixture regressions; these do not sign or approve a real AAB."""

import base64
import hashlib
import io
import json
from pathlib import Path
import runpy
import subprocess
import types
import unittest
import xml.etree.ElementTree as ET
import zipfile


HERE = Path(__file__).parent
check = types.SimpleNamespace(**runpy.run_path(str(HERE / "verify-release-aab.py")))
apk_fixture = types.SimpleNamespace(**runpy.run_path(str(HERE / "test_release_apk.py")))
A = check.A


def manifest():
    def decode(node):
        element = ET.Element(node.name)
        for key, value in node.attrs.items():
            key = key.replace("android:", A)
            element.set(key, json.loads(value) if value.startswith('"') else value)
        element.extend(decode(child) for child in node.children)
        return element

    root = decode(check.apk.xml_tree(apk_fixture.production_accounts(apk_fixture.MANIFEST.replace(apk_fixture.PACKAGE, check.PRODUCTION_ID))))
    root.set(A + "versionCode", "1")
    root.set(A + "versionName", "1.0")
    ET.SubElement(root, "uses-permission", {A + "name": check.apk.PRODUCTION_PROMOTION_PERMISSION})
    ET.SubElement(root, "uses-permission", {A + "name": "android.permission.INTERNET"})
    ET.SubElement(root, "uses-sdk", {A + "minSdkVersion": "31", A + "targetSdkVersion": "36"})
    app = root.find("application")
    app.set(A + "label", "Justnoise")
    app.set(A + "allowBackup", "false")
    service = app.find("service")
    service.set(A + "exported", "true")
    service.find("meta-data").set(A + "resource", "@xml/gate0_accessibility_service")
    ET.SubElement(app, "provider", {
        A + "name": "androidx.startup.InitializationProvider", A + "exported": "false",
        A + "authorities": check.PRODUCTION_ID + ".androidx-startup",
    })
    return root


def dump(root):
    return ET.tostring(root, encoding="unicode")


CERT = b"pure certificate-fingerprint fixture; not a real certificate"
PIN = hashlib.sha256(CERT).hexdigest()
PEM = "Signer #1:\nCertificate #1:\n-----BEGIN CERTIFICATE-----\n" + base64.b64encode(CERT).decode() + "\n-----END CERTIFICATE-----\n"
IDENTITY = "Owner: CN=Approved Upload, O=Justnoise\n"
VERIFIED = subprocess.CompletedProcess([], 0, "jar verified.\n", "")
ELF = """fixture.so: file format elf64-littleaarch64
Program Header:
    LOAD off    0x0000000000000000 vaddr 0x0000000000000000 paddr 0x0000000000000000 align 2**14
         filesz 0x0000000000000140 memsz 0x0000000000000140 flags r--
    LOAD off 0x0000000000004000 vaddr 0x0000000000014000 paddr 0x0000000000014000 align 2**14
         filesz 0x0000000000000040 memsz 0x0000000000000040 flags r-x
"""


class ReleaseAabTests(unittest.TestCase):
    def test_account_callback_accepts_bundletool_single_top_integer_only(self):
        root = manifest()
        account = next(node for node in root.find("application").findall("activity")
                       if node.get(A + "name") == check.apk.ACCOUNT_ACTIVITY)
        account.set(A + "launchMode", "1")
        check.manifest_boundary(dump(root))
        for invalid in ("0", "2", "singleTask", ""):
            account.set(A + "launchMode", invalid)
            with self.subTest(invalid=invalid), self.assertRaises(check.apk.BoundaryError):
                check.manifest_boundary(dump(root))

    def test_reviewed_production_manifest(self):
        result = check.manifest_boundary(dump(manifest()))
        self.assertEqual(result["application_id"], "shop.justnoise.app")
        self.assertEqual(result["version_code"], 1)

    def test_live_updates_require_promotion_but_no_foreground_service(self):
        root = manifest()
        requested = {node.get(A + "name") for node in root.findall("uses-permission")}
        self.assertIn("android.permission.POST_NOTIFICATIONS", requested)
        self.assertIn(check.apk.PRODUCTION_PROMOTION_PERMISSION, requested)
        check.manifest_boundary(dump(root))
        for permission in ("android.permission.POST_PROMOTED_NOTIFICATIONS",
                           "android.permission.FOREGROUND_SERVICE",
                           "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"):
            root = manifest()
            ET.SubElement(root, "uses-permission", {A + "name": permission})
            with self.subTest(permission=permission), self.assertRaises(check.Error):
                check.manifest_boundary(dump(root))
        root = manifest()
        for node in root.findall("uses-permission"):
            if node.get(A + "name") == check.apk.PRODUCTION_PROMOTION_PERMISSION:
                root.remove(node)
        with self.assertRaises(check.Error):
            check.manifest_boundary(dump(root))

    def test_notification_receiver_remains_explicit_private_and_filterless(self):
        for label, value in (("exported", "true"), ("enabled", "false"), ("enabled", ""),
                             ("permission", "android.permission.DUMP"), ("process", ":notification"),
                             ("directBootAware", "true"), ("directBootAware", "false"),
                             ("intent-filter", None), ("meta-data", None),
                             ("missing", None), ("duplicate", None), ("implicit_export", None)):
            root = manifest()
            app = root.find("application")
            receiver = next(node for node in app.findall("receiver")
                            if node.get(A + "name") == check.apk.NOTIFICATION_DISMISS_RECEIVER)
            if label in ("intent-filter", "meta-data"):
                ET.SubElement(receiver, label)
            elif label == "missing":
                app.remove(receiver)
            elif label == "duplicate":
                app.append(ET.fromstring(ET.tostring(receiver)))
            elif label == "implicit_export":
                del receiver.attrib[A + "exported"]
            else:
                receiver.set(A + label, value)
            with self.subTest(mutation=label, value=value), self.assertRaises(check.Error):
                check.manifest_boundary(dump(root))

    def test_development_identity_cannot_be_configured_to_pass(self):
        root = manifest()
        root.set("package", "com.justnoise.androiddev")
        with self.assertRaisesRegex(check.Error, "production application ID"):
            check.manifest_boundary(dump(root))

    def test_development_label_version_or_low_target_fails(self):
        for field, value in (("label", "Justnoise Android Dev"), ("versionName", "0.1-integrated-dev"),
                             ("versionCode", "0"), ("targetSdkVersion", "35")):
            root = manifest()
            element = root.find("application") if field == "label" else root.find("uses-sdk") if field == "targetSdkVersion" else root
            element.set(A + field, value)
            with self.subTest(field=field), self.assertRaises(check.Error):
                check.manifest_boundary(dump(root))

    def test_debuggable_testonly_backup_and_cleartext_fail_closed(self):
        for attribute in ("debuggable", "testOnly", "allowBackup", "usesCleartextTraffic"):
            for value in ("true", "@bool/unknown"):
                root = manifest()
                root.find("application").set(A + attribute, value)
                with self.subTest(attribute=attribute, value=value), self.assertRaises(check.Error):
                    check.manifest_boundary(dump(root))

    def test_allow_backup_must_be_explicitly_false(self):
        root = manifest()
        del root.find("application").attrib[A + "allowBackup"]
        with self.assertRaisesRegex(check.Error, "allowBackup must explicitly be false"):
            check.manifest_boundary(dump(root))

    def test_reuses_exact_activity_and_receiver_boundaries(self):
        for tag, name in (("activity", "androidx.compose.ui.tooling.PreviewActivity"),
                          ("activity-alias", "com.justnoise.PreviewAlias"), ("receiver", "com.justnoise.Unexpected")):
            root = manifest()
            ET.SubElement(root.find("application"), tag, {A + "name": name, A + "exported": "false"})
            with self.subTest(tag=tag), self.assertRaises(check.Error):
                check.manifest_boundary(dump(root))

    def test_instrumentation_provider_or_service_changes_fail(self):
        for label in ("instrumentation", "provider", "service", "process"):
            root = manifest()
            app = root.find("application")
            if label == "instrumentation":
                ET.SubElement(root, label)
            elif label == "process":
                app.set(A + "process", ":unreviewed")
            else:
                app.find(label).set(A + "exported", "true" if label == "provider" else "false")
            with self.subTest(label=label), self.assertRaises(check.Error):
                check.manifest_boundary(dump(root))

    def test_permission_broadening_and_missing_reference_fail(self):
        root = manifest()
        ET.SubElement(root, "uses-permission", {A + "name": "android.permission.ACCESS_NETWORK_STATE"})
        with self.assertRaises(check.Error):
            check.manifest_boundary(dump(root))
        root = manifest()
        root.find("application/service/meta-data").set(A + "resource", "@xml/other")
        with self.assertRaises(check.Error):
            check.manifest_boundary(dump(root))

    def test_strict_success_requires_approved_pin(self):
        result = check.signature_boundary(VERIFIED, PEM, IDENTITY, PIN)
        self.assertEqual(result["certificate_sha256"], PIN)
        with self.assertRaisesRegex(check.Error, "differs"):
            check.signature_boundary(VERIFIED, PEM, IDENTITY, "0" * 64)

    def test_unsigned_partial_untrusted_or_invalid_signatures_fail(self):
        for code, output in ((0, "jar is unsigned.\n"), (4, "jar verified.\n"),
                             (16, "jar verified.\n"), (1, "jar verified.\n")):
            result = subprocess.CompletedProcess([], code, output, "")
            with self.subTest(code=code, output=output), self.assertRaises(check.Error):
                check.signature_boundary(result, PEM, IDENTITY, PIN)

    def test_debug_multiple_or_missing_certificate_rejected(self):
        for pem, owner in ((PEM, "Owner: CN=Android Debug, O=Android, C=US\n"),
                           (PEM + PEM, IDENTITY), ("", IDENTITY), (PEM, "")):
            with self.subTest(pem=pem, owner=owner), self.assertRaises(check.Error):
                check.signature_boundary(VERIFIED, pem, owner, PIN)

    def test_pin_format_and_normalization(self):
        self.assertEqual(check.normalize_pin(":".join(["AB"] * 32)), "ab" * 32)
        for pin in ("", "ab", "x" * 64):
            with self.subTest(pin=pin), self.assertRaises(check.Error):
                check.normalize_pin(pin)

    def test_elf_segment_alignment_and_congruence(self):
        self.assertEqual(check.elf_alignment(ELF), [16384, 16384])
        for changed in (ELF.replace("2**14", "2**12"), ELF.replace("vaddr 0x0000000000014000", "vaddr 0x0000000000015000"),
                        ELF.replace("LOAD", "UNKNOWN"), ELF.replace("file format elf64", "file format mach64")):
            with self.subTest(changed=changed), self.assertRaises(check.Error):
                check.elf_alignment(changed)

    def test_native_configuration_is_separate_from_elf_evidence(self):
        config = {"optimizations": {"uncompressNativeLibraries": {"enabled": True, "alignment": "PAGE_ALIGNMENT_16K"}}}
        check.native_config_boundary(config, True)
        check.native_config_boundary({}, False)
        for invalid in ({}, {"optimizations": {"uncompressNativeLibraries": {"enabled": True, "alignment": "PAGE_ALIGNMENT_4K"}}}):
            with self.subTest(config=invalid), self.assertRaises(check.Error):
                check.native_config_boundary(invalid, True)

    def test_archive_module_and_path_boundaries(self):
        basic = ["BundleConfig.pb", "base/manifest/AndroidManifest.xml"]
        for extra in ([], ["base/lib/arm64-v8a/libexample.so"], ["../unsafe"],
                      ["other/manifest/AndroidManifest.xml"], ["assets/hidden.so"]):
            memory = io.BytesIO()
            with zipfile.ZipFile(memory, "w") as archive:
                for name in basic + extra:
                    archive.writestr(name, b"fixture")
            with zipfile.ZipFile(memory) as archive:
                if extra and (extra[0].startswith(("..", "other", "assets"))):
                    with self.assertRaises(check.Error):
                        check.archive_boundary(archive)
                else:
                    self.assertEqual(check.archive_boundary(archive), extra)


if __name__ == "__main__":
    unittest.main()

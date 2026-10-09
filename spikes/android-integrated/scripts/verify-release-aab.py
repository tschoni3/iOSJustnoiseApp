#!/usr/bin/env python3
"""Read-only, fail-closed checks for the final Justnoise production AAB.

Requires local JDK tools, bundletool, llvm-objdump when native code is present,
and an existing external truststore containing the approved upload certificate.
This script creates no signing keys, downloads nothing and never signs/builds an
artifact. Its PASS is artifact evidence, NOT device/policy/Play approval.
"""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import runpy
import shutil
import subprocess
import sys
import tempfile
import types
import xml.etree.ElementTree as ET
import zipfile


apk = types.SimpleNamespace(**runpy.run_path(str(Path(__file__).with_name("verify-release-apk.py"))))
Error = apk.BoundaryError
require = apk.require
PRODUCTION_ID = "shop.justnoise.app"
A = "{http://schemas.android.com/apk/res/android}"


def normalize_pin(value):
    value = value.replace(":", "").lower()
    require(re.fullmatch(r"[0-9a-f]{64}", value), "Expected a 64-hex SHA-256 upload-certificate pin")
    return value


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def as_apk_node(element):
    """Adapt bundletool-decoded XML to the existing component boundary helpers."""
    node = apk.Node(element.tag)
    for key, value in element.attrib.items():
        key = key.replace(A, "android:")
        node.attrs[key] = value if value in {"true", "false"} else json.dumps(value)
    node.children = [as_apk_node(child) for child in element]
    return node


def manifest_boundary(text):
    require("<!DOCTYPE" not in text and "<!ENTITY" not in text, "Unexpected manifest declarations")
    root = ET.fromstring(text)
    require(root.tag == "manifest" and root.get("package") == PRODUCTION_ID,
            f"Expected production application ID {PRODUCTION_ID}; development bundles cannot pass")
    require(not root.findall("instrumentation"), "Instrumentation must not ship")
    version = root.get(A + "versionCode", "")
    version_name = root.get(A + "versionName", "")
    require(version.isascii() and version.isdigit() and int(version) > 0, "Invalid versionCode")
    require(version_name and not re.search(r"(?:^|[._-])(dev|debug|prototype|integrated)(?:$|[._-])", version_name, re.I),
            "Missing or development versionName")
    sdk = apk.one(root.findall("uses-sdk"), "uses-sdk")
    target = sdk.get(A + "targetSdkVersion", "")
    require(target.isascii() and target.isdigit() and int(target) >= 36, "targetSdkVersion must be numeric and at least 36")
    require(sdk.get(A + "minSdkVersion") == "31", "Unreviewed minimum Android version")
    app = apk.one(root.findall("application"), "application")
    for name in ("debuggable", "testOnly", "usesCleartextTraffic"):
        require(app.get(A + name, "false") == "false", f"Production application {name} must be false")
    require(app.get(A + "allowBackup") == "false", "Production application allowBackup must explicitly be false")
    require(app.get(A + "label") == "Justnoise", "Expected resolved public app label Justnoise")
    require(app.get(A + "enabled", "true") == "true", "Application is disabled")
    require(all(A + "process" not in element.attrib for element in app.iter()), "Unreviewed process override")
    apk.activity_boundary(as_apk_node(app), PRODUCTION_ID)
    apk.receiver_boundary(as_apk_node(app))
    service = apk.one(app.findall("service"), "Accessibility service")
    require(service.get(A + "name") == apk.SERVICE and
            service.get(A + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE" and
            service.get(A + "exported") == "true", "Accessibility service boundary differs")
    metadata = [node for node in service.findall("meta-data")
                if node.get(A + "name") == "android.accessibilityservice"]
    require(apk.one(metadata, "Accessibility metadata").get(A + "resource") == "@xml/gate0_accessibility_service",
            "Unexpected Accessibility configuration reference")
    provider = apk.one(app.findall("provider"), "startup provider")
    require(provider.get(A + "name") == "androidx.startup.InitializationProvider" and
            provider.get(A + "exported") == "false" and
            provider.get(A + "authorities") == PRODUCTION_ID + ".androidx-startup",
            "Unexpected provider boundary")
    permission = PRODUCTION_ID + apk.RECEIVER_PERMISSION
    requested = [node for node in root if node.tag.startswith("uses-permission")]
    expected = apk.permissions_for(PRODUCTION_ID) | {permission}
    require(len(requested) == len(expected) and
            all(node.tag == "uses-permission" and set(node.attrib) == {A + "name"} and not len(node) for node in requested) and
            {node.get(A + "name") for node in requested} == expected, "Requested permissions differ from reviewed APK boundary")
    declared = apk.one(root.findall("permission"), "app-scoped permission")
    require(declared.get(A + "name") == permission and declared.get(A + "protectionLevel") == "0x00000002",
            "App-scoped permission is not signature-only")
    return {"application_id": PRODUCTION_ID, "label": "Justnoise", "version_code": int(version),
            "version_name": version_name, "target_sdk": int(target)}


def archive_boundary(archive):
    entries = archive.infolist()
    names = [entry.filename for entry in entries]
    require(len(names) == len(set(names)), "Duplicate ZIP entries are not allowed")
    require(all(not PurePosixPath(name).is_absolute() and ".." not in PurePosixPath(name).parts
                and "\\" not in name for name in names), "Unsafe ZIP path")
    require(sum(entry.file_size for entry in entries) <= 512 * 1024 * 1024,
            "Bundle exceeds this checker's reviewed 512-MiB uncompressed limit")
    require("BundleConfig.pb" in names and "base/manifest/AndroidManifest.xml" in names, "Incomplete AAB structure")
    manifests = [name for name in names if name.endswith("/manifest/AndroidManifest.xml")]
    require(manifests == ["base/manifest/AndroidManifest.xml"], "Only the reviewed single-base-module bundle is supported")
    native = [name for name in names if name.endswith(".so")]
    require(all(re.fullmatch(r"base/lib/(arm64-v8a|armeabi-v7a|x86|x86_64)/[^/]+\.so", name) for name in native),
            "Unreviewed native library location or ABI")
    return native


def signature_boundary(result, pem_output, identity_output, expected_pin):
    # -strict makes unsigned entries / invalid or untrusted certificates failures.
    # An explicitly supplied external truststore supports the normal self-signed
    # upload certificate; no permissive exception for jarsigner exit code 4.
    require(result.returncode == 0 and re.search(r"^jar verified\.$", result.stdout, re.M),
            "jarsigner strict verification failed; unsigned/debug/untrusted bundles cannot pass")
    certificates = re.findall(r"-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----", pem_output)
    require(len(certificates) == 1 and re.findall(r"^Signer #(\d+):", pem_output, re.M) == ["1"],
            "Expected exactly one upload signer/certificate; multi-signer or certificate-chain bundles require review")
    certificate = base64.b64decode(re.sub(r"\s", "", certificates[0]), validate=True)
    fingerprint = hashlib.sha256(certificate).hexdigest()
    require(fingerprint == normalize_pin(expected_pin), "Upload certificate SHA-256 differs from independently approved pin")
    owners = re.findall(r"^Owner: (.+)$", identity_output, re.M)
    require(len(owners) == 1 and "android debug" not in owners[0].lower(),
            "Missing or Android Debug certificate owner")
    return {"certificate_sha256": fingerprint, "certificate_owner": owners[0]}


def elf_alignment(dump):
    """Check llvm-objdump's ELF PT_LOAD alignment, not APK ZIP/runtime behavior."""
    require(re.search(r"file format elf(?:32|64)-", dump), "Native object is not a recognized ELF file")
    loads = [line.strip() for line in dump.splitlines() if re.match(r"\s*LOAD\s", line)]
    require(loads, "No PT_LOAD segments found")
    alignments = []
    for line in loads:
        match = re.fullmatch(r"LOAD\s+off\s+(0x[0-9a-fA-F]+)\s+vaddr\s+(0x[0-9a-fA-F]+)\s+paddr\s+0x[0-9a-fA-F]+\s+align\s+2\*\*(\d+)", line)
        require(match is not None, "Unrecognized llvm-objdump PT_LOAD format")
        exponent = int(match[3])
        require(14 <= exponent <= 63 and (int(match[1], 16) - int(match[2], 16)) % 16384 == 0,
                "Native PT_LOAD lacks 16-KB alignment/congruent offsets")
        alignments.append(2 ** exponent)
    return alignments


def native_config_boundary(config, has_native):
    if has_native:
        native = config.get("optimizations", {}).get("uncompressNativeLibraries", {})
        require(native.get("enabled") is True and native.get("alignment") == "PAGE_ALIGNMENT_16K",
                "Bundle configuration does not request 16-KB uncompressed native-library alignment")


def run(command):
    result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, text=True, timeout=120)
    require(result.returncode == 0, f"Local tool failed: {Path(command[0]).name}; {result.stderr.strip()[:500]}")
    return result.stdout


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aab", type=Path)
    parser.add_argument("--upload-cert-sha256", required=True, help="Independently approved upload-certificate pin, not copied blindly from this artifact")
    parser.add_argument("--truststore", type=Path, required=True, help="Existing external truststore containing the approved public upload certificate")
    parser.add_argument("--truststore-password-env", help="Name of an already-set environment variable; never pass the password itself")
    bundle = parser.add_mutually_exclusive_group(required=True)
    bundle.add_argument("--bundletool-jar", type=Path, help="Existing standalone bundletool JAR")
    bundle.add_argument("--bundletool-classpath", help="Explicit local classpath for a thin bundletool JAR and its dependencies")
    parser.add_argument("--llvm-objdump", default="llvm-objdump")
    args = parser.parse_args(argv)
    pin = normalize_pin(args.upload_cert_sha256)
    path = args.aab.resolve(strict=True)
    require(path.suffix == ".aab" and path.is_file(), "Expected an existing AAB file")
    original_hash = sha256(path)
    print(f"Artifact SHA-256: {original_hash}", flush=True)
    java_home = os.environ.get("JAVA_HOME", "")
    tools = {name: str(Path(java_home) / "bin" / name) if java_home else shutil.which(name)
             for name in ("java", "jarsigner", "keytool")}
    require(all(value and Path(value).is_file() and os.access(value, os.X_OK) for value in tools.values()),
            "Local JDK java, jarsigner and keytool are required; no downloads are performed")
    require(args.truststore.is_file(), "Existing certificate truststore is required; none is created")
    signature_command = [tools["jarsigner"], "-J-Duser.language=en", "-J-Duser.country=US", "-verify", "-strict",
                         "-keystore", str(args.truststore)]
    if args.truststore_password_env:
        require(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", args.truststore_password_env) and
                bool(os.environ.get(args.truststore_password_env)), "Truststore password environment variable is missing")
        signature_command += ["-storepass:env", args.truststore_password_env]
    signature_result = subprocess.run(signature_command + [str(path)], stdin=subprocess.DEVNULL,
                                      capture_output=True, text=True, timeout=120)
    require(signature_result.returncode == 0 and "jar verified." in signature_result.stdout,
            "Strict AAB signature verification failed; provide the approved external certificate truststore and a signed production bundle")
    keytool = [tools["keytool"], "-J-Duser.language=en", "-J-Duser.country=US", "-printcert", "-jarfile", str(path)]
    signature = signature_boundary(signature_result, run(keytool + ["-rfc"]), run(keytool), pin)
    if args.bundletool_jar:
        require(args.bundletool_jar.is_file(), "Standalone bundletool JAR is unavailable")
        command = [tools["java"], "-jar", str(args.bundletool_jar)]
    else:
        require(all(Path(part).is_file() for part in args.bundletool_classpath.split(os.pathsep)), "Bundletool classpath must name existing local files")
        command = [tools["java"], "-cp", args.bundletool_classpath, "com.android.tools.build.bundletool.BundleToolMain"]
    bundle_version = run(command + ["version"]).strip()
    run(command + ["validate", "--bundle=" + str(path)])
    manifest = manifest_boundary(run(command + ["dump", "manifest", "--bundle=" + str(path), "--module=base"]))
    config = json.loads(run(command + ["dump", "config", "--bundle=" + str(path)]))
    native_evidence = []
    with zipfile.ZipFile(path) as archive:
        native = archive_boundary(archive)
        native_config_boundary(config, bool(native))
        if native:
            objdump = shutil.which(args.llvm_objdump)
            require(objdump, "Installed llvm-objdump is required for native ELF evidence")
            with tempfile.TemporaryDirectory(prefix="justnoise-aab-preflight-") as temp:
                for index, name in enumerate(native):
                    library = Path(temp) / f"library-{index}.so"
                    library.write_bytes(archive.read(name))
                    alignments = elf_alignment(run([objdump, "-p", str(library)]))
                    native_evidence.append({"entry": name, "pt_load_alignments": alignments})
    require(sha256(path) == original_hash, "AAB changed during verification; candidate is not immutable")
    print(json.dumps({**manifest, **signature, "aab_sha256": original_hash, "bundletool_version": bundle_version,
                      "native_elf_evidence": native_evidence}, indent=2))
    print("PASS: signed production AAB artifact preflight only. Generated-APK ZIP alignment, 16-KB runtime, "
          "Accessibility XML behavior, device acceptance, Play registration/policy and upload authorization remain separate checks.")


if __name__ == "__main__":
    try:
        main()
    except (Error, OSError, ValueError, TypeError, ET.ParseError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print(f"AAB preflight FAILED: {error}", file=sys.stderr)
        sys.exit(1)

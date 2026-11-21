#!/usr/bin/env python3
"""Verify the already-built signed local candidate. No install/upload/build/key creation."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile


def main():
    root = Path(__file__).resolve().parent
    scripts = root.parent / "spikes/android-integrated/scripts"
    outputs = root / "app/build/outputs"
    apk = outputs / "apk/release/app-release.apk"
    aab = outputs / "bundle/release/app-release.aab"
    credentials_file = Path(os.environ["JUSTNOISE_SIGNING_PROPERTIES"])
    # Same generated Java-properties subset used by create-upload-key.py.
    properties = dict(line.split("=", 1) for line in credentials_file.read_text().splitlines()
                      if line and not line.startswith("#"))
    pin = (credentials_file.parent / "upload-certificate.sha256").read_text().strip()
    environment = {**os.environ, "JUSTNOISE_VERIFY_TRUST_PASSWORD": properties["storePassword"]}
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    hashes = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in (apk, aab)}

    def run(command):
        result = subprocess.run(command, env=environment, stdin=subprocess.DEVNULL,
                                capture_output=True, text=True, timeout=180)
        if result.returncode:
            # Verifiers take no literal secrets; do not echo the environment.
            raise RuntimeError(result.stdout[-2500:] + result.stderr[-2500:])
        return result.stdout

    print(run([sys.executable, "-B", str(scripts / "verify-release-apk.py"),
               str(outputs / "apk/release/output-metadata.json"), "shop.justnoise.app"]))
    signatures = run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)])
    if f"Signer #1 certificate SHA-256 digest: {pin}" not in signatures or "Signer #2" in signatures:
        raise ValueError("APK signer does not match the external upload certificate")
    print(signatures)
    alignment = run([str(tools / "zipalign"), "-c", "-P", "16", "-v", "4", str(apk)])
    print(alignment.splitlines()[-1])
    classpath = (root / "app/build/verifier-classpath.txt").read_text()
    objdump = run(["xcrun", "--find", "llvm-objdump"]).strip()
    print(run([sys.executable, "-B", str(scripts / "verify-release-aab.py"), str(aab),
               "--upload-cert-sha256", pin,
               "--truststore", str(credentials_file.parent / "upload-truststore.p12"),
               "--truststore-password-env", "JUSTNOISE_VERIFY_TRUST_PASSWORD",
               "--bundletool-classpath", classpath, "--llvm-objdump", objdump]))
    # Development component/tooling declarations are independently checked above.
    # Also reject the known debug-probe namespace and full Compose tooling in DEX.
    for path in (apk, aab):
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.endswith(".dex"):
                    dex = archive.read(name)
                    for forbidden in (b"Lcom/justnoise/gate0/debug/", b"Lcom/justnoise/integrated/debug/",
                                      b"Landroidx/compose/ui/tooling/PreviewActivity;"):
                        if forbidden in dex:
                            raise ValueError("Development class found in release DEX")
        if hashlib.sha256(path.read_bytes()).hexdigest() != hashes[path.name]:
            raise ValueError("Candidate changed during verification")
    metadata = json.loads((outputs / "apk/release/output-metadata.json").read_text())
    elements = metadata.get("elements", [])
    if metadata.get("applicationId") != "shop.justnoise.app" or len(elements) != 1:
        raise ValueError("Unexpected release APK identity or artifact count")
    version_code = elements[0].get("versionCode")
    version_name = elements[0].get("versionName")
    if version_code != 21 or version_name != "1.0.19":
        raise ValueError("Expected Play update 1.0.19 (21)")
    report = {"application_id": "shop.justnoise.app", "version_code": version_code, "version_name": version_name,
              "sha256": hashes, "upload_certificate_sha256": pin,
              "local_artifact_checks": "PASS", "device_acceptance": "NOT RUN",
              "installation": "NOT AUTHORIZED", "distribution": "HOLD",
              "secure_offline_key_backup": "OWNER ACTION STILL REQUIRED"}
    (root / "app/build/local-candidate-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, KeyError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print("Candidate verification FAILED: " + str(error), file=sys.stderr)
        sys.exit(1)

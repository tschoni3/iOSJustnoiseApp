#!/usr/bin/env python3
"""Archive only Android build inputs, never credentials, caches or owner data."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile


ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOTS = (
    "android-app", "spikes/android-integrated", "spikes/android-gate0",
    "spikes/android-shared", "product", "scripts", "docs",
)
ARTWORK = (
    "justnoiseradio/Assets.xcassets/zap_button.imageset/zap_button.png",
    "justnoiseradio/Assets.xcassets/Icon.imageset/Instagram post - 4.png",
    "justnoiseradio/Assets.xcassets/Justnoise_logo_nav.imageset/Subtract.png",
    "JN_video1.mp4", "JN_video2.mp4", "JN_onboarding_intro.mp4",
)
REFERENCE_INPUTS = (
    "justnoiseradio/Features/NFC/NFCViewModel.swift", "AGENTS.md", ".gitignore",
)
EXCLUDED_PARTS = {"build", ".git", ".gradle", ".kotlin", ".idea", "evidence", "__pycache__"}
EXCLUDED_SUFFIXES = {".jks", ".p12", ".pfx", ".pem", ".key", ".keystore", ".pyc"}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def allowed(relative):
    path = Path(relative)
    name = path.name.lower()
    return not (
        EXCLUDED_PARTS.intersection(path.parts)
        or path.suffix.lower() in EXCLUDED_SUFFIXES
        or name == "local.properties"
        or name.startswith(".env")
        or ("signing" in name and name.endswith(".properties"))
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()
    candidate = args.candidate.resolve()
    candidate.relative_to(ROOT / "android-app/build/candidates")
    report = json.loads((candidate / "local-candidate-report.json").read_text())
    for name, expected in report["sha256"].items():
        if sha256(candidate / name) != expected:
            raise ValueError("Candidate artifact checksum mismatch")
    outputs = (candidate / "android-source.tar.gz", candidate / "source-manifest.json")
    if any(path.exists() for path in outputs):
        raise FileExistsError("Do not overwrite a frozen source snapshot")
    listed = subprocess.check_output([
        "git", "ls-files", "-z", "--cached", "--others", "--exclude-standard",
        "--", *SOURCE_ROOTS,
    ], cwd=ROOT).decode().split("\0")
    selected = sorted({name for name in listed if name and allowed(name)}
                      | set(ARTWORK) | set(REFERENCE_INPUTS))
    manifest = {}
    for name in selected:
        path = ROOT / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("Missing or symlinked source input: " + name)
        path.resolve().relative_to(ROOT)
        manifest[name] = sha256(path)
    with tarfile.open(outputs[0], "w:gz") as archive:
        for name in selected:
            archive.add(ROOT / name, arcname=name, recursive=False)
    # Reject source drift during archive creation, not merely after compilation.
    for name, expected in manifest.items():
        if sha256(ROOT / name) != expected:
            raise ValueError("Source changed while freezing: " + name)
    with tarfile.open(outputs[0], "r:gz") as archive:
        if archive.getnames() != selected:
            raise ValueError("Archive input list mismatch")
        for member in archive:
            content = archive.extractfile(member)
            if content is None or hashlib.sha256(content.read()).hexdigest() != manifest[member.name]:
                raise ValueError("Archive content mismatch")
    metadata = {
        "application_id": report["application_id"],
        "version_code": report["version_code"],
        "version_name": report["version_name"],
        "artifact_sha256": report["sha256"],
        "source_archive_sha256": sha256(outputs[0]),
        "source_files": manifest,
        "limitations": [
            "Requires the existing external signing properties and matching upload key.",
            "Requires Java 17+ and Android SDK; external Maven/Gradle dependencies are not vendored.",
            "Captures current reviewed build inputs, not a bit-reproducible rebuild guarantee.",
            "Does not include Git history, owner device data, credentials or cached build outputs.",
            "Cross-platform contract verification additionally requires the original unchanged iOS tree.",
        ],
    }
    outputs[1].write_text(json.dumps(metadata, indent=2) + "\n")
    print(f"Frozen {len(manifest)} Android source/resource inputs; archive contents verified.")
    print("Source archive SHA256: " + metadata["source_archive_sha256"])


if __name__ == "__main__":
    main()

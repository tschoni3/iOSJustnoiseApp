#!/usr/bin/env python3
"""Credential-free structural check; never grants device/distribution approval."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parent
build = (root / "app/build.gradle.kts").read_text()
required = (
    'applicationId = "shop.justnoise.app"',
    'versionName = "1.0.0"',
    'isDebuggable = false',
    'signingConfig = signingConfigs.getByName("upload")',
    'JUSTNOISE_SIGNING_PROPERTIES',
    'variant.enable = variant.buildType == "release"',
    'integrated.resolve("src/main/java")',
    'capability.resolve("src/main/java")',
    'Installation is not authorized',
)
for fragment in required:
    if fragment not in build:
        sys.exit("Production layout FAILED: missing " + fragment)
for source in list((root / "app/src").rglob("*.kt")) + list((root / "app/src").rglob("*.java")):
    relative = source.relative_to(root / "app/src").as_posix()
    # Connected-release adapters are unique production code, not copied spike engines.
    # Their tests compile in the same one-app module. Keep all other runtime reuse strict.
    if not relative.startswith(("main/java/com/justnoise/app/auth/", "test/java/com/justnoise/app/auth/")):
        sys.exit("Production layout FAILED: unreviewed runtime source " + relative)
    if not source.read_text().startswith("package com.justnoise.app.auth\n"):
        sys.exit("Production layout FAILED: unexpected adapter namespace " + relative)
if 'include(":app")' not in (root / "settings.gradle.kts").read_text():
    sys.exit("Production layout FAILED: expected one app module")
print("Production layout verified: build-only candidate, canonical sources, explicit signing; distribution HOLD.")

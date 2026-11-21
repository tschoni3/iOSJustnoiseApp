#!/usr/bin/env python3
"""Credential-free structural check; never grants device/distribution approval."""
from pathlib import Path
import hashlib
import struct
import xml.etree.ElementTree as ET
import sys

root = Path(__file__).resolve().parent
build = (root / "app/build.gradle.kts").read_text()
required = (
    'applicationId = "shop.justnoise.app"',
    'versionCode = 21',
    'versionName = "1.0.19"',
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
release = root / "app/src/release"
android = "{http://schemas.android.com/apk/res/android}"
application = ET.parse(release / "AndroidManifest.xml").getroot().find("application")
if application is None or application.get(android + "icon") != "@mipmap/ic_launcher":
    sys.exit("Production layout FAILED: missing branded launcher icon")
icon = ET.parse(release / "res/mipmap-anydpi-v26/ic_launcher.xml").getroot()
if icon.tag != "adaptive-icon" or icon.find("foreground").get(android + "drawable") != "@drawable/justnoise_launcher_foreground":
    sys.exit("Production layout FAILED: missing adaptive foreground")
for path, size in ((root / "branding/zap-logo-original.png", 1080),
                   (root / "branding/play-store-icon.png", 512),
                   (release / "res/drawable-nodpi/justnoise_launcher.png", 432)):
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (size, size):
        sys.exit("Production layout FAILED: invalid icon size")
if hashlib.sha256((root / "branding/zap-logo-original.png").read_bytes()).hexdigest() != "bc370df34c451c28ef15e4e73fc0f90a3d4e1171a270e653d0d23a26599520fe":
    sys.exit("Production layout FAILED: owner logo source drift")
print("Production layout verified: build-only candidate, canonical sources, explicit signing; distribution HOLD.")

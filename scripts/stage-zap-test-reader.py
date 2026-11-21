"""Stage an internal JustNoise reader with an additional test issuer. No writing capability."""
from pathlib import Path
import json
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
if len(sys.argv) != 3:
    sys.exit("Usage: stage-zap-test-reader.py NEW_DIR TEST_READER_TRUST_JSON")
dest = Path(sys.argv[1]).resolve()
dest.mkdir(mode=0o700, parents=True, exist_ok=False)
sources = ["justnoise.xcodeproj", "AppIconJN.icon", "JustNoiseFocusMonitor", "JustNoiseWidget",
           "JustNoiseWidgetExtension.entitlements", "ShieldConfigurationExtension", "justnoiseradio",
           "justnoiseradioTests", "product", *[x.name for x in repo.glob("JN_*.mp4")]]
subprocess.run(["cp", "-cR", *sources, dest], cwd=repo, check=True)
trust = json.loads(Path(sys.argv[2]).read_text())
if set(trust) != {"verificationKey", "contentKey"}:
    raise ValueError("Expected reader trust configuration")
(dest / "justnoiseradio/Features/NFC/ZapWorkshopTestTrust.json").write_text(json.dumps(trust))
project = dest / "justnoise.xcodeproj/project.pbxproj"
settings = project.read_text()
marker = "INFOPLIST_KEY_CFBundleDisplayName = Justnoise;"
assert settings.count(marker) == 2
project.write_text(settings.replace(marker, marker + '\n\t\t\t\tOTHER_SWIFT_FLAGS = "$(inherited) -DZAP_READER_TEST_TRUST";'))

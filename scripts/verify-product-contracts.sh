#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

command -v python3 >/dev/null || {
  echo "error: python3 is required to verify product contracts" >&2
  exit 1
}

python3 - "${ROOT_DIR}" <<'PY'
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
token_path = root / "product/design/tokens.v1.json"
copy_path = root / "product/copy/en.v1.json"
fixture_path = root / "product/behavior/portable-fixtures.v1.json"
workflow_path = root / ".github/workflows/product-contracts.yml"
ios_workflow_path = root / ".github/workflows/ios-verification.yml"
signal_api_root = root / "product/api/signal/v1"
signal_provenance_path = signal_api_root / "provenance.json"

required_contract_paths = (
    "AGENTS.md",
    "docs/MOBILE_ARCHITECTURE.md",
    "docs/MOBILE_BEHAVIOR_V1.md",
    "docs/ANDROID_GATE_0.md",
    "product/behavior/portable-fixtures.v1.json",
    "product/copy/en.v1.json",
    "product/design/tokens.v1.json",
    "product/api/signal/v1/openapi.yaml",
    "product/api/signal/v1/provenance.json",
    "product/api/signal/v1/fixtures/comment.json",
    "product/api/signal/v1/fixtures/error.json",
    "product/api/signal/v1/fixtures/existing-thread.json",
    "product/api/signal/v1/fixtures/request.json",
    "product/api/signal/v1/fixtures/safety-hold.json",
    "product/api/signal/v1/fixtures/silence.json",
    "justnoiseradioTests/SignalBackendContractFixtureTests.swift",
    "scripts/verify-product-contracts.sh",
    ".github/workflows/product-contracts.yml",
    ".github/workflows/ios-verification.yml",
)


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            fail(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json(path: Path):
    if not path.is_file():
        fail(f"missing contract: {path.relative_to(root)}")
    try:
        with path.open(encoding="utf-8") as handle:
            return json.load(handle, object_pairs_hook=reject_duplicate_keys)
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        fail(f"invalid JSON in {path.relative_to(root)}: {error}")


def require_mapping(value, label: str):
    if not isinstance(value, dict):
        fail(f"{label} must be a JSON object")
    return value


def require_list(value, label: str):
    if not isinstance(value, list):
        fail(f"{label} must be a JSON array")
    return value


def require_v1_version(document, version_key: str, label: str) -> None:
    if document.get("schemaVersion") != 1:
        fail(f"{label}.schemaVersion must be 1")
    version = document.get(version_key)
    if not isinstance(version, str) or not re.fullmatch(r"1\.[0-9]+\.[0-9]+", version):
        fail(f"{label}.{version_key} must be a semantic v1 version such as 1.0.0")


expected_signal_source = {
    "repository": "https://github.com/tschoni3/JustNoiseApp",
    "mergeCommit": "d445e7a45025b59ced388465cff3c84dd9ee86fd",
    "contractPath": "contracts/signal/v1",
    "openAPIDocumentVersion": "1.0.0",
    "responseContractVersion": 1,
}
expected_signal_files = {
    "openapi.yaml": (
        "contracts/signal/v1/openapi.yaml",
        "ab5fe3290917ef81bc74ca24293664e19692cf2992150c313cf513ea645f4ab0",
    ),
    "fixtures/comment.json": (
        "contracts/signal/v1/fixtures/comment.json",
        "833826c4697bd7c973369ebd34034119d241547db2c9b532e7d058bd73d0bfc0",
    ),
    "fixtures/error.json": (
        "contracts/signal/v1/fixtures/error.json",
        "a25a9cf3f9567004f2c37c4cf27903f26e4c2a37642a5656e73605000cb2bc9c",
    ),
    "fixtures/existing-thread.json": (
        "contracts/signal/v1/fixtures/existing-thread.json",
        "a46a79037e2cad0caa049c5ea435a76a1fb533a73c08b9941c36d6a6998cadca",
    ),
    "fixtures/request.json": (
        "contracts/signal/v1/fixtures/request.json",
        "5e665cfe17ac3d38262f7f3393d69fc9f72e1e0e8cc07f0c36935c14d2015c5d",
    ),
    "fixtures/safety-hold.json": (
        "contracts/signal/v1/fixtures/safety-hold.json",
        "e122a956d5f4ff5d9c5703e52bd2724a3b9a6e88f41add439d88c2d7ac4cff81",
    ),
    "fixtures/silence.json": (
        "contracts/signal/v1/fixtures/silence.json",
        "ef28f49337f49dfb9e15c9d83f96ede85e9c70b8dce5cb944406d06f0ec44a7f",
    ),
}

signal_provenance = require_mapping(load_json(signal_provenance_path), "Signal provenance")
if signal_provenance.get("provenanceVersion") != 1:
    fail("Signal provenance.provenanceVersion must be 1")
if set(signal_provenance) != {"provenanceVersion", "source", "files"}:
    fail("Signal provenance must contain only provenanceVersion, source, and files")
signal_source = require_mapping(signal_provenance.get("source"), "Signal provenance.source")
if signal_source != expected_signal_source:
    fail("Signal provenance.source does not match the released backend pin")

signal_file_entries = require_list(signal_provenance.get("files"), "Signal provenance.files")
actual_signal_files = {}
for index, entry_value in enumerate(signal_file_entries):
    entry = require_mapping(entry_value, f"Signal provenance.files[{index}]")
    if set(entry) != {"path", "sourcePath", "sha256"}:
        fail(f"Signal provenance.files[{index}] must contain only path, sourcePath, and sha256")
    relative = entry.get("path")
    if not isinstance(relative, str) or not relative or relative.startswith("/") or ".." in Path(relative).parts:
        fail(f"Signal provenance.files[{index}].path must be a safe relative path")
    if relative in actual_signal_files:
        fail(f"duplicate Signal provenance path: {relative}")
    source_path = entry.get("sourcePath")
    checksum = entry.get("sha256")
    if not isinstance(source_path, str) or not source_path:
        fail(f"Signal provenance.files[{index}].sourcePath must be a non-empty string")
    if not isinstance(checksum, str) or not re.fullmatch(r"[0-9a-f]{64}", checksum):
        fail(f"Signal provenance.files[{index}].sha256 must be a lowercase SHA-256")
    actual_signal_files[relative] = (source_path, checksum)

if actual_signal_files != expected_signal_files:
    fail("Signal provenance.files does not match the released backend file manifest")

actual_vendored_paths = {
    path.relative_to(signal_api_root).as_posix()
    for path in signal_api_root.rglob("*")
    if path.is_file() and path != signal_provenance_path
}
if actual_vendored_paths != set(expected_signal_files):
    fail("product/api/signal/v1 contains an unpinned or missing vendored file")

for relative, (_, expected_checksum) in expected_signal_files.items():
    vendored_path = signal_api_root / relative
    if not vendored_path.is_file():
        fail(f"missing vendored Signal file: product/api/signal/v1/{relative}")
    actual_checksum = hashlib.sha256(vendored_path.read_bytes()).hexdigest()
    if actual_checksum != expected_checksum:
        fail(
            f"vendored Signal file drifted from backend merge d445e7a: "
            f"product/api/signal/v1/{relative}"
        )

signal_openapi = require_mapping(load_json(signal_api_root / "openapi.yaml"), "Signal OpenAPI")
if signal_openapi.get("openapi") != "3.1.0":
    fail("Signal OpenAPI version must remain 3.1.0")
signal_info = require_mapping(signal_openapi.get("info"), "Signal OpenAPI.info")
if signal_info.get("version") != expected_signal_source["openAPIDocumentVersion"]:
    fail("Signal OpenAPI info.version does not match its provenance")

for fixture_name in ("comment.json", "existing-thread.json", "safety-hold.json", "silence.json"):
    response_fixture = require_mapping(
        load_json(signal_api_root / "fixtures" / fixture_name),
        f"Signal fixture {fixture_name}",
    )
    if response_fixture.get("contractVersion") != expected_signal_source["responseContractVersion"]:
        fail(f"Signal fixture {fixture_name} has the wrong response contractVersion")


tokens = require_mapping(load_json(token_path), "tokens")
require_v1_version(tokens, "tokenSetVersion", "tokens")
if tokens.get("colorSpace") != "sRGB":
    fail("tokens.colorSpace must be sRGB")
if tokens.get("colorEncoding") != "#RRGGBB or #RRGGBBAA, with non-premultiplied alpha in the trailing byte":
    fail("tokens.colorEncoding must define trailing, non-premultiplied alpha")
colors = require_mapping(tokens.get("colors"), "tokens.colors")

required_colors = {
    "surface.zap.idle",
    "surface.zap.active",
    "surface.shield",
    "text.zap.idle.primary",
    "text.zap.idle.secondary",
    "text.zap.active.primary",
    "text.zap.active.secondary",
    "text.shield.primary",
    "text.shield.secondary",
    "action.zap.stop.background",
    "action.zap.stop.label",
    "accent.noiseRewind",
    "action.shield.background",
    "action.shield.label",
}
missing_colors = sorted(required_colors - colors.keys())
if missing_colors:
    fail(f"tokens.colors is missing required keys: {', '.join(missing_colors)}")

hex_color = re.compile(r"#[0-9A-F]{6}(?:[0-9A-F]{2})?")
for key, value in colors.items():
    if not isinstance(value, str) or not hex_color.fullmatch(value):
        fail(f"tokens.colors.{key} must be an uppercase #RRGGBB or #RRGGBBAA value")

if "accent.zap" in colors:
    fail("tokens.colors.accent.zap is ambiguous; use action.zap.stop.background or accent.noiseRewind")


def decode_contract_color(value: str):
    digits = value[1:]
    red = int(digits[0:2], 16)
    green = int(digits[2:4], 16)
    blue = int(digits[4:6], 16)
    alpha = int(digits[6:8], 16) if len(digits) == 8 else 255
    return red, green, blue, alpha


if colors["action.zap.stop.background"] != "#FFFF00E6":
    fail("tokens.colors.action.zap.stop.background must preserve iOS yellow at 0.9 alpha as #FFFF00E6")
if decode_contract_color(colors["action.zap.stop.background"]) != (255, 255, 0, 230):
    fail("Zap stop-action RGBA decoding changed")
if colors["accent.noiseRewind"] != "#DDFF00":
    fail("tokens.colors.accent.noiseRewind must preserve the characterized #DDFF00 accent")
if decode_contract_color(colors["accent.noiseRewind"]) != (221, 255, 0, 255):
    fail("Noise Rewind RGBA decoding changed")

copy = require_mapping(load_json(copy_path), "copy")
require_v1_version(copy, "copyVersion", "copy")
if copy.get("locale") != "en":
    fail("copy.locale must be en for en.v1.json")
strings = require_mapping(copy.get("strings"), "copy.strings")

required_copy_keys = {
    "brand.displayName",
    "navigation.zap",
    "navigation.capture",
    "zap.idle.title",
    "zap.idle.subtitle",
    "zap.active.title",
    "zap.active.action.stop",
    "mode.selector.label",
    "mode.selector.empty",
    "mode.list.title",
    "mode.list.prompt",
    "mode.list.emptyAction",
    "schedule.list.title",
    "schedule.list.empty",
    "shield.title",
    "shield.item.message",
    "shield.application.fallbackName",
    "shield.website.fallbackName",
    "shield.primaryAction",
    "session.complete.title",
    "session.complete.body",
}
missing_copy = sorted(required_copy_keys - strings.keys())
if missing_copy:
    fail(f"copy.strings is missing required keys: {', '.join(missing_copy)}")

for key, value in strings.items():
    if not isinstance(value, str) or not value.strip():
        fail(f"copy.strings.{key} must be a non-empty string")

placeholder_pattern = re.compile(r"\{([A-Za-z][A-Za-z0-9]*)\}")
allowed_placeholders = {"shield.item.message": {"appName"}}
for key, value in strings.items():
    placeholders = set(placeholder_pattern.findall(value))
    expected = allowed_placeholders.get(key, set())
    if placeholders != expected:
        fail(
            f"copy.strings.{key} placeholders must be "
            f"{sorted(expected)}, found {sorted(placeholders)}"
        )
    without_placeholders = placeholder_pattern.sub("", value)
    if "{" in without_placeholders or "}" in without_placeholders:
        fail(f"copy.strings.{key} contains a malformed template placeholder")

expected_shield_message = "{appName} is currently Zapped.\nTap your Zap to access it."
if strings["shield.item.message"] != expected_shield_message:
    fail("copy.strings.shield.item.message does not match the approved v1 template")
if colors["surface.shield"] != "#18181A":
    fail("tokens.colors.surface.shield must preserve the characterized #18181A surface")
if colors["action.shield.background"] != "#FFFFFF":
    fail("tokens.colors.action.shield.background must preserve the approved white button")

fixtures = require_mapping(load_json(fixture_path), "fixtures")
require_v1_version(fixtures, "fixtureSetVersion", "fixtures")

selection_cases = require_list(
    fixtures.get("targetSelectionValidity"),
    "fixtures.targetSelectionValidity",
)
required_selection_ids = {
    "empty",
    "application-only",
    "category-only",
    "website-only",
    "mixed",
}
seen_selection_ids = set()
selection_by_id = {}
for index, case_value in enumerate(selection_cases):
    case = require_mapping(case_value, f"fixtures.targetSelectionValidity[{index}]")
    case_id = case.get("id")
    if not isinstance(case_id, str) or not case_id:
        fail(f"fixtures.targetSelectionValidity[{index}].id must be a non-empty string")
    if case_id in seen_selection_ids:
        fail(f"duplicate target-selection fixture id: {case_id}")
    seen_selection_ids.add(case_id)
    selection_by_id[case_id] = case
    counts = []
    for key in ("applicationCount", "categoryCount", "websiteCount"):
        value = case.get(key)
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            fail(f"target-selection fixture {case_id}.{key} must be a non-negative integer")
        counts.append(value)
    expected = case.get("expectedValid")
    if not isinstance(expected, bool):
        fail(f"target-selection fixture {case_id}.expectedValid must be a boolean")
    if expected != any(count > 0 for count in counts):
        fail(f"target-selection fixture {case_id} contradicts the portable non-empty rule")

if seen_selection_ids != required_selection_ids:
    fail(
        "target-selection fixtures must contain exactly: "
        + ", ".join(sorted(required_selection_ids))
    )
website_only = selection_by_id["website-only"]
if (
    website_only["applicationCount"],
    website_only["categoryCount"],
    website_only["websiteCount"],
    website_only["expectedValid"],
) != (0, 0, 1, True):
    fail("website-only fixture must remain a valid website-only target selection")

stop_guard = require_mapping(fixtures.get("accidentalStopGuard"), "fixtures.accidentalStopGuard")
minimum_elapsed = stop_guard.get("minimumElapsedMilliseconds")
if minimum_elapsed != 3000:
    fail("fixtures.accidentalStopGuard.minimumElapsedMilliseconds must be 3000")
guard_cases = require_list(stop_guard.get("cases"), "fixtures.accidentalStopGuard.cases")
required_guard_values = {0: False, 2999: False, 3000: True, 3001: True}
seen_guard_values = {}
seen_guard_ids = set()
for index, case_value in enumerate(guard_cases):
    case = require_mapping(case_value, f"fixtures.accidentalStopGuard.cases[{index}]")
    case_id = case.get("id")
    if not isinstance(case_id, str) or not case_id or case_id in seen_guard_ids:
        fail("accidental-stop fixture ids must be unique non-empty strings")
    seen_guard_ids.add(case_id)
    elapsed = case.get("elapsedMilliseconds")
    if isinstance(elapsed, bool) or not isinstance(elapsed, int) or elapsed < 0:
        fail(f"accidental-stop fixture {case_id}.elapsedMilliseconds must be a non-negative integer")
    expected = case.get("expectedStopAllowed")
    if not isinstance(expected, bool):
        fail(f"accidental-stop fixture {case_id}.expectedStopAllowed must be a boolean")
    if expected != (elapsed >= minimum_elapsed):
        fail(f"accidental-stop fixture {case_id} contradicts the threshold rule")
    if elapsed in seen_guard_values:
        fail(f"duplicate accidental-stop elapsed value: {elapsed}")
    seen_guard_values[elapsed] = expected
if seen_guard_values != required_guard_values:
    fail("accidental-stop fixtures must cover 0, 2999, 3000, and 3001 milliseconds")

shield_cases = require_list(fixtures.get("shieldMessageRendering"), "fixtures.shieldMessageRendering")
required_shield_ids = {"application-name", "blank-application-name", "blank-website-name"}
seen_shield_ids = set()
for index, case_value in enumerate(shield_cases):
    case = require_mapping(case_value, f"fixtures.shieldMessageRendering[{index}]")
    case_id = case.get("id")
    if not isinstance(case_id, str) or not case_id or case_id in seen_shield_ids:
        fail("shield-message fixture ids must be unique non-empty strings")
    seen_shield_ids.add(case_id)
    display_name = case.get("displayName")
    fallback_key = case.get("fallbackCopyKey")
    expected_message = case.get("expectedMessage")
    if not isinstance(display_name, str):
        fail(f"shield-message fixture {case_id}.displayName must be a string")
    if fallback_key not in {"shield.application.fallbackName", "shield.website.fallbackName"}:
        fail(f"shield-message fixture {case_id} has an unsupported fallbackCopyKey")
    rendered_name = display_name.strip() or strings[fallback_key]
    rendered_message = strings["shield.item.message"].replace("{appName}", rendered_name)
    if expected_message != rendered_message:
        fail(f"shield-message fixture {case_id} does not match copy-template rendering")
if seen_shield_ids != required_shield_ids:
    fail("shield-message fixtures are missing a required application or fallback case")

zap_cases = require_list(fixtures.get("zapReadHandling"), "fixtures.zapReadHandling")
expected_zap_cases = {
    "authorized-first-read": {
        "payloadClassification": "authorized",
        "acceptedReadAlreadyHandled": False,
        "expectedOutcome": "accepted",
        "expectedMutationEligible": True,
    },
    "authorized-duplicate-read": {
        "payloadClassification": "authorized",
        "acceptedReadAlreadyHandled": True,
        "expectedOutcome": "duplicateIgnored",
        "expectedMutationEligible": False,
    },
    "unauthorized-read": {
        "payloadClassification": "unauthorized",
        "acceptedReadAlreadyHandled": False,
        "expectedOutcome": "unauthorized",
        "expectedMutationEligible": False,
    },
    "malformed-read": {
        "payloadClassification": "malformed",
        "acceptedReadAlreadyHandled": False,
        "expectedOutcome": "invalid",
        "expectedMutationEligible": False,
    },
    "canceled-read": {
        "payloadClassification": "canceled",
        "acceptedReadAlreadyHandled": False,
        "expectedOutcome": "canceled",
        "expectedMutationEligible": False,
    },
}
seen_zap_ids = set()
for index, case_value in enumerate(zap_cases):
    case = require_mapping(case_value, f"fixtures.zapReadHandling[{index}]")
    case_id = case.get("id")
    if not isinstance(case_id, str) or not case_id or case_id in seen_zap_ids:
        fail("Zap-read fixture ids must be unique non-empty strings")
    seen_zap_ids.add(case_id)
    expected = expected_zap_cases.get(case_id)
    if expected is None:
        fail(f"unexpected Zap-read fixture id: {case_id}")
    if set(case) != {"id", *expected.keys()}:
        fail(f"Zap-read fixture {case_id} must remain identifier-free and use only the canonical fields")
    if not isinstance(case.get("acceptedReadAlreadyHandled"), bool):
        fail(f"Zap-read fixture {case_id}.acceptedReadAlreadyHandled must be a boolean")
    if not isinstance(case.get("expectedMutationEligible"), bool):
        fail(f"Zap-read fixture {case_id}.expectedMutationEligible must be a boolean")
    if {key: case.get(key) for key in expected} != expected:
        fail(f"Zap-read fixture {case_id} contradicts the portable read rule")
if seen_zap_ids != set(expected_zap_cases):
    fail("Zap-read fixtures are missing an authorization, duplicate, malformed, or canceled case")

session_cases = require_list(fixtures.get("sessionLifecycle"), "fixtures.sessionLifecycle")
expected_session_cases = {
    "authorized-idle-start": ("idle", "accepted", True, "available", 0, "start", "active", "apply"),
    "missing-selection-start-fails": ("idle", "accepted", False, "available", 0, "startFailed", "failed", "none"),
    "permission-denied-start-fails": ("idle", "accepted", True, "permissionDenied", 0, "startFailed", "failed", "none"),
    "unauthorized-idle-no-change": ("idle", "unauthorized", True, "available", 0, "noChange", "idle", "none"),
    "authorized-active-too-early": ("active", "accepted", True, "available", 2999, "stopRejected", "active", "none"),
    "authorized-active-stop": ("active", "accepted", True, "available", 3000, "stop", "idle", "clear"),
    "duplicate-active-no-change": ("active", "duplicateIgnored", True, "available", 3000, "noChange", "active", "none"),
}
session_fields = (
    "priorState",
    "readOutcome",
    "selectionValid",
    "capability",
    "elapsedMilliseconds",
    "expectedTransition",
    "expectedState",
    "expectedRestrictionAction",
)
seen_session_ids = set()
for index, case_value in enumerate(session_cases):
    case = require_mapping(case_value, f"fixtures.sessionLifecycle[{index}]")
    case_id = case.get("id")
    if not isinstance(case_id, str) or not case_id or case_id in seen_session_ids:
        fail("session-lifecycle fixture ids must be unique non-empty strings")
    seen_session_ids.add(case_id)
    expected = expected_session_cases.get(case_id)
    if expected is None:
        fail(f"unexpected session-lifecycle fixture id: {case_id}")
    if set(case) != {"id", *session_fields}:
        fail(f"session-lifecycle fixture {case_id} must use exactly the canonical fields")
    if not isinstance(case.get("selectionValid"), bool):
        fail(f"session-lifecycle fixture {case_id}.selectionValid must be a boolean")
    elapsed = case.get("elapsedMilliseconds")
    if isinstance(elapsed, bool) or not isinstance(elapsed, int) or elapsed < 0:
        fail(f"session-lifecycle fixture {case_id}.elapsedMilliseconds must be a non-negative integer")
    actual = tuple(case.get(field) for field in session_fields)
    if actual != expected:
        fail(f"session-lifecycle fixture {case_id} contradicts the portable state rule")
if seen_session_ids != set(expected_session_cases):
    fail("session-lifecycle fixtures are missing a required start, failure, stop, or no-change case")

weekdays = require_mapping(fixtures.get("weekdayIdentifiers"), "fixtures.weekdayIdentifiers")
required_weekdays = {
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
}
if set(weekdays) != required_weekdays:
    fail("fixtures.weekdayIdentifiers must contain exactly the seven named weekday identifiers")
for identifier, label in weekdays.items():
    if not isinstance(label, str) or not label.strip():
        fail(f"fixtures.weekdayIdentifiers.{identifier} must have a non-empty display label")

for relative in required_contract_paths:
    if not (root / relative).is_file():
        fail(f"missing required product-contract path: {relative}")

required_document_fragments = {
    "AGENTS.md": (
        "product/behavior/portable-fixtures.v1.json",
        "contracts/signal/v1/openapi.yaml",
        "released and pinned",
        "d445e7a45025b59ced388465cff3c84dd9ee86fd",
        "product/api/signal/v1/",
    ),
    "docs/MOBILE_ARCHITECTURE.md": (
        "## Color-token mapping",
        "#RRGGBBAA",
        "TYPE_ACCESSIBILITY_OVERLAY",
        "contracts/signal/v1/openapi.yaml",
        "product/api/signal/v1/",
        "d445e7a45025b59ced388465cff3c84dd9ee86fd",
    ),
    "docs/MOBILE_BEHAVIOR_V1.md": (
        "## Current iOS conformance audit",
        "website-only",
        "NFC-00",
        "Raw Zap payloads must not be sent to analytics.",
        "zapReadHandling",
        "Capture audio ownership is crash-safe in code",
        "This contract intentionally makes no daylight-saving or time-zone choice yet.",
    ),
    "docs/ANDROID_GATE_0.md": (
        'android:isAccessibilityTool="false"',
        'android:canRetrieveWindowContent="false"',
        "G0-18",
        "local-only data inventory",
        "Force Stop",
    ),
}
for relative, fragments in required_document_fragments.items():
    document_text = (root / relative).read_text(encoding="utf-8")
    for fragment in fragments:
        if fragment not in document_text:
            fail(f"{relative} is missing required contract marker: {fragment!r}")

workflow_text = workflow_path.read_text(encoding="utf-8")
for required_path_filter in (
    '"AGENTS.md"',
    '"docs/**"',
    '"product/**"',
    '"justnoiseradioTests/SignalBackendContractFixtureTests.swift"',
    '"scripts/verify-product-contracts.sh"',
    '".github/workflows/product-contracts.yml"',
    '".github/workflows/ios-verification.yml"',
):
    if workflow_text.count(required_path_filter) < 2:
        fail(f"product-contract workflow must filter pull requests and pushes for {required_path_filter}")
if "run: bash scripts/verify-product-contracts.sh" not in workflow_text:
    fail("product-contract workflow must run the repository verifier")

ios_workflow_text = ios_workflow_path.read_text(encoding="utf-8")
if ios_workflow_text.count('"product/api/signal/**"') < 2:
    fail("iOS workflow must run for pull requests and main pushes that change the Signal contract pin")

contract_scopes = [
    "AGENTS.md",
    "docs",
    "product",
    "justnoiseradioTests/SignalBackendContractFixtureTests.swift",
    "scripts/verify-product-contracts.sh",
    ".github/workflows/product-contracts.yml",
    ".github/workflows/ios-verification.yml",
]
try:
    tracked = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *contract_scopes],
        check=True,
        capture_output=True,
    ).stdout.decode("utf-8").split("\0")
except (OSError, UnicodeError, subprocess.CalledProcessError) as error:
    fail(f"could not inspect product-contract paths: {error}")

contract_files = []
for relative in filter(None, tracked):
    parts = Path(relative).parts
    if any(part != part.strip() for part in parts):
        fail(f"product-contract path has leading or trailing whitespace: {relative!r}")
    path = root / relative
    if path.is_file():
        contract_files.append(path)

stale_references = ("Features" + "  /", "Justnoise" + "Widget/")
for path in contract_files:
    if path.suffix not in {".md", ".json", ".sh", ".yml", ".yaml"} and path.name != "AGENTS.md":
        continue
    text = path.read_text(encoding="utf-8")
    for stale in stale_references:
        if stale in text:
            fail(f"{path.relative_to(root)} contains stale malformed path reference {stale!r}")

print(
    f"Verified {len(colors)} semantic colors, {len(strings)} English copy entries, "
    f"and {len(selection_cases) + len(guard_cases) + len(shield_cases) + len(zap_cases) + len(session_cases)} portable behavior cases."
)
print("Product contracts passed.")
PY

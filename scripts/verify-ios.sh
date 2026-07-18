#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="${PROJECT:-${ROOT_DIR}/justnoise.xcodeproj}"
SCHEME="${SCHEME:-justnoiseradio}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-${TMPDIR:-/tmp}/justnoise-derived-data}"
SPM_CACHE_PATH="${SPM_CACHE_PATH:-${HOME}/Library/Caches/JustNoise/SourcePackages}"
RESULT_BUNDLE_PATH="${RESULT_BUNDLE_PATH:-${TMPDIR:-/tmp}/justnoise-test-results.xcresult}"

find_simulator_id() {
  local destinations
  destinations="$(xcodebuild "${common_arguments[@]}" -showdestinations 2>/dev/null || true)"

  awk '
    /platform:iOS Simulator/ && /name:iPhone/ {
      if (match($0, /\([0-9A-F-]+\)/)) {
        print substr($0, RSTART + 1, RLENGTH - 2)
        exit
      }
      if (match($0, /id:[[:space:]]*[0-9A-Fa-f-]+/)) {
        id = substr($0, RSTART, RLENGTH)
        sub(/^id:[[:space:]]*/, "", id)
        print id
        exit
      }
    }
  ' <<< "${destinations}"
}

command -v xcodebuild >/dev/null || {
  echo "error: Xcode command-line tools are unavailable" >&2
  exit 1
}

mkdir -p "${DERIVED_DATA_PATH}" "${SPM_CACHE_PATH}"
rm -rf "${RESULT_BUNDLE_PATH}"

common_arguments=(
  -project "${PROJECT}"
  -scheme "${SCHEME}"
  -derivedDataPath "${DERIVED_DATA_PATH}"
  -clonedSourcePackagesDirPath "${SPM_CACHE_PATH}"
)

echo "==> Xcode"
xcodebuild -version
echo "==> Resolve pinned Swift packages"
xcodebuild \
  -resolvePackageDependencies \
  "${common_arguments[@]}" \
  -onlyUsePackageVersionsFromResolvedFile

# `-showdestinations` filters out runtimes older than the app's deployment target.
SIMULATOR_ID="${IOS_SIMULATOR_ID:-}"
if [[ -z "${SIMULATOR_ID}" ]]; then
  for attempt in 1 2 3 4 5 6; do
    SIMULATOR_ID="$(find_simulator_id)"
    [[ -n "${SIMULATOR_ID}" ]] && break

    if [[ "${attempt}" -lt 6 ]]; then
      echo "==> Waiting for CoreSimulator (${attempt}/6)"
      sleep 5
    fi
  done
fi

if [[ -z "${SIMULATOR_ID}" ]]; then
  echo "error: no compatible iPhone simulator was found" >&2
  echo "Install an iOS simulator runtime in Xcode, or set IOS_SIMULATOR_ID." >&2
  xcrun simctl list devices available >&2 || true
  xcodebuild "${common_arguments[@]}" -showdestinations >&2 || true
  exit 1
fi

echo "==> Release build (app and embedded extensions)"
xcodebuild \
  build \
  "${common_arguments[@]}" \
  -configuration Release \
  -destination "generic/platform=iOS Simulator" \
  -disableAutomaticPackageResolution

echo "==> XCTest on simulator ${SIMULATOR_ID}"
xcodebuild \
  test \
  "${common_arguments[@]}" \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=${SIMULATOR_ID}" \
  -resultBundlePath "${RESULT_BUNDLE_PATH}" \
  -disableAutomaticPackageResolution

echo "==> Verification passed"
echo "Test results: ${RESULT_BUNDLE_PATH}"

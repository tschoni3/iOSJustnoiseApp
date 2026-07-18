#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="${PROJECT:-${ROOT_DIR}/justnoise.xcodeproj}"
SCHEME="${SCHEME:-justnoiseradio}"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-${TMPDIR:-/tmp}/justnoise-derived-data}"
SPM_CACHE_PATH="${SPM_CACHE_PATH:-${HOME}/Library/Caches/JustNoise/SourcePackages}"
RESULT_BUNDLE_PATH="${RESULT_BUNDLE_PATH:-${TMPDIR:-/tmp}/justnoise-test-results.xcresult}"

find_simulator_id() {
  xcodebuild "${common_arguments[@]}" -showdestinations 2>/dev/null | awk '
    /platform:iOS Simulator/ && /name:iPhone/ {
      if (match($0, /\([0-9A-F-]+\)/)) {
        print substr($0, RSTART + 1, RLENGTH - 2)
        exit
      }
      if (match($0, /id:[0-9A-F-]+/)) {
        print substr($0, RSTART + 3, RLENGTH - 3)
        exit
      }
    }
  '
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
SIMULATOR_ID="${IOS_SIMULATOR_ID:-$(find_simulator_id)}"
if [[ -z "${SIMULATOR_ID}" ]]; then
  echo "error: no compatible iPhone simulator was found" >&2
  echo "Install an iOS simulator runtime in Xcode, or set IOS_SIMULATOR_ID." >&2
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

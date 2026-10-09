# Android History timeline — SQLite emulator verification

Status: **PASS — 13 History instrumentation tests, 0 failures/errors/skips**.
Run on 2026-09-06 against the working tree based on
`11d4d9816809ff99b1d2720c8eff5fe4fa3d9d82`, including the Android History timeline
read implementation. This is emulator storage evidence, not physical visual,
NFC, blocking-continuity, release, or full iOS-parity acceptance.

## Target and isolation

- Explicit target: `ANDROID_SERIAL=emulator-5554`, AVD `medium_phone`, Android 16
  / API 36, arm64-v8a, 1080×2400 at 420 dpi.
- Launched with `-read-only -no-snapshot-save -no-window -no-audio`; renderer
  mismatch caused a cold boot after snapshot load was rejected. No saved AVD
  was wiped, no snapshot was saved, and no caches or user data were deleted.
- About 6.7 GiB host storage was free before and during the run.
- Only `com.justnoise.gate0.HistoryInstrumentationTest` was selected. Its synthetic
  data and orchestrated cleanup belong to the emulator test package. The owner's
  connected physical A16 was never selected, installed to, or otherwise operated.
- The emulator was closed after the successful test run.

## Result

The copied [JUnit report](history-instrumentation.xml) records all 13 cases and the
`medium_phone(AVD) - 16` device property. Gradle completed successfully in 43 seconds;
the report's overall test duration is 14.018 seconds.

The new `timelineReadsAllRowsIndependentlyOfPageAndKeepsExistingStorageExactly`
case passed (1.905 seconds). It inserts 125 cross-midnight completed rows plus one
backwards-clock row into the existing SQLite schema, confirms the old list still
returns a 100-row page, then reads all 126 records into day summaries. It checks
the exact 7,500,042 ms all-time total, 3,750,000 / 3,750,042 ms daily attribution,
separate interruption/uncertainty counts, identical existing persisted rows after
the read, and identical results after reopening through `Gate0Preferences`.

All 12 existing History cases also passed: stable IDs/target snapshots, idempotent
SQLite retries/paging, expected-ID stop behavior, legacy consent/no backfill,
consent-at-start eligibility, sticky interruption, process/clock fallback,
database failure and retained stop, failed stop rollback, failed outbox
acknowledgement retry, and withdrawal/deletion recovery without resurrection.

## Reproduction

After booting the isolated AVD and verifying its serial and API:

```sh
ANDROID_SERIAL=emulator-5554 \
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/tj/Library/Android/sdk \
spikes/android-gate0/gradlew --no-daemon -p spikes/android-gate0 \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.justnoise.gate0.HistoryInstrumentationTest \
  --console=plain
```

Never run these destructive synthetic storage tests against the owner's paired app.

## Tested artifact SHA-256

- Gate 0 debug APK: `6ab315494883ec81458f6622a59c3997d56126094180e5a352fab72887230f5b`
- Gate 0 AndroidTest APK: `692d3841be3ace6744471fc6b112c011f97c6ff9b3088f4eaba976b7ade57f00`

These are disposable Gate 0 test artifacts, not the integrated Android UI candidate.

# Schedule foundation — local verification only

Verified 2026-09-03 Europe/Berlin, based on `7a38a6b` plus this change.
The owner approved automatic scheduled start and paired-Zap stop. This is the
first implementation slice, not evidence of working background scheduling.

## Implemented boundary

- Portable fixture set 1.5.0 adds 38 temporal cases: 9 strict one-time resolutions,
  16 next-occurrence identities and 13 delivery decisions. Both pure native rules
  consume them, including DST gaps/first overlaps, travel/rollback, IANA aliases,
  Gregorian validation, grace boundaries and active-session precedence.
- Android schema-v1 private configuration supports create/edit/enable/delete,
  UUID identity, expected revisions, 100-record/encoded-size limits, schema/type
  validation and malformed-store protection. It uses existing `org.json`; duplicate
  record IDs/weekdays reject, but raw duplicate-object-key parsing retains each
  platform's `org.json` behavior. There is no external configuration importer.
- Configuration writes use the existing preference lock/rollback boundary and do
  not start sessions, broadcast state changes or change active identity/targets,
  pairing or History on successful CRUD. Withdrawal clears the same configuration.
- No UI caller, alarm, new permission, scheduled-start command, occurrence receipt
  or stored outcome is connected. Current v3 disclosure remains unchanged and must
  be updated before activation. Shipped iOS scheduling and UI are not migrated.

## Results

- `bash scripts/verify-product-contracts.sh`: 18 semantic colors, 27 copy entries
  and 89 portable behavior cases passed.
- Full `verify-android-predevice-readiness.sh`: **116 JVM tests** (97 Gate 0,
  15 UI, 4 integrated), lint/builds, integrated unsigned release APK/AAB,
  10 release-checker tests and actual packaged-release checks passed. Unchanged
  suites were up-to-date. The new 7 timing and 17 codec methods passed; their XML
  reports are saved beside this file.
- **48 emulator instrumentation tests**, zero failures/errors/skips, on API-36
  `medium_phone` selected exclusively with `ANDROID_SERIAL=emulator-5554`.
  This includes 8 new schedule-storage tests covering CRUD/revisions, active-state
  preservation, corrupt/newer schema, past one-times, reference resolution,
  injected commit failure/rollback and withdrawal/new identity.
  Saved XML reports 86.301 seconds; Gradle completed in 2m 7s. XML line endings
  are normalized to LF; reported results are otherwise unchanged.
- Final full `verify-ios.sh` passed the Release app/extensions build and **66
  XCTest methods**, including 6 schedule methods, against the frozen source and
  all 38 temporal fixtures. The exact result bundle is
  `/private/tmp/justnoise-schedule-phase1-ios-final.xcresult`.
- The emulator ran read-only without saving a snapshot and was closed afterward.
  No iOS simulator remains booted. The paired A16 was not installed, instrumented,
  cleared or otherwise changed.

Logs: `/private/tmp/justnoise-schedule-phase1-android-final.log`,
`/private/tmp/justnoise-schedule-phase1-instrumentation.log` and
`/private/tmp/justnoise-schedule-phase1-ios-final.log`.

## Artifact SHA-256

| Artifact | Digest |
| --- | --- |
| Gate 0 debug APK tested | `39835d9609e3ec9230534ef724c85f64fd231ff8df7c90ab4a242ca9fcd89c85` |
| Instrumentation APK | `cb979835b2060ad219eb25ea3e7883b0b3c0c2926464d2252cc09c19f2044754` |
| Integrated debug APK built | `bcc56a2ed54f7e3bfc76ba41faca36747b3353d0b7c8319b44809a6f9bf7b0e1` |
| Integrated unsigned release APK | `dad95e39a15b5f72d0356f9e80b7fdc564a2ec819adc258588a4c43dd17eceaa` |
| Integrated unsigned AAB | `e63ce004a0c8d1e0bced807b7b45d89b18736a76d21f6c855afd5c39a16ea49c` |
| Saved instrumentation XML | `93638cc8859336c5d0d8a73176aac0fcdafa7edcf78155b740b2c59972a07587` |
| Saved timing JVM XML | `baa3de6515224d2aeaaeeab3ccfe7c42d3010e6d1fe6cc9731dfd7cf8ed48f52` |
| Saved codec JVM XML | `e80e7783a7eda01ddc9ee3b8e9fb4625ca4b48959dec1ce5f499ae99f9a1734b` |

These checks prove pure rules and synthetic local storage paths, not physical
alarm delivery, foreground interception at schedule time, OEM recovery, release
signing or Play approval. No device-matrix cell or gate decision changes.

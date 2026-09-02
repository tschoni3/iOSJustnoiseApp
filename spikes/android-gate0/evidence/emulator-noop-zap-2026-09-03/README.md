# Rejected-Zap notification regression — emulator only

Verified 2026-09-03 Europe/Berlin, based on `81fe9a3` plus the controller fix and
`NoOpZapInstrumentationTest` in this change. This is not physical A16 evidence.

## Change and scope

The service dismisses its overlay when receiving `ACTION_STATE_CHANGED`. The
controller previously published that signal even when the three-second stop guard
rejected an otherwise authorized read. It now publishes only for a real restriction
action or a changed persistence-failure decision. Recovery notifications remain.
The portable reducer, NFC authorization, UI, permissions and iOS are unchanged.

Four new instrumentation cases use synthetic local HMAC pairing and a recording
context; they do not enable Accessibility or read a physical tag:

- Authorized stop at 2,999 ms preserves active identity, revision and targets,
  produces no state-change broadcast and creates no completion.
- Unauthorized/duplicate input likewise leaves the session unchanged.
- Valid stop emits one scoped broadcast and one idempotent History completion.
- Refusing to overwrite an existing completion preserves it and the active session,
  marks recovery and still broadcasts once. This is not an injected disk failure.

## Results

- Full `verify-android-predevice-readiness.sh` passed: 92 JVM tests (including
  unchanged up-to-date suites), lint/debug builds, integrated unsigned release
  APK/AAB, 10 release-checker tests and actual packaged-release checks.
- All **40 instrumentation tests** passed on API-36 `medium_phone`, selected only
  through `ANDROID_SERIAL=emulator-5554`: zero failures/errors/skips. The saved XML
  reports 46.373 seconds; Gradle completed in 1m 6s. XML line endings are normalized
  to LF; results are otherwise copied from the generated report.
- Logs: `/private/tmp/justnoise-noop-zap-readiness.log` and
  `/private/tmp/justnoise-noop-zap-instrumentation.log`.
- Emulator ran read-only without saving a snapshot and was closed after testing.
  The paired A16 was not installed, instrumented, cleared or otherwise changed.

SHA-256:

| Artifact | Digest |
| --- | --- |
| Gate 0 debug APK tested | `c1f2c1c62720b5f72037727b27bfbcba93658544635772b9e345879be37ef5de` |
| Instrumentation APK | `cfec494ae2eea1ff8f5a627c955c3b25813ce943de2c640c83afd646f90651fd` |
| Integrated debug APK built | `29d5d533de139c6f48ae9daf8bc6dcd15252a7af94276f76ba60fada76f669fe` |
| Integrated unsigned release APK | `ea92deb6edddad184c389ef391b0bb75b6ace97f03841af8e689e6fc3fdc282e` |
| Integrated unsigned AAB | `ac63f818ddbbac37abdccd729f7342b11fa84e934ee89c76743eaec0f183e77a` |
| Saved instrumentation XML | `8d96a0e5d26d90d3ea260fd59c90cb481eb7bc3c0ad8371779c1c61a193dc91c` |

This proves the controller notification boundary and synthetic persistence paths,
not actual overlay/physical-Zap timing, background schedules, release signing or
Play approval. No device-matrix cell or gate decision changes.

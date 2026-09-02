# Local History verification — 2026-09-03

Development-source checkpoint based on parent `2b537ad`; the commit containing this
record includes the History implementation and its fixes. No A16 installation,
physical NFC result, Gate 0 promotion, release signing or Play upload occurred.

## Results

- All **36 instrumentation tests** passed on API-36 `medium_phone`, explicitly
  targeted as `emulator-5554`: zero failures, errors or skips. This includes twelve
  History tests for SQLite persistence, UUID retries, immutable snapshots, legacy
  consent, interruption, failed commits/database delivery and withdrawal recovery.
- [instrumentation-results.xml](instrumentation-results.xml) is the actual final
  JUnit report, with CRLF normalized to LF only; test execution 33.974 seconds.
  Gradle completed in 45 seconds. Full log remains at
  `/private/tmp/justnoise-history-instrumentation-final.log`.
- The final Android readiness verifier passed **92 JVM tests**, debug/release lint
  and builds, ten release-checker tests and the packaged release boundary. Log:
  `/private/tmp/justnoise-history-readiness-final.log`.
- Earlier failures and their fixes are preserved in
  [the readiness record](../../../../docs/ANDROID_PREDEVICE_READINESS.md#history-implemented-locally--2026-09-03).
  No failure was reclassified as a passing hardware result.

## Bounded UI check

Installed the final integrated debug APK on the read-only emulator and opened the
visible History button. [history-empty.png](history-empty.png) shows its actual
empty database, zero summary and empty-state guidance. Normal Android Back returned
to Zap. No sample records were injected for this image, no Accessibility permission
was enabled and no NFC session was claimed. Populated storage behavior is exercised
by instrumentation; populated UI and real-Zap History still need A16 acceptance.

The emulator was launched with `-read-only -no-snapshot-save`; the owner's paired
`com.justnoise.androiddev` A16 installation was untouched.

## SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Integrated debug APK | `9e0cb1102fd0b7965f4b9abff7fc9cc6202acbe77ad780281deba3bbf361de45` |
| Integrated unsigned release APK | `93e99f1aa30a75dc090ecd35b0efbe16b0fe48819850faf461a6139f0b301168` |
| Integrated unsigned App Bundle | `5ce356bb0b62cc49339e624236093b19e85ba56a4d726baf3f996863dd2c35e4` |
| Instrumentation XML | `3961e8d42b7dfb646cd373535684008c63a1d4d5a76deee4fa2f8b17b42f389b` |
| History screenshot | `d90a1fc7f2920f0c2dc389333ec104469a4c26f0b4f85ec1eb0952c9a3e9131c` |

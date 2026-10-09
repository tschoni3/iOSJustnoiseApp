# Real app-selection verification — 2026-09-06

This slice connects a reusable selection-management interface to the existing real
native setup picker. It is not the future Compose sheet, full iOS visual parity,
A16 acceptance, production promotion or a signed Play upload candidate.

## Verified results

- Full Android readiness verifier: PASS, **192 JVM tests** (147 capability, 15
  isolated UI prototype, 30 integrated), contracts/static boundary, lint,
  debug/release builds, **17 Python checker tests** and the integrated release APK
  boundary. Unchanged tasks can reuse Gradle results; preview tests do not earn
  real-feature or visual completion credit.
- [Selection/storage/native UI report](selection-storage-ui.xml): **64 tests**, zero
  failures/errors/skips: 14 selection transaction, eight native picker, ten local
  state, four no-op Zap, 11 first-run persistence, 13 History and four consent UI.
- [Real integrated first-run report](integrated-first-run-ui.xml): **five tests**,
  zero failures/errors/skips, confirming onboarding/pairing progression still opens
  the real unchecked disclosure/setup screen without fabricating consent or a session.
- Independent read-only selection persistence/caller/lifecycle review: no remaining
  actionable findings after the withdrawal-generation and token-equality fixes.

The six new JVM tests cover worker-facing discovery/read ordering, frozen authority,
missing/removal-only entries, normalized discovery, truthful errors and no retry.
The selection transaction tests inject failed commits and rollback, stale/concurrent
edits, active/recovery/deletion state, same-millisecond re-consent, failed withdrawal,
foreign-process tokens, legacy storage and preserved unrelated pairing/History.
Native UI tests cover real Save/Cancel, unchanged legacy targets, protected/missing
row removal, rejection/retry, scroll rebinding, pause/recreation and withdrawal
conflict requiring explicit Reload. No synthetic records are in production code.

## Corrections during verification

The first run found an offscreen-row test that asserted native checked state before
row binding and an older History test expecting writes during unfinished deletion.
The test now waits for visible rows; the History regression requires rejection plus
an exactly unchanged preference map, retaining cleanup/restart assertions.

The next run reached the removal-only redraw assertion and exposed a real native
picker issue: rejecting a recheck reset ListView state but not AlertDialog's Boolean
array, allowing a rebound row to look checked. Both are now reset. The regression
yields for redraw, scrolls away/back and checks visible and logical state, then proves
the excluded packages were not saved. This behavior was checked against the
[Android framework implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/app/AlertController.java).
After a test-only Kotlin return-expression correction, the complete 64-test suite
passed. Failed intermediate runs are not counted as successful evidence.

## Isolation and limits

- Explicit device target: `emulator-5554`, read-only `medium_phone`, API 36/arm64,
  launched with `-read-only -no-snapshot-save -no-window -no-audio`. Closed with
  `adb -s emulator-5554 emu kill`; no emulator snapshot saved.
- No A16 command, install, instrumentation, clear, uninstall or permission change.
  New selection tests guard emulator hardware and capability package identity before
  synthetic cleanup. Integrated tests guard emulator and development package too.
- Current tests exercise native picker behavior in one emulator configuration, not
  new compact/large-text visual acceptance. Earlier first-run render evidence remains
  separately scoped. There was no physical NFC or scheduled-delivery test this slice.
- Discovery, eligibility validation and selection commits now execute off the UI
  thread. This is source-level responsiveness work, not a measured performance claim.
- No new persisted fields, manifest permission, disclosure version or copied engine.
  Draft/token state stays transient. No owner data or legitimate History warnings
  were removed.
- Git comparison against `11d4d98` is empty for the iOS app/tests/project, all three
  extensions, entitlements, original icon/videos/assets and product contracts.

## Artifact SHA-256

- Gate 0 debug APK: `a14eb1043e450756bb27223cf77777cf2bce77948f6f700fc491bf087d6a8b2a`
- Gate 0 test APK: `636fc377cab35d1937126fdd66a36074f5a3c1c18882ab9d0252a6e5b8dcc727`
- Integrated debug APK: `84ff6e2805266a622b1883640937961a0cb9b1fd948692d4f3c148ccbf000747`
- Integrated test APK: `cce887af4405794d034b50df3480a0957cf6b4ef6f54db4019fd442d4609f573`
- Unsigned development release AAB: `54a88a01dde40428ca60f8c50d112322a195b4c4562909422838bcffacb192e2`

The AAB remains `com.justnoise.androiddev`, **not** the requested signed
`com.justnoise.app` candidate. No signing-key creation, production promotion,
internal-beta GO, commit, Play registration or upload occurred.

Logs: `/private/tmp/justnoise-selection-final-verification.log`,
`/private/tmp/justnoise-selection-emulator-tests-final.log` and
`/private/tmp/justnoise-selection-integrated-ui.log`.

Next: remaining Release reference captures/comparisons and real main-screen,
selection-sheet, History and schedule UI work. The requested source-first sequencing
decision is still unanswered; this slice did not silently waive screenshot-first.

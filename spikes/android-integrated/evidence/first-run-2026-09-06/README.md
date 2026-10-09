# Integrated Android first-run verification — 2026-09-06

This evidence covers the real two-page onboarding and initial Zap activation
slice in `com.justnoise.androiddev`. It is not A16 RF evidence, full UI parity,
production promotion, a signed release, or Play upload approval.

## Results

- Full Android readiness verifier: PASS, 186 JVM tests (141 capability, 15 isolated
  UI prototype, 30 integrated), 17 Python release-checker tests, contracts, lint,
  debug/release builds and integrated release APK boundary. Prototype tests do not
  count as real feature or visual completion. Some unchanged tasks reused Gradle
  results; the eight new first-run routing tests executed in this run.
- [Real-activity UI, standard](ui-standard.xml): five tests, zero failures/errors/skips.
- [Real-activity UI, compact/enlarged text](ui-compact-2x.xml): the same five tests,
  zero failures/errors/skips. This is ten executions of five distinct tests.
- [Persistence/History/NFC](storage-nfc.xml): 33 tests, zero failures/errors/skips:
  11 first-run persistence, 13 History, one existing HMAC, three NDEF, one direct NFC
  Activity and four no-op Zap cases.

The UI tests launch the actual integrated Activity. They cover two-page completion,
rotation on page two, relaunch without an Activity saved-state bundle, legacy pairing
preservation, and newly paired confirmation requiring explicit continuation to the
existing unchecked disclosure/setup screen. They assert no consent, selected targets,
service enablement or session is fabricated. Relaunch testing is Activity-level, not
a process-termination test. Synthetic pairing lives only in emulator test code.

The first UI attempt failed because its fixture used `suspendSchedules()` in a fully
empty store, where that method intentionally does not persist suspension. The fixture
was corrected to seed only the suspended Boolean on the guarded emulator. The complete
five-test suite then passed twice. This is suspended-state regression coverage, not
proof that every fresh API 31–34 install had a production defect.

First-run storage tests exercise idempotent durable completion, failure rollback and
retry without clearing genuine recovery, withdrawal, initial-only pairing/replacement
refusal, exactly one concurrent pairing winner, active/recovery/deletion rejection,
and pairing without clearing schedule-only suspension. Existing History records and
UUIDs remain unchanged through the read interface; no History warnings were cleared.

## Isolation and render scope

- Explicit target throughout: `emulator-5554`, read-only `medium_phone`, API 36,
  arm64, 420 dpi. Started with `-read-only -no-snapshot-save -no-window -no-audio`;
  closed with `adb -s emulator-5554 emu kill` after the final captures.
- Standard viewport: 1080×2400 px (about 411×914 dp), font scale 1.0.
- Compact viewport: 945×1680 px (360×640 dp), Android font scale 2.0. The body is
  scrollable; tests reach text/actions even when not all fit at once. Rotation is
  also tested. This does not constitute complete TalkBack or final A16 acceptance.
- Both automated UI passes use `animator_duration_scale=0`; saved PNGs show the
  still-frame fallback. Normal silent video playback was visually inspected before
  that setting was disabled. Source review confirms offscreen, pause, user-pause,
  reduced-motion and release lifecycle handling; no long-running decoder soak or
  resource-allocation benchmark is claimed.
- No owner A16 command, install, instrumentation, data deletion or permission change.
  New tests guard emulator hardware before synthetic storage cleanup; integrated
  tests additionally guard package identity and do not request pre-guard package
  clearing. Test runners install/clean only their emulator packages.
- No iOS source, project, extension, entitlement or original media/asset changes.

## Screenshots

Compared with the three unchanged [iOS Release captures](../../../../docs/reference/ios-release-2026-09-06/README.md).
The hierarchy, dimensions, artwork, dark onboarding, white activation and capsule
actions follow those references. Intentional Android changes: Select Apps copy,
native system bars, accessible paging/Back, explicit setup continuation and a
Set up later action. Different video frames are not pixel-diff failures.

| Capture | State |
| --- | --- |
| [01](01-onboarding-select-apps.png) | Standard page one, reduced-motion still |
| [02](02-onboarding-stay-focused.png) | Standard page two, reduced-motion still |
| [03](03-zap-activation.png) | Standard unpaired activation, real empty configuration |
| [04](04-onboarding-compact-2x.png) | Compact page one at font scale 2.0; lower body scrolls |
| [05](05-activation-compact-2x-actions.png) | Compact activation scrolled to reachable lower actions |

Pairing confirmation was exercised through the real controller with synthetic test
records; it is not presented as a completed physical NFC scan. The fresh first-run
scan still needs affected physical release/A16 acceptance later. The owner's existing
pairing must not be erased to manufacture that test.

## Artifact SHA-256

- Integrated debug APK: `f46e2f8dbe835c2f7a38935dc492a69589c520a08cb2549be7acac498181a066`
- Integrated test APK: `cce887af4405794d034b50df3480a0957cf6b4ef6f54db4019fd442d4609f573`
- Gate 0 debug APK: `c5f7a751eea0d949ffd9ce8e66b66576f49feb39276507d036125770e6321ae1`
- Gate 0 test APK: `f74e35e0126890cb5b6a7b945511098a4522f718c5ba5914eafbc2c9c452dab3`
- Unsigned development release AAB: `43510c80e19eb329b88bc5d470b17ae70b6725a2daea9c1475b4dcce4f02a2df`

The AAB still uses `com.justnoise.androiddev`; it is **not the requested signed
`com.justnoise.app` upload candidate**. No production promotion, signing-key creation,
Play registration/upload or internal-beta GO is inferred.

Logs: `/private/tmp/justnoise-first-run-verification.log`,
`/private/tmp/justnoise-first-run-ui-tests.log`,
`/private/tmp/justnoise-first-run-ui-compact-tests.log`, and
`/private/tmp/justnoise-first-run-storage-nfc-tests.log`.

# Android Gate 0 decision record

Decision: **HOLD / NO-GO for the full Android port**

Internal beta decision: **HOLD — smaller A16 safety checkpoint incomplete**

Latest bounded safety run: on installed `909b41b`, the owner-authorized system-UI
Accessibility disable retained pairing, Chrome selection and session intent/revision
`22`. Setup and main truthfully reported recovery; opening Chrome did not restore a
stale overlay. After the owner's manual re-enable, service heartbeat resumed, main
showed active protection and Chrome was blocked again. Native Home remained usable.
This recovery smoke passed without clearing data or another Zap scan. It is not
Force Stop, cleanup/reinstall or a complete Gate 0 PASS. The earlier Keep going reset
is fixed and physically retested; do not credit that earlier unintended end as a
Zap stop. Dedicated emergency access, physical negative input and cleanup remain
open. Prior stop/unblock results stand. See the September 2 record. Play Console
still shows account setup; no upload occurred.

Date opened: 2026-07-22

Last updated: 2026-09-02

The repository now contains a reviewable candidate and CI design, but the complete required consumer-device and policy evidence does not exist. All 19 required A16 cells remain `BLOCKED`, which is a gate failure. No production `android-app/` should be created from this spike yet.

Owner scope decision, 2026-09-02: use the existing A16 as the only required initial hardware target. A17 validation is deferred/unverified and does not block development or this scoped gate; no additional phone purchase or tester is required. All 19 A16 criteria and product/policy review remain intact. No device result or approval was granted by narrowing the scope.

Subsequent lean-delivery instruction, 2026-09-02: first target a local-only, single-selected-app Play internal beta. The smaller safety checkpoint in `docs/ANDROID_GATE_0.md` may authorize that implementation separately, without waiting for the full endurance matrix or pretending it passed. The owner has confirmed normal Chrome access after stopping; that confirmation is complete and must not be requested again. The cleanup-feedback fix `dbe2469` and later `909b41b` restoration fix were installed while idle, with pairing record and selection retained. Remaining safety evidence still does not complete the smaller checkpoint. No internal-beta GO, upload approval, or public-release approval has been granted.

The September 2 A16 follow-up records one foreground cycle and one warm direct
Home start/stop cycle. A further direct-start/Chrome-blocking check exposed missing
normal Android navigation controls on `d93ae01`. The window-bounds candidate
`d09833d` passed the local verifier and was tested on the phone. A portrait retest
confirms visible navigation controls and corrected window bounds; later coordinate
touches of native Home/Back confirm limited touch routing. Remaining safety coverage
is still incomplete. The app's own Go Home button is not a substitute for system
navigation. The latest installed follow-up is `909b41b`, not a new full safety PASS.
See [the build-scoped evidence](A16_PARTIAL_RUN_2026-09-02.md). No safety PASS is implied.

## Pre-device evidence now recorded

At repository commit `4bb4631148a1c5fdab0455c4e09b247c3ca74428`, product-contract and static-policy verification passed; the local prototype passed 23 JVM tests, lint and APK assembly; five hardware-independent instrumentation tests passed on an API 36 Google Play emulator; and the APK cold-launched. GitHub Android Gate 0 run [`31009963974`](https://github.com/tschoni3/iOSJustnoiseApp/actions/runs/31009963974) also passed on the exact commit.

This evidence changes no device-matrix cell. It validates only the build, declared static boundary, portable fixtures, local state/Keystore behavior, synthetic NDEF parsing, and basic launchability.

## What the green prototype evidence proves

- The disposable project compiles with its pinned toolchain.
- Portable reducer fixtures pass in Kotlin.
- Static source/manifest claims match the minimal declared boundary.
- Lint and instrumentation-test APK compilation pass.
- The five hardware-independent instrumentation tests execute successfully on an API 36 emulator.

## What it cannot prove

- Interception happens before target content is usable.
- Navigation, split-screen, alternate entry, or OEM behavior cannot bypass it.
- No persistent overlay or lockout can occur under real lifecycle stress.
- Default-battery and overnight durability pass on the exact required A16 variant/build. A17 compatibility remains unverified and deferred.
- Real authorized/unauthorized NFC behavior is reliable.
- An unlocked direct Zap tap routes to Justnoise and toggles once without app-open, Scan-button, chooser, or confirmation friction.
- Google Play accepts the declared Accessibility use.
- Product accepts the onboarding, battery, OEM, and escape-path tradeoffs.

## Decision owner checklist

Only change this record to `PASS` when every `G0-01` through `G0-19` cell is `PASS` on the required A16 variant/build, all evidence is attached, and product plus policy reviewers sign off. Any required A16 `FAIL` or `BLOCKED` remains `NO-GO`; policy uncertainty remains `HOLD`. Optional A17 results do not enter this gate's arithmetic, and an A16 PASS must not be presented as tested A17 or universal Android compatibility.

# Android Gate 0 decision record

Decision: **HOLD / NO-GO for the full Android port**

Date opened: 2026-07-22

Last updated: 2026-09-02

The repository now contains a reviewable candidate and CI design, but the required consumer-device and policy evidence does not exist. All 38 A16/A17 launch-family cells remain `BLOCKED`, which is a gate failure. No production `android-app/` should be created from this spike yet.

The September 2 A16 follow-up records one foreground cycle and one warm direct
Home start/stop cycle. A further direct-start/Chrome-blocking check exposed missing
normal Android navigation controls on `d93ae01`. A window-bounds fix is being
verified; the app's own Go Home button is not a substitute for system navigation.
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
- Default-battery and overnight durability pass on the exact Galaxy A16 and A17 variants intended for launch.
- Real authorized/unauthorized NFC behavior is reliable.
- An unlocked direct Zap tap routes to Justnoise and toggles once without app-open, Scan-button, chooser, or confirmation friction.
- Google Play accepts the declared Accessibility use.
- Product accepts the onboarding, battery, OEM, and escape-path tradeoffs.

## Decision owner checklist

Only change this record to `PASS` when every `G0-01` through `G0-19` cell is `PASS` on both required device families, all evidence is attached, and product plus policy reviewers sign off. Any `FAIL` or `BLOCKED` remains a broad-port `NO-GO`; policy uncertainty remains `HOLD`.

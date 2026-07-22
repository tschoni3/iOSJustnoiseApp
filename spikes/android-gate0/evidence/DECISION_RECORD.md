# Android Gate 0 decision record

Decision: **HOLD / NO-GO for the full Android port**

Date opened: 2026-07-22

The repository now contains a reviewable candidate and CI design, but the required consumer-device and policy evidence does not exist. All 36 Pixel/Samsung cells remain `BLOCKED`, which is a gate failure. No production `android-app/` should be created from this spike yet.

## What a green prototype build would prove

- The disposable project compiles with its pinned toolchain.
- Portable reducer fixtures pass in Kotlin.
- Static source/manifest claims match the minimal declared boundary.
- Lint and instrumentation-test APK compilation pass.

## What it cannot prove

- Interception happens before target content is usable.
- Navigation, split-screen, alternate entry, or OEM behavior cannot bypass it.
- No persistent overlay or lockout can occur under real lifecycle stress.
- Default-battery and overnight durability pass on Pixel and Samsung.
- Real authorized/unauthorized NFC behavior is reliable.
- Google Play accepts the declared Accessibility use.
- Product accepts the onboarding, battery, OEM, and escape-path tradeoffs.

## Decision owner checklist

Only change this record to `PASS` when every `G0-01` through `G0-18` cell is `PASS` on both required device families, all evidence is attached, and product plus policy reviewers sign off. Any `FAIL` or `BLOCKED` remains a broad-port `NO-GO`; policy uncertainty remains `HOLD`.

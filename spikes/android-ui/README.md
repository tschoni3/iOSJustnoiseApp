# Justnoise Android UI prototype

This is a user-permission-free Jetpack Compose prototype for the Android presentation layer. It exists under `spikes/` because the production `android-app/` remains blocked by the physical-device and policy decision in [`docs/ANDROID_GATE_0.md`](../../docs/ANDROID_GATE_0.md).

## What this prototype proves

- The published iOS Zap screen can be expressed as a responsive native Compose screen.
- Idle and active presentation states use the shared copy and semantic color contracts.
- Mode selection can use native Android interaction while preserving the same product intent.
- Compose previews expose important states without adding Storybook or a second component runtime.
- The existing Zap artwork is reused at build time rather than copied into another source of truth.

## What it deliberately does not claim

- No NFC scan is started.
- No application is restricted or observed.
- No Accessibility service or user-facing/sensitive system permission is declared.
- No production package name, signing identity, account, schedule, backend, or Play listing is created.

Tapping the Zap only switches the visual preview between idle and active. The launcher label and on-screen `UI PREVIEW` marker make that boundary explicit.

## Run

The Gate 0 wrapper is reused so the repository does not track a second Gradle wrapper binary:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ../android-gate0/gradlew -p . testDebugUnitTest assembleDebug
```

Install the resulting APK from `app/build/outputs/apk/debug/app-debug.apk` on an emulator. Android Studio can also open this directory directly and render the idle and active previews in `ZapPrototype.kt`.

## Promotion boundary

After Gate 0 is recorded as `PASS`, promote the UI into the single production `android-app/app` module, replace the preview reducer with real ports/adapters, normalize the shared Zap asset into the production Android resource pipeline, choose the permanent application ID, and rerun the contract and physical-device verification. Do not promote the preview state switch as product behavior.

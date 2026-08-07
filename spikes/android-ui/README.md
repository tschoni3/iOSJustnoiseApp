# Justnoise Android UI prototype

This is a user-permission-free Jetpack Compose prototype for the Android presentation layer. It exists under `spikes/` because the production `android-app/` remains blocked by the physical-device and policy decision in [`docs/ANDROID_GATE_0.md`](../../docs/ANDROID_GATE_0.md).

## What this prototype proves

- The published iOS Zap screen can be expressed as a responsive native Compose screen.
- The published Release launch order is represented as two onboarding pages, Zap activation, signed-out authentication, and Zap home; Capture remains absent.
- The existing onboarding videos and app icon are reused through generated build resources rather than copied into Android source.
- Idle and active presentation states use the shared copy and semantic color contracts.
- Mode selection can use native Android interaction while preserving the same product intent.
- Compose previews expose important states without adding Storybook or a second component runtime.
- The existing Zap artwork is reused at build time rather than copied into another source of truth.

## What it deliberately does not claim

- No NFC scan is started.
- No application is restricted or observed.
- Email, Google, password-reset, and activation actions change local preview state only; no authentication or reset request is sent.
- No Accessibility service or user-facing/sensitive system permission is declared.
- No production package name, signing identity, account, schedule, backend, or Play listing is created.

Tapping the activation Zap advances the preview to authentication, the Google button advances to Zap home, and the home Zap switches the visual idle/active state. The launcher label and on-screen preview notices make those boundaries explicit.

The prototype omits the iOS-only Sign in with Apple button. Production Android authentication must make a deliberate provider decision and configure its own package/signing identity; this UI spike does not infer that decision from the iOS SDK.

## Run

The Gate 0 wrapper is reused so the repository does not track a second Gradle wrapper binary:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ../android-gate0/gradlew -p . testDebugUnitTest assembleDebug
```

Install the resulting APK from `app/build/outputs/apk/debug/app-debug.apk` on an emulator. Android Studio can also open this directory directly and render the idle and active previews in `ZapPrototype.kt`.

## Promotion boundary

After Gate 0 is recorded as `PASS`, promote the UI into the single production `android-app/app` module, replace the preview reducer with real ports/adapters, normalize the shared Zap asset into the production Android resource pipeline, choose the permanent application ID, and rerun the contract and physical-device verification. Do not promote the preview state switch as product behavior.

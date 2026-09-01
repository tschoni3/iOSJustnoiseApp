# Justnoise Android integrated development shell

This disposable, one-module development app combines the reviewed Android Gate 0
capability sources with a small native Compose Zap surface. It deliberately remains
under `spikes/`; it is not the production Android application and does not change the
Gate 0 or permission-free UI evidence projects.

The launcher shows only real local Gate 0 truth. It does not expose the prototype's
fake authentication, modes, history, schedules, or navigation. **Setup** opens the
existing Gate 0 screen for disclosure, one target-app selection, Accessibility
enablement, local Zap pairing, and foreground scanning. After setup, the same APK
also exposes the narrow live-tag direct NDEF entry candidate so an unlocked home-screen
Zap tap can start or stop without navigating through the setup screen.

When the real local truth is ready or active, the polished Zap surface itself owns a
lifecycle-scoped one-shot foreground reader. The large Zap and bottom action therefore
start a real scan rather than opening the technical setup screen; pause/cancellation or
a stale callback cannot mutate session state. The foreground path reserves the same
three-second process claim before reader mode restores direct dispatch, preventing one
physical tap from being applied once by each Android NFC route.

Build with the pinned Gate 0 wrapper:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ../android-gate0/gradlew -p . assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk` with the
disposable application ID `com.justnoise.androiddev`. A fresh install has its own
Android sandbox and therefore requires its own one-time local Zap pairing.

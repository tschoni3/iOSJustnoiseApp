# Source-first Zap and real selection — 2026-09-06

Scope: the disposable `com.justnoise.androiddev` app on the read-only API 36
`medium_phone` emulator, serial **emulator-5554**. No owner-phone operation,
production promotion, signing or upload. iOS source/assets were read only.

## Verified checkpoint

- Integrated debug/JVM/test-APK build passed before these runs.
- `ui-standard.xml`: **23 passed, 0 failed, 0 skipped**: 5 first-run, 7 selection
  ViewModel, 4 actual selection Activity, and 7 pure Zap rendered-surface tests.
- `capture-standard.txt`: the 4 actual selection Activity + 7 pure Zap tests
  passed again using the same APKs, retaining screenshots after the runner exited.
  Gradle's connected test task uninstalls its APKs, so the original capture files
  were removed by that task; the direct emulator-only runner preserved these files.
- `capture-compact-font200.txt`: all **4 actual selection Activity tests passed**
  at 945×1680 px / 420 dpi (360×640 dp), system font scale 2.0.
- Standard viewport: 1080×2400 px / 420 dpi, font scale 1.0. Android animator
  duration scale was 0; these runs verify reachable static layouts, not animation
  performance or every reduced-motion path. The compact recovery fixture sets a
  360×640 dp Compose container and 2.0 font scale inside that standard window.

APK SHA-256 values at this checkpoint (later History/schedule builds differ):

```text
a732ac8c4b6f8f8a602fee0a4d9981229a741819336e496e4f21b5aec1024438  app-debug.apk
4d73538ae8ed6d2ba8c38a5b31067d5e1952b80ee3ddbad3d7e3fa77c41ebc29  app-debug-androidTest.apk
```

## Screenshots and limits

`zap-ui-evidence/fixture-zap-idle.png` and `fixture-zap-active.png` are pure UI
fixtures, not real NFC/enforcement evidence. Their geometry, original read-only
artwork/logo, toolbar placement, dark/light states and reachable actions were
visually inspected. Recovery screenshots capture lower actions after scrolling;
they are not whole-page composites.

`zap-ui-evidence/real-selection-unsaved-two-apps.png` and
`real-selection-compact-font200.png` use the actual Activity/selection manager and
real emulator launcher choices. They show an unsaved synthetic selection. The
compact screenshot is mid-scroll: the introduction is partly above the viewport,
while Cancel, title, choices and Save remain reachable without overlap. Header
stacking deliberately differs at enlarged text to prevent Cancel/title collision.

The actual Activity tests prove Save/Cancel, removal-only protected/missing rows,
draft discard on recreation, and cancellation of a claimed NFC request when
selection opens. A simulated late NFC result is rejected and its bytes cleared;
this is not a physical tag test. VM tests cover stale-write/recovery/load errors
and confirmed-save lifecycle behavior. No stored owner data is used.

These are Android implementation checks against the audited iOS source metrics.
Missing authenticated iOS Release render comparisons and final A16 acceptance
remain open; no percentage or full visual-parity credit is inferred.

## Reproduction

Use JDK 17 and the installed Android SDK. Start only a disposable read-only AVD.
Every command must explicitly target `emulator-5554`; never substitute an owner
device. The tests independently enforce emulator hardware and the development ID
before cleanup.

```sh
env ANDROID_SERIAL=emulator-5554 JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/Users/tj/Library/Android/sdk ./spikes/android-gate0/gradlew -p spikes/android-integrated :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.justnoise.integrated.IntegratedSelectionViewModelInstrumentationTest,com.justnoise.integrated.IntegratedSelectionActivityInstrumentationTest,com.justnoise.integrated.IntegratedZapUiInstrumentationTest,com.justnoise.integrated.IntegratedFirstRunInstrumentationTest --console=plain
```

For retained screenshots, install those debug/test APKs on the emulator and run:

```sh
adb -s emulator-5554 shell am instrument -w -e class com.justnoise.integrated.IntegratedSelectionActivityInstrumentationTest,com.justnoise.integrated.IntegratedZapUiInstrumentationTest -e captureZapEvidence true com.justnoise.androiddev.test/androidx.test.runner.AndroidJUnitRunner
```

Copy the unmodified PNGs from the emulator app's external-files `zap-ui-evidence`
directory. For the compact run, set emulator `wm size 945x1680` and system
`font_scale 2.0`, then run only `IntegratedSelectionActivityInstrumentationTest`.
Restore emulator display settings afterward; never save the read-only AVD state.

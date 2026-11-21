# Justnoise Android Gate 0

Status: **HOLD — prototype code exists; no physical-device or Play-policy gate has passed**

This is a disposable, one-module Android Views/XML capability spike. It exists only to decide whether a normal consumer-installed Android app can offer a reliable and supportable Justnoise restriction experience. It is intentionally under `spikes/android-gate0/`, not `android-app/`. A minimal internal-test release app may be scaffolded after the smaller A16 safety checkpoint in `docs/ANDROID_GATE_0.md` is recorded as GO; the complete 19-cell matrix and product/policy acceptance still gate the full product/public launch. Both decisions are currently HOLD. A17 validation is deferred/unverified and non-blocking; no second phone is required.

The canonical decision criteria remain in [`../../docs/ANDROID_GATE_0.md`](../../docs/ANDROID_GATE_0.md). This directory contains prototype code and evidence templates; it does not change those criteria.

## What the spike implements

- One application module, native Kotlin, platform Views/XML, min SDK 31.
- Scoped discovery of launcher activities through a manifest `<queries>` intent; no `QUERY_ALL_PACKAGES`.
- A prominent disclosure and unchecked affirmative consent before opening Accessibility settings. The current multi-app wording uses disclosure version `gate0-accessibility-v2`; an older acceptance does not silently become consent to this version.
- One locally saved set of non-protected launchable app identifiers, edited with **Select apps to block**, **Save**, and **Cancel**. An empty saved selection is allowed, but cannot start a session.
- An active-session target snapshot copied from the saved selection at start and removed on stop. Changing the selection is refused while a session is requested, including recovery; saving an unchanged set is a no-op. Existing single-app selection and Zap pairing are preserved on upgrade.
- Foreground-only, one-shot NFC reader mode for one NDEF message containing exactly one bounded UTF-8 NDEF Text record. Non-Text auxiliary records are allowed; multiple Text records are rejected as ambiguous.
- A narrow unlocked-device `ACTION_NDEF_DISCOVERED` candidate for legacy Text Zaps. It requires and re-reads a live `EXTRA_TAG`; copied `EXTRA_NDEF_MESSAGES` alone can never mutate session state.
- A lifecycle generation guard invalidates an armed or in-flight scan on pause, cancellation, or replacement. Reader mode is disabled after tag I/O and before any accepted state mutation.
- Android Keystore HMAC-SHA256 pairing. Only the digest is stored; tag IDs and raw NDEF records are not stored or logged. Since 2026-11-21 the tag UID is read in memory only, to verify issuer-signed JNZ1 Zaps (see `docs/ANDROID_NFC_ACTIVATION.md`).
- An Accessibility service subscribed only to `TYPE_WINDOW_STATE_CHANGED`, with window-content retrieval disabled and no accessibility flags.
- Exact membership of the transient event `packageName` in the active-session target snapshot; the blocked surface names the matched foreground app.
- A Justnoise-owned `TYPE_ACCESSIBILITY_OVERLAY` with visible Home, Justnoise, Accessibility-settings, and App-info escape routes.
- Local heartbeat and process-start invalidation. A stale or disconnected service produces `RECOVERY_REQUIRED`, never a false active claim.
- Explicit withdrawal that stops the session, removes the overlay, deletes the Keystore key and local Gate 0 configuration, and asks the service to disable itself.
- JVM contract/static tests and instrumentation smoke tests. The JVM suite directly consumes `product/behavior/portable-fixtures.v1.json`.

There is no backend, account, analytics, network permission, `QUERY_ALL_PACKAGES`, usage-stats permission, draw-over-other-apps permission, device-owner path, Compose runtime, or production identifier in this spike.

The multi-app behavior above describes current source, not a new A16 installation or
physical acceptance result. The dated September 2 runs used single-target builds;
their evidence remains specific to those builds. This source change adds no schedule
delivery or completed-session history. Both remain required MVP work, not optional
features or capabilities delivered by preview screens.

## Multi-app source verification — 2026-09-03

The complete `scripts/verify-android-predevice-readiness.sh` passed for the initial
multi-app commit `d80c4ce` (base `a97b054`): 86 JVM tests across the three projects, product/static
contracts, debug lint/builds, instrumentation APK compilation, integrated release
lint/APK/App Bundle builds, 10 release-checker tests, and the packaged release boundary.
Local log: `/private/tmp/justnoise-multiapp-final-verification.log`.

The earlier Mac disk-space block is cleared. The first executed instrumentation run
had two picker-test failures caused by test synchronization with queued button work
and list layout. The tests now wait for those UI operations; the production picker
was unchanged for those failures. A separate emulator picker check found Google
Personal Safety selectable, so `com.google.android.apps.safetyhub` was explicitly
protected and a regression was added.

The rerun passed **all 24 instrumentation tests, with zero failures or skips**, on
the API-36 `medium_phone` emulator (`emulator-5554`). This includes storage/migration,
picker Save/Cancel, setup restoration/withdrawal, blocker labels and the exact Safety
package exclusion. Local log:
`/private/tmp/justnoise-multiapp-instrumentation-recheck.log`.
Instrumentation ran only on the disposable emulator, not the owner's paired A16.
The [JUnit report](evidence/emulator-multiapp-2026-09-03/instrumentation-results.xml)
is retained with this change (line endings normalized; report contents unchanged).

Initial `d80c4ce` integrated debug APK SHA-256 (historical, before the Safety fix):
`c00bcd112cfb18b4eeb6676bdfbc6be2482e5dcae226d8ef86c674c249e3947a`.
The refreshed full verifier after the Safety fix also passed: all 86 JVM tests,
lint/builds, 10 release-checker tests and the packaged release boundary. Local log:
`/private/tmp/justnoise-multiapp-verified-release.log`.
Final integrated debug APK SHA-256:
`6bdb339817d24284a40415488cc2e9c11cf158326d8759d634a41723c425bd6d`.
The final debug build was installed only on the emulator, not the A16. A bounded
combined-app visual smoke check confirmed that Calendar and Chrome selected through
the picker remained saved after the APK update: the [Zap surface shows two apps](evidence/emulator-multiapp-2026-09-03/hero-two-apps.png),
and the [reopened picker restores both checks](evidence/emulator-multiapp-2026-09-03/picker-two-apps.png)
without offering Personal Safety. Consent remained unchecked and the service was
not enabled. This is not full visual, NFC or physical restriction acceptance.
No A16 permission, pairing, app data, session or
tag was changed during this multi-app work; no artifact was signed for upload or sent to Play.
New physical multi-target checks and the v2 consent upgrade remain pending, and no
device-matrix cell is promoted by this local verification.

## Verified pre-device baseline

Recorded 2026-08-07 at repository commit `4bb4631148a1c5fdab0455c4e09b247c3ca74428`:

- Product-contract verification passed: 14 semantic colors, 21 English copy entries, and 24 portable behavior cases.
- The Gate 0 static-policy boundary passed.
- The pinned local build passed 23 JVM tests, lint, debug APK assembly, and instrumentation APK assembly with JDK 17 and SDK 36.
- Five hardware-independent instrumentation tests passed on an Apple Silicon API 36 Google Play emulator: disclosure/consent UI, Android Keystore HMAC Zap storage, local session-state persistence, and synthetic NDEF parsing.
- The debug APK installed and cold-launched successfully on the same emulator.
- GitHub **Android Gate 0** run [`31009963974`](https://github.com/tschoni3/iOSJustnoiseApp/actions/runs/31009963974) passed on the exact commit.

This is supporting preflight evidence only. The Android Emulator has no NFC hardware and does not reproduce the physical A16's lifecycle, battery, escape-path, interception timing, or direct tag-routing behavior. Therefore it changes zero cells in `evidence/DEVICE_MATRIX.md`; all 19 required A16 cells remain `BLOCKED`. Optional A17 coverage is excluded from the initial gate.

## Toolchain and wrapper bootstrap

The project is pinned to Android Gradle Plugin 9.3.0, Gradle 9.5.0, built-in Kotlin, JDK 17, and compile/target SDK 36. At spike creation the Mac had no Java, Gradle, Android SDK, or ADB, so the standard wrapper was generated in the pinned CI environment rather than copied from an unknown local installation. It passed Gradle wrapper validation and is checked in so every build uses the same entry point. The official Gradle 9.5.0 binary ZIP SHA-256 is `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`; the official wrapper JAR SHA-256 is `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

To regenerate the wrapper later without Android Studio, use a trusted JDK 17 and an official Gradle 9.5.0 binary:

```sh
cd spikes/android-gate0
JAVA_HOME=/absolute/path/to/jdk-17 /absolute/path/to/gradle-9.5.0/bin/gradle wrapper --gradle-version 9.5.0 --distribution-type bin --gradle-distribution-sha256-sum 553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746
./gradlew --version
```

Review the generated `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and properties; run wrapper validation before committing the binary. Do not substitute a wrapper JAR copied from an unknown project.

## CI artifact installation

1. Run the **Android Gate 0** workflow and require the static policy check, JVM tests, lint, debug APK, and instrumentation-test APK build to pass.
2. Download the `android-gate0-debug` artifact and verify each APK against `SHA256SUMS`.
3. On the existing unmanaged Galaxy A16 `SM-A165F` with its current public stable OS, install `app-debug.apk` normally. Record the full model code, region/CSC, RAM tier, OS build, and One UI version. `adb install app-debug.apk` is acceptable for installation only; do not grant a production permission through ADB. Other-device runs may be added later but are not prerequisites.
4. Open Justnoise Gate 0. Confirm the disclosure appears before an unchecked control and that declining leaves the service off.
5. Under **Apps to block**, press **Select apps to block**, choose Chrome, YouTube, and one third-party launchable app together, then **Save**. Confirm the selection is restored after reopening setup; use **Cancel** to confirm unsaved picker changes do not apply. Record any app omitted by scoped visibility or marked unavailable. Unavailable saved targets are shown rather than silently erased; deselect them while idle before starting.
6. Accept the current disclosure, use the button to open Accessibility settings, and manually enable the service. Confirm no draw-over-other-apps permission is requested. On an upgrade from disclosure v1, confirm fresh affirmative v2 consent is required while the saved target and pairing remain intact.
7. Press **Pair or replace test Zap** and scan one real UTF-8 NDEF Text tag. Use a different real NDEF tag for unauthorized cases.
8. Press **Scan Zap to start or stop**, then repeat from the unlocked home screen by tapping the paired Zap without opening Justnoise. While active, verify each selected app is intercepted with its own name, non-selected apps remain usable, and **Select apps to block** is disabled with guidance to stop with the Zap before changing targets. After a valid stop, verify the targets are released and selection editing is available again. Record whether Android routes directly, displays a chooser, or does nothing.
9. Complete every scenario in `evidence/DEVICE_MATRIX.md`, including default battery mode, unrestricted battery mode, reboot, natural Doze, eight-hour overnight, service death, Force Stop, and uninstall.
10. Run the instrumentation APK on-device only as supporting evidence. It cannot replace any physical matrix cell.

Collect screen recordings and redacted system/log evidence. Do not capture a raw Zap record or unrelated foreground package history. Record model, full OS build, security patch, battery mode, tester, prototype commit, and exact alternate entry paths.

### Do not let inspection interrupt the blocker

Do not use `adb shell uiautomator dump` during an Accessibility enforcement or
recovery check. Android's [`UiAutomation` API](https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
suppresses other Accessibility services by default. The legacy
[`uiautomator dump` command](https://android.googlesource.com/platform/frameworks/uiautomator/+/17fac436d78f6ac642386a245fb4fdb7243a91a4/cmds/uiautomator/src/com/android/commands/uiautomator/DumpCommand.java)
uses the default automation connection, so inspection can create a service disconnect
and invalidate the observation; its [connection wrapper](https://android.googlesource.com/platform/prebuilts/fullsdk/sources/android-31/+/refs/heads/main/com/android/uiautomator/core/UiAutomationShellWrapper.java)
does not request service preservation. Do not attribute a disconnect to the app
without a clean retest.

For the owner-assisted blocking run:

- Keep the phone manually unlocked and Justnoise resumed for the readiness check.
  Wait at least three seconds after resuming, then use `adb exec-out screencap -p`
  or `adb shell screenrecord`, not an accessibility-tree dump.
- Correlate the visible state with scoped `dumpsys activity` / `dumpsys accessibility`
  checks and only the desired-active, recovery, connected, heartbeat, and revision
  fields from local state. Never print the full preference file, Zap digest, or payload.
- If inspection already interrupted the service, repeat the observation after it
  reconnects. Preserve pairing and session state; do not toggle permissions or clear
  app data merely to repair the test environment.
- Keep instrumentation smoke tests separate from the paired physical run. Test runners
  can disturb services or app data. Future UI automation for the blocker must explicitly
  preserve services with `FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` and verify that
  assumption on the device before its results count as evidence.

## Gate interpretation

Every current device cell is `BLOCKED`, which counts as `FAIL`. Code review or green CI proves only that the candidate is buildable and matches its declared static boundary. It cannot establish interception timing, OEM durability, escape safety, Google Play acceptance, or the Android product promise.

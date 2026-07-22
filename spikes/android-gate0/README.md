# Justnoise Android Gate 0

Status: **HOLD — prototype code exists; no physical-device or Play-policy gate has passed**

This is a disposable, one-module Android Views/XML capability spike. It exists only to decide whether a normal consumer-installed Android app can offer a reliable and supportable Justnoise restriction experience. It is intentionally under `spikes/android-gate0/`, not `android-app/`. A production Android application must not be scaffolded until every Gate 0 cell passes on both required device families and the policy/product reviews accept the result.

The canonical decision criteria remain in [`../../docs/ANDROID_GATE_0.md`](../../docs/ANDROID_GATE_0.md). This directory contains prototype code and evidence templates; it does not change those criteria.

## What the spike implements

- One application module, native Kotlin, platform Views/XML, min SDK 31.
- Scoped discovery of launcher activities through a manifest `<queries>` intent; no `QUERY_ALL_PACKAGES`.
- A prominent disclosure and unchecked affirmative consent before opening Accessibility settings.
- One locally selected, non-protected package identifier.
- Foreground-only, one-shot NFC reader mode for exactly one bounded UTF-8 NDEF Text record.
- Android Keystore HMAC-SHA256 pairing. Only the digest is stored; tag IDs and raw NDEF records are not stored or logged.
- An Accessibility service subscribed only to `TYPE_WINDOW_STATE_CHANGED`, with window-content retrieval disabled and no accessibility flags.
- Exact equality between the transient event `packageName` and the locally selected package.
- A Justnoise-owned `TYPE_ACCESSIBILITY_OVERLAY` with visible Home, Justnoise, Accessibility-settings, and App-info escape routes.
- Local heartbeat and process-start invalidation. A stale or disconnected service produces `RECOVERY_REQUIRED`, never a false active claim.
- Explicit withdrawal that stops the session, removes the overlay, deletes the Keystore key and local Gate 0 configuration, and asks the service to disable itself.
- JVM contract/static tests and instrumentation smoke tests. The JVM suite directly consumes `product/behavior/portable-fixtures.v1.json`.

There is no backend, account, analytics, network permission, `QUERY_ALL_PACKAGES`, usage-stats permission, draw-over-other-apps permission, device-owner path, Compose runtime, or production identifier in this spike.

## Toolchain and wrapper bootstrap

The project is pinned to Android Gradle Plugin 9.3.0, Gradle 9.5.0, built-in Kotlin, JDK 17, and compile/target SDK 36. The creating Mac had no Java, Gradle, Android SDK, or ADB, so no unreviewed local toolchain was installed and the Gradle wrapper JAR could not be generated locally. `gradle-wrapper.properties` is checked in; the workflow uses a pinned official Gradle action to obtain Gradle 9.5.0, generates the standard wrapper, validates its JAR against Gradle's known checksums, and then builds only through that wrapper.

To check in the wrapper later without Android Studio, use a trusted JDK 17 and an official Gradle 9.5.0 binary:

```sh
cd spikes/android-gate0
JAVA_HOME=/absolute/path/to/jdk-17 /absolute/path/to/gradle-9.5.0/bin/gradle wrapper --gradle-version 9.5.0 --distribution-type bin
./gradlew --version
```

Review the generated `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and properties; run wrapper validation before committing the binary. Do not substitute a wrapper JAR copied from an unknown project.

## CI artifact installation

1. Run the **Android Gate 0** workflow and require the static policy check, JVM tests, lint, debug APK, and instrumentation-test APK build to pass.
2. Download the `android-gate0-debug` artifact and verify each APK against `SHA256SUMS`.
3. On an unmanaged Pixel 8-or-newer or Galaxy S23-or-newer with the current public stable OS, install `app-debug.apk` normally. `adb install app-debug.apk` is acceptable for installation only; do not grant a production permission through ADB.
4. Open Justnoise Gate 0. Confirm the disclosure appears before an unchecked control and that declining leaves the service off.
5. Select Chrome, YouTube, and one third-party launcher app in turn. Record any app omitted by scoped visibility.
6. Accept the disclosure, use the button to open Accessibility settings, and manually enable the service. Confirm no draw-over-other-apps permission is requested.
7. Press **Pair or replace test Zap** and scan one real UTF-8 NDEF Text tag. Use a different real NDEF tag for unauthorized cases.
8. Press **Scan Zap to start or stop** and complete every scenario in `evidence/DEVICE_MATRIX.md`, including default battery mode, unrestricted battery mode, reboot, natural Doze, eight-hour overnight, service death, Force Stop, and uninstall.
9. Run the instrumentation APK on-device only as supporting evidence. It cannot replace any physical matrix cell.

Collect screen recordings and redacted system/log evidence. Do not capture a raw Zap record or unrelated foreground package history. Record model, full OS build, security patch, battery mode, tester, prototype commit, and exact alternate entry paths.

## Gate interpretation

Every current device cell is `BLOCKED`, which counts as `FAIL`. Code review or green CI proves only that the candidate is buildable and matches its declared static boundary. It cannot establish interception timing, OEM durability, escape safety, Google Play acceptance, or the Android product promise.

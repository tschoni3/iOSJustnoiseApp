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

The Zap hero copy and semantic colors use the same native mapping as the permission-free
UI proof, compiled from `../android-shared/`. Contract tests reject copy/color drift,
and the active scan action uses the shared Zap-stop yellow rather than the separate
Noise Rewind lime. Idle, active, and recovery Compose previews are available without
changing real session state. Local state tests keep the primary action and its label
aligned when setup or service recovery is required.

Build with the pinned Gate 0 wrapper:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ../android-gate0/gradlew -p . testDebugUnitTest lintDebug assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk` with the
disposable application ID `com.justnoise.androiddev`. A fresh install has its own
Android sandbox and therefore requires its own one-time local Zap pairing.

Physical NFC/blocking checks follow the
[non-interfering inspection protocol](../android-gate0/README.md#do-not-let-inspection-interrupt-the-blocker).
In particular, do not use `uiautomator dump` to inspect this app while testing its
Accessibility service. Preserve the paired installation and use screenshots instead.

## Unsigned release preflight — 2026-09-02

On Android source commit `dbe2469` (only documentation/checker changes in the
worktree), `assembleRelease bundleRelease lintRelease` completed successfully.
Release lint reported zero errors and 23 warnings. Packaged-manifest inspection
found no debuggable flag or Compose `PreviewActivity`; permissions remained NFC
plus AndroidX's app-scoped signature receiver permission. Packaged Accessibility
metadata remained window-state-only, with content retrieval and accessibility-tool
flags false and no retrieval flags. The unsigned APK passed `zipalign -c -P 16 4`;
this packaging check is not 16-KB-device runtime or native ELF alignment evidence.

- `app/build/outputs/apk/release/app-release-unsigned.apk`: SHA-256 `d9c0640212fff02b145fdcdfdc7f39f45468be2e9a1257f6fbe5b0dc199c8626`.
- `app/build/outputs/bundle/release/app-release.aab`: SHA-256 `1bea5d8de8735892d0a1293393022c70f60eeea1e55ab96c1b745753990707f3`; `jarsigner -verify` confirmed it is unsigned.

These artifacts still use `com.justnoise.androiddev`, version `0.1-integrated-dev`.
They are local packaging evidence, not upload-ready or physically accepted release
artifacts. The subsequently selected production ID is `com.justnoise.app`, with
public name **Justnoise**; Play registration/availability is not yet verified.
External upload signing, release labels and the internal-beta safety decision
remain open. Nothing was uploaded or installed from
these unsigned release artifacts; the phone received only the separately verified
debug cleanup-feedback follow-up described in the Gate 0 evidence.

The pre-device verifier now repeats the release build and checks the actual APK
boundary using `scripts/verify-release-apk.py` and SDK build-tools 36.0.0. It resolves
the optimized Accessibility resource through the APK resource table rather than
assuming the source XML filename survives packaging. Passing this check is not
signing, device-safety, or Play approval.

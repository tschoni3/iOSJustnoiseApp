# Keyboard-window correction candidate

Android-only adapter correction for the September 8 signed-release schedule failure.
The 20:13 alarm started a real session, but focused Chrome address entry remained
accessible until address entry was exited. The exact device event sequence was not
recorded. This candidate addresses the source-backed IME context-loss mechanism;
it is not yet a physical fix PASS.

## Bounded change

- Retain the last app package only when a window-state event has the exact framework
  SoftInputWindow class AND belongs to the currently selected input-method package.
- All other classes, including a keyboard's own settings activity, remain ordinary
  transitions. Protected-package handling wins even over a matching IME identity.
- A cold/unknown previous app stays unknown; screen-off, lifecycle resets, consent,
  recovery and scheduled readiness checks remain unchanged. No polling is added.
- Runtime class metadata is immediately classified and discarded; only the existing
  single package observation remains. No new event type, retrieval flag, node/window
  inspection, text reading, permission, persistence or network access is introduced.
- Shared product behavior/copy/tokens and iOS are unchanged. This is native window
  interpretation, not a new portable session rule or a keyboard-package exemption.

## Verification and remaining scope

Nine pure regression cases cover scheduled starts beneath IME show/hide, active
blocker continuity, protected/unselected transitions, keyboard settings, unknown
foreground, missing/changed IME identity, missing event package and stopped/recovery
eligibility. Local execution and release packaging results will be recorded below.
The old signed snapshot must be preserved. No phone update is authorized by a local
test result; affected A16 acceptance remains required after an approved update.

Required affected device checks: scheduled start while Chrome address entry is
focused (keyboard shown and hidden), target entry with restored keyboard, Home and
Settings/keyboard settings escape, then owner Zap stop. Do not repeat pairing or
unrelated completed feature tests. Unknown OEM window-class variants are not claimed
supported by this exact-class candidate.

References: Android [AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent)
documents source-package/window-state metadata; AOSP [Dialog](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/app/Dialog.java)
sets the runtime class on accessibility events. These support the approach, not
Samsung device acceptance.

## Local results

- Targeted execution: 9 new ForegroundWindowRules tests and 6 existing overlay
  rule tests PASS (zero errors/failures/skips).
- Full mandatory `verify-android-predevice-readiness.sh`: PASS, including contract
  boundaries, Android build/lint/tests and 41 artifact-checker tests. Instrumentation
  APKs were compiled, not run on the owner device.
- Production testReleaseUnitTest/lintRelease/assembleRelease/bundleRelease: PASS;
  292 release tests, zero failures/errors/skips across 35 suites.
- `verify-candidate.py`: PASS, including existing signature/certificate, non-debug
  package/capability checks, APK alignment and AAB structure/native alignment.
- iOS tracked diff remains empty; `git diff --check` PASS. No phone command,
  installation, pairing, permission change, release upload or iOS edit in this turn.

New snapshot: `android-app/build/candidates/1.0.0-1-7c49dce6/` from the repository root.
Version remains 1.0.0 (1), local candidate only, same existing upload certificate.

| Artifact | SHA-256 |
| --- | --- |
| justnoise-1.0.0-1.apk | `1bd44cd67ef91d15aadb1473f522cd95e445ad76dce10d99c9acaecc9dad0d09` |
| justnoise-1.0.0-1.aab | `7c49dce606ebb33b938a0ee235263d13d8917031b68c9c9b79f896f231dc2273` |

The September 8 snapshot (AAB 96e50f7c / APK c2e8c95d) remains intact and is the
last installed candidate. New candidate physical acceptance is NOT RUN; release
acceptance/distribution stays HOLD. Source tests do not prove Samsung emits the
exact class covered by this candidate or that the observed hole is closed.

Local logs: `/private/tmp/justnoise-keyboard-regressions-2026-09-09.log`,
`/private/tmp/justnoise-keyboard-release-build-2026-09-09.log`, and
`/private/tmp/justnoise-keyboard-release-verification-2026-09-09.log`.

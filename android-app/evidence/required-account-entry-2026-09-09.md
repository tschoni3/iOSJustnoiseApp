# Required Android account entry — September 9, 2026

Owner clarified that account access must be mandatory, like the iOS experience.
The preceding `ffb1ec06` install intentionally supported optional local use; that
behavior is superseded by this Android-only correction. iOS is unchanged.

## Implemented boundary

- Product navigation requires both reconciled storage and a signed-in account.
  Closing/backing out of the account screen does not reveal the guest product.
  Account entry is offered automatically only from resumed Main, once per account
  generation; the separate credential screen retains FLAG_SECURE.
- Production Application owns a required-account start permit, initially closed.
  Development Application does not opt into it and remains offline/local-only.
- Both real NFC/manual and scheduled session commits consult that permit under
  the same product transaction lock. It is closed before auth journal mutations
  and opened only for the matching, reconciled SIGNED_IN workspace. No NFC call
  takes an auth/Keystore lock or performs an online token refresh.
- Expired credentials do not block a restored signed-in offline workspace.
  Scheduled occurrences are not consumed while cold local auth restoration is
  pending; a resolved signed-out occurrence is recorded as ACCOUNT_REQUIRED,
  with no later catch-up. Existing timing/grace limits remain unchanged.
- Auth refusal between NFC preflight and commit is not misreported as a storage
  failure and does not set a spurious recovery flag.
- Sign-out and deletion intent close new starts. Existing sessions can still be
  stopped, including upgraded local sessions and unreadable secure auth storage.
  Android Accessibility recovery settings remain reachable from the entry gate.
- Existing unowned modes, schedules and History are preserved, not reassigned to
  an account. They are no longer presented as an operational guest workspace.
- Optional analytics controls are shown on the account page, not the sign-in form.
  No new events, payload fields, consent changes or live telemetry were introduced.

## Verification

- Product contract verifier: PASS, including the new Android-only access fixture.
- Production `testReleaseUnitTest`: **422 tests, 0 failures, 0 errors, 0 skipped**.
  Eleven new real-store/coordinator/UI-policy regressions plus the six-case fixture
  test cover cold/signed-out starts, automatic schedule refusal, cold schedule
  deferral, expired offline restoration, logout, deletion intent, unreadable auth,
  upgraded active guest stop, NFC/auth race and concurrent journal/sign-out commit.
  Existing runtime tests now explicitly assert product denial before/after login.
- Production release lint, signed APK assembly and signed AAB bundle: PASS.
- Full `verify-android-predevice-readiness.sh`: PASS with
  `ORG_GRADLE_PROJECT_kotlin.incremental=false`; includes all three development
  roots and 44 artifact-verifier regressions. The first default run failed to
  resolve the existing Gate0AccessibilityService in integrated release compilation;
  non-incremental compilation passed without deleting files or changing that service.
- An initial malformed-workspace regression exposed an unguarded owner read in
  the new start check. The check now returns denied on unreadable owner metadata;
  the existing regression and final full test suite pass.
- APK/AAB package boundaries, pinned upload signatures, native 16-KiB segment
  alignment and APK zip alignment: PASS via `android-app/verify-candidate.py`.
- `git diff --check`: PASS. Diff of Swift, Xcode, iOS assets and entitlements: empty.

## Preserved signed candidate

Directory: `android-app/build/candidates/1.0.0-1-f3004e27/`.
Public name Justnoise; package com.justnoise.app; version 1.0.0 (1), target 36;
non-debuggable. Existing external upload key reused; no credentials printed.

| Artifact | SHA-256 |
| --- | --- |
| app-release.apk | 34b9a3d5af419b5dfd1d8037f06ba92c4d4994c1e88c98fb595e6fa8dae7db56 |
| app-release.aab | f3004e27da1f0c2b6e57e26d4e9602c96e810b70a45cd7593142116005f43fcb |

`local-candidate-report.json` is preserved beside the artifacts. Copied artifact
hashes were independently recomputed and match the verified outputs.

## Remaining acceptance — not a goal completion claim

This candidate has **not been installed or tested on the A16**. The prior exact
ffb1ec06 installation approval is not reused as approval for this candidate.
No phone mutation, provider change, real account operation, telemetry, upload or
distribution was performed in this slice. Earlier candidates remain preserved.

Next affected device checks: mandatory entry/Back behavior, real sign-in returning
to the product, signed-in offline NFC including cold process, signed-out NFC
refusal, preserved safe stop and automatic schedules. Authentication-screen visual
alignment/onboarding sequence also needs rendered review; JVM policy tests are not
screen acceptance. Reuse prior ordinary start/stop and Google provider evidence.

The full SMART goal remains open: A16 Chrome keyboard-window regression, normal-
settings Samsung Now Bar, broader auth/recovery/deletion/A-B/History acceptance,
consented analytics acceptance and secure offline key backup remain unproved.
Distribution stays HOLD; this is a verified local build, not Play/device GO.

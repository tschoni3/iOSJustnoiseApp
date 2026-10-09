# Required account entry — frozen personal-test candidate

Date: September 12, 2026. Local checks PASS; full release goal and distribution HOLD.
The September 10, 18:00 Europe/Berlin target was missed. This is a test candidate,
not a claim that every original requirement has been achieved.

## Scope of this candidate

- Read-only iOS reference: `justnoiseradio/Core/justnoiseradioApp.swift` routes
  hydration/cleanup, onboarding, activation, auth resolution and sign-in before
  the authenticated container. Android preserves introduction/pairing before
  required account entry and guards the pairing-to-setup continuation as well.
- Android login uses the existing icon, centered headline, dark form background,
  blue primary action and white Google action. Successful required sign-in returns
  to the product; settings visits and unfinished recovery do not auto-dismiss.
- Production manual, direct-NFC and scheduled starts require the reconciled
  signed-in workspace. Offline restored account use and safe active-session stops
  remain available. Unowned local data is preserved without silent migration.
- Direct NFC waits for cold account restoration within the existing 1,500 ms
  service-readiness deadline. Claim cancellation and the final locked start gate
  remain enforced; no network authentication is added to the NFC path.
- iOS files were not edited. The current Git diff for Swift, Xcode project,
  asset-catalog and entitlement paths is empty. No phone data/settings were changed.

## Verified local evidence

- Final production release build: session 48540, exit 0; release tests, lint,
  APK/AAB generation and verifier classpath completed successfully.
- Current release XML: 48 test suites, **431 tests, 0 failures, 0 errors, 0 skipped**.
- Signed artifact verifier: session 84274, exit 0. The saved report records local
  artifact checks PASS, device acceptance NOT RUN and distribution HOLD.
- Final full development verifier: session **87291**, exit **0**, polled to terminal
  completion September 12. Command:

  ```sh
  env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
    ANDROID_HOME=/Users/tj/Library/Android/sdk \
    JAVA_TOOL_OPTIONS='-Dorg.gradle.workers.max=1 -Dorg.gradle.parallel=false' \
    bash scripts/verify-android-predevice-readiness.sh
  ```

  Product contracts, capability/UI/integrated development checks and 44 artifact
  regression tests passed. Existing lint warnings remain; PASS does not mean zero
  warnings. The obsolete parallel run 58453 was cancelled (exit 130) before this
  fresh final-source run. No detector or test was disabled to obtain a pass.
- Independently recomputed hashes of both preserved snapshot files match the
  signed candidate report. No install or upload was performed.

## Deliverables

Directory: `android-app/build/candidates/1.0.0-1-1e0b85fd/`

| File | SHA-256 |
| --- | --- |
| `app-release.apk` | `4d38aa82eb72342c57938a0f399bceba7a144aff3db529dcb25adb797fc9e99f` |
| `app-release.aab` | `1e0b85fd8879cb4ad7f67ac63497016e533f6dc6919b74f0c4be1b56cbf1bf84` |

The same directory contains `local-candidate-report.json`. Application ID
`com.justnoise.app`, version 1.0.0 (1), existing upload signer retained. Signing
credentials remain outside Git; secure offline key backup remains an owner action.

## Next acceptance, without repeating completed tests

ADB currently returns an empty device list. The last verified installed candidate
is ffb1ec06, whose Google login/logout evidence does not validate this new required
entry. This exact replacement candidate still requires installation approval.
Use an in-place update only after observing an idle session; never clear data or
uninstall the owner's paired app.

1. Required entry and existing-data safety: sign-in returns to the main screen;
   signed-out setup cannot bypass entry; existing account data is retained.
2. After a successful account restore, verify offline/cold-start Zap behavior,
   including safe stopping. No requirement to repeat ordinary accepted Zap tests.
3. Verify the affected scheduled Chrome-address-field sequence with the keyboard
   visible/hidden/restored, plus safe native navigation and stopping. The known
   interception gap is not closed by pure-rule tests alone.
4. Check Now Bar with normal Samsung settings and developer override off. A
   lock-screen notification or promoted flag alone is not a Now Bar PASS.
5. Complete the still-missing live registration/recovery, distinct-account History
   isolation and consent-only PostHog checks. Account deletion requires a disposable,
   explicitly authorized account, never unapproved deletion of the owner's account.

Rendered visual acceptance is still pending. Preserve truthful interruption
warnings. The owner's current progress estimate is approximately 85%, explicitly
an estimate rather than measured parity. Freeze this build for personal testing;
fix demonstrated defects rather than expanding scope. Do not infer final GO from
the completed local checks.

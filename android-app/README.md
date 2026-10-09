# Justnoise — local signed release candidate

Latest Play-registration candidate, September 20:
**`build/candidates/1.0.0-1-94741f2a/`**. Google Play reported the earlier planned
`com.justnoise.app` identity as already in use and confirmed the domain-derived
`shop.justnoise.app` identity was available. This candidate changes only the
production application ID and exact Android auth callback. Supabase now allows the
new callback while retaining the existing iOS and prior Android redirects. All 470
production tests, release lint (0 errors, 74 warnings, 2 hints), product contracts,
full pre-device verification, 44 artifact-verifier regressions, signer/package/
permission boundaries, APK alignment and AAB/native checks pass. It is not installed;
use an eventual Play internal-track install for one fresh identity/setup smoke test.

Previous installed candidate, September 20:
**`build/candidates/1.0.0-1-8dc7ba54/`**.
Fixes PostHog opt-out resurrection after failed storage and process restart by
binding optional consent to the app process. New process/legacy grant defaults off;
fresh explicit opt-in is required. Payload and core product state are unchanged.
470 production tests, release lint (0 errors, 74 warnings, 2 hints), contracts and
signed APK/AAB checks pass. Owner-confirmed idle A16 updated in place September 20
at 14:52:07; installed APK SHA-256 matches this candidate and the Accessibility
service is bound. No data clearing or permission change. Live analytics and affected
physical acceptance were subsequently advanced: a real consented PostHog start/end
pair and one owner-controlled opt-out start/end cycle passed their bounded checks;
the latter produced no new events. The later consolidated owner journey confirmed
the Samsung Now Bar, and live account lifecycle/isolation was
skipped by the owner because the required password is not known. Not uploaded. See
`evidence/a16-update-8dc7ba54-2026-09-20.md` and
`evidence/posthog-audit-2026-09-20.md`. Release HOLD.

Previous candidate, September 20: **`build/candidates/1.0.0-1-75b4c6dd/`**.
Owner is away: physical stop/update and device acceptance are explicitly deferred.
Full release lint now passes (0 errors, 74 warnings, 2 hints); triage and technical
handoff are in `evidence/local-release-review-2026-09-20.md`. Candidate contains
checksums and the full lint report. No further phone changes in this follow-up.
Focused fix for the real A16 stale Chrome overlay above Justnoise on a direct
app switch. Build, vital lint, static contracts and signed artifact checks PASS;
not installed or device-accepted yet. Owner physical stop of the active hardware
test is pending before the authorized in-place update. On installed `8b40bd8f`,
the 23:54 automatic Chrome/keyboard start succeeded, but direct own-app navigation
failed. Promoted timer is visible on lock screen with developer override OFF;
exact Samsung Now Bar remains unverified. Release HOLD. See
`evidence/a16-hardware-pass-2026-09-20.md`.

Previous candidate, September 19: **`build/candidates/1.0.0-1-8b40bd8f/`**.
Includes the scheduled-start discovery correction below and removes the remaining
permanent schedule/import success messages. Signed build, vital lint, static
policy/contracts and independent APK/AAB verification pass. Owner-approved in-place
A16 update installed at 23:39:14; installed APK hash matches. Idle Focus, visible
History totals and disabled schedules are retained; removed helper text checked.
No new session was started. Targeted schedule/keyboard acceptance remains pending.
See `evidence/a16-update-8b40bd8f-2026-09-19.md`. Bounded account/analytics
source review found no new defect but does not prove live acceptance. Release HOLD.
See `evidence/connected-boundary-review-2026-09-19.md`.

Previous candidate, September 19: **`build/candidates/1.0.0-1-e553541c/`**.
Moves package discovery before the scheduled-start readiness lease, eliminates
unneeded label loading/sorting from eligibility, and includes helper-copy cleanup.
The previous A16 attempt returned `LEASE_EXPIRED`; its UI receipt and idle state
are confirmed. Original lease/safety gates remain intact. Signed build, vital
lint, static contracts and APK/AAB verification pass. Not installed; affected
hardware retry awaits owner approval. Release HOLD. See
`evidence/schedule-discovery-order-2026-09-19.md`.

Previous candidate, September 19: **`build/candidates/1.0.0-1-766a1b5b/`**.
Removes owner-rejected permanent helper paragraphs and historical warning copy
from normal product screens, retaining underlying flags, action failures,
permission recovery and consent. Includes the bounded readiness diagnostic.
Signed APK/AAB and build-integrated vital lint pass; not installed or visually
accepted. No JVM/emulator rerun. Installed A16 version is diagnostic `cfa5e637`;
the scheduled-start failure remains open. See
`evidence/helper-copy-removal-2026-09-19.md`.

Previous candidate, September 19: **`build/candidates/1.0.0-1-25098600/`**.
Owner-requested Android UI-copy cleanup is installed in place on the A16 at
22:31:27; installed APK hash matches. Schedule execution diagnostics removed,
History safety warnings retained in plain language, Live Update settings copy
simplified. Schedule/History/settings rendering checked on the actual phone.
Build/signature/package checks pass; no emulator or JVM suite rerun for this
presentation-only change, per owner hardware-testing direction. See
[copy-cleanup evidence](evidence/ui-copy-cleanup-2026-09-19.md). Full release HOLD.

Previous candidate, September 19: **`build/candidates/1.0.0-1-7ee1df3c/`**.
Adds owner-approved, explicit one-time import of unowned local modes, app
selections, disabled schedules and History into the signed-in account. Existing
account data is preserved; deferral keeps import available in Account settings.
**452 release JVM tests, release lint, full development verifier, two real-SQLite
emulator tests and signed APK/AAB checks pass.** Subsequently installed in place
on the owner's idle A16 September 19 at 22:22:47 following the real-hardware test
request; installed APK hash matches. Not distributed. After owner-confirmed
import, the A16 shows Focus with 1 selected app, 4 History sessions with existing
warnings, and the disabled Release Check schedule linked to Focus. Basic import
visibility is physically verified; remaining account/release checks stay open.
See [import evidence](evidence/local-data-import-2026-09-19.md).
The full release goal remains HOLD. No iOS source was edited by this work; a
separate existing/concurrent Shield source change was preserved untouched.

Previous candidate, September 12: **`build/candidates/1.0.0-1-1e0b85fd/`**,
required account entry with refined login presentation, guarded setup routing and
bounded cold-NFC account restoration. **431 release JVM tests, release lint, full
development verifier and signed package checks pass.** Frozen for owner testing;
installed in place on the owner's A16 September 19 at 21:48:40 after explicit
approval. Installed APK hash matches; existing data-directory inodes and
Accessibility enablement are retained. Required AccountActivity opens; the owner
subsequently used Google sign-in and the product screen was observed. Its mode
picker was empty, motivating the explicit local import above. Remaining device
acceptance is pending. Not distributed.
See `evidence/account-entry-refinement-2026-09-12.md` for hashes, acceptance
steps and limits. The full release goal remains HOLD.

**Historical source checkpoint, September 9:** native account Settings/Activity, real auth/workspace
coordination and separate opt-in PostHog capture now compile, with 410 passing
release JVM tests. This is not live provider/device acceptance or a completed
connected release. Signed consent-fix snapshot `build/candidates/1.0.0-1-ffb1ec06/`
passes local APK/AAB verification but remains distribution HOLD. See
[current implementation evidence](evidence/connected-goal-audit-2026-09-09.md).
The owner-approved in-place update to this exact snapshot succeeded September 9
at 13:05:41. Its installed APK hash matches; visible mode/selection/History are
retained. Real account acceptance is pending owner-entered sign-in. See
[installation evidence](evidence/connected-resume-2026-09-09.md).
Dated build statements below retain their historical scope.

**Current implementation, September 9:** owner-approved Live Update-only release
extension, using the iOS-style headline, timer, active-mode name, Zap artwork and
lime accent. No ordinary fallback. See
[Live Update evidence](evidence/live-update-release-2026-09-09.md) for the final
candidate/checks and Samsung limitations. Snapshot `build/candidates/1.0.0-1-db0c2798/`:
296 release tests, lint and signed artifact checks PASS; not installed or physically
accepted. Sign-in/PostHog remain pending.

**Previous local candidate, September 9:** keyboard-window correction built and
verified (292 release tests plus full Android/package checks). Snapshot
`build/candidates/1.0.0-1-7c49dce6/`; not installed or physically accepted. The
September 8 installed candidate and hashes below remain preserved. See
[correction evidence](evidence/keyboard-window-fix-2026-09-09.md). Distribution
remains HOLD; local success does not close the Samsung Chrome interception gap.

Owner-authorized build-only milestone, September 8, 2026. **Not approved for
installation, upload or distribution.** Internal-beta/full safety decisions remain
HOLD. No physical result is changed by successful packaging.

Subsequent September 8 owner approval: "ok done" authorized separate installation
on the identified A16 and temporary development-package disablement, preserving
its data. This supersedes only the installation prohibition above. Pairing and
Accessibility enablement remain explicit owner actions; upload/distribution and
safety decisions remain HOLD. Original build reports retain their historical scope.

## Architecture

This Gradle root has one `app` module, application ID `shop.justnoise.app` and
release branding **Justnoise**. It compiles the canonical integrated UI, Gate 0
capability engine and native shared mapping by source reference. There are no
copied Kotlin/Java implementations here. The development root and its existing
`com.justnoise.androiddev` outputs are preserved. Moving source directories later
is not required to build this candidate.

Only the release variant and its JVM unit tests are enabled. No debug/cleanup
source set, full Compose tooling dependency or device instrumentation is imported.
Gradle install tasks explicitly fail. Release overlays replace public branding,
request the approved promoted-notification permission and alias the existing Zap
artwork. The capability/development release manifests retain their smaller
permission sets; runtime sources remain canonical. iOS stays unchanged.

## Original September 8 build and verification

- Version **1.0.0**, version code **1**, min SDK 31 / target SDK 36.
- `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, `bundleRelease`: PASS.
- **283 release JVM tests**, zero failures/errors/skips. These reuse existing
  canonical engine/UI tests; they are not 283 new tests. The separate permission-free
  UI prototype's 15 tests are not part of this production module.
- Required development regression verifier PASS, including 41 artifact tests.
- Signed APK: non-debuggable, not test-only, exact reviewed components/permissions,
  packaged Accessibility XML checked, v2 signature verified against the external
  upload certificate. APK ZIP alignment check PASS with 16-KB page option.
- Signed AAB: bundletool 1.18.3 validates structure; strict JDK signature verification
  passes against the external truststore and previously generated certificate pin.
  Production name/ID/version checked. All four packaged native ABI libraries have
  16-KB PT_LOAD alignment/congruent offsets; AAB configuration requests 16-KB native
  alignment. This is not a physical 16-KB runtime test or a Play-generated APK test.
- Negative build check: removing only the signing environment variable makes
  `assembleRelease` fail at configuration, without touching the real credentials.
- Initial build setup needed the installed plugin's `HostTestBuilder` UnitTest
  type enabled explicitly; enabling all host tests also enabled an unconfigured
  component. The final configuration enables only release JVM unit tests and built
  successfully. No application runtime workaround was introduced.

## Original September 8 candidate snapshot

Files are preserved under `build/candidates/1.0.0-1-96e50f7c/`, outside Git:

| File | SHA-256 |
| --- | --- |
| `justnoise-1.0.0-1.aab` | `96e50f7c63287acdd54ff531ca92f8e79add078cf4ddf75cc84ee150995608b5` |
| `justnoise-1.0.0-1.apk` | `c2e8c95d37ecbf8ac5f05b0289f7bd12f30f2953c9d3d932bc2f95ddc0d27722` |

`verification.json` and `verification.log` accompany the snapshot. Do not overwrite
it or confuse it with a later file in `app/build/outputs/`.

Public upload-certificate SHA-256:
`2b51da4e2d3fc6345480db87fdc15a185b12a1480358faf8e00e6d2991b26ebd`.
This is the local upload identity, not a Google Play app-signing certificate.

## Signing and rebuild

The encrypted RSA-3072 upload keystore and signing properties were created under
`~/.config/justnoise/signing/upload-2026-09-08/`, outside the repository. The directory
is owner-only (0700), and the keystore/password file are 0600. No password is
printed or passed as a literal signing-tool argument. **A secure offline backup
of the signing material is still required before distribution.** Never regenerate
the key for an ordinary rebuild. `create-upload-key.py` refuses existing destinations.

From the repository root, using the installed SDK/JDK and the existing pinned wrapper:

```sh
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JUSTNOISE_SIGNING_PROPERTIES="$HOME/.config/justnoise/signing/upload-2026-09-08/signing.properties"
spikes/android-gate0/gradlew --no-daemon -p android-app testReleaseUnitTest lintRelease assembleRelease bundleRelease writeVerifierClasspath
python3 -B android-app/verify-candidate.py
```

The verifier currently uses macOS `xcrun` for the installed LLVM tool. It does not
build, sign, install or upload. Its bundletool dependency is host-only, not shipped.
Credential-free CI runs `verify-layout.py` plus the existing development verifier;
it does not possess the upload key or claim a signed production build.

Logs: `/private/tmp/justnoise-production-build-2026-09-08.log`,
`/private/tmp/justnoise-production-verification-2026-09-08.log`,
`/private/tmp/justnoise-production-regressions-2026-09-08.log`, and
`/private/tmp/justnoise-production-missing-key-2026-09-08.log`.

The A16 was not touched. The next boundary is explicitly authorized separate-release
device acceptance, keeping development pairing/data intact and preventing competing
services/NFC routing. Existing foreign-tag/rapid-repeat evidence gaps remain visible
in the decision record. Store/privacy work stays paused at the owner's request.

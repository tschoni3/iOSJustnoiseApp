# Justnoise — local signed release candidate

October 11 Settings cleanup: next local candidate **1.0.13 (15)** groups the main
screen into Account, Your Zap and Help, with More settings for device/data options.
Emergency and Now Bar stay directly reachable. Tutorial/contact become compact
rows; support email fallback appears only after a failed handoff; version is a
quiet footer. No session/account/permission/storage behavior or iOS changes.
Physical visual acceptance and distribution of 15 remain pending.
Local verification: 551 JVM tests / 70 suites, no failures/errors/skips; Release
build succeeds and lint has zero errors (106 warnings, three hints). The changed
emulator UI tests compile; no phone instrumentation or tests were performed.

Owner has accepted all three 1.0.12 (14) additions. Its internal Play publication
and in-place A16 update are recorded in the frozen candidate HANDOFF. Those passed
checks must not be repeated for this presentation-only Settings cleanup.

October 11 next candidate: **1.0.12 (14)** adds the source-matched Zap image to
the blocked screen, start-only long press on Home's Zap artwork and Emergency
Unzap in Settings. Five installation-owned unlocks use confirmation and the
existing atomic stop/completion path; no token reset on account changes, deletion
or consent withdrawal. History stays reachable while active so Emergency is not
hidden. Native settings/recovery remain available; no permissions, iOS edits,
owner-data clearing or repeated passed phone tests. New-path physical acceptance
and upload/install remain pending. Published/installed 1.0.11 (13) is unchanged.

Local verification for 1.0.12 (14): 547 JVM tests across 69 suites, no failures,
errors or skips; release lint has zero errors, 96 warnings and three hints.
Signed APK/AAB checks, shared contracts, layout and the full pre-device verifier
pass. New emulator UI checks compile but were not executed on the owner's A16.

October 11 bundled follow-up: **1.0.11 (13)** adds best-effort return after an
explicit Accessibility setup action, automatic selection of a newly saved UI mode,
and a clearer typography hierarchy for permission/video guidance. Creation and
selection share one guarded storage commit; editing preserves selection and never
starts a session. Return is process-only, once, within two minutes, from recognized
Settings on an unlocked idle phone with current consent. If Samsung denies launch
or emits no qualifying event, manual return still advances setup automatically.
No automatic permission grant, iOS change, data reset or repeated physical tests.
Published to the existing internal Play track October 11 at 13:45 Europe/Berlin:
Active, available to internal testers, one version code. At 13:53 the A16 updated
in place through Play: version 1.0.11 (13), installer com.android.vending. No data
reset, permission change or passed core-test repeat; new physical acceptance is
still pending.
See `evidence/setup-return-mode-selection-2026-10-11.md` and its frozen HANDOFF.

October 11 guided-first-session candidate: **1.0.10 (12)** builds with 532 JVM
tests / 64 suites, no failures/errors/skips, zero release-lint errors (94 warnings,
two hints), and verified signed APK/AAB packages. Permission setup has a static
two-step visual guide and safe shortest-supported Settings routing. Existing
automatic continuation uses real permission state. Ready, idle Home shows the
owner's unchanged local video once, with brief mode/Zap instructions and one
button: Choose a mode, or Got it when a mode is selected. No automatic permission,
mode/session creation, data clearing or iOS changes. It was published to internal
testing and installed through Play on the A16 at 13:19/13:21 Europe/Berlin; the
visual first-run/video acceptance remains pending. See
`evidence/first-session-guide-2026-10-11.md` and the candidate HANDOFF.

October 11 account UI release: **1.0.9 (11)** builds successfully with 524 JVM
tests / 62 suites, no release-lint errors (92 warnings, two hints), and verified
signed APK/AAB packages. Google is the primary account entry; email forms appear
after choosing email. Accessibility setup copy is shorter, without removing the
disclosure or manual consent. Distribution/install evidence belongs to the new
candidate HANDOFF, separately from these local checks. iOS remains unchanged.

The earlier SMTP blocker below is resolved: auth.justnoise.shop is verified,
Resend delivered the owner's fresh confirmation email, and Supabase recorded
confirmation. The link was opened on a different device; that does not prove an
Android PKCE callback or email/password-to-Home acceptance. Owner reports Google
signup/sign-in works. No owner account, pairing or app data was erased.

October 11 RC1 technical handoff: **1.0.8 (10)** is frozen for final owner testing.
The new Be Right Back -> Android Home behavior is now owner-reported PASS.
Curated source snapshot/checksums accompany the signed binaries. No new runtime
change, upload or repeated core test was needed. Remaining first-run/account and
historical interruption limits, plus out-of-scope public-distribution blockers,
are consolidated in `evidence/release-candidate-closure-2026-10-11.md`.

October 11 signed Year overview candidate: **`build/candidates/1.0.8-10-f2f046d2/`**.
All 365/366 days fit without scrolling; real day details remain available by dot
or Choose day. Production build, 512 JVM tests / 61 suites, zero-error release
lint, signed APK/AAB verification, production layout and product contracts pass.
Owner authorized internal Play publication and A16 Play Store update. See the
candidate HANDOFF for subsequent distribution/install and visual evidence;
local checks alone do not prove device acceptance. iOS remains unchanged.
Published October 11 at 00:30 Europe/Berlin: Play shows Active, available to
internal testers, one version code. At 00:35, A16 updated through Play to 1.0.8
(10), installer `com.android.vending`. Existing selected mode/app remain visible.
At 00:36, the full 2026 grid fits on the A16 without scrolling; tapping October 10
opens the existing day details. Phone left on Year for owner testing. No data
clearing, uninstall, permission change or passed core-test repeat. Evidence and
remaining untested size/date-picker boundaries are recorded in HANDOFF.

October 11 source-only follow-up: Year History is now a non-scrolling 365/366-dot
overview fitted to the remaining viewport, with a full-size Choose day alternative.
Ninety-day scrolling, calendar aggregation and stored records are unchanged. This
is not in the published/installed 1.0.7 (9) snapshot below; a new version code and
signed bundle are required before the next internal Play update. iOS unchanged.
Verification: 512 production unit tests / 61 suites pass; production release lint
has zero errors (93 warnings, two hints). Product contracts and full Android
pre-device verifier pass. The isolated 365/366-dot rendered check compiles but was
not run: no emulator is connected. No owner-phone instrumentation or installation.

Latest bundled candidate, October 10: **`build/candidates/1.0.7-9-f95857f5/`**.
**Be Right Back** targets Android Home without stopping a session. Schedule times
omit raw zone identifiers; scan/account messages no longer use technical reload,
NDEF or UTF-8 wording. Account retry is conditional rather than a permanent
diagnostic control. Real errors and required consent remain. 509 production tests
/ 60 suites, signed artifact checks, contracts and full Android pre-device checks
pass; release lint has zero errors, 93 warnings and two hints. iOS unchanged.
Owner authorized internal Play upload and A16 update. The version-code-8
intermediate artifact was removed from the release draft before publication;
never install/publish it. Final distribution/install evidence follows in HANDOFF.
Published October 10 at 23:48 Europe/Berlin: Play shows Active, **1.0.7 (9) -
Internal**, Available to internal testers, one included version code. Updated A16
through Play at 23:49: versionName 1.0.7, versionCode 9, installer
`com.android.vending`. Existing selected mode/app and schedule are visible; raw
zone suffix is absent on the real schedule screen. No data deletion or permission
change. The new Home action still needs one affected physical check; installation
alone does not establish that behavior. Evidence is in the candidate HANDOFF.

Latest final blocker candidate, October 10: **`build/candidates/1.0.6-7-d2f08d5b/`**.
Only **Be Right Back** is shown; Help and technical overlay controls are removed.
Primary navigation, native Home/Back and Settings recovery remain available.
504 production tests / 58 suites and signed release artifact verification pass;
product contracts and full Android pre-device checks also pass. Owner authorized
internal Play upload/update. Published October 10 at 23:22 Europe/Berlin: Play shows
**Active — 1.0.6 (7) - Internal — Available to internal testers**. Only version 7
is included; supported device counts are unchanged. Two non-blocking warnings
concern deobfuscation mapping/native symbols. Proof: candidate
`play-internal-active.jpg`. After the owner stopped the session, A16 `RFGYC3DYKBR`
was updated through Play at 23:27 Europe/Berlin: passive package inspection shows
version 1.0.6 (7), installer `com.android.vending`. Normal idle Home opens with
the existing selected mode and one app retained. No data reset, permission change,
new session, intrusive instrumentation or public rollout. The new blocker still
requires one affected physical visual check; installation is not that evidence.
The previous `888be688` candidate remains immutable and must not be uploaded.

October 10 source follow-up: the blocker now has only **Be Right Back**, without
the Help link or technical panel. It still opens Justnoise without ending a
session; native navigation and recovery in Settings remain available. This change
is not included in the immutable `1.0.6-7-888be688` snapshot below; a replacement
signed candidate must be built before uploading it. Product contracts, the full
Android pre-device tests/lint/build verifier and scoped diff checks pass. iOS is
unchanged. No phone install or Play upload was performed for this follow-up.

Latest signed local candidate, October 10: **`build/candidates/1.0.6-7-888be688/`**.
Normal blocker: Keep going and small Need help? action; technical escape controls
are collapsed behind Help, with native Home/Back unchanged. Includes the iOS-label
NFC gate described below. 504 production tests / 58 suites pass; lint has zero
errors, 94 warnings and two hints. Signed APK/AAB artifact verification, contracts
and full pre-device checks pass. NOT uploaded or installed; Play/A16 remain 1.0.5
(6). The new blocker/Help and NFC gate require affected physical acceptance.

Bundled NFC follow-up, October 10: initial/replacement NFC pairing now
requires the unchanged iOS compatibility labels, in addition to Android's exact
local pairing. Unknown labels cannot start new NFC sessions; an already paired
legacy label retains stop-only escape. 503 production JVM tests / 58 suites pass
(including six new NFC regressions); release lint remains zero errors, 94 warnings
and two hints at that source-only checkpoint. Now included in the signed local
1.0.6 (7), but NOT in the immutable 1.0.5 (6) candidate or installed Play app.
See `evidence/nfc-label-parity-2026-10-10.md`; iOS is unchanged.

Latest local delivery, October 10: **`build/candidates/1.0.5-6-c3355e1b/`**.
Removes mandatory first-mode creation and prevents missing-mode presentation from
masking revoked Accessibility access. 497 production JVM tests / 57 suites pass;
release lint has zero errors, 94 warnings and two hints. Signed APK/AAB artifact
checks, contracts, full pre-device verification and one new isolated rendered UI
regression pass. Fresh Samsung first-run acceptance and the historical interruption
trigger remain open. See the new HANDOFF.

**Play internal distribution, October 10 at 20:13 Europe/Berlin:** the exact
`1.0.5-6-c3355e1b/app-release.aab` was accepted and published through the Codex
in-app browser. Play shows **Active — Latest release: 1.0.5 (6) - Internal —
Available to internal testers**. Screenshot: candidate `play-internal-active.jpg`.
Two non-blocking warnings concern missing deobfuscation/native symbol files;
supported device counts are unchanged. No public release or tester membership
change. At 20:18 Europe/Berlin, the connected A16 `RFGYC3DYKBR` was updated
through its Play Store: passive package inspection confirms versionName 1.0.5,
versionCode 6, installer `com.android.vending`; screenshot
`a16-play-update-complete.png` shows Open rather than Update. No uninstall,
data clearing, permission change or acceptance-test repeat was performed.
Retention of visible modes/History and fresh onboarding remain to be accepted.

**Play internal distribution, October 10 at 18:07 Europe/Berlin:** the exact
`1.0.4-5-a0da8b20/app-release.aab` below was accepted and published to the existing
internal track through the Codex in-app browser. Play shows **Active — Latest
release: 1.0.4 (5) - Internal — Available to internal testers**. No public release
or tester membership change. Affected A16 sheet/setup acceptance remains open;
see the candidate HANDOFF and `play-internal-active.jpg`. The local-build account
below records the earlier boundary and is superseded only for distribution.

Latest bundled candidate, October 10: **`build/candidates/1.0.4-5-a0da8b20/`**.
Includes portrait-only phone UI, one stable presentation for all four bottom sheets,
and guided app-blocking/scheduled-start setup outside the normal product screens.
Parent-sheet drag is disabled so child scrolling cannot move/rebound the editor;
Cancel, Back and scrim dismissal remain available, with saving guards preserved.
Home has no setup/recovery headlines or permanent helper paragraphs. Schedules have
no setup cards, permanent Refresh button or historical execution diagnostics.
Missing capability and genuine interruption use a separate guided entry/recovery
surface; an unconfirmed session is never presented as protected and can still be
ended with the paired Zap. Permission grants stay manual, prior grants are skipped,
and optional schedule refusal returns to manual Zap use. History and execution
records, safety flags and the native session/alarm engines are unchanged.
489 production JVM tests/56 suites pass with no failures/errors/skips. Release lint
has 0 errors, 94 warnings and 2 hints; signed APK/AAB, alignment/package checks,
contracts and the full pre-device verification pass. Updated emulator-only UI
fixtures compile but were not run. No A16 was connected: new scrolling, onboarding
and recovery presentation are **not physically verified**. The underlying cause of
the owner's photographed interruption is also not diagnosed from screenshots.
No phone data/settings were changed, and nothing was installed or uploaded.
iOS was untouched, including preservation of the pre-existing Shield source diff.
This snapshot supersedes the earlier unuploaded 1.0.4 snapshot below; see HANDOFF.

Latest local candidate, October 10: **`build/candidates/1.0.4-5-b0ae7dc6/`**.
The Accessibility button first attempts direct navigation to Justnoise's service
switch, with standard Settings fallback if the action is unavailable or denied.
The disclosure is 47 words with two numbered activation steps. Permission grants
remain manual; refusal, account/session recovery and automatic continuation on
return are preserved. 475 production JVM tests, release lint/build, contracts,
layout and signed artifact checks pass. No connected A16 was available to verify
Samsung's direct route or rendered layout. This candidate is **not installed or
uploaded**; the prior 1.0.3 (4) release remains the available internal test version.
See its HANDOFF for the exact checksums and the single affected phone check.

Previous UX update, October 10: **`build/candidates/1.0.3-4-f2dd0069/`**.
First-time Android setup now asks for a real saved/selected mode before a concise
standalone Accessibility disclosure. One affirmative “Agree & open Android Settings”
action replaces the duplicate checkbox and button; returning with the service
enabled advances automatically. Declining exits, and active-session/recovery routes
remain reachable. The AAB is signed with the existing upload key and has SHA-256
`f2dd00699be94dc55d977795a236a41774e0c07961ebf37b086adede3a53aacd`.
Production unit tests, release lint and artifact checks passed. This candidate is
available to internal testers: Play Console confirmed active 1.0.3 (4) on October 10.
New-screen A16 visual acceptance remains outstanding. The iOS source was not changed by
this Android work.

Previous local update candidate, October 10: **`build/candidates/1.0.2-3-e2e07c45/`**.
It removes the duplicate welcome/recovery dialog, presents a focused Android NFC
scan dialog, reduces repeated activation copy, and requires the separate
Accessibility disclosure/manual enablement step before an inactive user reaches
the product screen. Declining setup exits without granting consent; an active
session or recovery remains accessible. The normal Home no longer carries the
Accessibility disclosure helper and redundant setup action. Required account
sign-in remains in place. The existing external upload key signed the APK/AAB;
no key was regenerated. Production unit tests, release lint, product contracts,
full Android pre-device verification, and signed artifact checks pass. The AAB
SHA-256 is `e2e07c4561a84183a0e7c6e9b631e300ea84bef3459d066bcb2a9f4b849dc3f3`.
Google Play accepted this exact bundle as **1.0.2 (3) – Internal** on October 10;
the internal track shows it active and available to testers. It has not been
installed or visually accepted on the A16 yet. The candidate's verifier JSON
records the earlier build-time boundary, before the Play upload.

Previous internal-track update, October 10: **`build/candidates/1.0.1-2-d561187a/`**.
This was uploaded to the Play internal track and is the version shown in the
owner's three October 10 onboarding screenshots. Its local HANDOFF predates that
upload and therefore describes its status at creation time.

Latest Play-registration candidate, September 20:
**`build/candidates/1.0.0-1-94741f2a/`**. Google Play reported the earlier planned
`com.justnoise.app` identity as already in use and confirmed the domain-derived
`shop.justnoise.app` identity was available. This candidate changes only the
production application ID and exact Android auth callback. Supabase now allows the
new callback while retaining the existing iOS and prior Android redirects. All 470
production tests, release lint (0 errors, 74 warnings, 2 hints), product contracts,
full pre-device verification, 44 artifact-verifier regressions, signer/package/
permission boundaries, APK alignment and AAB/native checks pass. Google Play
accepted this exact AAB as release **1.0.0 (1) – Internal** for app
`4972093831482851244` on internal track `4701314183475386469`. The track is active,
and the owner reported the Play-installed A16 smoke test successful. The
`verification.json` file inside the immutable candidate directory records the
earlier build-time distribution/device boundary; the later Play result above and
`evidence/final-closure-audit-2026-09-20.md` are authoritative for distribution
status. Do not repeat the unchanged hardware flows.

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

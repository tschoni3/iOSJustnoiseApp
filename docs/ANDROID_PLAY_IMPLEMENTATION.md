# Android iOS-style MVP → Play internal-test upload

Owner-approved implementation plan, 2026-09-06. This is execution tracking, not a
release approval. The original architecture-preparation goal is separate/completed.

## Defined delivery goal

Deliver **Justnoise for Android as a real, native four-feature MVP that matches the
agreed iOS Release experience**, while leaving the shipped iOS app completely
unchanged. Finish with a verified, non-debuggable, signed Android App Bundle for
`shop.justnoise.app`, ready for **Google Play internal
testing**, plus the complete internal-test submission/handoff materials. Actual
upload or rollout is a separately authorized action.

The four features are:

1. Select and save multiple eligible apps in named modes, with Save/Cancel and
   protected system/escape-app exclusions (named modes approved September 7).
2. Tap the paired Zap to start and stop real sessions using the last explicitly
   selected mode; missing/empty modes never silently fall back to another.
   Preserve unobtrusive direct taps and safe Android navigation/recovery.
3. Create, edit, enable/disable and delete schedules that start automatically and
   end with a Zap tap, each resolving its own saved mode ID; preserve explicit alarm access and no missed-start catch-up
   after Force Stop. Disabling/deleting a schedule does not end an active session.
4. View real preserved session History in the agreed 90-day/year calendar and day
   details, with accurate session-duration allocation and visible interruptions or
   timing uncertainty rather than a claim of verified continuous protection.

### Definition of done

- All agreed MVP screens and interactions match the frozen iOS Release references,
  including onboarding/pairing, Zap idle/active, selection, schedules, History,
  Settings, completion and blocker. Only the documented Android differences apply.
  Compact/standard layouts, enlarged text, reduced motion and A16 navigation pass.
- One canonical native Android application implements real persisted state, with
  UI state/actions separate from NFC, restrictions, persistence and alarm adapters.
  No preview reducers, fake records or duplicated production/session engines stand
  in for working features. Existing session identity/history is preserved.
- Targeted tests, contracts, lint, builds and package checks pass. The essential
  existing-A16 safety checkpoint supports internal-beta GO, and the separately
  installed signed release passes affected checks without erasing development data
  or leaving competing services/NFC routes. No additional phone purchase is required.
- Final signed AAB, checksum, version/identity and verification evidence are
  delivered. Signing secrets stay outside Git. Live account eligibility, package
  registration and current upload requirements are checked, not assumed.
- Release notes, icon, real screenshots, tester/reviewer instructions and working
  feedback channel are ready. Privacy publisher/contact/URL and its in-app entry
  are real, and Accessibility disclosure/declaration/video plus workable physical-
  Zap reviewer access are prepared for the final release.
- iOS source, project, extensions, entitlements and assets remain unchanged.

**100% means all of those agreed MVP/release checks are satisfied.** It does not
mean full iOS feature parity, public launch, or guaranteed Google approval. Accounts,
cloud sync, Capture, widgets and weekly insights stay out. The previous named-mode
exclusion was superseded by the owner's September 7 approval.
The planning estimate remains five focused iterations plus one possible correction
iteration, not a guaranteed timetable or a completion claim.

This definition records the destination; it does **not** mark Android delivery
complete. The evidence-backed implementation status and open dependencies below
remain authoritative.

## Current execution checkpoint — 2026-09-07

The owner subsequently approved real Android named modes. The new bounded contract
is [`android-modes.v1.json`](../product/behavior/android-modes.v1.json), with native
rule/codec fixtures and unchanged iOS/shared-fixture baselines. The integrated
development app now preserves legacy `local-default` targets as **Noise**, provides
explicit mode selection and per-schedule references, rejects referenced deletion,
and keeps active snapshots/History/NFC authorization intact. See
[`ANDROID_NAMED_MODES.md`](ANDROID_NAMED_MODES.md) for implementation and verification.
The tested debug APK was subsequently installed in place on the A16, and the owner
created **power**, then confirmed both scheduled and manual Zap sessions work.
The named-mode record distinguishes direct observations from owner-reported
acceptance. Do not repeat those ordinary feature flows. This does not close the
distinct release/safety gates below.

The owner paused Live Updates/Now Bar and authorized proceeding toward the **Play
internal-track upload**, not a public release. Nothing in the notification feature
was removed, and iOS remains read-only. Read-only Console inspection still shows
identity-document review pending, phone verification dependent on it, and **Create
app disabled**. No app record, upload, signing key or tester invitation was created.

The existing AAB is unsigned and still uses `com.justnoise.androiddev`; production
signing/identity is not configured. The smaller A16 safety decision remains HOLD
for the distinct real rejected/rapid-repeat NFC and separate-install cleanup/fresh
consent evidence, plus final review. Existing clean sessions/schedules, held-Zap,
navigation and recovery evidence are reused, not requested again. See the updated
[internal-upload handoff](ANDROID_PLAY_INTERNAL_UPLOAD.md) for exact current
artifact identity, open prerequisites and the owner's upload authorization.

The latest local preflight after the publisher/contact, system-description and
cleanup-variant updates completed successfully: 237 JVM tests with no failures,
errors or skips, debug/release lint and development builds, 36 artifact-checker
tests, and all product contracts. No physical instrumentation or installation was
performed. The newly built AAB remains unsigned/development-ID, verified after the
build; it is not a signed candidate and does not close the remaining physical gate.

The owner subsequently supplied **Justnoise Media** as publisher and
**hello@justnoise.shop** for privacy and tester feedback. These replace the
publisher/contact placeholders in the Android privacy draft, in-app text and
internal-upload handoff. The public Android policy URL remains unpublished, and
business-registry/email-delivery verification is not claimed. This text-only slice
does not change the phone, iOS, session behavior or the paused Now Bar work.
The new local JVM case keeps publisher/contact copy consistent across resources
and the declaration draft while retaining the unpublished-policy warning. Verification
Publisher-only log: `/private/tmp/justnoise-publisher-readiness-2026-09-07.log`.
Latest full verification:
`/private/tmp/justnoise-release-preparation-final-2026-09-07.log`.

The next release-preparation slice adds local reader-facing
[privacy](play/ANDROID_PRIVACY_POLICY_DRAFT.md),
[tester](play/INTERNAL_TESTER_GUIDE.md) and
[reviewer/recording](play/ACCESSIBILITY_REVIEW.md) drafts. No public policy, video,
review tag or invitation is claimed. Existing Shopify connection does not match
Justnoise, so no external publication was attempted. A misleading active-only
phrase in Android's service description is corrected to the already disclosed
while-enabled temporary foreground observation; runtime/v4 consent is unchanged.
Obsolete roadmap instructions to rebuild the MVP or add unapproved backend/Capture
were removed. Named modes were excluded at that checkpoint and subsequently approved
on September 7 above. The real remaining safety
checkpoint and production-promotion boundary are unchanged.

An isolated **Justnoise Cleanup Check** APK is now built locally from the same
implementation, under `com.justnoise.androiddev.cleanupcheck`. It is test-only,
debug-signed and has its direct NFC entry disabled so it cannot compete for Home
tag dispatch. Three new source-boundary tests and cleanup-variant lint pass; actual
manifest/resources and signature were inspected. No installation or device cleanup
occurred. The remaining cleanup/reinstall exercise still requires owner coordination,
then separate release validation; this preparation is not internal-beta GO.

## Fixed boundaries

- iOS source, project, entitlements and assets are read-only. Build/capture its
  existing Release configuration; Debug's Capture surface is not the reference.
- Four real local features: selected apps, paired-Zap sessions, schedules, History.
  Android History gains the approved iOS-style 90-day/year grid. No accounts,
  cloud sync, widgets, Capture or weekly insights. Named-mode management is now
  included as the approved September 7 app-selection extension.
- Development ID stays `com.justnoise.androiddev`. Release ID is the Play-confirmed
  available `shop.justnoise.app`, with one-time fresh setup/pairing.
  Preserve the development app/data; do not imply cross-package data migration.
- Production promotion follows the smaller A16 beta GO; public full-matrix approval
  is separate. Actual Play upload requires separate authorization.
- Use the owner-supplied publisher **Justnoise Media** and contact
  **hello@justnoise.shop**. The remaining public-policy URL placeholder is allowed
  during implementation, never released as a working privacy link.

## Iterations and acceptance

| Iteration | Deliverable | Current state |
| --- | --- | --- |
| 1 | Freeze Release reference captures and screen checklist; regression for readiness clock ordering; investigate interrupted History | Three Release captures saved; clock regression and bounded current-build clean physical completion pass; authenticated references remain open; old warning cause unknown/preserved |
| 2 | Real Zap/navigation, selected-app sheet, onboarding/pairing, completion and grouped Settings | All agreed source-backed screens implemented; final rendered comparison/device acceptance open |
| 3 | Real day-aggregated History grid and restyled schedule list/editor | Real calendar/day dialog and compact schedule list/modal editor implemented; local verification/evidence below, final iOS render/A16 acceptance open |
| 4 | Essential A16 safety GO, canonical release module, signed AAB and release-device checks | HOLD until checkpoint evidence |
| 5 | Privacy, Accessibility video/declaration, real screenshots, tester/reviewer instructions and upload checklist | Drafts prepared; publisher/contact supplied and inserted; public policy URL, final release/account/signing and reviewer materials incomplete |

### Current A16 acceptance checkpoint — 2026-09-06

The `3d9ff301…` UI APK was installed in place with pairing, two selected apps,
the original saved schedule and all History preserved. Physical idle/active Zap, selection
Cancel, History/day, grouped Settings and schedule-list/edit-Cancel surfaces were
observed. A real 210,307 ms session saved exactly once with two targets and no new
interruption/timing warning; History reloading retained five rows and the same two
old warnings. Dedicated emergency keypad non-obstruction was checked without
digits or a call. [Exact build-scoped evidence and screenshots](../spikes/android-integrated/evidence/a16-current-ui-2026-09-06/README.md).

Do not repeat the clean-History or prior Chrome-unblock checks merely because the
older warning's cause is unknowable. Remaining distinct work includes physical
rejected/rapid-repeat input, separately authorized cleanup/fresh consent, final
visual/release review and signing/store dependencies. The follow-up below closes
the clean scheduled cycle and the recorded modal status-icon contrast defect.
No internal-beta GO or full parity claim.

### Clean schedule cycle and modal-only correction — 2026-09-06

A separate one-time 20:20 test fired with a STARTED receipt at 20:20:00.058 while
Chrome was already open; the blocker and two active targets were observed. Zap
stop committed the same UUID once, 131,764 ms, without interruption/timing warning.
History now has six rows / 38m and the same two historical warnings. The original
schedule was untouched; the new one-time test correctly became disabled.

The contrast-only `e217eb1a…` APK is now installed/hash-verified on the idle A16.
Selection and completion style their own modal status/navigation icons separately;
the actual A16 selection sheet is readable. All prior History and setup survived.
The unchanged NFC/restriction/schedule runtime reuses the clean cycle evidence;
do not repeat it just because modal colors changed. The full verifier (217 JVM +
17 package-checker tests) and 11 focused emulator tests pass. Exact results,
artifact hashes and visual-capture limitations are in
[the follow-up evidence](../spikes/android-integrated/evidence/schedule-modal-contrast-2026-09-06/README.md).

## Source-first sequencing approved — 2026-09-06

The owner explicitly approved: implement the remaining Android screens from the
audited unchanged iOS source, then compare the missing actual renders afterward.
This supersedes the earlier screenshot-first implementation pause, **not** the
final rendered-comparison or A16 acceptance requirements. The owner asked for
accuracy, so source-backed implementation, synthetic render checks, real feature
tests and physical-device evidence remain distinct.

The [Zap source baseline](reference/ios-release-2026-09-06/zap-source-baseline.md)
records exact metrics and deliberate Android differences. iOS and product contracts
remain read-only; this slice realizes existing intent in Android only.

## Zap and Compose selection checkpoint — 2026-09-06

The main Android screen now uses the audited source layout: original logo centered,
calendar left and History right while idle, source-sized hero/artwork/spacing,
dark idle and light active states, and a real Unzap scan action. Development
branding/permanent diagnostics are removed from that screen; truthful setup,
permission and recovery actions remain. Active state is still derived from the
restriction controller, not the persisted desired flag alone.

The selection capsule opens the real Compose app picker backed by the previously
verified selection-management interface. Save/Cancel, protected/missing removal-only
rows, stale-write rejection, explicit reload, pause draft discard and one-time
confirmed save behavior remain. Navigation cancels the one-shot reader; a targeted
actual-Activity regression rejects a late NFC result without changing state.

[Evidence and exact scope](../spikes/android-integrated/evidence/zap-selection-2026-09-06/README.md):
23 emulator cases passed, including 4 actual selection Activity tests; those 4 also
passed at compact size with 200% text. Unmodified screenshots were visually checked.
Zap idle/active captures are explicitly synthetic render fixtures; selection uses
the real Activity/manager with emulator-only choices. These are not A16 NFC or
blocking evidence and do not close final iOS render comparisons.

Completion presentation, grouped Settings, History/schedule final rendered acceptance,
the smaller A16 checkpoint and all signed-release/store dependencies remain open.

## History/schedule local checkpoint — 2026-09-06

The real History calendar/day dialog and compact schedule list/modal editor are
implemented. [Final evidence](../spikes/android-integrated/evidence/history-schedules-2026-09-06/README.md)
records 39 passing integrated emulator cases plus 9 affected cases at compact size
and actual 200% Android system text. The full local verifier passed 208 JVM cases,
lint/builds and 17 release-checker tests; the final contrast correction was rebuilt
and rechecked. Product contracts and iOS-unmodified checks pass.

History uses a worker-backed range read, preserves all rows/UUIDs and timing totals,
and invalidates stale results on background/range/date/zone change. Interruption
and uncertainty counts remain prominent. Year deliberately scrolls with 48dp day
targets; compact/large-text layouts scroll the summary too. Schedules retain the
existing real ViewModel, exact-alarm actions and timing rules. Their render tests
are not physical delivery evidence. Source baselines document all adaptations.

The generated AAB remains **unsigned and development-ID**; it is not the final Play
deliverable. The owner phone was not operated. Final rendered/A16 acceptance and
the audited follow-up below remain open.

### Settings/completion and pairing-safety implementation — 2026-09-06

[Source baseline](reference/ios-release-2026-09-06/settings-completion-source-baseline.md)
and [local evidence](../spikes/android-integrated/evidence/settings-completion-2026-09-06/README.md)
separate implementation from final iOS rendered/A16/release acceptance.

- **Completion implemented:** the existing atomic stop/outbox transaction returns
  its exact newly committed entry. Failed/stale/idempotent or legacy-ineligible
  stops return no receipt. Only the current resumed foreground scan consumes it,
  at the unchanged 20-minute source threshold. No generic active-to-idle polling,
  latest-History lookup or background/direct-entry popup is added. Real count/time
  and independent interruption/timing uncertainty are shown; no Signal/reflection
  or continuous-protection claim. Dismissal/background/new session drop the visible
  receipt; withdrawal also drops transient UUID suppression. No schema change.
- **Grouped Settings implemented:** History gear opens native Compose groups for
  real setup, read-only tutorial replay, draft privacy/data review, explicit Android
  App info and installed version. Routes survive recreation without serializing
  user records or granting authority. Tutorial replay never resets setup or pairing.
  Review local-data removal only opens native Setup; it does not erase data. Real
  publisher/contact/public privacy URL remain required before distribution.
- **Legacy replacement loophole fixed in source:** explicit confirmation freezes
  an opaque process/consent/session/digest-generation token. Commit atomically rejects
  active/recovery/failed/deletion/stale state and uses existing rollback handling.
  No-token compatibility calls are initial-only. Cancel/pause/recreation invalidates
  replacement scans. Initial pairing before disclosure remains valid and separate.
  The paired owner phone was not changed; physical release acceptance stays open.

Local standard-size regression: all **58 integrated emulator tests** and **36
targeted capability tests** passed. The full verifier passed **217 JVM tests**,
contracts, lint/builds and **17 release-checker tests**. See the evidence record for
the separately recorded compact/system-font-200 pass and exact artifact hashes.
The generated bundle is still **unsigned / `com.justnoise.androiddev`**, not a Play
upload artifact. No production promotion, signing, phone install or upload occurred.

## Earlier checkpoint — real selection editing, 2026-09-06

Completed the reusable selection-management/persistence slice and connected it to
the existing native setup picker. This is **not** the iOS-style Compose sheet or a
waiver of the remaining screenshot-first references.

- Launcher discovery, fresh protection checks and confirmed saves run on a worker.
  The picker keeps a separate unsaved draft, with explicit Save/Cancel. Load failure
  is not reported as an empty successful list; failed validation retains the draft.
- A transient process/consent/revision token is frozen when opening the editor and
  rechecked atomically at commit. Withdrawal, same-millisecond re-consent, concurrent
  edits and session changes reject stale saves; explicit Reload replaces a stale
  draft. Tokens/drafts are not persisted or automatically retried after recreation.
- Failed preference commits roll back the visible in-memory state and preserve
  quarantine. Incomplete History deletion rejects selection writes. Identical legacy
  selections remain harmless no-ops; saved missing/protected rows remain visible and
  removal-only. Rejected rechecks remain unchecked after scrolling/rebinding.
- Pause/recreation discards an unsaved picker. A user-confirmed in-flight Save may
  finish once; stale callbacks cannot reopen a picker or revive a pre-withdrawal draft.
- No new manifest permission, durable field, disclosure version or duplicated engine.
  No iOS mutation, owner-phone action, production promotion, signing or upload.

The [selection evidence record](../spikes/android-integrated/evidence/selection-2026-09-06/README.md)
records exact verification scope and artifacts. Main Zap/navigation, the styled
selection sheet, completion/Settings, History grid and schedule styling remain open.
An owner decision on implementing from the audited source before the remaining
rendered comparisons has been requested; no affirmative answer is recorded here.

## Earlier checkpoint — first-run implementation, 2026-09-06

The two educational screens and initial Zap activation now use the three captured
iOS Release references. They run in the real integrated app, use the original local
artwork/videos, and have accessible paging, scrolling, Back/Cancel and reduced-motion
handling. Android copy says **Select Apps / Choose the apps you want to block**.
This is a completed first-run slice, not completion of iteration 2 or the full MVP.

- Tutorial completion is durable but grants no consent, pairing or session state.
  Existing pairing/setup and real session recovery bypass education. Paired first-run
  users explicitly continue to the separate existing Android disclosure/setup UI.
- Initial pairing has an atomic no-replacement/no-active-session/no-recovery guard.
  HMAC key creation is serialized. Scan intent is captured with its lifecycle token;
  cancel/pause and direct-input suppression remain in force.
- Schedule-only suspension is separate from session recovery and stays suspended
  after initial pairing. A synthetic suspended-state regression covers this. A
  completely empty store intentionally does not persist suspension merely from
  `suspendSchedules()`; this is not claimed as a reproduced clean-install defect.
- Failed tutorial writes keep storage quarantine and expose a retry/open-setup dialog
  across route changes. An already-visible error marker survives Activity recreation.

Verification passed: **186 JVM tests** (141 capability, 15 prototype, 30 integrated),
contracts, lint/builds, **17 Python package-checker tests**, and release APK boundary.
Additionally, **33 targeted capability instrumentation tests** and **five real first-run
UI tests at each of standard and compact/200%-text sizes** passed with zero skips or
failures. Both UI passes used reduced motion; normal playback was also inspected.
[Screenshots, original JUnit reports and artifact hashes](../spikes/android-integrated/evidence/first-run-2026-09-06/README.md)
record the exact scope. Log: `/private/tmp/justnoise-first-run-verification.log`.

The A16 was not operated, installed to or cleared. iOS code/project/extensions/assets
remain unchanged. No production promotion, signing-key creation, commit or upload
occurred. Current unsigned development AAB SHA-256:
`43510c80e19eb329b88bc5d470b17ae70b6725a2daea9c1475b4dcce4f02a2df`.

Next: capture the remaining unchanged iOS Release states, then implement their real
Android counterparts: main Zap/navigation and selection, completion/Settings,
History grid and compact schedules. Source/read-layer work is available, but missing
rendered references and final A16 visual acceptance are still open.

## Earlier checkpoint — reference/read-layer implementation, 2026-09-06

Completed source work, not completion of all five iterations:

- Unmodified iOS Release build succeeded. [Three native captures](reference/ios-release-2026-09-06/README.md)
  cover the two onboarding pages and Zap activation, at 1206×2622. No NFC/authentication
  bypass was introduced. The isolated simulator was shut down afterward.
- The controller's readiness observation now has deterministic regression coverage
  for snapshot → service-status → monotonic-clock ordering. Future/stale heartbeats
  still fail closed; explicit test timestamps are not clamped. This prevents the
  demonstrated clock-order defect, but does **not** establish the cause of the A16's
  last interruption. A genuine heartbeat gap beyond eight seconds still marks an
  interruption; no legitimate warnings or existing records were cleared.
- Added `HistoryDateRange`, interval/day aggregation and the worker-thread
  `Gate0Preferences.loadHistoryTimeline(range)` read interface. The 90-day/year
  projection uses all stored rows independently of list pagination, preserving UUIDs,
  storage schema, outbox/deletion guards and exact stored duration. No UI caller or
  fake records were added to the real app.
- Tightened the packaged-release Activity boundary: exactly the launcher, private
  real setup and direct NFC entries, with explicit export expectations. Extra,
  missing, duplicate, disabled or aliased entry points are rejected. This is not
  AAB signing or Play acceptance.
- Prepared the [internal-upload handoff draft](ANDROID_PLAY_INTERNAL_UPLOAD.md),
  separating internal-only requirements from later public-launch requirements.
  Live Console inspection could not proceed because macOS was locked; account
  eligibility and package registration remain unverified, not failed.

Verification: the complete `verify-android-predevice-readiness.sh` passed with
**178 JVM tests** (141 capability, 15 UI prototype, 22 integrated; unchanged tasks
reuse Gradle results), lint/builds, **17 Python release-checker tests** and the
packaged release boundary. Final log:
`/private/tmp/justnoise-android-implementation-final-verification.log`.
Separately, **all 13 History instrumentation tests actually ran and passed** on the
explicitly targeted read-only emulator; [report and original JUnit XML](../spikes/android-gate0/evidence/emulator-history-timeline-2026-09-06/README.md).
The emulator was closed without saving its snapshot. The paired A16 was untouched.

That earlier unsigned development AAB SHA-256 was
`5bb378217ef9bb788057a00b08630edc573be1f53951c1b8d6ea150bd7d04244`.
Its sole native library, `libandroidx.graphics.path.so`, has three PT_LOAD segments
aligned to 16,384 bytes with congruent offsets for each of arm64-v8a, armeabi-v7a,
x86 and x86_64, inspected with installed `llvm-objdump`. That is ELF evidence only:
generated-APK ZIP alignment, 16-KB runtime behavior, release signing and final-device
acceptance are not inferred. This artifact is **not** the requested signed release.

Git comparison against `11d4d98` remains empty for the iOS app/tests, Xcode project,
icon, all three extensions and product contracts. No commits, device-data cleanup,
release installation, signing-key creation or Play upload occurred in this slice.

At that checkpoint, visual implementation was pending. First-run implementation has
since proceeded for the three captured screens, as recorded above. A current iOS
recording/screenshots is still requested for authenticated/physical states; the source
audit is not substituted for rendered acceptance.

## Reference checklist

Record source revision, build configuration, device dimensions, actual image and
observed state for each: onboarding pages 1/2; activation/pairing; Zap idle/active;
selected-app sheet; schedules empty/list/new/edit; History empty/populated/selected
day/year; Settings; session completion; actual blocked-app surface.

The source audit covers hierarchy, dimensions, typography, colors and interactions.
It is not rendered acceptance. The old `IMG_6096.png` shield photo is obsolete
(green button/old text); current white-button shield rendering still needs a current
reference. No existing complete current-release screenshot set was found.

## Intentional Android differences, approved by the implementation plan

- Owner-approved Android equivalent to iOS Live Activities (2026-09-06): optional
  ordinary ongoing session notification with native elapsed chronometer, tap to
  open Justnoise, and no stop action. Separate notification Settings/permission;
  refusal does not block sessions. No widget, promoted Live Update/Now Bar promise,
  foreground service, or new session engine. Dismissal hides only its session UUID.
  Known recovery replaces the timer with attention; known stop/withdrawal cancels.
  Abrupt process death can leave the last logical-session display until the next
  reconciliation, so copy never claims continuous protection. iOS stays unchanged.
- Same brand hierarchy/artwork/spacing intent, but native Android system navigation,
  accessible touch targets and permission/settings surfaces remain.
- The September 7 extension replaces the single **Selected apps** capsule with
  real named modes and their app-selection sheets. Preserve Save/Cancel and
  active/recovery edit protection; Apple categories/websites remain unsupported.
- History cards say **Zaps** and **Session time**. Interruption/uncertain timing is
  visible; no false continuous-protection claim or reflection count.
- Ninety-day grid spans today−60 through today+29; Year is the current local year.
  Recompute on device-zone/date change. Query all relevant records, not the page.
- Split positive wall intervals by local midnight and proportionally allocate the
  recorded duration with exact integer conservation. Unlike the observed iOS
  implementation, retain sub-minute slices. A wall/monotonic duration discrepancy
  over 1,000 ms is timing-uncertain; smaller differences allow clock-sampling jitter.
  Nonpositive wall intervals are attributed to the recorded end date and marked
  uncertain. Stored rows and sticky interruption flags are never rewritten.
- Preserve approved Android temporal behavior (automatic start/Zap stop, current
  saved selection, explicit alarm access, missed-start and Force Stop recovery).
  Do not transplant incidental iOS scheduling quirks or its long-press start bypass.
- Adapt misleading onboarding text to **Choose the apps you want to block** on
  Android only. Do not modify the existing iOS wording.

### First-run integration contract — implemented slice

The three captured first-run Release states are sufficient to implement their own
Android counterparts while the other reference comparisons remain open. This does
not waive the missing references or count the complete visual iteration as passed.

Two educational pages lead to actual initial pairing. Completing those pages saves
one local tutorial-completed flag, not Accessibility consent, readiness or a fake
pairing. Page position is retained for Activity recreation; a cold launch before
completion may restart education. A real saved digest drives pairing confirmation,
then an explicit **Continue setup** action opens the existing separate disclosure
and manual Android permission flow. There is no Android account screen.

Existing pairing/setup bypasses the new introduction. A requested session or
recovery state always wins over a restored introductory route, preserving the
normal stop/recovery actions. **Not now** leaves pairing unchanged and opens the
normal app; it is not a successful setup claim. Withdrawal clears the tutorial flag
with the existing local store. Initial pairing must atomically reject an existing
digest, active session or recovery state; legacy replacement remains unchanged.
Reader purpose is captured in its lifecycle token, and initial pairing shares the
direct-dispatch interaction guard. Cancel/pause invalidates late callbacks and no
pairing action accepts consent or toggles a session.

## Verification boundary

Pure/domain tests and disposable-emulator persistence/UI checks precede targeted
A16 samples. Never run instrumentation or `uiautomator dump` on the owner's paired
phone. No destructive cleanup of its development installation. Capture normal
release refusal/recovery and cleanup on the separate release installation with the
owner's permission. Keep current APK identity and evidence scopes explicit.

Each iteration runs the affected Android checks and the repository contract/release
boundary verifier. Final visual acceptance uses real rendered states at compact and
standard sizes, enlarged text/reduced motion and the A16. Compare iOS Git paths with
the starting revision to ensure no implementation edits occurred.

## Notification follow-up and debug-only Live Update experiment — 2026-09-06

The ordinary notification passed the A16 panel timer and tap-to-open checks. A later
passive observation confirmed idle revision 46 and no own notification 7101; the
stop gesture itself was not observed in that turn. The owner then approved a
**debug-only** promoted Live Update compatibility experiment using the public
API 36.1+/Core 1.18.0 path and exact debug manifest overlays. Open-ended-session
eligibility remains unresolved. The probe passed local checks and was installed
while idle. The owner's next real session, revision 47, produced an actual system
`PROMOTED_ONGOING` flag: **API-level promotion passed**. Samsung's panel showed the
session timer at the top, but no Home status-bar timer chip or settled lock-screen
Now Bar/session card was observed under the current settings. No notification
setting was changed, and absence in this sample is not a universal incompatibility
claim. At that checkpoint the session remained active and its owner Zap-stop/
cancellation check was pending; subsequent results are recorded below.
Main/release permissions and ordinary fallback remain intact, with no attention
promotion, new service or session authority. iOS is unchanged; this is not
production promotion or Play GO. See the chronological
[notification evidence](../spikes/android-integrated/evidence/session-notification-2026-09-06/README.md)
for the baseline observations, boundaries and official guidance.

The owner subsequently changed lock-screen presentation from Icons to Cards;
the ordinary session card and timer became visible, but Now Bar did not. The
StandardStyle sample later returned to idle revision 48 with no notification 7101.
An owner-approved **debug-only BigTextStyle comparison** passed the full verifier
(232 JVM tests, 36 package-checker tests), product contracts and 6 targeted emulator
construction tests. It was installed in place while idle; APK hash, pairing and
both selected apps were verified. Release/attention/fallback remain StandardStyle,
with no hidden Samsung API, fake progress, new service or setting override.

The owner's next physical start, revision 49, confirmed the actual native BigText
template and system `PROMOTED_ONGOING` flag. The lock-screen card showed **01:52**
and the panel **02:25**, but **no compact Now Bar or Home timer chip appeared**.
Thus this template comparison did not resolve the missing Samsung surface; it is
not a confirmed Now Bar fix or release promotion approval. This sample subsequently
returned to idle revision 50 with notification cancellation observed.

The owner-requested reversible Samsung developer-switch comparison then produced
**actual compact/expanded lock-screen Now Bar and a Home timer chip** at real active
revision **51**, using the same reviewed APK. The switch was
**Entwickleroptionen → Weitere Einstellungen → Live-Benachrichtig. für alle Apps**
(**Real Time Notification Test.**). It was enabled from its verified OFF baseline
and restored **OFF** at about 23:36 local. No code/build, permission or core-session
change was made. After restoration the same session remained active and connected,
but the own notification was absent and its dismissal marker matched the active
UUID; no forced repost was attempted. This proves override-enabled A16 rendering,
**not default-user availability, production promotion approval or Play readiness**.
See the chronological notification evidence for exact observations and boundaries.

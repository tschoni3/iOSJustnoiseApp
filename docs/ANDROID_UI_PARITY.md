# Android Release UI parity

November 6 language decision (supersedes earlier partial-localization notes):
iOS and Android ship English app-authored copy only until another language is
complete across the app. Remove partial German resources and their temporary lint
exception. Keep localization keys for future complete translations. Do not override
the device locale or translate user-entered mode names. OS-owned permission dialogs
and settings continue to follow the device language.


November 6 review fixes: welcome groups the headline and subline below the Zap
artwork. German mode setup now covers the list, editor, actions, plural app counts,
validation and recovery messages. The two portable list headings use native locale
resources with unchanged English contract values. Missing German mode translations
remain lint errors alongside setup/guide translations. Saved user mode names are
not translated. Existing mode persistence and session behavior remain unchanged.


November 5: welcome matches the iOS “Make space for you.” copy and Zap artwork.
The first-session guide matches the black video-led design, with no eyebrow,
subtitle or enclosing step card. Three numbered steps retain Android-specific
unlocked-phone NFC instructions. Video loops without visible media controls;
existing lifecycle/reduced-motion behavior remains. Welcome/guide have English
and German resources; mode names remain user-entered with localized examples.
New installs already have no seeded modes; legacy user configurations are preserved.
The purchase link now opens justnoise.shop/pages/the-zap. No setup-order, consent,
restriction, scheduling or account behavior changes; no phone installation.


October 11 owner follow-up: candidate 1.0.16 (18) bundles the account screenshot
policy with compact Android onboarding copy. The visible Accessibility disclosure
is one 13/19sp dark-gray paragraph without a Why we ask heading; data, purpose,
local-only processing and manual consent remain explicit. Font scaling and scrolling
remain enabled. Idle activation shows only its primary instruction, with no extra
body or cancellation status. The scan modal and genuine actionable errors remain.
No permission grants, NFC validation, persisted state or iOS changes.

October 11 account screenshots (owner): the production Account Activity starts
secure until its first composition, then allows screenshots on provider choice,
email recovery and normal account views. The same presentation rule that renders
password fields protects email sign-in, registration, password update and same-account
reauthentication, regardless of focus, empty/masked values or busy state. No credentials
are persisted or exposed to tests. Authentication, onboarding, session and storage
behavior are unchanged. This is an Android-only window-policy change, not a portable
product rule or iOS edit. Candidate 1.0.15 (17) requires separate build/distribution
evidence; prior accepted feature tests are retained, not repeated.

October 11 owner-provided Zap logo: production-only adaptive launcher icon and
512-pixel Play icon use the supplied PNG. The original is archived byte-for-byte;
launcher preparation removes only outer dark padding for the OEM mask safe zone.
No iOS asset, product UI, session or permission behavior changes. Version 1.0.14
(16) is the new candidate; published 1.0.13 (15) stays immutable. Physical icon
and Play-listing acceptance remain separate from local resource/build checks.

October 11 Settings cleanup (owner): main Settings has Account, Your Zap and Help,
followed by an explicit More settings row. Emergency Unzap stays directly reachable
with its count/confirmation; Now Bar remains in Your Zap. Tutorial and contact are
compact rows. The copyable email fallback appears only if email handoff fails.
Permission/pairing recovery, notification settings, app info and existing privacy/
local-data review live in the More settings page. Version becomes a quiet footer.
Back goes Privacy → More settings → Settings → History; subpage survives activity
recreation. No service, consent, storage, analytics, account or iOS change.
Privacy text is not edited. Physical visual acceptance of this cleanup is pending.

Owner has confirmed all three new 1.0.12 (14) shortcuts PASS. That version is
published internally and installed through Play; no repeated phone checks needed.

October 11 owner extension: Android 1.0.12 (14) adds the unchanged iOS Zap
artwork above the shield title as a decorative image. The scrollable shield keeps
one white Be Right Back action, Android Home destination and native navigation
bounds. No technical/help controls return. Holding the Home Zap artwork starts
the selected mode without opening the scanner; normal tap scans and active holds
are no-ops. Existing account, pairing, target, disclosure, capability and CAS
validation remain mandatory. This is an explicit UI shortcut, not NFC evidence.

Settings now contains Emergency Unzap: five installation-owned unlocks, a short
confirmation bound to the displayed active session UUID, and disabled idle/exhausted
states. Token consumption travels atomically with the existing stop/outbox commit;
failed writes, cancellation and stale confirmation consume nothing. Account
switch/deletion and consent withdrawal never replenish the count. Recovery can
use the same stop without service readiness or account-start access. To make the
entry usable, History remains reachable during active sessions, and guided recovery
offers Emergency Unzap settings without pretending blocking has recovered.
iOS is unchanged. Physical acceptance of these newly added paths is still pending;
prior successful hardware checks are retained, not repeated.

Local verification: 547 production JVM tests / 69 suites with no failures, errors
or skips; release lint zero errors (96 warnings, three hints); signed package,
contract, layout and full pre-device checks PASS. New UI instrumentation checks
compile only; no rendered/physical acceptance is asserted. Candidate 1.0.12 (14)
has not been uploaded or installed.

October 11 bundled follow-up: production candidate 1.0.11 (13) combines the
typography below with UI mode creation selecting the new mode in the same guarded
storage commit. Editing existing modes preserves the selected mode; failed writes,
session/recovery conflicts and schedule references retain their existing safety
semantics. No session starts as a side effect. Android-only fixture version 1.0.1
adds three creation/selection cases; shipped iOS is unchanged.

An explicitly opened Accessibility setup route arms one process-only, two-minute
return request. After the service is enabled, a Settings window-change event can
launch only this app's own launcher activity once. It requires accepted disclosure,
idle/non-recovery state, an interactive unlocked phone and a recognized Settings
package. Any first unrelated/invalid window, cancellation, interruption, unbind,
app return or process restart discards the request. No content retrieval, automated
permission clicks, global Back actions, new permissions or relaunch loops. If the
OEM emits no qualifying event or denies background launch, manual return and
existing onResume progression still work. This is best effort, not A16 acceptance
or a guaranteed automatic return. Guidance remains honest about manual return.
Reference: https://developer.android.com/guide/components/activities/secure-bal

Final bundled verification: 538 production tests / 66 suites, zero failures,
zero release-lint errors (94 warnings, two hints), signed APK/AAB checks, contracts,
layout and full local pre-device verifier PASS. The former Analytics mock-handler
cleanup race now has a request-join assertion, bounded engine cleanup observation
and post-revocation capture guards; no production analytics changes. Candidate
1.0.11-13-aa56794a was subsequently published and installed through Play; see
the candidate HANDOFF. Owner confirmed newly created modes auto-select. Other
new-flow A16 acceptance remains pending.

October 11 typography follow-up (source only, after published 1.0.10): setup and
first-session guidance share three readable levels: 30/36sp title, 17/24sp
semibold instructions, 15/22sp charcoal explanations. The permission disclosure
is unchanged, visible by default beneath a short Why we ask section label, not
hidden behind a link or rendered as tiny print. Secondary return instructions
retain 15sp text; font scaling and scrolling remain enabled. Static Settings
illustration labels use 13/18sp captions, with the actionable steps prominent.
No permission/state behavior, owner data or iOS changes. This source follow-up is
not in the immutable published 1.0.10 (12) bundle; a new versioned candidate is
needed before distribution. New physical layout acceptance is not yet recorded.
Verification: full Android pre-device verifier, contracts and zero-error production
release lint passed. All targeted integrated presentation tests and the new readable
typography regression passed. An unrelated analytics cancellation test failed once
in the full production suite and passed in the isolated class rerun; this is an
intermittent finding, not proof of a resolved analytics defect. No owner-phone tests,
installation or upload were performed for this follow-up.

October 11 owner-provided first-session video: ready Home shows one dismissible
“Your first Zap” dialog with the supplied 13.75-second local video, unchanged frames,
silent lifecycle-scoped playback, tap-to-pause and reduced-motion still-frame behavior.
Two short instructions explain mode selection and physical Zap start/stop. The single
primary button opens mode selection when none is selected, otherwise says “Got it”.
Native Back and the scrim also dismiss it; an installation-owned boolean remembers dismissal.
It never blocks active-session stop/recovery, a scan or another sheet and creates no
mode/session. Existing installations see it once when ready and idle as well.
The video depicts iOS; Android text describes the real physical tap flow. iOS assets
and source remain unchanged. Packaged media hash/acceptance are recorded separately.

October 11 visual permission guide (owner): one short first-run page now shows a
static two-step Settings illustration (Installed apps → Justnoise; switch on and
Allow), followed by the unchanged prominent disclosure and explicit consent action.
The illustration has one complete screen-reader description and no fake interactive
switch. The primary action attempts service details, Samsung Installed services,
then normal Accessibility settings; unsupported/denied routes fall back without
permission mutation or private fragments. The A16 has no validated direct shortcut,
so the fallback route remains expected. Returning to Justnoise rechecks current
consent/system access and automatically advances to the existing optional schedule
step or Home. Refusal, recovery priority and manual system activation are unchanged.
Android-only presentation/navigation; no iOS, data migration or account change.
Candidate 1.0.10 (12) passes the production build, 532 JVM tests / 64 suites,
zero-error release lint (94 warnings, two hints), product contracts, layout and
signed-artifact checks. The original media SHA matches the packaged bundle.
Affected visual acceptance is still pending; no upload or device acceptance is
recorded by these local checks. See android-app/evidence/first-session-guide-2026-10-11.md.

October 11 owner-requested signup UI: signed-out entry offers the official Google
button first and one secondary “Continue with email” action. Password fields,
email-registration and reset controls appear after choosing email, rather than
competing with Google on first entry. The explicit registration page also offers
Google above its email form. Auth primary actions use white/charcoal styling;
generic Close/Try again remain absent. Busy-state disabling, password draft
clearing, required authentication, PKCE, account ownership and recovery actions
are unchanged. This is Android-only presentation; iOS is read-only. The Google
light/pill asset comes from https://developers.google.com/identity/branding-guidelines
and retains its original proportions and branding. This source change is not
phone acceptance or a Play upload.
Verification for this slice: 53 targeted presentation/navigation/runtime/PKCE
tests passed, Release Kotlin/resources compiled, and release lint reported zero
errors (92 warnings, two hints). Product contracts and production-layout checks
passed. No owner-phone tests, installation or Play upload were performed.

October 11 owner-approved permission copy: first-run setup uses “Allow Justnoise
to block distractions”, two short actions and an “Agree & open settings” consent
button (“Open settings” after consent). The redundant subtitle is removed. The
production Accessibility service is labelled “Justnoise”, matching the setup and
recovery instructions. The disclosure still explains Android Accessibility,
app detection, session blocking, on-device processing and excluded screen/input
data. Consent, refusal, system navigation and readiness checks are unchanged.
This is Android-only presentation; iOS and portable product rules are unchanged.
Source checks/builds are not a Play upload or physical layout acceptance.
Affected first-run, permission-flow and settings-route JVM tests plus production
Release Kotlin/resource compilation passed on October 11. Product contracts and
scoped whitespace checks passed. No phone test, installation or upload was run
for this copy-only update.

October 11 account-form cleanup (owner): authentication forms omit the generic
Close and account-state Try again buttons. Failed sign-in/signup can be submitted
again through the existing primary action after entering the password again;
password drafts still clear on submission. Native Back and explicit Back to sign
in remain available. Signed-in account/import recovery and pending-link recovery
are unchanged. Android-only presentation change; iOS is read-only. This does not
resolve the separately reported live signup failure or constitute distribution.

October 11 owner correction: Year is a complete 365/366-dot overview without
scrolling. Its grid chooses cell size/columns from the remaining measured viewport;
summary and year progress remain visible. Small dot hit regions do not overlap.
An explicit >=48dp Choose day action opens a bounded native date picker as the
accessible alternative. Ninety-day scrolling/context and all History records,
aggregation, density and day-detail behavior are unchanged. Android-only layout
correction; iOS is read-only. Build/tests do not establish physical visual acceptance.

October 10 owner cleanup follow-up: schedule timing/next-start rows omit raw IANA
zone IDs and the “device time zone” suffix, while formatting still uses the same
stored zone/instant. No schedule, receipt or execution semantics change. NFC result
copy uses brief customer-facing messages rather than NDEF/UTF-8/payload or overlay
diagnostics. Account retry appears only for a message or interrupted import, not
as a permanent “Reload account state” action. Required disclosures, consent,
destructive-action explanations and genuine failure/recovery routes are retained.
This bundles the Android-Home blocker action; iOS is unchanged.

October 10 final blocker cleanup (owner): the blocked surface shows the shared
title/app-name message and one white **Be Right Back** action. There is no help
link or technical-action panel. Following the owner's destination clarification,
the action opens Android Home, not Justnoise, without stopping the session.
Recovery stays in app Settings and native Android Settings; native
Home/Back insets and protected-system exclusions remain unchanged. This is an
Android-only copy/presentation override of the older shared Keep going baseline;
iOS and portable session behavior are unchanged.

October 10 permission-return correction: product entry checks current disclosure
and Accessibility grant independently of mode-selection truth. A missing mode
must not mask revoked access after returning from Settings. Active-session stop
and recovery keep priority. This is presentation-only; enforcement, persistence,
alarm rules, NFC authorization and iOS are unchanged.

October 10 owner clarification: phone product, sign-in and setup activities request
portrait orientation. The invisible direct NFC entry remains orientation-neutral
so a background Zap tap does not rotate another app. iOS remains untouched. This
does not resolve or establish the cause of the reported shared sheet-scroll glitch.

October 10 shortest Accessibility route: the consent button now first attempts the
system service-details action with the installed Justnoise service component, then
falls back to normal Accessibility settings if unavailable or denied. No private
Settings fragment, automated permission grant, new permission or service change is
used. The disclosure retains its data boundaries in 47 words; two numbered actions
replace the long navigation paragraph. Existing consent/refusal and automatic
continuation on return remain unchanged. This is Android-only system navigation;
iOS is untouched. Direct Samsung routing/rendering still requires one A16 check.

October 10 later owner clarification: mandatory mode creation is removed from
Android onboarding. After pairing and required account entry, users complete
blocking setup and reach Home without an automatically opened mode sheet. They
create/select a mode through Home's selection capsule before starting a session;
no default mode or target selection is silently created. Existing modes remain
unchanged. This does not remove the tutorial pages or merge the permission screens.

October 10 Android first-run refinement: after pairing and required account entry,
first-time users see the standalone app-blocking disclosure. The single explicit
“Agree & open Android Settings” action replaces the
duplicate checkbox/button pair. Declining exits setup; Accessibility remains manually
enabled in system Settings. Returning with access enabled advances automatically to
the product, while active-session stop and recovery override setup routes. This is
Android-only permission presentation; the iOS app is unchanged. Local build and
tests do not yet prove the rendered A16 first-run experience.

September 19 later owner clarification ("please remove entirely"): remove
permanent helper paragraphs and historical interruption/timing-warning copy from
normal Android History, day details, completion, modes, selection, schedules and
Settings. This supersedes the earlier requirement below to display those History
warnings. Preserve records/flags and the truthful Session time label; do not call
elapsed duration verified protection. Keep action failures, permission recovery,
essential state labels, empty states and consent/destructive-action explanations.
Successful schedule saves/toggles/deletions and successful local-data imports
must not leave permanent confirmation/helper cards. The updated visible list or
account state confirms success; action failures and setup-required states remain.
No iOS changes.

September 19 owner-requested Android copy cleanup: normal product screens must
not show execution diagnostics or implementation terminology. Remove successful
schedule receipt text and healthy-state revalidation commentary from schedule
cards. Keep next planned starts, actionable setup/failure states, and History
interruption/uncertain-duration warnings. Keep the truthful Session time label
and its detailed explanation; do not relabel elapsed time as protected time.
Live Update settings use consumer-facing wording without claiming Samsung
placement or visibility is under app control. Storage, engine behavior, consent
and published iOS copy remain unchanged.

Status: **PRESENTATION ROUTE/STATE COVERAGE COMPLETE — PHYSICAL VISUAL ACCEPTANCE PENDING**

Current real-app implementation, 2026-09-06: the integrated development app now has
source-matched onboarding/Zap/selection, real calendar History and compact schedules,
grouped Settings and exact-receipt foreground completion. The latest scope/results
are in [ANDROID_PLAY_IMPLEMENTATION.md](ANDROID_PLAY_IMPLEMENTATION.md). The older
prototype and September 3 checkpoints below are historical, not the current real
app's feature inventory. Final iOS rendered/A16/release acceptance remains open.

This audit compares the launch-ready iOS surface (`CAPTURE_ENABLED` absent) with the permission-free Compose project in `spikes/android-ui/`. “Covered” means a native Compose route or deterministic preview state exists and builds; it does not mean Android system integration, stored data, backend behavior, or pixel-perfect device acceptance is complete.

| iOS Release surface or state | Compose evidence | Status and boundary |
| --- | --- | --- |
| Startup hydration, account cleanup, cleanup failure/retry, and auth resolution | `StartupBoundaryPrototype.kt` | **Covered.** Local reducer/presentation only. |
| Two onboarding pages and reused videos | `LaunchFlowPrototype.kt` | **Covered.** Generated Android resources reuse the iOS source assets. |
| Zap activation handoff | `LaunchFlowPrototype.kt` | **Covered.** No NFC scan is started. |
| Sign in, sign up, forgot-password, and reset-link password update | `LaunchFlowPrototype.kt`, `StartupBoundaryPrototype.kt` | **Covered.** No account or credential mutation. |
| Zap idle/active hero, artwork, mode sheet, toolbar, timer, and Unzap CTA | `ZapPrototype.kt` | **Covered.** State transitions are local preview actions. |
| First-run three-step Zap/mode/schedule coach tour | `CoachMarksPrototype.kt` | **Covered.** The order, copy, skip, next, and finish behavior match the Release iOS tour. |
| Mode selection, create, edit, delete confirmation, target counts, and app-picker entry | `ManagementFlowPrototype.kt` | **Covered.** The Android picker remains an explicit Gate 0 boundary. Nothing is stored. |
| History 90-day/year choice, summary cards, dot grid, and Settings entry | `ManagementFlowPrototype.kt`, `HistoryPresentationRules.kt` | **Covered.** Representative local durations flow through the shared Zap-count, duration-label, density-threshold, and semantic dot-color contracts; no session history is read or calendar-bucketed. |
| Schedule list, empty state, enable switch, create form, edit entry, and repeat-day controls | `ManagementFlowPrototype.kt` | **Covered in the permission-free prototype.** These preview controls do not register schedules. Real integrated-app source wiring is recorded below; it uses the now-defined temporal rules. |
| Settings account rows, onboarding, support/about links, emergency flow, account deletion confirmation, and sign out | `ManagementFlowPrototype.kt` | **Covered.** Destructive/external actions terminate in truthful preview notices. |
| Capture-disabled Session Complete | `SessionFeedbackPrototype.kt` | **Covered.** No reflection route is offered in the Release surface. |
| Conditional Noise Rewind ready card and seven-page flow | `NoiseRewindPrototype.kt` | **Covered.** Representative values; no analytics or persistence. |
| Blocked application/website surface, fallback names, long names, and white action | `PlatformBoundaryPrototype.kt` | **Covered.** Activity/preview only; it does not intercept another app. |
| Permission-required, denied, temporarily unavailable, and unsupported recovery | `PlatformBoundaryPrototype.kt` | **Covered.** Recovery never reports unavailable capability as successful. |
| Capture tab and Capture/Signal feature family | iOS compile-time `AppFeatures.captureEnabled` | **Correctly excluded.** The currently published Release configuration hides Capture. |

## Integrated History source — 2026-09-03

Separately from the permission-free prototype above, `spikes/android-integrated/`
now routes to `IntegratedHistorySurface`, backed by `Gate0Preferences` and local
SQLite completion records. It provides loading/error/empty states, a paged session
list, app counts, **Sessions** and **Session time** totals, and **Blocking interrupted**
labels. Shared duration formatting is reused; elapsed session time does not establish
continuous blocking. No real 90-day/year grid or weekly insight is claimed. Local
History instrumentation and the full repository verifier now pass; these are not
physical visual acceptance. A16 History/visual results remain pending.
See `ANDROID_SESSION_HISTORY.md` for persistence, consent and recovery boundaries.

## Integrated schedule source — 2026-09-03

The integrated app now also has `IntegratedScheduleSurface`: real list/empty/error
states, one-time and repeating editor, enable/delete actions, next planned occurrence
and latest persisted outcome. It uses the existing native schedule/session engine,
not the preview list. Native date/time pickers, localized weekday labels, wrapped
controls, safe insets and scrolling handle the platform presentation. Exact-alarm
access and existing Accessibility setup are explicit user actions; missing setup
remains visible. A historical STARTED outcome is not a claim of current blocking.
The retained ViewModel keeps drafts/in-flight mutations across rotation, with
explicit conflict/reload and process-death uncertainty states. Three Compose
previews cover empty, saved and editing surfaces. A [bounded emulator UI smoke](../spikes/android-gate0/evidence/emulator-schedule-ui-2026-09-03/README.md)
passed setup states, draft rotation, Settings denial/return, create/edit-cancel,
disable and delete confirmation; A16 visual and functional acceptance remains
pending. See `ANDROID_SCHEDULE_PLAN.md` for the native boundaries.

## What the percentage means

- **100% route/state coverage** means every launch-ready iOS destination and major user-visible branch above has a native Compose representation or an explicit truthful platform boundary.
- It does **not** mean 100% visual acceptance. Typography, insets, animation feel, touch ergonomics, video playback, long/localized copy, and device-specific rendering still require emulator screenshot comparison and then physical checks on the existing A16. A17 visual validation is deferred and non-blocking.
- It does **not** increase the functional Android completion percentage by itself. NFC, restrictions, persistence, authentication/backend adapters, schedules, notifications, billing, and Play distribution remain outside the UI prototype.

## Repeatable evidence

Run:

```sh
bash scripts/verify-android-predevice-readiness.sh
```

The UI test suite rejects shared-copy/color drift, consumes every portable History presentation fixture, verifies launch and management routing, checks the first-run coach sequence, verifies the seven-page Noise Rewind sequence, and protects recovery/startup/account boundaries. Lint and APK assembly cover every Compose source file. The manifest/source policy check rejects permissions, services, and restricted capability APIs in this project.

For deterministic inspection, launch `MainActivity` with `prototype_scene=coach-tour` or `prototype_scene=schedules-empty`, in addition to the scenes listed in `spikes/android-ui/README.md`.

## Acceptance still required

1. Compare captured Compose states against the current App Store iOS build at representative compact, standard, and large phone sizes.
2. Check Dynamic Type/font scaling, screen-reader labels, contrast, focus order, and reduced-motion behavior.
3. Repeat the visual pass on the exact physical A16 used for Gate 0 and record its RAM tier. Other variants, including A17, remain unverified until optional later testing; no additional phone is required for the initial milestone.
4. Record intentional Android differences and their user impact before promoting the prototype into `android-app/`.

# Google Play Accessibility declaration draft

Status: **draft / HOLD** — not legal advice, not submitted, and not evidence of Google Play approval

September 7 approved extension: Android named modes are being implemented and
require affected acceptance before distribution. They add local mode IDs, names,
revisions, target sets and the last explicitly selected mode ID. Schedules refer
to their own mode; active sessions retain a fixed mode ID/name and target snapshot.
Completed History and generic notifications gain no mode identity. This grouping
does not change Accessibility event/content access, the user-defined blocking
purpose or network handling, so the existing v4 prominent disclosure and affirmative
consent boundary remain unchanged. The full privacy notice/inventory below now
describe the additional local configuration; older evidence stays build-specific.

Scope update, 2026-09-03: this draft covers the multi-app development baseline and
the local History design recorded before integration in
[`ANDROID_SESSION_HISTORY.md`](../../../docs/ANDROID_SESSION_HISTORY.md). History
storage, the earlier v3 consent, list/summary and cleanup are implemented, with passing local
History instrumentation and full verification recorded in
[`ANDROID_PREDEVICE_READINESS.md`](../../../docs/ANDROID_PREDEVICE_READINESS.md).
These are not A16, policy or release acceptance. The dated single-target A16 checkpoint
remains unchanged and build-specific. Current source adds v4 disclosure, atomic
scheduled dispatch/execution records, bounded exact-alarm delivery, startup recovery
and one temporary foreground-package observation. The integrated app now connects
the native schedule list/editor, create/edit/enable/delete, latest outcome display
and explicit exact-alarm Settings entry. Final local checks passed (157 JVM tests,
83 emulator instrumentation tests, lint/build/release-boundary checks), with a
[bounded integrated UI smoke](emulator-schedule-ui-2026-09-03/README.md) of draft
rotation, Settings denial/return and sample schedule management. Successful alarm
permission/enforcement and complete visual acceptance were not established. This
source integration is not A16, policy or release acceptance.
Do not submit this draft unchanged: reconcile it with the implemented release,
including schedule-triggered behavior, retention, labels and reviewer steps. Preview
screens are not implemented features.

## September 6 release-material review

Rechecked Google's [AccessibilityService requirements](https://support.google.com/googleplay/android-developer/answer/10964491)
and [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
The current guidance permits narrow deterministic user-defined automation; this
supports review of selected-app sessions and configured starts, not approval of
Justnoise. Separate disclosure, affirmative consent and a declaration video remain
necessary for this non-accessibility-tool use.

Concrete release gaps (publisher/contact updated September 7):

- The owner supplied **Justnoise Media** and **hello@justnoise.shop** on September 7.
  These replace the publisher/contact placeholders; business-registration details
  and email delivery have not been independently verified.
- Grouped Settings now provides a readable, explicitly draft Android privacy entry.
  The public policy URL is still a placeholder; publish the Android-specific policy
  and verify its final in-app entry before distribution. The
  Accessibility disclosure does not replace that privacy policy.
- Release-facing text still says **Gate 0**, **prototype** and **test**. Reconcile
  labels and consent version with the final release before recording the walkthrough.
- Record launch, full disclosure, refusal/re-entry, affirmative consent, manual
  enablement and core functionality on the release build. Supply workable physical-
  tag reviewer access. No final video/access URL is supplied here.

The September 6 A16 record now includes multi-app interception, a scheduled start
and two History completions; the timing correction is subsequently installed.
These supersede older pending-install statements but do not resolve the History
warning or establish release/policy acceptance. Nothing was published or submitted.

## Core functionality

Justnoise is a focus product. At the user's explicit request, the candidate compares the foreground package identifier from Android window-state changes with the locally selected apps copied into the active session's target snapshot. During an eligible manual or configured scheduled session, an exact match displays a Justnoise-owned interruption overlay naming that foreground app. Manual starts resolve the last explicitly selected named mode; schedules resolve their own saved mode reference, without changing the recent manual selection. Missing or empty modes cannot start, and no other mode is substituted. The paired Zap stops a session independently of the selected mode. The service does not suspend the target package and is not a tool whose primary purpose is assisting people with disabilities.

## Why the Accessibility API is proposed

A normal consumer-installed application does not receive device-owner package-suspension authority. The spike is testing whether a narrowly configured Accessibility service can produce the disclosed user outcome without reading window content or requesting broad installed-app visibility. If Google Play policy review does not support this declared use, the broad consumer Android port remains on HOLD even if device tests pass.

## Data access and handling answer

- Accessibility event type: `TYPE_WINDOW_STATE_CHANGED` only.
- Fields accessed: `eventType` as a callback guard and `packageName` as the only observed app value.
- Derived value: exact package membership in the active-session target set or equality with Justnoise, combined with local consent/session/recovery state to show, hide, or retain the overlay.
- Accessibility node/window content, typed text, passwords, notifications, and UI content: not accessed.
- Foreground retention: only the latest package identifier in service memory to re-evaluate an already-open target after scheduled start; reset on screen off, interruption/disconnection and other uncertain lifecycle states. No event sequence, disk retention, logging or foreground-app history.
- Scheduled-start readiness: a process-local foreground generation and bound-service reference back a main-thread acknowledgment (maximum 750 ms wait) and a maximum one-second readiness lease. A changed generation, disconnected endpoint or expired lease invalidates readiness. These values are transient, not event history, persistent data or telemetry.
- Sharing or network destination: none. The app has no Internet permission.
- Configuration retained locally: mode IDs/names/revisions/selected packages and the last explicitly selected mode ID, active-mode ID/name, active-session UUID/target snapshot/clocks/process identity and interruption/eligibility flags, last-finished session ID, disclosure version/timestamp, enforcement state/revision/heartbeat, and a Keystore-backed Zap HMAC digest. Successful stop removes active state; saved modes, recent selection and pairing remain. Mode mutation/selection is blocked during a requested session or recovery.
- Active scheduled-session ownership: `active_schedule_id` stores the originating schedule UUID only during that active scheduled session. Successful stop, manual start, withdrawal, app-data clear or uninstall removes it; completed History does not gain a schedule-ID field.
- Local History retention in source: only session UUID, start/end times, non-negative duration, selected-target count and interruption flag. A pending completion in private preferences drains idempotently to `session_history.db` before later starts/History reads, not during stop; no package names, foreground-event stream or raw tag data enter History. Records remain until withdrawal, app-data clear or uninstall.
- Schedule configuration: private `schedules_v1` stores up to 100 records containing UUID, revision, name, enabled flag, one-time instant or repeating weekdays/hour/minute, named creation-zone ID and chosen mode reference (legacy references remain `local-default`). The native list/editor reads and changes these real records with current-consent, mode and revision checks. Dispatch resolves the referenced mode under the shared lock without changing the recent manual selection. Any referencing schedule, including disabled, prevents deletion of that mode. Records remain until deletion, withdrawal, app-data clear or uninstall.
- Schedule UI state: a retained AndroidViewModel and Android-managed saved-instance-state Bundle keep the route, raw editor fields, original schedule revision and original one-time instant/zone across recreation. A possible interrupted mutation shows an uncertainty warning and is not automatically repeated. Explicit reload discards unsaved edits. Observed current-consent revocation clears the ViewModel draft; a transient consent epoch frozen at the user action is checked at commit to reject stale queued work even after re-consent. It is not a new stored field or telemetry.
- Schedule execution: `schedule_executions_v1` stores at most one pending occurrence, independent replay-prevention local-date/one-time-instant markers and only the latest outcome per schedule. Occurrences identify schedule/revision/key/fire epoch/local date/zone; outcomes add code/handled epoch and a session UUID only for a committed start. Markers survive edits; deleting the schedule or withdrawing clears its execution entry, while completed session History has its separate retention above. No unbounded outcome log.
- Recovery and delivery metadata: private suspension/foreground-acknowledgment state; temporary own-process start PID/name/Force Stop evidence on API 35+, or conservative own-process exit reason/time evidence on API 31–34. Raw startup records are not retained or logged. A non-exported receiver uses exact-alarm, boot and wake-lock permissions, one running/one queued delivery, a 9-second wake-lock lease and an 8-second processing budget including queue wait. It revalidates persisted state; extras alone never start a session.
- Separate local platform metadata: enabled-service components for Justnoise readiness, protected-package resolution, window bounds/insets and interactive/locked Booleans for safe overlay placement. None is used as app-usage history or sent off-device; see `DATA_INVENTORY.md`.

## Prominent in-app disclosure shown before consent

> Justnoise Gate 0 uses Android Accessibility only while you have explicitly enabled this prototype. It reads window-change event types and the foreground app identifier to compare with the apps you selected. Only the latest app identifier is kept temporarily in service memory; it is cleared when the screen turns off or the service is interrupted or disconnected. It does not read screen content, typed text, passwords, notifications, or accessibility nodes, and does not save a foreground-app history. Nothing is sent off this device. Your selected apps, consent, session state and keyed Zap digest stay in private local storage. For schedules, you choose automatic start times; tap your paired Zap to stop. Schedule settings, replay-prevention markers and the latest outcome stay local until that schedule is deleted, you withdraw, clear storage, or uninstall. Android exact-alarm access is requested separately when you enable schedules. Delivery can be delayed or missed; Force Stop requires explicit app recovery and never catches up old starts. For History, the app saves a random session ID, start/end times, duration, selected-app count and whether the session was interrupted. History contains no app names or event log. Session time does not prove continuous blocking. History stays local until withdrawal, clearing storage or uninstall. You can withdraw below, disable Accessibility, Force Stop, or uninstall at any time.

The disclosure is followed by an initially unchecked control reading: “I understand this disclosure and choose to test the Accessibility capability.” Only the user's subsequent button press records consent and opens Accessibility settings. Installation, launch, onboarding, app selection, or NFC scan does not open settings. Current-version consent stays visibly checked until withdrawal. Current wording is `gate0-accessibility-v4`: v1/v2/v3 acceptance is not silently upgraded, and new starts/service use require affirmative v4 consent. Saved selection and Zap pairing remain. Existing v3 sessions retain their prior History eligibility when stopped; v1/v2 sessions are not backfilled. New History-eligible starts use v4.

The quoted v4 text remains unchanged and matches source. The connected editor/list
offers **Allow alarms & reminders** only when a draft or saved schedule requests
enabled starts and access is missing. Only pressing that button opens Android's
Settings; saving or toggling a schedule alone does not grant access or open Settings.
Enabled intent can remain saved with **setup required**. Verify refusal/recovery
and the final UI before using this text as release evidence. Declaring
`SCHEDULE_EXACT_ALARM` is not a claim that access was granted or delivery succeeded.

## User control and escape

The service is manually enabled in system Accessibility settings and can be manually disabled there. The white **Keep going** primary action returns to Justnoise; separate overlay buttons open Accessibility settings, App info/Force Stop, and Home. Justnoise, launchers, Settings, System UI, permission/package surfaces, installer/store routes, dialer/emergency packages, and resolved Home/dialer packages are excluded from target selection. Withdrawal attempts to remove local Gate 0 state and requests self-disable; the local follow-up reports incomplete cleanup instead of claiming successful deletion. The History extension must additionally clear its pending record/database through a durable deletion marker and report success only after all cleanup succeeds. Force Stop and uninstall remain user-controlled escape routes; a failed History insertion must not re-block a committed stop.

The schedule runtime also clears local definitions/execution/recovery state and
cancels the alarm on withdrawal; complete-cleanup feedback includes cancellation.
Force Stop/uncertain startup suspends background scheduling until explicit current-
consented foreground recovery discards due starts and acknowledges future work.
API 31–34 exit evidence is conservative and can also require recovery after Recents
removal; this is not claimed to be exact Force Stop attribution on those versions.
The schedule editor supports explicit Cancel and confirmed **Reload saved version**;
stale revisions are not silently overwritten. Rotation retains the one in-flight
mutation; process-death restoration warns when its outcome may be uncertain and
does not automatically retry. Disabling or deleting a schedule removes future
starts, not an already-active session; that session still requires the paired Zap
or the documented user-controlled escape routes.

## Static review checkpoint — 2026-09-02

Scope: internal source/package review, not Google Play acceptance or a completed
G0-13/G0-18 device cell. The integrated APK inspected at this checkpoint was source
`d09833d`, SHA-256
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
Both spike/integrated source and merged debug manifests were inspected; `aapt2`
also inspected the integrated APK's permissions and packaged service XML.

| Boundary | Static finding | Remaining proof |
| --- | --- | --- |
| Permissions | NFC plus AndroidX's app-scoped signature receiver permission; no Internet, broad package visibility, usage-access, or draw-over-apps permission in the inspected APK | Recheck every release dependency and merged/packaged manifest; existing source checks do not exhaustively cover merged dependency manifests |
| Accessibility | Packaged metadata remains window-state-only, `canRetrieveWindowContent=false`, `isAccessibilityTool=false`, with no retrieval flags | Runtime logs/network observation and complete physical safety matrix |
| Consent | Shared setup shows separate disclosure before an unchecked control; the enable button records consent before opening settings; the service refuses missing/current-version-mismatched consent | Record fresh install, refusal, re-entry, acceptance, revocation, and re-consent on the actual release flow |
| User-defined behavior | Exact selected-package rule during a user-started session; no autonomous planning, content-based decisions, or simulated control of the target app | Product/policy acceptance of the observed interruption behavior and limits |
| Data | Reviewed app source has no logging/network client; inventory now explicitly includes event-type guard, service-enable metadata, protected packages, and overlay geometry | Dependency/runtime review is still required; static absence is not a network trace |
| Withdrawal | Review found that setup ignored the cleanup Boolean and always claimed deletion; follow-up now maps success/failure to distinct feedback and was later installed while idle | Fault-injected deletion and recovery remain untested; installation is not proof of deletion behavior |
| Release identity | This checkpoint inspected a debuggable prototype with an exported Compose preview Activity, not a release artifact | Review production ID, signing, release-only dependencies, declarations, and final App Bundle after the applicable beta checkpoint |

The withdrawal UI wiring regression failed before the fix. Afterward, both pure
feedback tests and all nine static-policy tests passed, followed by the complete
`scripts/verify-android-predevice-readiness.sh` run (contracts, JVM tests, lint,
and all development APK builds). The follow-up integrated APK SHA-256 is
`c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`;
packaged permission/metadata inspection remained unchanged. It was initially kept
off the phone during navigation testing, then installed with `adb install -r` after
idle/revision `19` was confirmed. The installed checksum matches; pairing record,
Chrome selection, idle state and a connected/advancing service heartbeat were retained.
See the build-scoped September 2 record. No paired-device instrumentation, consent
withdrawal, or iOS changes were made.

Google's [Accessibility guidance](https://support.google.com/googleplay/android-developer/answer/10964491)
permits deterministic user-defined automation but requires a declaration and separate
in-app disclosure/affirmative consent for non-accessibility tools. This supports the
candidate's intended boundary; it is not approval of Justnoise.
The [sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241)
also requires accessible disable/uninstall routes and listing disclosure. Missing
system controls therefore remain a release issue until physically verified, even
when the app offers its own escape buttons.
The [package-visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779)
requires the narrowest justified inventory access. Scoped launchable-app discovery
has a documented selection purpose; removing `QUERY_ALL_PACKAGES` alone does not
establish approval of the complete product.

## Minimal internal-beta reviewer walkthrough — draft

Updated for the real first-run, calendar, Settings and completion UI on 2026-09-06. These are review
instructions and expected outcomes, not a claim that every step
has passed or that this source is installed on the A16. Build-specific single-target results remain in
[the September 2 evidence](A16_PARTIAL_RUN_2026-09-02.md).
The native schedule flow below is connected in source. The instructions describe
checks to perform, not completed emulator UI or A16 acceptance; reconcile every
label and observed result with the final release before submitting them.

- **Current development app:** `com.justnoise.androiddev`, label **Justnoise Android Dev**.
  The setup screen still uses Gate 0 wording. Do not upload its debug or unsigned
  development artifacts as the release.
- **Planned internal-test release:** `com.justnoise.app`, public name **Justnoise**.
  Registration, signing and a Play-delivered release are not established by this draft.
  Reconcile the labels below with the final release UI before supplying instructions.
- **Access:** no app login, subscription or review account is needed. An unlocked
  NFC-capable Android 12+ phone and a compatible physical tag containing one valid
  UTF-8 NDEF Text record are needed. Pair it through the visible local pairing flow;
  no private production payload, factory allow-list or hidden review bypass is used.

1. Begin with a fresh review installation on the agreed test device, not by clearing
   the owner's existing paired app. Follow the two introductory pages to Zap
   activation and pair the supplied physical tag. Pairing is device-local; it does
   not create an account, verify a purchase, grant consent or start a session.
   Use the explicit **Continue setup** action to open separate Android setup.
2. Read the separate Accessibility disclosure. Initially leave the consent box
   unchecked: **Continue to Accessibility settings** is disabled. Leave and return
   to demonstrate refusal/re-entry. Then explicitly check the box, press that button,
   and manually enable **Justnoise Android Dev app blocking** in Android settings.
   For a separate upgrade check, verify that v1/v2/v3 consent does not bypass v4
   acceptance, while selection and pairing remain present. A prior v3 active
   session keeps its History eligibility at stop; v1/v2 sessions remain stoppable
   without creating backfilled History.
3. Return to the main screen and open the mode selector. Create or edit a named
   mode, choose multiple eligible launchable apps, then **Save**. Explicitly select
   the mode for manual starts; saving alone must not change that selection. Show
   that reopening restores the saved selection and **Cancel** discards unsaved
   edits. Verify these new labels/actions against the final mode-enabled build.
   An empty or missing selected mode cannot start; unavailable/protected saved
   rows are removal-only. If replacement pairing is demonstrated, its separate
   native Setup action must ask for confirmation while idle; cancel must preserve
   the original Zap. Do not replace the owner's real pairing for review footage.
4. Tap the large Zap artwork to start the foreground reader, then scan the paired
   tag. Open the
   selected apps in turn: each should show its own name on the Justnoise interruption.
   **Keep going** returns to Justnoise without ending the session; opening a selected
   app again should block. While the session is requested, **Select apps to block**
   and all mode mutation/selection controls should be disabled, with guidance to
   stop using the Zap before editing targets. Recovery also prevents mode changes.
   Non-selected and protected
   system apps must remain usable.
5. After at least three seconds, use **Unzap Phone** and scan the paired tag;
   all selected apps should become usable and selection editing should return. Also demonstrate the advertised direct
   route from unlocked Home: tap the paired tag to start or stop without first opening
   Justnoise. Report any chooser or failure honestly; do not describe a foreground
   scan as a successful direct tap.
6. Open the real **History** calendar. The new v4 completion should contribute once
   to the summary and its local day; reopening must not duplicate it. Check the
   **90 Days** and current-year grids and selected-day details. The summary says
   **Session time**, not verified protected time. Interruption/timing uncertainty
   stays visible, with no app names, foreground-event log or weekly insights.
   A foreground stop lasting at least 20 minutes also shows its exact completion
   sheet; shorter sessions still enter History. Direct background taps must not
   open that sheet later. History's Settings entry exposes the privacy notice and
   read-only introduction replay without resetting setup or pairing.
7. Open **Schedules**, press the trailing **+**, name a future schedule and choose **One time**
   with the native date/time controls, or **Repeat weekly** with explicit weekdays.
   Choose the schedule's own **Mode**, confirm its app count and **Schedule enabled**
   choice, then **Save**. A different recent manual mode must not replace this
   reference or be changed by the schedule.
   The list must show the persisted record and distinguish enabled intent
   from setup readiness. Refuse alarm access first: no grant or automatic Settings
   launch should occur. When an enabled start is requested, explicitly press
   **Allow alarms & reminders** and make the choice in Android Settings. Reopen/edit,
   Cancel, and confirmed **Reload saved version** should retain or discard only the
   intended changes; stale revisions must not overwrite a newer saved record.
8. With setup ready, observe a due configured start without a manual start scan,
   then stop with the paired Zap and check one corresponding History completion.
   The schedule's **Last result** is a historical outcome, not current protection
   status. Record delayed/missed delivery and explicit stopped-state recovery
   honestly; recovery must not catch up old starts. Disable/delete removes future
   occurrences without stopping an active session. On disposable state, verify
   rotation and process-recreation: an uncertain save requires review/reload and is
   never automatically repeated. Withdrawal clears an observed draft, and queued
   actions under the old consent must remain rejected after re-consent.
9. Show the ordinary Home/Back and escape routes. **Disable Accessibility** opens
   Android settings; **App info / Force Stop** opens app controls. In a deliberate
   cleanup test, **Withdraw consent and clear Gate 0 data** requests local cleanup and
   service disablement, including pending/completed History and schedule/execution
   deletion plus alarm cancellation. Show its result,
   real empty History after successful cleanup and fresh consent requirement. Do not place an
   emergency call. Do not repeat an already-recorded owner stop/browse confirmation
   merely to prepare these instructions.

For the declaration video, capture launch, the entire disclosure, refusal/re-entry,
affirmative consent, manual enablement and the core interruption. Add short captions
where needed. These elements follow [Google's Accessibility video guidance](https://support.google.com/googleplay/android-developer/answer/10964491).
Give reviewers workable physical-tag access instructions/material; a video must not
be assumed to replace access to restricted functionality. See [app review access](https://support.google.com/googleplay/android-developer/answer/9859455).

## Justnoise Android internal beta — Privacy Policy draft

Draft updated: 2026-09-07. This text is for the planned local-only `com.justnoise.app`
beta with local History and the connected native schedule flow, not a claim that device or release
acceptance has finished. It does not describe the iOS app, web shop, or Android/Google Play's
own services. Validate it against the release package before publication.

**Draft — not yet published.** The owner supplied the publisher and contact below
on 2026-09-07. The public Android privacy-policy URL is still a placeholder, and
final release review remains pending. Business-registration details and email
delivery have not been independently verified.

**App/brand:** Justnoise

**Publisher:** Justnoise Media

**Privacy contact:** hello@justnoise.shop

**Public privacy-policy URL:** [YOUR PUBLISHED ANDROID PRIVACY POLICY URL]

For questions about this policy or your local app data, contact the publisher using
the privacy contact above.

Justnoise helps you interrupt apps you select during a focus session. This candidate
has no app accounts, purchases, cloud sync, advertising, analytics or crash-reporting
service. It does not transmit the data below to us or third parties or sell them.

While you have enabled its Accessibility service, Justnoise receives window-change
event types and foreground app identifiers, including when no focus session is active.
Only the latest identifier is held temporarily in service memory to re-evaluate an
already-open target at scheduled start; it is cleared on screen off, service
interruption/disconnection and uncertain locked/noninteractive refresh. There is no
identifier sequence, disk retention or event log. The interruption is shown only for an app in
the active session's saved target snapshot during an eligible active session. It does not read screen contents, typed text,
passwords, notifications or accessibility nodes, or save a foreground-app history.

A temporary generation counter and main-thread readiness acknowledgment check the
same bound service/foreground state during scheduled-start preflight. This is not
an atomic guarantee of OS readiness through later disk I/O; uncertain state after
start follows the existing interruption/recovery path. The
acknowledgment waits at most 750 ms; its readiness lease lasts at most one second
and becomes invalid after a relevant state change, disconnect or expiry. These
in-memory values are not saved, logged or used as telemetry.

You can keep up to 30 named modes locally. Each stores a stable ID, revision, a
trimmed name of up to 60 characters and up to 500 chosen app identifiers. The app
also stores the last mode you explicitly selected for manual Zap starts. Saving or
running a scheduled mode does not change that selection. Missing or empty modes
cannot start and are never replaced by another mode. An existing saved selection
migrates to Noise with the stable local-default ID; no old selection means an empty
catalog. Deleting all modes does not recreate defaults. Mode edits, deletion and
selection are blocked during a requested session or recovery. Any referencing
schedule, including a disabled one, prevents mode deletion. Deleting the recent
mode clears that selection without fallback. These data remain until edited or
deleted, withdrawal, app-data clear or uninstall. Mode names and identifiers are
not added to completed History or notification content.

The local app picker reads launchable app names and identifiers. Justnoise also reads
system app identifiers, service-enablement metadata and screen/inset geometry for
readiness and safe navigation. These observations are temporary. Session and pairing configuration includes your
selected targets, active-mode ID/name, active-session identity/target snapshot/clocks/process identity,
last-finished session ID, consent version/time, session state/revision,
interruption/recovery and service-liveness state, and the
keyed digest used to recognize your paired tag.
The first-run introduction also saves a local completed/not-completed flag. Android
temporarily retains its current page, route and a failed-save marker when recreating
the screen. These values do not grant Accessibility consent or start a session, and
the completed flag is removed with local withdrawal, app-data clear or uninstall.
The silent introduction videos are bundled in the app. The optional Zap-store link
opens an external website in your browser; that website has its own privacy terms.
An originating schedule ID is also stored only while its scheduled session is
active, for recovery when the foreground state is unknown. It is removed on
successful stop, a manual start, withdrawal, app-data clear or uninstall, and is
not included in completed History.
NFC Text data is processed temporarily, not saved or logged; the tag's hardware
identifier is not read. Configuration uses private app storage, and the digest's
non-exportable key is managed by Android Keystore. Configuration is not claimed to
be wholly encrypted. The app excludes its data from Android backup and transfer;
this is not a guarantee about every manufacturer's restore behavior.

For History, Justnoise saves a random session ID, start and end times, non-negative
duration, number of selected apps and whether continuity was interrupted. Completed
History contains no app names or foreground-event log. It shows **Session time**, not
verified continuously protected time; cross-process timing is marked interrupted.
Stopping clears the active-mode ID/name, target snapshot and clocks and queues one local completed
record for retryable insertion into a private SQLite database before a later start
or History read. Stopping itself does not wait for database delivery. Repeated insertion of
the same session ID must not add another record. History stays on this device until
withdrawal, app-data clear or uninstall; there is no automatic expiry in this slice.

The schedule runtime stores up to 100 configurations in private app
preferences: a random schedule ID, revision, name, enabled flag, one-time instant
or repeating weekdays/local time, named creation time zone and the chosen mode ID.
Existing references to `local-default` retain the migrated Noise mode. A schedule
resolves its own mode's then-current apps at start, not the recent manual selection;
missing or empty references cannot start and do not fall back to another mode.
It also stores one pending occurrence, independent last-resolved local-date and
one-time-instant markers, and only the latest outcome for each schedule. An
occurrence contains schedule ID/revision, nominal key, fire time, local date where
applicable and zone; an outcome adds a result code, handled time and session ID
only if a start committed. Markers survive edits to prevent replay. Configuration
and execution records remain until that schedule is deleted, consent withdrawal,
app-data clear or uninstall. Completed scheduled sessions use the same History
path and retention as manual sessions; deleting a schedule does not delete History.
The native editor offers one-time date/time or repeating weekdays and uses the
referenced mode's current app selection at each start. A retained ViewModel and Android-managed
saved-instance-state Bundle may keep the route and unsaved editor fields, original
revision and original one-time instant/zone for Activity/process recreation. This
adds no separate app preference/database schema. Unsaved state is discarded on successful save,
Cancel/explicit discard or observed consent revocation. If the process ends while a
save may be in progress, restoration warns that its result is uncertain; the app
does not automatically repeat the change. Explicit reload discards unsaved edits.
A transient consent timestamp frozen for each mutation rejects queued changes after
withdrawal, even if current consent is later accepted again.

The app temporarily checks its own process startup evidence: PID/name and Force Stop
status on Android API 35+, or its own exit reason/time on API 31–34. It does not save
or log those raw records or inspect other apps' process history. A local suspension
flag and foreground-recovery acknowledgment time prevent unattended recovery;
the flag clears after successful acknowledgment and the timestamp remains until
replaced, withdrawal, app-data clear or uninstall. Older Android exit evidence is
conservative and can require recovery after other user-requested process exits.

Schedules use separately granted exact-alarm access, boot/system-change callbacks
and a partial wake lock. A non-exported receiver revalidates local state with at
most one running and one queued delivery; each has a 9-second wake-lock lease and
an 8-second processing budget including queue wait. The alarm is replaced/canceled
through Android; worker state is transient. A callback alone is not proof of a
session start. Delivery can be delayed or missed, and stopped-state recovery does
not catch up old starts. No new network destination or analytics is introduced.
The separate alarm Settings button is offered only for requested-enabled schedules
with missing access and opens Settings only when pressed. Saving alone does not
grant access; enabled configuration can be retained while setup is still required.

**Withdraw consent and clear Gate 0 data** attempts to remove saved modes/recent selection,
active-mode identity and other configuration,
pending/completed History, schedule/execution/recovery state and the pairing key,
cancel the alarm and disable the service. A durable
cleanup marker retries an interrupted database deletion before new consent, session
start or History access. Success and incomplete cleanup have different feedback;
if cleanup cannot be confirmed, retry or use
Android App info to clear storage or uninstall. Disabling Accessibility alone stops
service access but does not erase saved configuration or History. There is no server
account to delete. Raw foreground events and NFC payloads are not retained as History.
Affirmative v4 disclosure consent is required for new starts/service use; earlier
consent is not silently upgraded. Existing v3 active sessions keep their prior
History eligibility; v1/v2 sessions are not backfilled. Verify the connected
schedule editor/enable and separate exact-alarm permission flow before release;
local checks and the bounded emulator UI smoke are not full UI, A16 or policy acceptance.

If you separately allow notifications, Justnoise can display generic session time
or an attention message in Android's notification panel and, depending on your
system settings, on the lock screen. It includes no mode names/IDs, selected-app names or tag
data. Tapping opens the app; dismissing does not end the session. One matching
session ID is saved locally to keep a dismissed display hidden for that session;
it is removed with withdrawal, app-data clear or uninstall. Notification refusal
does not prevent sessions. The display is not proof of uninterrupted blocking:
if Android abruptly ends the process, its last display may remain until the next
reconciliation. Known stops and withdrawal cancel it. There is no notification
foreground service, tracking or network destination.

The detailed implementation inventory is [DATA_INVENTORY.md](DATA_INVENTORY.md).
Before publishing, confirm the supplied contact can receive messages, review the
final release's actual data handling, host this notice at a public non-geofenced HTML URL, and expose the policy
in the app and Play Console. The current separate Accessibility disclosure is not a
substitute for a full privacy policy. These publication requirements follow [Google's User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
Apps exclusively on internal testing are exempt from the Data safety form, not the
applicable privacy/disclosure duties; see [Google's Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469).

## Facts needed before using these drafts externally

- Publish the Android-specific notice and replace the public policy URL placeholder.
  The publisher/contact above are owner-supplied, not independently registry/mailbox
  verified. The repository's existing shop-policy link is not
  evidence that this new notice is published or covers the Android beta.
- Verified Play account/track eligibility, registration and signed release identity,
  final UI labels, and the actual reviewer-access/video URLs and physical-tag provision.
- Verified v4 migration with preserved v3 History eligibility, interrupted-time
  labels, duplicate/persistence-failure recovery, bounded schedule delivery and
  complete withdrawal across preferences/database/key/alarm; the release editor,
  exact-alarm refusal and stopped-state recovery flows must be usable and recorded.
- The smaller A16 beta safety decision, any policy issues resolved for the actual
  release, and explicit owner approval to upload. A17 and the full 19-cell public
  reliability matrix are not additional prerequisites for this beta draft.

## Reviewer evidence to attach later

- For the first internal beta: the smaller A16 safety-checkpoint evidence and separate beta decision from `docs/ANDROID_GATE_0.md`, including the exact model/build, source and APK identity, and known limitations. The complete 19-cell A16 matrix is required for the later full-product/public-release decision, not this first learning milestone. A17 validation is deferred/unverified and non-blocking; a second phone is not required. Do not present A16 evidence as tested A17 compatibility.
- A short video beginning at app launch and showing the entire disclosure (scroll slowly if needed), initial unchecked consent, refusal/back-out, re-entry, affirmative acceptance, manual settings enablement, and the core selected-app interruption. Add captions where the Accessibility use is not obvious. Include revocation/re-consent and separately demonstrate Force Stop, uninstall, and all overlay escape routes. See [Google's required video flow](https://support.google.com/googleplay/android-developer/answer/10964491).
- Merged-manifest and static-policy reports.
- Redacted logs plus network observation matching `DATA_INVENTORY.md`.
- Exact store listing and privacy disclosures.
- Product explanation of onboarding burden, bypass behavior, battery/OEM caveats, and why the outcome is valuable.

Internal review cannot guarantee acceptance. A policy uncertainty is `HOLD`; complete the requirements of the applicable Play test track and account before distribution. The initial milestone is internal testing. A separate closed test is required only where the account's production-access rules or the later release plan require it; internal testing does not itself grant public-release approval.

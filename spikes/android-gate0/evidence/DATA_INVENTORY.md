# Gate 0 local data and Accessibility inventory

Status: draft for technical, privacy, and Google Play policy review

October 11 session shortcuts: `emergency_unlocks_remaining_v1` is one integer
in private `gate0_local_state` preferences, initially five, clamped to 0..5.
It belongs to the installation, not a workspace; account deletion/switching and
consent withdrawal retain it. A confirmed Emergency Unzap decrements it in the
same commit as active-state removal and the existing completion outbox. Failed
writes restore both. Uninstall/app-data clear removes it; no replenishment or
network/analytics field is added. The confirmation stores only an existing session
UUID in the current Android UI saved state, never app targets or NFC data.
Long-press start reuses the same session storage and existing bounded start event;
it collects no new data and does not grant any permission.

September 19 scheduled-start diagnosis: the process holds one current readiness
health enum (for example main-thread timeout, unknown foreground, expired lease,
or ready). Android's read-only service dump exposes only that allowlisted enum.
It contains no package/class names, generation values, account/mode/schedule/session
IDs, timestamps, NFC content or UI text. It resets on process death or the next
attempt; no event log, persistence, network, analytics or normal UI field is added.
Readiness decisions and the 750 ms acknowledgment / 1 second lease are unchanged.

Production-only September 9 extension: `android-app` now compiles the Supabase
Auth/Functions adapter and requests INTERNET; this does not add networking to this
capability app or the integrated development build. Explicit future account actions
send email/password or session credentials to the existing Supabase project over
HTTPS. Passwords are not stored; returned sessions must pass the encrypted journal
and account coordinator. No app selection, mode names, History, tag data or
Accessibility observations are included. SDK logging and automatic persistence/
refresh are disabled. No live account request has been performed; account UI and
pending-flow integration remain unfinished. See the production provider evidence.

September 9 connected-release foundation: the canonical local engine now supports
separate product workspaces without adding any network/auth SDK to the capability
app. Existing data remains `local`. A future production account coordinator can
select a validated UUID workspace; this API does not authenticate that UUID.
Versioned `workspace_profile_v1_*` preference archives hold only modes, schedules
and execution receipts; the current owner/epoch and pending-handoff Boolean guard
selection and stale work. History keeps its existing six-field row schema in one
private database per workspace (`session_history.db` remains the legacy local
file). No identifiers, mode/app sets or history are sent to a network destination.
Pairing, disclosure and onboarding stay installation-owned. Global withdrawal
retains `history_delete_workspaces_v1` until every known History store is cleared.
These fields are new source behavior, not a change to the owner's installed APK.

September 7 approved extension: Android named modes are implemented in the development source. The
local catalog adds mode IDs, names, revisions, app sets and the last explicitly
selected mode ID. Schedules reference their own mode; an active session keeps its
mode ID/name and fixed target snapshot until stop. Completed History
gains no mode names or identifiers. The September 9 owner-approved Live
Update extension displays the active mode name locally on system surfaces, never
target names or analytics. This is a source inventory, not
new device acceptance; the dated evidence below remains historical.

Gate 0 and the integrated development app have no Internet permission, backend client, account, analytics SDK, telemetry, crash reporter, or network destination. Their main app sources contain no logging calls. The inventory below is the intended data boundary, including platform metadata outside Accessibility events; device evidence must confirm it with reviewed logs and network observation. See the [September 2 static review](PLAY_DECLARATION_DRAFT.md#static-review-checkpoint--2026-09-02) for the inspected build and limits.

The multi-app baseline contains multiple selected identifiers and a separate active
target snapshot; its dated A16 evidence remains the earlier single-target build.
This inventory also covers the source-level local History integration designed in
[`ANDROID_SESSION_HISTORY.md`](../../../docs/ANDROID_SESSION_HISTORY.md). The final
36-test emulator suite includes 12 passing History tests, and the full local verifier
passed; see the [readiness record](../../../docs/ANDROID_PREDEVICE_READINESS.md).
A16 acceptance, device privacy observation and OEM recovery remain unverified.
The schedule-runtime slice now adds persisted execution state, atomic scheduled
dispatch, exact-alarm/system-change adapters, startup recovery and a bounded
in-memory foreground observation. The earlier runtime checkpoint had no schedule UI
caller. Current source connects a native schedule list/editor, revision-checked
create/edit/enable/delete, latest outcomes and an
explicit exact-alarm Settings entry. The [connected editor evidence](emulator-schedule-ui-2026-09-03/README.md)
records passing local checks and a bounded emulator UI smoke. The later
[draft-guard candidate](A16_MVP_CANDIDATE_2026-09-03.md) passed 161 JVM tests and
the full local verifier; its phone update remains pending. These are not full UI,
A16 privacy/delivery or release acceptance.
See the [schedule plan](../../../docs/ANDROID_SCHEDULE_PLAN.md).

| Source | Field or derived value | Purpose | Processing | Persistence and retention | Network destination |
| --- | --- | --- | --- | --- | --- |
| Optional session Live Update | Existing session UUID, elapsed start clock/process match, active mode name (at most 60 characters), consent/readiness projection, own notification permission/channel availability and own posted promotion flag | Show a real session timer and selected active-mode snapshot; tap opens Justnoise, never stops or authorizes a session. No ordinary/recovery notification fallback | One native Android notification and system chronometer; mode name is visible on user-enabled system/lock-screen surfaces. No selected-app names, tag values or foreground observation are placed in its content; no notification-listener access | Cancel on stop, recovery, refusal or dismissal. Check own promotion flag after posting and during existing refreshes; cancel rejected/demoted ordinary items. No foreground service or new periodic job. Abrupt process death may leave the last requested-session display until reconciliation; it is not proof of uninterrupted blocking | None |
| Notification dismissal | One matching session UUID (`dismissed_notification_session_id`) | Respect dismissal across refresh/recreation without ending a session; reject a stale dismissal for a newer UUID | Existing private preferences, best-effort commit with process-memory fallback; non-exported receiver and session-specific immutable PendingIntent; no session revision or history change | Replaced by a later session dismissal; ignored for any other session; cleared with withdrawal/app-data clear/uninstall | None |
| Accessibility `TYPE_WINDOW_STATE_CHANGED` | `packageName`, `eventType`, transient `className` equality with the framework `android.inputmethodservice.SoftInputWindow`; for our own package only, class lookup against our declared Activity metadata | Distinguish keyboard windows and blocker self-events from actual app transitions; never leave the blocker above Justnoise's real UI | In the service callback; exact keyboard comparison or own-package Activity lookup only. The transparent DirectNfcActivity is excluded so a validation trampoline does not expose a blocked target. No event content, node/window retrieval, class-name retention or new subscription | No event log, disk persistence or event queue | None |
| Current input-method setting | `Settings.Secure.DEFAULT_INPUT_METHOD` component, reduced to its package identifier | Confirm that a framework SoftInputWindow belongs to the currently selected keyboard; do not ignore keyboard settings/activity windows | Read locally only for that exact class; no input connection, keyboard text, enabled-keyboard catalog or new permission | Transient lookup only; no cache, persistence, logging or network | None |
| Latest foreground observation | At most one package identifier (`lastForegroundPackage`) | Re-evaluate the already-foreground target after a committed scheduled start without reading window content | Service memory only; replaced by window-change observations, excluding the blocker-owned self event and the exact current-IME SoftInputWindow; keyboard activity/settings windows still replace context | Cleared on screen off, interruption, disconnect, withdrawal/consent mismatch and uncertain locked/noninteractive scheduled refresh; no sequence/history, logging or persistence | None |
| Scheduled-start readiness | Foreground generation counter, bound-service reference, main-thread acknowledgment and elapsed-time lease expiry; one allowlisted health enum | Preflight rejects a stale/unresponsive service or changed foreground state; it is not an atomic guarantee of OS readiness through subsequent disk I/O | Process memory only; main-thread preparation waits at most 750 ms and yields a lease valid for at most 1 second, checked against the same bound endpoint/generation. Only the health enum is exposed through a read-only service dump | Invalidated by foreground/lifecycle changes, disconnect or expiry; not persisted, logged, collected as event history or sent to telemetry. Health enum is replaced by the next attempt and lost with process death | None |
| Accessibility event | Node tree, source, text, content description, password state, notification content, window list | Not used | APIs are not called; `canRetrieveWindowContent=false`; no retrieval flags | None | None |
| Scoped launcher query | App label and package identifier for launchable activities | Display the local multi-app picker and the matched foreground app name on the blocked surface | In memory while the picker or blocked surface is built | Only chosen package identifiers persist; labels are resolved locally | None |
| Platform safety resolution | Home activity package identifiers, resolved Settings/uninstaller package identifiers, and default dialer package; fixed protected-package list and own package | Exclude system/escape apps from selection and enforcement | Transient local PackageManager/TelecomManager queries and set comparison | Not persisted, logged, or collected as history | None |
| Accessibility enablement setting | Flattened enabled-service component list from `ENABLED_ACCESSIBILITY_SERVICES`; derived Boolean for this app's service | Report whether the user enabled Justnoise, separately from service heartbeat | Transient parsing and exact comparison with Justnoise's expected component | Component list is not retained; the Boolean is recomputed for readiness | None |
| Named modes and recent selection | Mode ID, positive revision, trimmed name, selected package set, and last explicitly selected mode ID | Keep up to 30 local app selections (60-character names; up to 500 targets each); manual start resolves only the explicit selection | Schema-v1 JSON under `modes_v1` in private `gate0_local_state` SharedPreferences; decode cache in process memory. Save does not select; mutation/selection rechecks consent, revision and idle/non-recovery state under the existing lock | Until changed/deleted, withdrawal, uninstall or app-data clear. Any referencing schedule, including disabled, prevents mode deletion; deleting the recent mode clears its ID without fallback | None |
| Legacy selection migration | Existing `selected_packages` or `selected_package` only when no mode catalog exists | Preserve an existing selection as stable `local-default` mode Noise, including a previously empty selection | Absent catalog projects legacy selection once; a normal catalog commit stores the migration and removes old selection keys. No legacy key means an empty catalog | No default repopulation after catalog deletion. Existing schedule references remain `local-default`; migration does not authorize a start | None |
| App-selection editing | Unsaved package set, locally resolved choices, unavailable/removal-only markers, loading/saving state and opaque edit token (process nonce, consent generation, existing session revision and consent version/epoch) | Run discovery/validation/storage off the UI thread; reject stale drafts after withdrawal, session changes or another edit; preserve explicit Save/Cancel | Process memory only; final token and state rechecked under the existing preferences lock; no platform discovery inside that lock | Unsaved draft discarded on Activity pause/recreation; an already-confirmed Save may finish once but is never replayed automatically; no token or draft persisted, logged or transmitted | None |
| Introductory flow | Tutorial-completed Boolean (`onboarding_completed_v1`); current page/route, failed-save Boolean and temporary busy/error/scan-purpose state | Show two real first-run education pages before initial pairing without granting consent, replacing an existing Zap, or blocking existing session recovery | Completion only in existing private preferences; page/route and failed-save Boolean in Android saved-instance state; busy/error copy and explicit NFC-purpose token in memory. Existing pairing/setup bypasses new education, not permission checks | Completion removed with withdrawal/app-data clear/uninstall; page/route/failure marker retained for Activity recreation only as managed by Android; no new session/history fields or telemetry | None |
| Grouped Settings and tutorial replay | Settings/privacy route and optional educational page index; installed app version and existing truth summary | Restore navigation and replay education without changing tutorial completion, consent, pairing or session authority; explicit links to native setup/App info | Route/page only in Android-managed saved-instance-state Bundle; existing truth/version read locally; privacy draft text bundled as native resources, placeholders nonclickable | Navigation metadata may survive recreation; no session rows, app identifiers, contacts or permission grants are saved by these screens | None |
| Confirmed Zap replacement intent | Opaque process/consent/session-revision token, existing keyed digest reference and process pairing generation | Reject a stale replacement after withdrawal/re-consent, a session start/stop, another pairing, failed write or recovery; require explicit replacement confirmation | Process memory only; HMAC/key operations remain serialized before the existing preference lock; token is rechecked atomically with rollback-protected digest commit | Dropped on cancel, pause, recreation or scan completion; never saved, logged, serialized or transmitted; one-shot generation consumed on attempted commit | None |
| Schedule configuration | Schedule UUID, positive revision, name, enabled flag, one-time instant or repeating weekdays/hour/minute, named creation-zone ID, and chosen mode ID (legacy references remain `local-default`) | Define chosen start times and resolve that mode's then-current targets at dispatch without changing the recent manual selection | Schema-v1 JSON under `schedules_v1` in private `gate0_local_state` SharedPreferences; connected native list/editor and revision-checked CRUD require current consent and valid mode at commit; dispatch rechecks the mode under the shared lock; maximum 100 records | Until explicit deletion, withdrawal, app-data clear or uninstall; edits replace configuration, not an edit-history log | None |
| Schedule UI state | Route, unsaved name/date/time/weekday/enabled fields and mode reference, schedule ID/original revision, original one-time instant/zone, and in-flight/uncertain-mutation marker | Retain an editor across rotation and restore it after Activity/process recreation without converting a draft into a saved schedule | Retained AndroidViewModel plus Android-managed saved-instance-state Bundle; no new app preference/database schema. Native date/time controls edit one-time or repeating inputs | Draft discarded on successful save, Cancel/explicit discard or observed current-consent revocation; Android may retain instance state for recreation. An interrupted save is shown as uncertain and is not automatically retried; explicit list review/reload is required | None |
| Schedule mutation consent guard | Frozen accepted-consent epoch captured when a user requests a mutation | Reject queued work after withdrawal, including after a later re-consent; validate alongside schedule revision under the preferences lock | Transient worker action value compared with the existing persisted consent epoch; not a new stored field or permission grant | Until that queued action finishes or the process ends; not included in the saved-instance-state draft | None |
| Schedule execution | Per schedule: at most one pending occurrence (schedule UUID/revision, nominal key, fire epoch, optional local date, zone), independent last-resolved local-date/one-time-epoch high-water markers, and only the latest outcome (code, occurrence, handled epoch, session UUID only if started) | Prevent replay, reconcile delivery and report start/skip/miss; successful receipt and active-session UUID/target snapshot commit together | Schema-v1 JSON under `schedule_executions_v1` in the same private preferences; maximum 100 entries, no unbounded outcome log | Pending replaced/cleared on resolution or edit; high-water markers survive edits; latest outcome replaced on resolution; entry removed with schedule deletion, withdrawal, app-data clear or uninstall. Schedule deletion does not erase completed session History | None |
| Schedule recovery state | Suspended Boolean (`schedule_suspended`) and acknowledgment epoch (`schedule_recovery_acknowledged_at`); process-local startup evidence latch | Prevent background restart after Force Stop or uncertain startup; explicit foreground recovery discards due starts before acknowledging future work | Private preferences plus an in-memory latch; acknowledgment requires current consent | Suspension removed only after successful foreground acknowledgment; acknowledgment replaced by later recovery; preferences cleared on withdrawal/app-data clear/uninstall, process latch ends with process | None |
| Own-process startup evidence | API 35+: process-start PID/name and `wasForceStopped`; API 31–34: own-process name, exit timestamp and whether reason is `USER_REQUESTED` | Distinguish current-process stopped starts; older APIs conservatively require recovery when user-requested/unknown evidence warrants it | Temporary ActivityManager records filtered to this app/process; compare with current PID/name or saved recovery acknowledgment | Raw process records are not retained or logged; only the derived recovery state above persists; no traces or other-app exit history are read | None |
| Schedule delivery adapter | Exact-alarm access Boolean, next fire epoch, package-scoped immutable broadcast PendingIntent; recognized alarm/boot/time/zone/update/access-change action; current wall/elapsed time and device zone | Revalidate the persisted plan, dispatch due candidates and rearm future work; intent extras never authorize a start | Non-exported receiver; `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK`; one running plus at most one queued delivery, each with a 9-second partial-wake-lock lease and 8-second processing budget including queue wait. The editor/list offers an explicit Settings button only when a draft or saved schedule requests enabled starts and access is missing | Alarm is replaced/canceled through the OS; worker/lease/deadline are transient and released on completion/rejection/timeout. No delivery event log; saving alone never grants access or opens Settings | None |
| Screen/lock readiness | Interactive and keyguard-locked Booleans; screen-off broadcast | Avoid showing a cached-target blocker over an uncertain locked/noninteractive surface | Temporary PowerManager/KeyguardManager checks and service callback | Not persisted or logged; screen off clears the latest foreground observation | None |
| Active mode and target snapshot | Mode ID/name (`active_mode_id`, `active_mode_name`) and package identifier set (`active_packages`) copied at start | Keep the session's identity and exact targets stable and identify the matching foreground target | Private `gate0_local_state` SharedPreferences; written atomically with the start state | Removed on successful stop, withdrawal, uninstall or app-data clear. Completed History gains no mode names/IDs or targets. Owner-approved Live Update may display only the active mode name, never target identifiers | None |
| Active scheduled-session ownership | Originating schedule UUID (`active_schedule_id`) | Identify an active scheduled session for truthful unknown-foreground recovery | Same private preferences; written atomically with a successful scheduled start | Exists only during the active scheduled session; removed on successful stop, manual start, withdrawal, app-data clear or uninstall; not added to completed History | None |
| Consent | Disclosure version and acceptance epoch milliseconds | Prove which prominent disclosure was affirmatively accepted | Local SharedPreferences | Until withdrawal, uninstall, app-data clear, or a new version requires fresh consent | None |
| Session state, including History identity | Desired-active Boolean, stable random session UUID, wall/monotonic start timestamps and random process UUID, sticky interruption and History-eligibility flags, monotonic session revision, fail-closed recovery marker | Start/stop guard and reconciliation; identify one completion; distinguish uncertain duration from verified protection | Private `gate0_local_state` SharedPreferences; same-process duration uses monotonic time, cross-process non-negative wall-time fallback is interrupted | Active identity/clocks/flags cleared at stop; eligible completed fields move to the pending record; revision/recovery follow existing transition/cleanup rules | None |
| Last-finished identity | Last completed/stopped session UUID (`last_finished_session_id`) | Recognize an idempotent repeated stop without ending a newer session | Private `gate0_local_state` SharedPreferences; no target names or observed events | Until a later stop replaces it, withdrawal, app-data clear or uninstall | None |
| Pending completion | Session UUID, start/end epoch milliseconds, non-negative duration milliseconds, selected-target count, interrupted Boolean | Bridge one committed stop to durable History without duplicate or lost retry identity | One pending value in private `gate0_local_state` SharedPreferences, written atomically with active-state removal; drain before later starts/History reads, not during stop | Retained until idempotent database insertion is confirmed or withdrawal/app-data clear/uninstall; no package names or event contents | None |
| Foreground completion presentation | Exact newly committed pending-completion value and a transient set of consumed session UUIDs | Show the source-matched completion sheet only after an eligible foreground stop of at least 20 minutes; suppress repeat presentation without querying latest History | Activity memory only; timing uncertainty calculated from the unchanged recorded fields; direct NFC entry ignores the receipt | Visible record cleared on dismissal, background or a new session; UUID suppression lasts only for that Activity; withdrawal drops both record and UUIDs. No saved-instance-state or schema addition | None |
| Completed History | Only session UUID, start/end epoch milliseconds, duration milliseconds, selected-target count and interrupted Boolean | Real History calendar/day details and shared summary; labeled Session time, not verified protected time | Private SQLite `session_history.db`, `sessions` table keyed by UUID; no foreground-event collection | Until withdrawal, app-data clear or uninstall; no cloud backup/transfer or network destination | None |
| History timeline read projection | Requested local date range/device zone; per-day elapsed duration, session count, interrupted count and timing-uncertain count; all-time totals | Drive the approved iOS-style Android calendar from all relevant completed records, independently of page size | Read-only calculation on a worker; split recorded intervals at local midnight and proportionally attribute stored elapsed time; discrepancy above 1,000 ms or nonpositive wall interval is uncertain | In memory for the requested view; no schema change, new persisted field, event source, row rewrite or new retention period | None |
| History presentation state | 90-day/year window and optional selected local date; current read generation/loading state | Restore calendar navigation without serializing session records or replaying a read result | Window/date only in Android-managed saved-instance-state Bundle; range snapshots in a retained ViewModel, cleared on background/navigation and reread on foreground/date/zone change | Android may retain window/date for recreation; no session rows, totals, package identifiers or timing details in saved-instance state; no app preference/database field | None |
| History cleanup state | Database-deletion-pending Boolean | Retry interrupted withdrawal before consent, session start or History read; prevent old records reappearing | Durable marker in private SharedPreferences, written while clearing active state/outbox/configuration | Removed only after database cleanup succeeds; app-data clear/uninstall also remove it | None |
| Failed-write recovery | Pre-commit preference-map snapshot and unconfirmed-write Boolean | Restore in-memory state after `commit(false)` and quarantine History delivery until durable rollback confirmation | Process-local snapshot/flag; restored map uses the same private preferences and recovery marker | Temporary snapshot per commit; quarantine flag until confirmation or process end | None |
| Service liveness | Connected marker and `elapsedRealtime` heartbeat | Reject stale active claims | Local SharedPreferences; refreshed about every two seconds | Removed on known disconnect; invalidated at process start; cleared on withdrawal/uninstall/data clear | None |
| NFC reader | One NDEF message; transient total-record count; transient well-known Text-record count; TNF/type metadata for each record; payload bytes only for the single Text candidate | Pair or authorize one Zap interaction and classify a local scan failure without exposing payload contents | Exactly one Text candidate is required; non-Text auxiliary records are ignored without reading their payload through the adapter; multiple Text candidates are rejected as ambiguous; the candidate is validated as UTF-8 with a 35-byte language and 512-byte text bound; lifecycle cancellation invalidates a late result; an accepted foreground interaction reserves the shared three-second mutation claim before reader mode restores direct dispatch; owned candidate/canonical byte arrays are zeroed after use | Counts and raw/canonical record bytes are not persisted, logged, or sent to analytics | None |
| Direct NFC dispatch | Intent action/MIME, transient live `EXTRA_TAG` object, and process-local elapsed-time mutation claim | Test tap-to-open activation from an unlocked home screen | Only exact NDEF Text dispatch is eligible; the physical tag is re-read through `Ndef`; copied `EXTRA_NDEF_MESSAGES` are never read or trusted; missing/unusable live tags cannot mutate state; a process-local three-second claim suppresses a second mutation caused by tag bounce without reading a tag ID; a bounded process-start handshake waits only for fresh service evidence and otherwise fails closed | Tag data is not persisted, logged, or sent to analytics; the elapsed-time claim lasts only for the process lifetime | None |
| NFC tag object | Hardware tag identifier (7-byte UID) | Verify that an issuer-signed JNZ1 Zap credential is bound to the tapped chip (owner decision 2026-11-21) | Read once per accepted scan in `NdefTagReader` via `Tag.id`, copied into a transient buffer passed to pairing/toggle verification, and zeroed with the canonical record after the scan; legacy labels ignore it | Never persisted, logged, sent to analytics or the network; not part of the HMAC pairing digest | None |
| Android Keystore | Non-exportable HMAC-SHA256 key | Compute a keyed digest of the canonical NDEF Text record | Android Keystore | Until withdrawal, uninstall, or app-data/key clear | None |
| Zap configuration | Base64 HMAC-SHA256 digest | Constant-time local authorization comparison | Local SharedPreferences | Until replacement, withdrawal, uninstall, or app-data clear | None |
| Window layout | Current WindowMetrics bounds, visible system-bar insets, stable display-cutout insets, and calculated overlay rectangle | Keep the actual touch-blocking window outside visible system bars and cutouts | Transient local geometry on show/inset/configuration updates; no view-tree or other-app content access | Current rectangle exists in window layout parameters only; not persisted or logged | None |
| Overlay | Justnoise copy template, matched foreground app display label, and buttons | Interrupt a matching active-session target and expose escape routes | In memory while visible | No overlay content is retained | None |

## Explicit emergency-app protection

The fixed protected-package list now explicitly includes Google Personal Safety,
`com.google.android.apps.safetyhub`, after an emulator picker check found it
selectable. Its [official Google Play listing](https://play.google.com/store/apps/details?id=com.google.android.apps.safetyhub)
describes emergency SOS, emergency sharing and medical-information functionality,
so it must be excluded from selection and enforcement. Its exclusion regression
passed in the 24-test API-36 emulator rerun, with zero failures or skips; see
[`../README.md`](../README.md#multi-app-source-verification--2026-09-03) for the run
and earlier test-synchronization failures. This source-level safety correction adds
no event fields, retention or network destination. It is not Google Play approval
or proof that all emergency surfaces on every OEM device are protected. The dated
A16 evidence remains unchanged.

## Selection migration and disclosure

When the set-based selection key is absent, the legacy single package is read as a
one-item selection. A legacy requested session without an active-snapshot key uses
that restored selection; an explicitly stored empty set never falls back to the old
key. Starting a session writes the set-based selection and snapshot together. These
changes do not erase or replace the Zap digest or Keystore key.

The asynchronous selection editor retains missing or newly protected saved packages
as removal-only rows instead of silently rewriting old configuration. Unchanged
legacy sets remain no-ops; a changed set must pass current platform exclusions.
The editor's frozen token is invalidated at disclosure/withdrawal entry, including
failed withdrawal and same-millisecond re-consent. Failed selection commits restore
the previous in-memory preference map and retain existing storage quarantine.
Pending History deletion rejects selection writes. None of these guards adds a
durable field, changes v4 disclosure, or grants permission/starts a session.

The multi-app baseline used `gate0-accessibility-v2`, and the History integration
used v3. Current source requires `gate0-accessibility-v4`, adding the single temporary
foreground observation, local schedule settings/replay markers/latest outcome,
scheduled start/Zap stop, separate exact-alarm access and delayed/missed/Force Stop
limitations. The exact source text is copied in the [declaration draft](PLAY_DECLARATION_DRAFT.md#prominent-in-app-disclosure-shown-before-consent).
Older acceptance is never silently upgraded; new starts/service use require fresh
affirmative v4 consent without clearing selection or pairing. A pre-existing v3
session keeps its previously granted History eligibility when stopped; v1/v2
sessions remain stoppable without backfilled History. New eligible starts use v4.
The September 3 connected editor used the single saved selection. The September 7
extension resolves the schedule's own mode ID, not a new per-schedule app inventory
or the recent manual selection. Its separate exact-alarm Settings button is user-triggered; an enabled
configuration can remain saved with setup still required. The v4 disclosure is
unchanged by this UI connection. [Local verification and a bounded emulator UI smoke](emulator-schedule-ui-2026-09-03/README.md)
cover the connected draft/list and Settings denial/return flow, not full visual
acceptance, physical delivery or a grant of alarm access.

## Withdrawal and deletion behavior

September 2 baseline: withdrawal first attempts to clear desired enforcement and publishes a state revision so any visible overlay is removed. It attempts to delete the Keystore alias and clear the dedicated Gate 0 SharedPreferences contents, sends an app-scoped non-exported withdrawal signal to the bound service, and asks the service to disable itself. The controller reports success only when both Zap cleanup and preference cleanup return success. Setup uses that result: it displays complete-deletion feedback only on success; a failure message asks the user to retry or use App info to clear storage/uninstall. That diagnostic is shown for 15 seconds instead of the normal 3.5-second success message. The feedback fix was installed as `dbe2469` while idle/revision `19`; see the September 2 record. Installation does not prove deletion under injected storage/Keystore failure.

History extension requirement: atomically clear active intent, pending completion
and configuration while leaving a durable database-deletion marker, then clear
History and remove the marker only on success. Consent/start/read retry pending
cleanup before proceeding. Complete-deletion feedback must also require successful
History cleanup; disabling Accessibility alone does not erase saved History. This
new cleanup path and its injected-failure cases passed emulator tests; this does not
prove every OEM/storage-failure outcome or the paired A16 result.

The schedule runtime uses the same preference file, so withdrawal also clears
`schedules_v1`, execution entries, suspension and recovery acknowledgment. The
controller also cancels the alarm and requires successful cancellation for complete
cleanup feedback. A late callback cannot recreate a deleted definition. Deleting
one schedule clears only its configuration/execution entry, not completed History.
These source paths add no separate database or external deletion destination.
When current-consent revocation is observed, the retained schedule ViewModel clears
its draft. A queued mutation checks the frozen consent epoch at commit, so it cannot
revive an old draft after withdrawal followed by a new acceptance. Process-death
restoration never automatically retries an uncertain mutation; explicit reload or
discard is required before proceeding with that editor.

Re-enabling instructions require a newly accepted current disclosure. Android uninstall or Clear storage is intended to start with consent unchecked because cloud backup and device transfer are explicitly excluded. Failed durable writes and OEM restore behavior remain device-validation cases, not guaranteed deletion outcomes.

## Evidence still required

Production-only extension, September 9: `com.justnoise.app` now implements the
optional `FocusSessionEvents` sink for successful product starts/stops. The
capability/development Applications do not implement it and remain offline.
The sink passes only a transient consent-generation number and start/end Boolean,
not Accessibility observations, packages, mode names, NFC bytes or session IDs.
Production maps these to existing PostHog `focus_session_started`/`focus_session_ended`
events after a separate opt-in. Capture uses an unidentified random ID, no disk
queue/retry, and the existing US ingestion host. Account boundaries revoke consent
and cancel/join pending sends; already delivered network requests cannot be undone.
See `ANDROID_CONNECTED_RELEASE.md` for account storage and production scope. This
does not change the zero-network expectation for the Gate 0 package below.

- Confirm through redacted logs that no foreground package history or NDEF data appears.
- Observe network traffic on the required A16; expected Gate 0 traffic from the app is zero. A17 validation is deferred and non-blocking.
- Confirm OEM backups do not restore consent or pairing; `allowBackup=false` is a source claim until verified.
- Verify History stores only the six declared fields, retries one completion without duplicates, and clears both pending/database records through interrupted withdrawal without claiming premature success.
- Verify v4 re-consent and preserved v3 active-session eligibility; single-observation reset, bounded alarm delivery, explicit stopped-state recovery and schedule/receipt/alarm cleanup on the candidate release. Existing dated A16 evidence does not cover this runtime slice.
- Verify the connected editor's process-death handling, successful exact-alarm setup and physical lifecycle behavior. Local tests and a bounded rotation/Settings-denial/CRUD smoke pass, but they are not full UI or device acceptance.
- Review every dependency and merged manifest from the CI report before policy sign-off.

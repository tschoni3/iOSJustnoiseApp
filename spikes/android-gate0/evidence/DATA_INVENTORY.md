# Gate 0 local data and Accessibility inventory

Status: draft for technical, privacy, and Google Play policy review

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
| Accessibility `TYPE_WINDOW_STATE_CHANGED` | `packageName` and `eventType` | Event type guards the declared callback; package identifier is the only observed app value and is compared with the active target set and Justnoise | In the service callback; the latest package may replace the single in-memory observation below | No event log, disk persistence or event queue | None |
| Latest foreground observation | At most one package identifier (`lastForegroundPackage`) | Re-evaluate the already-foreground target after a committed scheduled start without reading window content | Service memory only; replaced by window-change observations, excluding the blocker-owned self event | Cleared on screen off, interruption, disconnect, withdrawal/consent mismatch and uncertain locked/noninteractive scheduled refresh; no sequence/history, logging or persistence | None |
| Scheduled-start readiness | Foreground generation counter, bound-service reference, main-thread acknowledgment and elapsed-time lease expiry | Preflight rejects a stale/unresponsive service or changed foreground state; it is not an atomic guarantee of OS readiness through subsequent disk I/O | Process memory only; main-thread preparation waits at most 750 ms and yields a lease valid for at most 1 second, checked against the same bound endpoint/generation | Invalidated by foreground/lifecycle changes, disconnect or expiry; not persisted, logged, collected as event history or sent to telemetry | None |
| Accessibility event | Node tree, source, text, content description, password state, notification content, window list | Not used | APIs are not called; `canRetrieveWindowContent=false`; no retrieval flags | None | None |
| Scoped launcher query | App label and package identifier for launchable activities | Display the local multi-app picker and the matched foreground app name on the blocked surface | In memory while the picker or blocked surface is built | Only chosen package identifiers persist; labels are resolved locally | None |
| Platform safety resolution | Home activity package identifiers, resolved Settings/uninstaller package identifiers, and default dialer package; fixed protected-package list and own package | Exclude system/escape apps from selection and enforcement | Transient local PackageManager/TelecomManager queries and set comparison | Not persisted, logged, or collected as history | None |
| Accessibility enablement setting | Flattened enabled-service component list from `ENABLED_ACCESSIBILITY_SERVICES`; derived Boolean for this app's service | Report whether the user enabled Justnoise, separately from service heartbeat | Transient parsing and exact comparison with Justnoise's expected component | Component list is not retained; the Boolean is recomputed for readiness | None |
| User configuration | Selected package identifier set (`selected_packages`); legacy single-package fallback (`selected_package`) | Saved restriction targets for the next session | Private `gate0_local_state` SharedPreferences; explicit Save while idle; Cancel makes no change | Until changed, withdrawal, uninstall, or app-data clear; changed selections are rejected while a session is requested | None |
| Schedule configuration | Schedule UUID, positive revision, name, enabled flag, one-time instant or repeating weekdays/hour/minute, named creation-zone ID, and mode reference (new records use `local-default`) | Define chosen start times and reference the current saved selection at dispatch | Schema-v1 JSON under `schedules_v1` in private `gate0_local_state` SharedPreferences; connected native list/editor and revision-checked CRUD require current consent at commit; maximum 100 records | Until explicit deletion, withdrawal, app-data clear or uninstall; edits replace configuration, not an edit-history log | None |
| Schedule UI state | Route, unsaved name/date/time/weekday/enabled fields, schedule ID/original revision, original one-time instant/zone, and in-flight/uncertain-mutation marker | Retain an editor across rotation and restore it after Activity/process recreation without converting a draft into a saved schedule | Retained AndroidViewModel plus Android-managed saved-instance-state Bundle; no new app preference/database schema. Native date/time controls edit one-time or repeating inputs | Draft discarded on successful save, Cancel/explicit discard or observed current-consent revocation; Android may retain instance state for recreation. An interrupted save is shown as uncertain and is not automatically retried; explicit list review/reload is required | None |
| Schedule mutation consent guard | Frozen accepted-consent epoch captured when a user requests a mutation | Reject queued work after withdrawal, including after a later re-consent; validate alongside schedule revision under the preferences lock | Transient worker action value compared with the existing persisted consent epoch; not a new stored field or permission grant | Until that queued action finishes or the process ends; not included in the saved-instance-state draft | None |
| Schedule execution | Per schedule: at most one pending occurrence (schedule UUID/revision, nominal key, fire epoch, optional local date, zone), independent last-resolved local-date/one-time-epoch high-water markers, and only the latest outcome (code, occurrence, handled epoch, session UUID only if started) | Prevent replay, reconcile delivery and report start/skip/miss; successful receipt and active-session UUID/target snapshot commit together | Schema-v1 JSON under `schedule_executions_v1` in the same private preferences; maximum 100 entries, no unbounded outcome log | Pending replaced/cleared on resolution or edit; high-water markers survive edits; latest outcome replaced on resolution; entry removed with schedule deletion, withdrawal, app-data clear or uninstall. Schedule deletion does not erase completed session History | None |
| Schedule recovery state | Suspended Boolean (`schedule_suspended`) and acknowledgment epoch (`schedule_recovery_acknowledged_at`); process-local startup evidence latch | Prevent background restart after Force Stop or uncertain startup; explicit foreground recovery discards due starts before acknowledging future work | Private preferences plus an in-memory latch; acknowledgment requires current consent | Suspension removed only after successful foreground acknowledgment; acknowledgment replaced by later recovery; preferences cleared on withdrawal/app-data clear/uninstall, process latch ends with process | None |
| Own-process startup evidence | API 35+: process-start PID/name and `wasForceStopped`; API 31–34: own-process name, exit timestamp and whether reason is `USER_REQUESTED` | Distinguish current-process stopped starts; older APIs conservatively require recovery when user-requested/unknown evidence warrants it | Temporary ActivityManager records filtered to this app/process; compare with current PID/name or saved recovery acknowledgment | Raw process records are not retained or logged; only the derived recovery state above persists; no traces or other-app exit history are read | None |
| Schedule delivery adapter | Exact-alarm access Boolean, next fire epoch, package-scoped immutable broadcast PendingIntent; recognized alarm/boot/time/zone/update/access-change action; current wall/elapsed time and device zone | Revalidate the persisted plan, dispatch due candidates and rearm future work; intent extras never authorize a start | Non-exported receiver; `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK`; one running plus at most one queued delivery, each with a 9-second partial-wake-lock lease and 8-second processing budget including queue wait. The editor/list offers an explicit Settings button only when a draft or saved schedule requests enabled starts and access is missing | Alarm is replaced/canceled through the OS; worker/lease/deadline are transient and released on completion/rejection/timeout. No delivery event log; saving alone never grants access or opens Settings | None |
| Screen/lock readiness | Interactive and keyguard-locked Booleans; screen-off broadcast | Avoid showing a cached-target blocker over an uncertain locked/noninteractive surface | Temporary PowerManager/KeyguardManager checks and service callback | Not persisted or logged; screen off clears the latest foreground observation | None |
| Active target snapshot | Package identifier set copied from saved selection at start (`active_packages`) | Keep the session's exact targets stable and identify the matching foreground target | Private `gate0_local_state` SharedPreferences; written with the start state | Removed on successful stop, withdrawal, uninstall, or app-data clear; not appended to a history log | None |
| Active scheduled-session ownership | Originating schedule UUID (`active_schedule_id`) | Identify an active scheduled session for truthful unknown-foreground recovery | Same private preferences; written atomically with a successful scheduled start | Exists only during the active scheduled session; removed on successful stop, manual start, withdrawal, app-data clear or uninstall; not added to completed History | None |
| Consent | Disclosure version and acceptance epoch milliseconds | Prove which prominent disclosure was affirmatively accepted | Local SharedPreferences | Until withdrawal, uninstall, app-data clear, or a new version requires fresh consent | None |
| Session state, including History identity | Desired-active Boolean, stable random session UUID, wall/monotonic start timestamps and random process UUID, sticky interruption and History-eligibility flags, monotonic session revision, fail-closed recovery marker | Start/stop guard and reconciliation; identify one completion; distinguish uncertain duration from verified protection | Private `gate0_local_state` SharedPreferences; same-process duration uses monotonic time, cross-process non-negative wall-time fallback is interrupted | Active identity/clocks/flags cleared at stop; eligible completed fields move to the pending record; revision/recovery follow existing transition/cleanup rules | None |
| Last-finished identity | Last completed/stopped session UUID (`last_finished_session_id`) | Recognize an idempotent repeated stop without ending a newer session | Private `gate0_local_state` SharedPreferences; no target names or observed events | Until a later stop replaces it, withdrawal, app-data clear or uninstall | None |
| Pending completion | Session UUID, start/end epoch milliseconds, non-negative duration milliseconds, selected-target count, interrupted Boolean | Bridge one committed stop to durable History without duplicate or lost retry identity | One pending value in private `gate0_local_state` SharedPreferences, written atomically with active-state removal; drain before later starts/History reads, not during stop | Retained until idempotent database insertion is confirmed or withdrawal/app-data clear/uninstall; no package names or event contents | None |
| Completed History | Only session UUID, start/end epoch milliseconds, duration milliseconds, selected-target count and interrupted Boolean | Real History list and shared summary; labeled Session time, not verified protected time | Private SQLite `session_history.db`, `sessions` table keyed by UUID; no foreground-event collection | Until withdrawal, app-data clear or uninstall; no cloud backup/transfer or network destination | None |
| History cleanup state | Database-deletion-pending Boolean | Retry interrupted withdrawal before consent, session start or History read; prevent old records reappearing | Durable marker in private SharedPreferences, written while clearing active state/outbox/configuration | Removed only after database cleanup succeeds; app-data clear/uninstall also remove it | None |
| Failed-write recovery | Pre-commit preference-map snapshot and unconfirmed-write Boolean | Restore in-memory state after `commit(false)` and quarantine History delivery until durable rollback confirmation | Process-local snapshot/flag; restored map uses the same private preferences and recovery marker | Temporary snapshot per commit; quarantine flag until confirmation or process end | None |
| Service liveness | Connected marker and `elapsedRealtime` heartbeat | Reject stale active claims | Local SharedPreferences; refreshed about every two seconds | Removed on known disconnect; invalidated at process start; cleared on withdrawal/uninstall/data clear | None |
| NFC reader | One NDEF message; transient total-record count; transient well-known Text-record count; TNF/type metadata for each record; payload bytes only for the single Text candidate | Pair or authorize one Zap interaction and classify a local scan failure without exposing payload contents | Exactly one Text candidate is required; non-Text auxiliary records are ignored without reading their payload through the adapter; multiple Text candidates are rejected as ambiguous; the candidate is validated as UTF-8 with a 35-byte language and 512-byte text bound; lifecycle cancellation invalidates a late result; an accepted foreground interaction reserves the shared three-second mutation claim before reader mode restores direct dispatch; owned candidate/canonical byte arrays are zeroed after use | Counts and raw/canonical record bytes are not persisted, logged, or sent to analytics | None |
| Direct NFC dispatch | Intent action/MIME, transient live `EXTRA_TAG` object, and process-local elapsed-time mutation claim | Test tap-to-open activation from an unlocked home screen | Only exact NDEF Text dispatch is eligible; the physical tag is re-read through `Ndef`; copied `EXTRA_NDEF_MESSAGES` are never read or trusted; missing/unusable live tags cannot mutate state; a process-local three-second claim suppresses a second mutation caused by tag bounce without reading a tag ID; a bounded process-start handshake waits only for fresh service evidence and otherwise fails closed | Tag data is not persisted, logged, or sent to analytics; the elapsed-time claim lasts only for the process lifetime | None |
| NFC tag object | Hardware tag identifier | Not used | `Tag.id`/`getId()` is never read | None | None |
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

The multi-app baseline used `gate0-accessibility-v2`, and the History integration
used v3. Current source requires `gate0-accessibility-v4`, adding the single temporary
foreground observation, local schedule settings/replay markers/latest outcome,
scheduled start/Zap stop, separate exact-alarm access and delayed/missed/Force Stop
limitations. The exact source text is copied in the [declaration draft](PLAY_DECLARATION_DRAFT.md#prominent-in-app-disclosure-shown-before-consent).
Older acceptance is never silently upgraded; new starts/service use require fresh
affirmative v4 consent without clearing selection or pairing. A pre-existing v3
session keeps its previously granted History eligibility when stopped; v1/v2
sessions remain stoppable without backfilled History. New eligible starts use v4.
The connected editor uses the current saved selection, not a new per-schedule app
inventory. Its separate exact-alarm Settings button is user-triggered; an enabled
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

- Confirm through redacted logs that no foreground package history or NDEF data appears.
- Observe network traffic on the required A16; expected Gate 0 traffic from the app is zero. A17 validation is deferred and non-blocking.
- Confirm OEM backups do not restore consent or pairing; `allowBackup=false` is a source claim until verified.
- Verify History stores only the six declared fields, retries one completion without duplicates, and clears both pending/database records through interrupted withdrawal without claiming premature success.
- Verify v4 re-consent and preserved v3 active-session eligibility; single-observation reset, bounded alarm delivery, explicit stopped-state recovery and schedule/receipt/alarm cleanup on the candidate release. Existing dated A16 evidence does not cover this runtime slice.
- Verify the connected editor's process-death handling, successful exact-alarm setup and physical lifecycle behavior. Local tests and a bounded rotation/Settings-denial/CRUD smoke pass, but they are not full UI or device acceptance.
- Review every dependency and merged manifest from the CI report before policy sign-off.

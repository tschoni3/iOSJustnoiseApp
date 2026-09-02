# Android MVP schedule plan

Status: **START/ZAP-STOP APPROVED — implementation in progress** (2026-09-03).

Scope: the existing local Android MVP: select apps, real Zap sessions, schedules
and History. No accounts, widget, new phone, UI framework or extra Gradle module.
The owner confirmed automatic scheduled start and paired-Zap stop on 2026-09-03.
The accompanying defaults below are the documented MVP implementation choices,
now represented by `scheduleTemporal` fixtures in portable fixture set 1.5.0.
They do not change the shipped iOS scheduler; its runtime migration is separate.

## Confirmed product behavior

Use **automatic start at the chosen time; tap the paired Zap to stop**.
There is no automatic end, configurable duration or midnight cutoff in this MVP.
An automatic end-time window is out of this MVP. The observed iOS 20-minute
monitoring interval is not copied as a product duration.

## MVP implementation defaults

| Decision | MVP rule |
| --- | --- |
| Time model and input | A one-time local date/time is resolved with its named IANA zone to a fixed instant; display the resolved time and reject past input. Do not silently move it to tomorrow. Repeating schedules store named weekdays and local hour/minute. Use Gregorian dates for persistence, locale for display. |
| Travel | Repeating schedules follow the current device time zone. One-time schedules keep their fixed instant. Recompute future alarms after a time-zone change and show the next occurrence. |
| Daylight-saving changes | Reject a nonexistent one-time local time. Skip a nonexistent repeating time for that day. For a repeated local time, use only the first occurrence. Persist a nominal-occurrence receipt so repeated callbacks, clock rollback or a zone change cannot replay the same occurrence. |
| Duration and midnight | Start only, Zap stop. Crossing midnight does not end or restart an active session. Existing non-destructive permission/Force Stop escape routes remain available. |
| Multiple schedules | Consider every enabled schedule, dispatch due occurrences in fire-instant then stable schedule-ID order. An implementation may arm the next required alarm, but must not ignore other schedules. Disable a one-time schedule when its occurrence is resolved, including missed/skipped outcomes. |
| Collision and apps | An existing session, including active recovery, wins. A due schedule skips without stopping/replacing it, changing its targets or dismissing its blocker. Use the single currently saved app selection at fire time; clearly show this in the editor. A successful start takes the same immutable target snapshot as Zap start. Multiple named modes are not a prerequisite. |
| Missed delivery | Grace: from the scheduled instant through **5 minutes late**, inclusive. Later delivery is missed, never a surprise catch-up hours later. No early start. Missing consent, pairing, eligible apps or live restriction capability means no start and a visible outcome. Reboot/update/time changes revalidate; they never manufacture service readiness. After Force Stop, require explicit recovery before future scheduling resumes; do not catch up the stopped interval. |
| Stop and recovery ownership | Use the existing session UUID, stop guard, atomic completion/outbox and History path. Schedule callbacks are start-only and never toggle. Service loss keeps the existing truthful recovery behavior; no stale overlay or claim of uninterrupted protection. |
| Notifications | No separate reminder system in this MVP. The app shows the stored occurrence outcome. If a status notification becomes necessary, derive it from the same committed occurrence/session and respect notification permission; never report a start from an alarm delivery alone. |

The five-minute grace is a product tolerance, **not** an Android delivery guarantee.
The owner explicitly approved start/stop ownership; the remaining defaults are the
previously proposed, now recorded implementation choices, not additional claims of
individual owner sign-off. iOS conformance/differences remain explicit. Runtime
activation still requires native delivery, consent and A16 verification.

Occurrence identity uses `oneTime:{epochMilliseconds}` or
`repeating:{Gregorian-local-date}:{HH:mm}`, scoped to a persisted schedule. A
repeating schedule's last resolved local date is a high-water mark: rollback/travel
cannot replay that date or an earlier one. Supported instants are non-negative
Unix milliseconds through `253402300799999` (end of year 9999 UTC). Invalid shapes,
dates, times and zones are rejected, not normalized into another schedule.

Represent the one saved selection with a stable default mode/selection reference;
this needs no mode-management screen. Keep frozen eligibility precedence:
`disabled` then `modeUnavailable` for a missing reference, then
`targetSelectionEmpty` for a resolved but empty selection. Do not equate missing
configuration with a valid mode.

## Native delivery candidate and limitations

Evaluate `AlarmManager` with a package-scoped immutable `PendingIntent` and
`setExactAndAllowWhileIdle`, guarded by user-granted `SCHEDULE_EXACT_ALARM` access.
Ask only when the user enables schedules. If access is missing, retain the draft
and show setup required; do not silently claim precise scheduling via an inexact
fallback. Revalidate permission before arming and after grant/reboot. This is a
candidate for the user-visible chosen-time requirement, not a Play-approval claim.
[Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

Do not use the auto-granted `USE_EXACT_ALARM` permission or disguise the app as an
alarm clock. Google restricts that permission to qualifying core use cases and
points other apps toward evaluating user-granted access.
[Google Play exact-alarm policy](https://support.google.com/googleplay/android-developer/answer/16558241).

Even allow-while-idle alarms are throttled; closely spaced schedules can miss the
proposed grace. Record actual delivery and explain missed outcomes rather than
promise exact-to-the-second behavior. Listener-only alarms do not provide the
required process-independent delivery boundary.
[AlarmManager reference](https://developer.android.com/reference/android/app/AlarmManager).

Force Stop is an intentional user escape, not a condition to bypass. Android 15+
cancels pending intents in stopped state; recovery must re-register future work
only after appropriate user action.
[Android stopped-state changes](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state).

The completed first implementation slice added pure rules and versioned configuration
storage; it did not register alarms, execute scheduled sessions or change consent.
Any new native permission, wake-lock need or background work must be justified in
the data inventory, disclosure and packaged-release checks before delivery.
Use an explicit non-exported alarm receiver and validate the persisted schedule ID,
revision and due occurrence; intent extras alone never authorize a start. Withdrawal
must cancel alarms and clear schedules, outcomes and receipts through retryable
cleanup, without allowing a late callback to recreate revoked state.

### Native delivery implementation boundary — 2026-09-03

The runtime source slice adds one non-exported alarm receiver and one immutable explicit
wakeup PendingIntent. It revalidates all persisted due candidates in instant/ID order;
intent extras carry no start authority. A schema-v1 execution record per schedule
stores the pending candidate, independent repeating-date/one-time-instant high-water
markers and only the latest outcome. Configuration edits invalidate pending revisions
but retain those replay guards; deleting a schedule removes its execution record.
Successful start commits its receipt and UUID/target snapshot in the same private
preference write and uses the existing completion/History path. An active collision
does not publish a state-change signal or dismiss its blocker.

Native permissions are user-granted `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`
for revalidation, and `WAKE_LOCK` for a timeout-bound 9-second partial CPU lease around
asynchronous broadcast work. The processing budget is 8 seconds including queue wait,
with one worker and one queued delivery maximum. No foreground service, broad package
query, network, notification or battery-exemption permission is added. A deadline is
cooperative, not a guarantee that an individual disk/binder call can be interrupted.

Startup uses API 35+ current-process `ApplicationStartInfo.wasForceStopped()` evidence;
unknown evidence remains paused. Older API 31–34 uses conservative user-requested-exit
evidence, not a precise Force Stop detector. A visible explicit app entry resolves due
work without starting it before acknowledging recovery and arming future work. Boot,
time and permission broadcasts never count as that acknowledgment: Android 15+ may
send `BOOT_COMPLETED` when stopped state is removed without a real reboot.

Scheduled capability preflight uses the actual currently bound Accessibility service,
not inherited heartbeat data. A main-thread acknowledgment has a 750 ms wait limit and
a one-second generation/endpoint lease. It is checked inside the session transaction,
not an atomic OS guarantee through later disk I/O; subsequent uncertainty enters
interrupted/recovery state. One latest package-only foreground observation is retained
in service memory while idle or active, reset on screen-off/interruption/disconnection;
this enables an immediate scheduled interruption without a new app-navigation event.
No node/window-content retrieval or event history is introduced. This behavior and its
unknown-foreground/screen-off limits require A16 evidence before a reliability claim.

The native disclosure moves to v4 before activation; existing pairing/selection and
eligible History remain, but earlier consent is not silently upgraded. Schedule UI,
physical acceptance and Play/signing remain separate work. This section describes the
implementation boundary, not a passed device test or released feature.

Recovery uses one fixed explicit-entry cutoff for planning, discarding due work and
acknowledgment. Repeating high-water dates cover the whole stopped interval, including
rollback/westward-travel replay; one-times after the entry cutoff remain future work
even if processing finishes later. Normal dispatch samples its wall clock inside the
preference lock, after failed-write recovery, so waiting for that lock cannot reuse
an earlier grace-window timestamp. The active scheduled session also retains its
schedule ID for conservative unknown-foreground recovery, not in completed History.

## Three focused implementation slices

1. **Rules and configuration storage — locally verified.** Add portable fixtures/native domain tests for the approved
   defaults; version local schedule storage, edits and occurrence identity. Cover
   DST gap/overlap, travel, past times, grace boundaries, collisions, disabled or
   deleted revisions, duplicate delivery and failed atomic writes.
   [The first-slice evidence](../spikes/android-gate0/evidence/emulator-schedule-foundation-2026-09-03/README.md)
   records 38 temporal fixtures, configuration CRUD and passing local checks.
   Persisted occurrence receipts/outcomes belong to the atomic start slice below;
   this completed foundation does not execute a schedule.
2. **Real background start — locally verified.** Add an explicit scheduled-start command using shared
   start validation, not a synthetic NFC read. Atomically commit the occurrence
   receipt with the active UUID/target snapshot so failed or repeated delivery
   cannot consume or replay a session. Reuse History and recovery. Add the native
   alarm adapter and permission/rearm handling. A live bound service must establish
   readiness; stale heartbeats after idle are not proof of enforcement.
   [Runtime evidence](../spikes/android-gate0/evidence/emulator-schedule-runtime-2026-09-03/README.md)
   records 143 JVM tests, 69 emulator tests, lint/build/release checks and their exact
   boundaries. The real alarm test proves refusal without a bound service, not A16
   blocking reliability or a complete user-facing scheduling feature.
3. **Usable schedule screen and A16 acceptance.** Connect create/edit/enable/delete,
   next occurrence and outcome states to real data. Test a due schedule while a
   selected app is already open: today's event-triggered blocker alone does not
   prove this. Any minimal in-memory foreground-package cache must be documented,
   reset on uncertain lifecycle state, and avoid node/window-content retrieval.
   Verify one combined real session/schedule/History flow plus new background,
   duplicate, permission and recovery boundaries on the existing A16 candidate.

These are estimated work slices, not a promise of three chat turns. Essential
safety and Play signing/account/declaration steps remain; A17, advanced analytics,
calendar History and full visual parity are not added to this MVP.

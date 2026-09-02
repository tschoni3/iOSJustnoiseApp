# Android MVP schedule plan

Status: **PROPOSED — owner approval pending, not implemented** (2026-09-03).

Scope: the existing local Android MVP: select apps, real Zap sessions, schedules
and History. No accounts, widget, new phone, UI framework or extra Gradle module.
This proposal resolves the open questions in `SCHEDULE_PORTABILITY_AUDIT.md`; it
does not replace the frozen contract or change the shipped iOS scheduler.

## One product choice to confirm

Recommend **automatic start at the chosen time; tap the paired Zap to stop**.
There is no automatic end, configurable duration or midnight cutoff in this MVP.
The alternative is a start/end window, which needs a separate end alarm and safe
ownership of exactly the session it started. The observed iOS 20-minute monitoring
interval is not an approved duration. The owner's answer about freeing Mac storage
is not approval of either schedule behavior.

## Recommended defaults as one proposal

| Open decision | Proposed MVP rule |
| --- | --- |
| Time model and input | A one-time local date/time is resolved with its named IANA zone to a fixed instant; display the resolved time and reject past input. Do not silently move it to tomorrow. Repeating schedules store named weekdays and local hour/minute. Use Gregorian dates for persistence, locale for display. |
| Travel | Repeating schedules follow the current device time zone. One-time schedules keep their fixed instant. Recompute future alarms after a time-zone change and show the next occurrence. |
| Daylight-saving changes | Reject a nonexistent one-time local time. Skip a nonexistent repeating time for that day. For a repeated local time, use only the first occurrence. Persist a nominal-occurrence receipt so repeated callbacks, clock rollback or a zone change cannot replay the same occurrence. |
| Duration and midnight | Start only, Zap stop. Crossing midnight does not end or restart an active session. Existing non-destructive permission/Force Stop escape routes remain available. |
| Multiple schedules | Consider every enabled schedule, dispatch due occurrences in fire-instant then stable schedule-ID order. An implementation may arm the next required alarm, but must not ignore other schedules. Disable a one-time schedule when its occurrence is resolved, including missed/skipped outcomes. |
| Collision and apps | An existing session, including active recovery, wins. A due schedule skips without stopping/replacing it, changing its targets or dismissing its blocker. Use the single currently saved app selection at fire time; clearly show this in the editor. A successful start takes the same immutable target snapshot as Zap start. Multiple named modes are not a prerequisite. |
| Missed delivery | Proposed grace: from the scheduled instant through **5 minutes late**, inclusive. Later delivery is missed, never a surprise catch-up hours later. No early start. Missing consent, pairing, eligible apps or live restriction capability means no start and a visible outcome. Reboot/update/time changes revalidate; they never manufacture service readiness. After Force Stop, require explicit recovery before future scheduling resumes; do not catch up the stopped interval. |
| Stop and recovery ownership | Use the existing session UUID, stop guard, atomic completion/outbox and History path. Schedule callbacks are start-only and never toggle. Service loss keeps the existing truthful recovery behavior; no stale overlay or claim of uninterrupted protection. |
| Notifications | No separate reminder system in this MVP. The app shows the stored occurrence outcome. If a status notification becomes necessary, derive it from the same committed occurrence/session and respect notification permission; never report a start from an alarm delivery alone. |

The five-minute grace is a proposed product tolerance, **not** an Android delivery
guarantee. Every row remains proposed until approved and represented in portable
fixtures. iOS conformance/differences must be explicit; accepting this plan does not
authorize silently replacing its shipped scheduling behavior.

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

No scheduler, permission, receiver or persisted schedule is added by this plan.
Any new native permission, wake-lock need or background work must be justified in
the data inventory, disclosure and packaged-release checks before delivery.
Use an explicit non-exported alarm receiver and validate the persisted schedule ID,
revision and due occurrence; intent extras alone never authorize a start. Withdrawal
must cancel alarms and clear schedules, outcomes and receipts through retryable
cleanup, without allowing a late callback to recreate revoked state.

## Three focused implementation slices after approval

1. **Rules and storage.** Add portable fixtures/native domain tests for the approved
   defaults; version local schedule storage, edits and occurrence identity. Cover
   DST gap/overlap, travel, past times, grace boundaries, collisions, disabled or
   deleted revisions, duplicate delivery and failed atomic writes.
2. **Real background start.** Add an explicit scheduled-start command using shared
   start validation, not a synthetic NFC read. Atomically commit the occurrence
   receipt with the active UUID/target snapshot so failed or repeated delivery
   cannot consume or replay a session. Reuse History and recovery. Add the native
   alarm adapter and permission/rearm handling. A live bound service must establish
   readiness; stale heartbeats after idle are not proof of enforcement.
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

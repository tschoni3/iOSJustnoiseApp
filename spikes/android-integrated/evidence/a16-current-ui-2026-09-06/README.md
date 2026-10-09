# A16 current UI installation — 2026-09-06

Later same-day follow-up: the [clean scheduled cycle and contrast-only A16 update](../schedule-modal-contrast-2026-09-06/README.md)
close the ordinary schedule-cycle and modal status-icon gaps recorded below.
Historical build/state statements here remain scoped to their individual samples.

## Scope and build

Owner reconnected the same Galaxy A16 (`SM-A165F`). Only one explicit ADB
connection was used despite duplicate wireless discovery entries.

- Package: `com.justnoise.androiddev`.
- Previous installed APK SHA-256:
  `4fee383141d2977ec9f693b9d4d3ec27a32e43e688723f2713a682f3ddd166d3`.
- Installed tested UI APK SHA-256:
  `3d9ff301d41895509387c4f6758c7b53004e2ec538e13e3d19e6d0681c8fc85b`.
- `adb install -r` returned `Success`; no uninstall, data clear, consent reset,
  pairing replacement, permission grant, instrumentation or UI-tree dump.
- Before installation: idle (`desired_active=false`), revision 32, service
  connected, current v4 disclosure, paired, two selected apps, one saved schedule.
- After normal launch and UI navigation: same revision, idle, connected, current
  consent. Pairing/selection/schedule-content integrity fingerprint matched before
  and after. No Zap payload or stored pairing digest was printed or copied.

An initial diagnostic counted the two fields of the schedule container instead of
its `schedules` array. The actual saved count is **one**, verified from the array
and UI. Nothing was deleted; the mistaken commentary count was corrected.

## History preservation

Read-only database snapshots before and after normal History navigation showed:

| State | Stored completions | Stored duration (ms) | Interrupted rows |
| --- | ---: | ---: | ---: |
| Before installation | 2 | 1,940,019 | 2 |
| After viewing History | 3 | 1,947,483 | 2 |

Before installation the durable completion outbox already contained a third real
session: UUID `0a7c3af9-f334-41aa-add4-b566209ff1a1`, duration 7,464 ms, two targets,
`interrupted=false`. Normal History loading delivered that same UUID exactly once
and cleared the outbox. The two pre-existing UUIDs, times, durations, target counts
and interruption flags matched. This is **preservation/outbox delivery evidence**,
not a newly performed NFC cycle on the installed UI candidate.

## Observed current UI

These are unedited physical A16 screenshots, not preview data or an iOS-rendered
comparison. Native navigation was visible throughout these screens.

- [Idle Zap](justnoise-a16-current-ui.png): centered logo, calendar left, History
  right, dark artwork, two-app selection capsule.
- Selection sheet opened with Browser and Chrome selected, readable Cancel/Save;
  Cancel closed it without modifying the saved selection. Its screenshot is kept
  in the local temporary diagnostic directory, not this evidence folder, because
  it includes the rest of the installed-app list.
- [History](justnoise-a16-history.png): real three-session total and two retained
  interruption warnings; four-column calendar presentation.
- [Day detail](justnoise-a16-day.png): real date/count/session-time and interruption
  labels. Android Back dismissed the overlay.
- [Settings](justnoise-a16-settings.png): actual idle/service summary, draft privacy
  entry, grouped controls, visible Done. Done returned to History.
- [Schedules](justnoise-a16-schedules.png): the existing one-time schedule and its
  historical start outcome remained visible. Disabled is expected after its prior
  resolved one-time occurrence.
- [Edit schedule](justnoise-a16-schedule-edit.png): correct existing date/time and
  saved two-app selection. Cancel returned without saving or changing the schedule.

## Open visual issue and next checks

**Observed visual defect:** the white selection modal uses dark status-bar icons
over the dark idle-screen scrim, making the clock/status icons difficult to read.
Bottom Android navigation and all selection controls remain visible. Fix the
modal's status-bar appearance independently from its white sheet/navigation area;
inspect the related completion-sheet path too. No acceptance is claimed for that
unobserved physical completion sheet.

This run does not close current-candidate active-session/History, clean schedule
cycle, physical rejected/rapid-repeat Zap, dedicated emergency-route, or separately
authorized cleanup/fresh-consent evidence. Existing ordinary Chrome unblock,
Home/Back, revocation/recovery and Force Stop evidence remains reusable and was not
repeated. Internal-beta decision stays **HOLD**; no release signing or upload.

iOS source/project/entitlement and shared-product tracked diffs remained empty.

## Owner-assisted current-build sessions and emergency surface

After the owner replied `started`, the first passive observation was already idle:
revision 34, service connected, same two selected apps, pending completion UUID
`001c18f1-bc99-4ec2-8572-9995ce7f7234`. It began at 19:56:37.849 local time and
ended at 19:56:45.835, with 7,790 ms stored monotonic duration, two targets and
`interrupted=false`. No agent device mutation occurred during that start/stop.

Normal History loading delivered exactly one row for this UUID. Totals increased
from three to four completions and 1,947,483 to 1,955,273 ms; the interrupted-row
count stayed at two. The 196 ms wall/monotonic difference is below the existing
1,000 ms uncertainty tolerance. This establishes a clean short current-build
completion, not verified continuous protection or physical repeat-input acceptance.

The owner was asked whether this was two deliberate presentations or one held
presentation; the answer was still pending at this checkpoint. Source inspection
found both duplicate/accidental-stop windows are 3,000 ms, with no physical
tag-removal/re-arm tracking. A fresh authorized read approximately eight seconds
later is eligible to stop. Do not infer the owner's physical action or mark the
repeat-input check passed from this storage record alone. A screenshot taken
during rotation also showed an incomplete-NDEF-read Toast; it is not a controlled
unauthorized-tag test and does not establish the cause of the earlier stop.

A subsequent passive snapshot observed a new real active session, UUID
`cb2c0b42-604c-4672-a7df-f4af3559537a`, revision 35, beginning at epoch
`1788717583998`, with two active targets and History eligibility. The agent did not
inject a tag or start a session programmatically.

- [Active Zap](justnoise-a16-active-zap.png) shows the real running timer, white
  active state, two selected apps, Unzap Phone and Session help. Android navigation
  is visible. The capture was made about two seconds after start.
- Another sample approximately 54 seconds after start retained the same UUID,
  revision and process; connected heartbeat age was approximately 1.17 seconds,
  with no recorded interruption/recovery flag.
- The platform resolver identified Samsung's dedicated emergency dialer for
  `com.android.phone.EmergencyDialer.DIAL`. Launching that action without data
  displayed the [empty emergency keypad](justnoise-a16-emergency-route.png) while
  the session was active. No overlay covered it; native navigation was visible.
  No number, call control, emergency-number shortcut or medical-information
  control was touched. Android Back/Home exited normally.
- The final post-exit sample retained the same active UUID/revision/process and
  a connected heartbeat approximately 0.31 seconds old. The phone was left on
  Home with the session active, awaiting an owner-authorized physical Zap stop.

This closes the dedicated emergency **surface/non-obstruction** observation for
the smaller checkpoint, alongside prior ordinary phone/navigation evidence. It
does not claim a lock-screen emergency-route traversal, a live call or the full
Gate 0 safety matrix. Current active UI is now physically observed; this second
session's final completion is pending. No app install, settings change, data
reset, service toggle or instrumentation occurred during either session.

### Confirmed owner stop and exactly-once History delivery

The owner replied `stopped`. The first passive snapshot confirmed idle revision
36, no active targets, service connected, and the pending receipt for the exact
previously observed UUID `cb2c0b42-604c-4672-a7df-f4af3559537a`:

- Start epoch: `1788717583998`; end epoch: `1788717794306`.
- Stored duration: **210,307 ms (3m 30.307s)**; target count: **2**.
- `interrupted=false`; independent timing uncertainty is false (wall/monotonic
  difference is **1 ms**).
- Same pairing/selection/schedule-content fingerprint as before installation;
  two saved apps, one saved schedule and current disclosure retained.

Ordinary History loading delivered the exact UUID once and cleared its pending
outbox. Reopening History again retained **five rows, 2,165,580 ms total duration,
two interrupted rows, and exactly one row for this session UUID**. All four prior
rows retained their original timestamps, duration, target count and flags.
[The physical History screen](justnoise-a16-confirmed-stop-history.png) displays
**5 Zaps / 36m**, with the same two historical interruption warnings. The app is
idle; no additional stop/unblock question, permission change or destructive test
was performed.

This passes the bounded **current-candidate real session → clean exactly-once
History completion** check and observed active/idle UI transitions. The historical
second warning's cause remains unknown and is not erased or claimed resolved;
another blind replay to explain that old Boolean is not required. This was not a
scheduled start, physical rejected/rapid-repeat acceptance, a >=20-minute foreground
completion sheet, cleanup/reinstall, or signed-release validation. Those distinct
gaps and the modal status-icon defect remain open; internal-beta decision is HOLD.

After reconciling the readiness/decision records, the full local
`verify-android-predevice-readiness.sh` check passed (exit 0), including contracts,
Android JVM/lint/build checks, 17 release-checker tests and the development-package
boundary. Log: `/private/tmp/justnoise-a16-clean-cycle-verifier.log`. No connected
instrumentation ran on the owner phone. iOS/shared-product diffs remained empty;
`git diff --check` passed. The development AAB is still not a signed Play candidate.

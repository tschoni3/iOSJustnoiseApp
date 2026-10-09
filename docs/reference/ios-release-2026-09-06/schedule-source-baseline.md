# Schedule presentation: source-first baseline

Date: 2026-09-06. Scope: the approved Android-only presentation continuation.
The iOS files below were read only. This is a source reference, **not** an iOS
schedule screenshot, App Store build capture, pixel-parity result, or scheduling
reliability claim. Existing Android schedule behavior remains authoritative under
`docs/ANDROID_SCHEDULE_PLAN.md`; no iOS/runtime/storage migration is included.

## Read-only iOS references

- `justnoiseradio/Features/Schedule/SchedulesView.swift:18`: native list, empty text
  and access warning. Lines 30–66 establish name/subtitle/weekday-dot hierarchy,
  trailing enabled switch and tap-to-edit. Lines 74–95 establish the Schedules
  title, trailing plus and separate create/edit sheets. Lines 152–167 render the
  small decorative weekday dots.
- `justnoiseradio/Features/Schedule/NewScheduleView.swift:38`: grouped DETAILS and
  REPEAT sections, name/date/time/Mode rows, and one-time/repeating footnote.
  Lines 65–69 establish New Schedule/Edit Schedule plus Cancel/Save navigation
  actions. Lines 75–102 distinguish date-and-time versus repeating time-only.
  Lines 339–362 establish Monday–Sunday circular controls, 34-point circles,
  narrow labels and full weekday accessibility names.

Source SHA-256 values at implementation:

```text
5e2ca1bd7dff02ee456936b182ff3285bd8f8ddc2f7eb0bfcc55e89e862bc9ce  SchedulesView.swift
88c652dd283981e8c8f1a4138c6f3a87915ff4c69547b3d3f92c48df0895b8e4  NewScheduleView.swift
```

## Android adaptation

`IntegratedScheduleSurface.kt` keeps the same public callback API and retained
`IntegratedScheduleViewModel`. No scheduling engine, draft model, permission,
alarm, storage, consent, session ownership or iOS source was changed.

- Compact inset white rows on grouped gray, prominent Schedules title, accessible
  trailing add, tappable real schedule name/details and separate enabled switch.
  Real next-planned and latest-outcome text stays visible; STARTED history never
  claims current blocking.
- Modal rounded edit/create sheet above the list, sticky Cancel/Save, grouped
  DETAILS/REPEAT, native Android date/time pickers, and a scrolling form. Dismissal
  is disabled during a confirmed in-flight save; rendering does not initiate a save.
- Circular Monday–Sunday controls reuse immutable draft callbacks. Android keeps
  the explicit One time/Repeat weekly choice and its existing validation rather
  than silently interpreting an empty repeating draft as another schedule type.
  Circles enlarge with text and wrap when needed; touch targets remain at least
  48 dp. Full localized weekday names and checked states remain accessible.
- The one saved Android app selection replaces iOS's named Mode row. No account,
  extra mode, fake stored record, five-minute wheel/clamping or automatic end is
  introduced. Current-device repeating time and fixed-instant one-time travel
  explanations remain Android-specific.
- Exact-alarm/setup actions, disabled/missing-consent states, load/write failures,
  uncertain mutation, explicit refresh/reload confirmation and Delete confirmation
  remain. Delete lives in the editor instead of a row swipe. It is offered only
  while the editor's ID/revision matches the loaded record; stale edits must reload
  before deleting a newer record.

## Verification boundary

Added `IntegratedScheduleUiInstrumentationTest` with six rendered-fixture cases:
list edit/toggle/add separation; draft-only edits and Cancel; explicit missing
alarm access; loading/saving/uncertain/consent/error refusals; reload/delete
confirmation and revision guard; reachable weekday and sheet actions at the actual
Android system font scale (including the root's separate compact 200% run).
Fixtures are restricted to `ranchu`/`goldfish` and `com.justnoise.androiddev` before
launching the host. They write no schedule fixture to storage and do not establish
real alarm delivery, retained-ViewModel behavior, physical NFC or A16 acceptance.

The existing guarded screenshot helper can optionally capture the compact list
and weekday sheet when instrumentation receives `captureZapEvidence=true`.
Enlarged modal text must be tested using the emulator's real system font setting;
overriding only the parent composition's density is not evidence for sheet text.
Names explicitly start with `fixture-`; these must not be represented as real
stored sessions/schedules or iOS reference captures.

XML parse and `git diff --check` passed before handoff. Builds and emulator tests
are coordinated by the main task; their final results belong in the run evidence,
not inferred from this source note. Existing editor JVM and schedule-management
instrumentation suites should remain in the regression run. Final visual spacing,
native picker presentation, compact-window behavior and A16 delivery remain
separate checks; no new phone is required by this presentation change.

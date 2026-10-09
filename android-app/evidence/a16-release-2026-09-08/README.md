# A16 signed-release installation — September 8, 2026

**Latest scoped result: scheduled dispatch/start PASS; scheduled blocking with
Chrome's focused address field FAILED this sample.** The alarm ran at 20:13 and
the session became active, but Chrome remained accessible until its address-entry
state was exited and it was foregrounded again. Release acceptance remains HOLD.
The earlier ordinary Zap cycle / History completion PASS remains valid within its
scope. Earlier pending paragraphs below are chronological checkpoints.

Owner replied "ok done" to the explicit question authorizing separate release
installation and temporary development-package disablement. This is scoped owner
device acceptance, not internal-track/public distribution or a safety GO.

## Preflight and operations

- Identified USB serial RFGYC3DYKBR, Galaxy A16 SM-A165F. Two wireless entries refer
  to the same phone; operations explicitly used the USB serial, not a default target.
- Only com.justnoise.androiddev was installed. Its application state was enabled=0;
  its Accessibility service was enabled. Scoped preference inspection showed
  desired_active=false, session_revision=68, service_connected=true. No active
  session was interrupted.
- Temporarily disabled that package with Android's disabled-user state. This
  preserves its sandbox, prevents its NFC/service/alarm routes competing with the
  release, and is reversible. No clear-data, uninstall, pairing replacement or
  permission grant was used. Restoring the development app later requires explicit
  enablement and may require the owner's manual Accessibility re-enable.
- Installed the preserved signed APK as a new com.justnoise.app package, without
  replacement flags. Android returned Success. Version 1.0.0, versionCode 1,
  min31/target36, no DEBUGGABLE flag. Enabled Accessibility list was null afterward;
  release consent/enablement was not inferred or granted.
- Installed base.apk SHA-256 matches the candidate exactly:
  `c2e8c95d37ecbf8ac5f05b0289f7bd12f30f2953c9d3d932bc2f95ddc0d27722`.
- Explicit launcher start returned Status ok / COLD / TotalTime 522 ms. This is a
  single Android launch result, not a performance benchmark. The screen capture
  still showed the screen-off charging display, so visible onboarding acceptance
  is pending manual unlock. Do not classify that image as an app rendering failure.

## Current boundary

### One-time release schedule checkpoint (20:10–20:13 CEST)

The owner manually enabled the release's alarms-and-reminders permission. The
system switch was observed on; after returning to Justnoise, the alarm-access
warning disappeared. Only the release Accessibility service remained enabled.
The release schedule list was initially empty. Through the normal editor, created
`Release Check`, once on September 8 at 20:13 Europe/Berlin, using existing mode
`hunk` / one selected app. The saved list showed that next planned time. Passive
Android alarm inspection confirmed an RTC_WAKEUP at 20:13:00, window=0,
exactAllowReason=permission, repeatInterval=0, owned by com.justnoise.app.
No clock change, injected alarm broadcast, permission grant or private production
data access was used. Chrome was opened before the deadline with Justnoise in the
background; Chrome's address field was initially unobstructed.

At 20:12:44 Chrome was still visible with its address field focused and keyboard
hidden. At 20:13:16 it remained visible without a blocker. Android alarm statistics
then recorded one delivered com.justnoise.app SCHEDULE_ALARM wakeup. An address-bar
tap made the keyboard appear without a blocker. The release schedule list showed
the one-time entry now disabled, last result "A session started" at 20:13. The main
screen showed Phone Zapped, hunk, elapsed 00:01:43. Thus actual session start is
verified, but immediate enforcement on this already-open Chrome state failed.

Returning Home and reopening Chrome with address entry still focused also showed
no blocker. After two Back actions to leave keyboard/address entry, Home and a new
Chrome foreground transition, the Chrome blocker became visible. This narrows the
observed failure to the address-entry/keyboard window sequence, not a missing alarm
or selected mode. Exact root cause remains unverified; no private release data or
accessibility event payloads were extracted. Source review identifies that the
service tracks every window-state package as the foreground and hides for an
unselected package; investigate IME/auxiliary-window handling without weakening
Home/Settings escape rules. No source fix or replacement build was made here.

The phone was returned Home with the scheduled session still active; owner Zap
stop and its resulting History entry are pending. The one-time schedule visibly
disabled itself after dispatch, so it will not repeat tomorrow. Retained screenshots
include schedule-saved, schedule-fired, schedule-after-touch, schedule-outcome,
schedule-active and schedule-blocker-after-address-exit. Full schedule acceptance
is NOT PASS and must be rechecked after a targeted fix.

Owner subsequently replied "gestoppt". At the 20:21 inspection, the release main
screen was idle (Zap to Block, hunk / one app, no recovery prompt). History showed
2 completed sessions / 10m total, and September 8 day detail showed the same count
and duration without a visible interruption warning. This verifies the scheduled
session's completion at UI level, not its exact duration or continuous protection.
The observed interception failure is not invalidated by an absent History warning:
the service remained alive, so heartbeat-based interruption detection cannot prove
that Chrome was actually intercepted. No additional scan was requested.

Read-only source diagnosis: onAccessibilityEvent replaces lastForegroundPackage
for every TYPE_WINDOW_STATE_CHANGED package; OverlayEventRules hides a visible
overlay for an unselected package. There is no separate IME/auxiliary-window
classification. Consequently an IME event can replace the underlying app in the
scheduled-start observation and/or dismiss its overlay. This is a source-backed
failure mechanism consistent with the device sequence, not proof of the exact
unrecorded Android event order. Gate0Controller.startScheduled invokes the scheduled
enforcement endpoint, not the generic hide-only state-change broadcast. Next work
is a targeted window-classification regression/fix preserving Home, Settings,
permission and emergency escape behavior, followed by affected device acceptance.
No runtime change, rebuilt candidate or fix PASS is claimed in this checkpoint.

### Earlier setup and ordinary session checkpoints

Follow-up: the owner reports "gekoppelt" for the release. A passive screenshot
shows the release's Blocking & Zap setup screen with the disclosure awaiting consent
and no apps selected. Enabled Accessibility services remain null; development is
still disabled-user. Pairing is owner-reported, not a private release-data read.
Manual release consent/service enablement and app selection remain pending.

Subsequent owner "aktiviert": Android's enabled-service setting now names only
com.justnoise.app's Gate0AccessibilityService, and passive service inspection
confirms its bound running process. Samsung's Installed apps screen visibly shows
"Justnoise app blocking — Ein". Development remains disabled-user. This closes
release manual service enablement, not app selection or an active blocking test.

Next owner "bereit" checkpoint: the release was already active, visibly showing
Phone Zapped, mode "hunk" and elapsed 00:00:29. No additional start was requested;
the exact start route was not independently observed. Only the release Accessibility
service was enabled and the development package stayed disabled. Launching Chrome
showed the correctly named Chrome blocker. It remained visible after one tap and
one swipe within its non-action area. Native Home returned to Samsung's launcher.
This is bounded active/interception/navigation evidence, not a video-based no-flash,
rapid-repeat, endurance or complete safety PASS. No stop/disable action was used;
the next step is the owner's single physical Zap stop followed by release/History
inspection. No production private-state extraction or instrumentation was used.

After the owner's "gestoppt", the release Accessibility service remained enabled.
Chrome reopened to its existing about:blank tab with no blocker, and tapping its
address bar produced a focused editable field. No text was entered or URL submitted.
Justnoise then showed idle "Zap to Block", mode "hunk", one app selected and no
recovery prompt. History displayed 1 Zap / 3m; the September 8 day detail displayed
one completed session / 3m with no visible interruption warning. These are UI-level
observations, not an extraction of exact duration milliseconds or database UUIDs.
The start route and the stop toast/chooser were not directly observed; the physical
stop action is owner-reported and its subsequent result is verified above.

Relevant unedited release screenshots are retained in screenshots/. No new scan
or service disablement was requested, and the development app remains paused with
its existing data. This ordinary release cycle should not be requested again.

The release is installed; development is temporarily disabled, with data retained.
The owner must unlock, follow release onboarding/pairing, and separately consent
and enable the release Accessibility service. Do not start a scan or grant consent
automatically. No release physical Zap cycle, scheduled execution, blocking,
navigation or recovery PASS is claimed yet. Existing development evidence remains
valid within its previous scope; do not request another phone/tag or repeat its
ordinary accepted flows as if those results were missing.

No instrumentation or accessibility-tree dump was used. No iOS, runtime, signing,
store or privacy-publication changes occurred during this installation.

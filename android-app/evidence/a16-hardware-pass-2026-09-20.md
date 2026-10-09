# A16 real hardware acceptance in progress — September 19–20, 2026

Owner authorization: **Whole testing then update**. Production package
`com.justnoise.app`, candidate `1.0.0-1-8b40bd8f`, Galaxy A16 SM-A165F,
serial `RFGYC3DYKBR`, Android 16 / full SDK 36.1 / One UI property 80500.
This report is bounded evidence, not a full release PASS.

## Scheduled start: affected sample PASS

- Reused only the agent-created A16 Keyboard Check: once, September 19 at
  23:54 Europe/Berlin, Focus / Chrome. Owner Release Check remained disabled.
- Registered RTC_WAKEUP metadata matched epoch 1789854840000. Chrome about:blank
  and its empty address keyboard were foreground before the deadline.
- Actual automatic dispatch started the session. Read-only service diagnostic
  returned READY at 23:54:18; the Chrome blocker covered the prior keyboard.
- A 90.512-second recording spans the deadline. Periodically sampled frames show
  the transition into a stable blocker; this is not frame-by-frame latency proof.
- Native Home worked. Settings remained usable during the same session.
- Normal lock/unlock and reopening Chrome at 00:02 still displayed the blocker.
- One-time test schedule was visibly disabled after resolution; active product UI
  at 00:04 showed Focus / Phone Zapped / 00:10:25. Session continued over midnight.

## Found defect: direct return to Justnoise FAIL

At 00:03 a direct Activity launch from blocked Chrome brought integrated
MainActivity to the top (Android activity metadata) but retained the Chrome
overlay. Home cleared it, and opening Justnoise from Home restored normal product
access. This is a real stale-overlay defect, not a claimed device lockout.

Source correction distinguishes our manifest-declared UI Activity event from
blocker-owned self events. It preserves the original self-event flicker guard,
excludes transparent DirectNfcActivity, and updates foreground generation on real
own-UI transitions. No event type, permission, content retrieval or telemetry is
added. Only own-package class metadata is classified and discarded. The inventory
and pure regression source are updated; hardware acceptance is still required.
No iOS edits. No emulator, JVM suite or owner-app instrumentation in this pass.

Correction candidate `build/candidates/1.0.0-1-75b4c6dd/` built successfully in
34 seconds with vital lint. Static policy/product contracts and independent
signed APK/AAB/package verification PASS. Affected regression source was added,
not executed (hardware-first owner direction). Not installed yet: owner physical
stop is still pending. This build is not evidence that the defect is resolved.

- APK SHA-256: `ce9df28855476bebb924867e9528016c0d7d5f98d189f60a0a35e9aedc74e412`
- AAB SHA-256: `75b4c6dda32074f085ee6abd80a01837b1ccc570ac5fda33325e6f250b601902`

The generic verifier report still says installation NOT AUTHORIZED; it is an
artifact-only template, not a fresh authority decision. The owner's current
request authorizes an in-place corrective update after the active test is ended.

## Live Update / Now Bar: partial, not accepted as exact Now Bar

- Samsung developer setting Live notifications for all apps / Real Time
  Notification Test was visibly OFF. No toggle or permission was changed.
- Own notification record has PROMOTED_ONGOING, ONGOING_EVENT and SILENT flags.
- Lock screen shows Justnoise artwork, mode Focus and a moving timer (04:26,
  05:01, later 08:13), in a notification-stack card.
- The requested bottom Samsung Now Bar capsule was not observed. API promotion
  is not proof of the requested OEM presentation. Exact Now Bar remains open.

## Pending in this current cycle

Owner follow-up September 20: physically away; defer the physical check for now
and continue locally. No further phone interaction, session stop, installation,
permission change or account mutation was performed during that follow-up.
Stop/History and correction acceptance are **DEFERRED, NOT PASSED**. Do not keep
requesting a tap until the owner says they are available again. The last observed
session was active; no remote stop is inferred from this deferral.

Owner physical Zap stop was requested from Home. Actual release of Chrome,
notification removal, new History record and midnight allocation are not yet
verified. Further multi-app and connected account/consent checks remain open;
no owner account deletion or silent analytics opt-in is authorized by this run.
Release HOLD; do not upload or advertise full acceptance.

## Local evidence

Evidence remains local under `/private/tmp/` (not uploaded):

- `justnoise-schedule-2354.mp4`, `justnoise-2354-contact.png`
- `justnoise-fulltest-armed.png`, `justnoise-fulltest-keyboard-before.png`
- `justnoise-fulltest-after-deadline.png`, `justnoise-fulltest-interactions-settled.png`
- `justnoise-fulltest-home-settled.png`, `justnoise-fulltest-dev-override.png`
- `justnoise-fulltest-lockscreen.png`, `justnoise-fulltest-lockscreen-timer.png`
- `justnoise-fulltest-current.png`, `justnoise-fulltest-unlock-chrome.png`
- `justnoise-fulltest-active-zap.png` (stale Chrome overlay above MainActivity)
- `justnoise-fulltest-active-after-home.png`, `justnoise-fulltest-active-product.png`

Framework reference checked September 20:
[Android AccessibilityEvent](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent).
This supports event-field interpretation, not a claim of A16 runtime acceptance.

# Focused closure review — October 10, 2026

Later follow-up: the isolated API-36 emulator now supplies actual release tutorial/
activation screenshots and seven passing canonical activity checks. See
`onboarding-emulator-2026-10-10/README.md` for exact scope, the corrected test
assumption and remaining real production/Samsung first-run boundaries. This
supersedes the initial absence of emulator execution, not the unresolved trigger.

Scope: first-run routing and the photographed interrupted session only. No
previously accepted NFC, Chrome, Now Bar or PostHog device flow was repeated.
iOS, owner accounts, phone settings, app data and Zap programming are unchanged.

## New owner acceptance

The owner explicitly confirms that sheets no longer jump and setup text is gone
from the product screens. Subsequent package inspection established that this
observation was on 1.0.3 (4), not 1.0.4 (5); it is not release-specific sheet
acceptance for the new bundle. The Play update to 1.0.4 (5) was then completed at
19:05:27, and normal idle Home without setup copy was observed afterward. Neither
observation establishes fresh onboarding or the underlying interruption cause.

## New local onboarding checks

`IntegratedFirstRunJourneyTest` adds five combined routing regressions and was
the only test class executed in this follow-up: 5 tests, zero failures/errors/skips.
The previous whole-suite result is separate historical evidence, not rerun here.

Verified logical transitions:

- Fresh tutorial -> activation -> required reconciled account -> first mode ->
  disclosure and actual system blocking access -> optional scheduled-start access.
- Returning/cold-launching after pairing cannot substitute for permission setup.
- Disclosure acceptance alone cannot substitute for system service enablement.
- Alarm refusal permits manual use, but later schedule entry still needs access.
- Previously granted access skips repeated setup without starting a session.
- An active session with lost readiness cannot be hidden by restored onboarding.

The real MainActivity source advances on onResume using current system facts and
keeps service-details navigation with a standard Accessibility-list fallback.
No new runtime implementation or release artifact was produced. Current AAB
remains SHA-256 `a0da8b20ee3f2688b691dc037269827556c3e94f4cdd04bb0476eeeba1b38718`.
These tests do not prove real Google login, NFC pairing, Samsung settings navigation,
rendered first-run UI or permission refusal/return on a fresh device installation.

## Interruption: established mechanism, historical trigger unknown

The pictured Home state means a session was requested while ACTIVE could not be
confirmed. Schedules consequently refuse/suspend starts rather than claim protection.
Source review identifies the following distinct mechanisms:

1. Accessibility off, disconnected service or heartbeat outside its 0..8,000 ms
   freshness range makes readiness fail. A snapshot is read before the clock to
   avoid the already-covered concurrent-heartbeat future-timestamp race.
2. Persistent recovery can be marked on overlay failure, storage uncertainty,
   or unknown interactive foreground during a scheduled session.
3. `onInterrupt` deliberately hides enforcement and marks persistent recovery;
   a later fresh heartbeat cannot clear it. Android documents this callback as
   interruption of accessibility feedback, not service unbinding. The current
   conservative policy is retained; its occurrence in the pictured session is
   not established.
4. Legacy UiAutomation inspection can itself suppress Accessibility services.
   There is no timestamp-correlated diagnostic record here proving that it caused
   the October 10 screenshot. It must not be called the historical root cause.

Official API reference:
https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onInterrupt()
Local inspection protocol: `spikes/android-gate0/README.md`, section
"Do not let inspection interrupt the blocker".

At the initial source review, ADB listed no connected devices. The later USB
inspection and Play update are recorded in `a16-passive-diagnosis-2026-10-10.md`;
no fresh install was performed. The original screenshot contains neither failure reason nor service timing;
the historical trigger therefore remains unresolved. No flags, timeouts, History
warnings or consent checks were weakened merely to remove the symptom.

## Exact remaining evidence

Connect the A16 and leave Justnoise open without starting another session. Read
only the current enabled/bound service facts and scoped app readiness diagnostics;
if the warning recurs, correlate its screenshot with those facts. Avoid raw NFC
data, foreground-app history and private account fields. Never use instrumentation
or uiautomator on the owner's paired app.

Fresh onboarding requires an isolated, explicitly approved test installation or
profile; do not reset/uninstall the owner's paired Play app to obtain it. Until
that real first-run journey is observed, record it as NOT RUN rather than PASS.

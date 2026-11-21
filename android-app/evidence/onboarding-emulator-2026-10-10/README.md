# Isolated first-run verification — October 10, 2026

## Later change: mode creation removed from mandatory onboarding

The owner subsequently removed mandatory first-mode creation. The shared Android
activity now goes from pairing/account entry to blocking setup independently of
the mode catalog. Completing permission setup does not automatically open the
mode editor. Home's existing selection capsule remains the entry point for modes;
session-start selection validation, existing data and iOS remain unchanged.
Old saved first-mode presentation state cannot bypass the blocking permission gate.

Verification of this source change:

- Product-contract and full Android pre-device verification passed.
- First-run JVM suites: 16 tests, zero failures/errors/skips, including a regression
  against automatically opening the editor after setup.
- Production `compileReleaseKotlin` passed; no production bundle was rebuilt.
- Only four affected activity tests ran on `emulator-5580`, disposable package
  `com.justnoise.androiddev`: legacy pair opens blocking setup; explicit pairing
  Continue opens blocking setup; refusal/relaunch retains the gate; returning
  without enabling the service retains the gate with an empty mode catalog.
  Result: **4/4 PASS**, 9.829 seconds.
- No owner-phone installation, data deletion, hardware test or Play upload.

The existing release screenshots and immutable version 1.0.4 (5) below predate
this runtime change. They do not establish distribution or physical acceptance of
the new source. Tutorial-page removal and merged permission screens remain only
earlier proposals, not part of this narrowly requested change.

## Scope and isolation

The owner requested fresh onboarding and interruption investigation without deleting
owner data or repeating accepted hardware tests. A new AVD was created under
`/private/tmp/justnoise-onboarding-avd`, using the already-installed API 36 Google
Play arm64 image. Serial: **emulator-5580**, hardware: **ranchu**. The owner A16,
serial RFGYC3DYKBR, was used only for scoped, read-only process history queries.
No permissions, pairing, modes, History, accounts or sessions on the A16 changed.

## Exact release-screen observations

The immutable 1.0.4 (5) upload-signed APK was installed on the fresh emulator.
Its corresponding AAB remains SHA-256
`a0da8b20ee3f2688b691dc037269827556c3e94f4cdd04bb0476eeeba1b38718`.
Observed actual release screens at 1080 × 2340, density 420:

1. Fresh launch shows the Create a Mode tutorial and Get Started action.
2. Get Started shows Stay Focused with Start Activating and Back.
3. Start Activating shows the Activate Zap screen, not normal product Home.

Screenshots in this directory are unmodified emulator pixels. NFC is unavailable
in this environment: no real scan, pairing or post-pair production sign-in was
claimed. This is upload-signed APK evidence, not a Play-installed fresh A16 journey.

## Actual shared-activity instrumentation: seven distinct tests PASS

The disposable app `com.justnoise.androiddev` reuses the canonical integrated and
capability sources, but not the production Supabase account adapter. The test
class checks emulator hardware and this exact disposable package before cleanup.
No instrumentation or UiAutomation was run on the A16 or production package.

First run: the five previously unexecuted tests in
`IntegratedFirstRunInstrumentationTest` passed (18.94 seconds):

- Two introduction pages reach activation without granting consent or starting.
- Configuration recreation preserves the second page without completing it.
- Tutorial completion persists across fresh activity launch.
- Synthetic legacy pairing skips education and opens first-mode setup.
- Synthetic pairing confirmation requires explicit Continue before mode setup.

Two new checks were then added, without runtime changes:

- Declining required blocking setup destroys the activity, preserves the stored
  synthetic selection/pairing and consent state, and requires setup on relaunch.
- Explicit consent followed by return from Settings **without enabling the
  Justnoise service** keeps the blocking setup visible, preserves synthetic
  pairing/selection and does not start a session.

The refusal check passed on its first run. The return check initially failed an
incorrect global `ACCESSIBILITY_ENABLED` invariant (expected 0, observed 1).
The instrumentation can affect this global bit; the app's actual gate is its
specific component in `ENABLED_ACCESSIBILITY_SERVICES`. The corrected check retains
the unchanged enabled-services-list assertion and explicitly requires
`AccessibilityServiceStatus.isEnabled(context) == false`, alongside no requested
session and no connected Justnoise service. Only that failed check was repeated;
it passed in 3.3 seconds. The six already-passed tests were not repeated.

The first build attempt lacked ANDROID_HOME; setting it to the existing SDK solved
the environment issue. The final test APK compiled successfully. No production
bundle rebuild, upload or owner installation was necessary.

## What this does not close

- Production Google sign-in after physical pairing on a genuinely fresh install.
- Samsung service-details/fallback navigation and manual enablement, followed by
  automatic continuation, on a fresh setup.
- Optional schedule permission grant/decline on the actual new onboarding route.
- Physical NFC or release-specific sheet scrolling; accepted earlier hardware
  tests were deliberately not repeated.

The seven tests are real emulator activity checks, not a claim of a complete
physical first-run journey. Synthetic pairing/selection is limited to the
disposable emulator app and was never applied to the owner data.

## Interruption investigation

Scoped A16 `dumpsys activity exit-info shop.justnoise.app` retains one exit:
October 10, 19:05:28.063, reason 16 **PACKAGE UPDATED**, for the old process 22789.
This matches the authorized Play update, not the earlier warning.

The retained package-scoped start records report `wasForceStopped=false` for all
displayed records, with repeated old-process starts followed by the update-era
service/current process. No positive evidence of a historical crash or Force Stop
was found. Absence from retained history cannot exclude an older unretained event.

The original screenshot did not contain a reason code or service timing. The app
stores recovery/interruption flags, but not the timestamped causal event that set
them. Source mechanisms and the passive pre/post-update evidence are in the
adjacent review documents. Therefore the original trigger remains **UNRESOLVED**;
no callback, heartbeat timeout, overlay failure or inspection effect is asserted
as the historical cause. No recovery flags or service checks were weakened.

If the symptom returns, preserve the state and collect a timestamp-correlated
passive sample. Reliable attribution beyond that would require a separately
reviewed, local-only reason-code diagnostic change; do not fabricate a root cause
or repeat already accepted blocking tests merely to seek a green result.

iOS remains unchanged. The pre-existing Shield Swift checksum is still
`ba11a54393985636cc314fdcb5e55363dd6b2439a1901af711a38b224c5b3173`.

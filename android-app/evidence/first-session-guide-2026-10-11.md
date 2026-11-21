# Guided permission setup and first-session video — local candidate

Candidate: Justnoise 1.0.10 (12), application ID shop.justnoise.app.

## Requested changes

- Short, visual permission instructions before the core product experience.
- Best-effort service-details route, Samsung installed-services route, then safe
  general Accessibility fallback. No permission auto-grant or private fragment.
- On return, existing current-consent/current-system-state checks advance setup
  automatically; optional schedule permission behavior is unchanged.
- Ready, idle Home shows the owner's 13.746-second video in a dismissible dialog.
  It uses silent, lifecycle-scoped playback, tap to pause and a reduced-motion
  still frame. Video frames are not edited; they show an iOS phone. Android text
  describes mode selection and the actual physical start/stop interaction.
- One primary action opens mode selection when no mode is selected, otherwise
  dismisses with Got it. Back/scrim also dismiss. A separate installation-owned
  preference remembers dismissal; existing ready installations see it once too.
- Guidance never starts a session or creates a mode, and is suppressed during
  scanning, requested sessions, recovery, missing permissions and other sheets.

## Local evidence

- Production testReleaseUnitTest: 532 tests, 64 suites, zero failures/errors/skips.
- Release build, lint, signed APK/AAB, certificate pin, package boundary and 16-KB
  ZIP/native alignment: PASS. Lint: zero errors, 94 warnings, two hints.
- Product contracts: 18 semantic colors, 27 copy entries, 89 behavior cases PASS.
- Production layout and scoped whitespace checks: PASS.
- Full local Android pre-device verifier passed; the final integrated
  testDebugUnitTest/lintDebug/assembleDebug check also passed after the last CTA
  adjustment. No instrumented owner-device tests were executed.
- Source and final packaged media SHA-256:
  bef7fcedaa502b0918587742dd8254c669287a091969b523632fe9d5e2acaf10.
- iOS Shield source retains the pre-existing hash:
  ba11a54393985636cc314fdcb5e55363dd6b2439a1901af711a38b224c5b3173.
  No Swift, Xcode project or iOS asset edits were made in this iteration.

## Acceptance boundary

No owner-phone install, permission change, reset or repeated core test. No new
rendered/physical acceptance was run: no emulator is configured. On the A16 the
direct service action was previously permission-protected and the Samsung shortcut
did not resolve, so normal Accessibility fallback is expected, not a promised
one-click activation. New layout/video acceptance is separate from local tests.

Only the affected check is needed after the next authorized internal update:
view the short permission guide where setup is genuinely needed; grant manually;
return and check automatic progression. On ready idle Home verify video framing,
brief copy, primary action and dismissal remembered on reopening. Do not erase
owner data to manufacture a fresh first run. Do not rerun passed blocking/NFC tests.

No Play upload or public-release claim. Prior live signup/email callback and
historical interruption boundaries are not resolved by this presentation change.

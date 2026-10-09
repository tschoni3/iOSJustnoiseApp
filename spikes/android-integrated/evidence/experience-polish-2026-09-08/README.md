# Android timer and mode-aware onboarding — September 8, 2026

Local implementation and emulator evidence only. No owner-phone install, scan,
permission change, production promotion, signing or Play upload occurred.

## Changes

- Active timer uses opaque #595959 on white (about 7:1 contrast), replacing the
  source-characterized 16% black for this Android informational text only.
- Requested-session recovery subtitle uses white on the existing dark surface;
  recovery does not acquire active-session styling or state. Idle decorative copy,
  shared iOS color tokens, geometry, timer calculation and all adapters are unchanged.
- The two-page introduction explains creating a mode, choosing blocked apps,
  manual use of the last selected mode, and automatic starts with each schedule's
  saved mode. Pairing still explicitly does not enable blocking.
- Three JVM presentation regressions and one native-resource copy regression were
  added. The existing first-run UI test now checks the longer second-page body and
  optionally captures both pages through the guarded emulator helper.

## Verification

- Final full local verifier: PASS. 296 JVM tests, zero failures/errors/skips;
  36 artifact-checker tests; debug/release lint and builds; 18 colors, 27 copy entries
  and 89 portable cases. The initial sandboxed invocation could not lock the
  existing Gradle cache; an authorized run using that cache passed. Final verification
  was repeated after adding the UI assertion/capture hooks.
- Standard emulator: both affected classes passed, **12 tests**, 24.199 seconds.
- Compact emulator, Android system font 200%: same **12 tests**, 17.854 seconds.
  These are 24 executions of 12 cases, not 24 distinct new tests.
- Target was explicitly `emulator-5558`, `medium_phone`, verified `ranchu`, booted
  with `-read-only -no-snapshot-save`. Standard 1080x2400 at 420 dpi; compact
  945x1680 at 420 dpi (360x640 dp), system `font_scale=2.0`. Animation scales were
  zero for deterministic/reduced-motion captures. The emulator was closed without
  saving state after evidence retrieval. Neither A16 connection was targeted.
- iOS app/project/test tracked comparison and `git diff --check`: empty/passed.

## Screenshot interpretation

`standard/` and `compact-font200/` contain unmodified emulator screenshots.
`real-onboarding-*` are actual Activity routes with disposable test storage;
`fixture-zap-*` are rendered supplied states, not physically started sessions.
The generic Selected apps label in a fixture is not the current real mode selection.

The active standard capture shows the darker timer. Compact captures can show
scrolled content: the active screenshot follows scrolling to Session help, so the
top timer is partly outside the viewport. Tests first locate the timer and then
scroll to actions. Onboarding checks locate the heading/body and then activate the
footer. Do not interpret a scrolled capture as all content fitting simultaneously.
Normal and compact timer/onboarding captures were visually inspected; this is not
full iOS rendered parity, a video playback benchmark, TalkBack sign-off or A16
release acceptance. Existing media/reduced-motion behavior was not changed.

## Artifact identity

Both outputs retain the development application ID `com.justnoise.androiddev`.

- Debug APK SHA-256: `cb828a3708c2eee0e91001dd0e1e5b43b34c68106441350f7147cb60859841e8`
- Unsigned development AAB SHA-256: `d54cbf4c425deac4e1349b7c93f4b11b3376181ca38ec5f0d17703c40f36409a`

These replace files at the usual local build-output paths, not the owner's installed
modes APK. Production identity, release signing, final privacy/support and the
separate internal-beta decision remain open.

Logs retained alongside this note: `local-verifier.log`, `ui-build.log`,
`ui-standard.log`, `ui-compact.log`.

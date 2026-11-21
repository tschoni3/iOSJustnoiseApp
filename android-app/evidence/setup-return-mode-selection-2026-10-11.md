# Setup return, new-mode selection and guidance typography

Target: Justnoise 1.0.11 (13), shop.justnoise.app. Local candidate only.
Published/installed version remains 1.0.10 (12); no phone operations this iteration.

## Changes

- Saving a new mode through the UI persists its selection in the same guarded
  commit. Editing another mode preserves the selected mode. Validation, protected
  apps, revision conflicts, recovery, active-session and storage-failure guards
  remain. No automatic session starts; schedule mode references are unchanged.
- An explicit permission setup action arms a process-only, two-minute return
  request. The connected Accessibility service can attempt this app's launcher
  once after a Settings window change, current consent, idle/non-recovery state,
  unlocked screen and interactive device are confirmed. Invalid events consume
  the request; manual app return, interruption, teardown and restart cancel it.
  No extra permissions, screen/node reads, automated consent or global Back taps.
  Background launch is best effort; absent events or OEM denial retain manual
  return and normal permission recheck/progression.
- Setup/video guidance has distinct 30sp titles, 17sp semibold instructions and
  15sp explanations. Disclosure remains visible and unchanged. Font scaling and
  scrolling remain; no technical helper panels are added.
- Android mode fixture version 1.0.1 covers UI creation, non-UI creation and
  editing. Storage schema and shared iOS contracts are unchanged.

## Verification and boundaries

New return-gate tests cover one-shot use, cancellation, expiry, clock reset,
unrelated windows and process recreation. Mode regressions cover creation/selection
in one commit and failed writes preserving the prior catalog while quarantining
normal reads. Service teardown checks still require shutdown and now also request
cancellation. Typography hierarchy has a readable-size regression.

The previous intermittent Analytics mock cancellation assertion was reproduced:
it inspected engine-owned handler cleanup immediately after the caller completed.
The test now checks that the actual request is joined before revocation returns,
then waits with a bounded timeout for mock-engine cancellation and checks that
neither stale nor current generation can send after opt-out. No production
analytics behavior, event contract or consent mechanism was changed.

Final production build: PASS; 538 tests / 66 suites, no failures/errors/skips.
Release lint has zero errors, 94 warnings and two hints. Signed APK/AAB preflight,
package/production boundary, alignment, product contracts, production layout and
the full local pre-device verifier pass. Final media SHA matches the original.
Frozen candidate: `build/candidates/1.0.11-13-aa56794a/`; details and checksums are
recorded in its HANDOFF.
No A16 behavior is established by local tests, no data was erased, and no already
passed physical blocking/NFC tests were repeated. Historical interruption and
email-link cross-device acceptance are not resolved by this change.

## Only affected owner checks after an authorized update

1. If Accessibility setup is genuinely needed, enable it manually and observe
   whether Samsung returns to Justnoise; otherwise return manually and confirm
   progression. Do not revoke a working grant or erase data just for this check.
2. Save one new mode: its name should immediately be selected on Home, without
   starting a session. Editing a different existing mode must retain selection.
3. View permission/tutorial guidance for readable hierarchy, including enlarged
   text where practical. Do not repeat core session or NFC acceptance.

Android background-launch reference:
https://developer.android.com/guide/components/activities/secure-bal

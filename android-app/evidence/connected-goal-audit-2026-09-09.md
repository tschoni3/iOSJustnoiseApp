# Connected Android goal audit — September 9, 2026

## Decision

**Goal not achieved; release acceptance and distribution remain HOLD.** A signed,
locally verified integration candidate exists. Its real account and Samsung
acceptance is not proved. Deadline remains September 10, 18:00 Europe/Berlin;
scope has not been reduced to the locally passing subset.

**Subsequent checkpoint:** the Android callback is now saved and the owner-approved
`ffb1ec06` update is installed, with its on-device APK hash verified. See
`connected-resume-2026-09-09.md` for the superseding installation and retained-UI
evidence. On the read-only 13:14 follow-up, the A16 remains connected and the
production process was running. Subsequently the Google provider configuration
was corrected and the owner confirmed successful Google sign-in on this build.
See `google-oauth-provider-failure-2026-09-09.md`; this closes the single Google
login check, not the remaining account isolation/lifecycle acceptance.

| Goal requirement | Evidence inspected | What remains |
| --- | --- | --- |
| iOS unchanged | Empty tracked app/widget/Xcode-project diff; Android-only edits | Preserve this invariant through final acceptance |
| Custom modes/app selection reused by schedules | `docs/ANDROID_NAMED_MODES.md`: owner-confirmed scheduled and manual mode routes on A16; real-store emulator evidence in `named-modes-2026-09-07` | New account-owned workspace UI has not been exercised on the signed candidate |
| Zap start/stop, scheduled starts, truthful History | September 8 signed A16 report verifies ordinary block/unblock, session completion/History and an actual scheduled alarm/start; existing regression suites pass | Scheduled Chrome address-entry enforcement failed in that sample; source fix still needs the affected A16 sequence |
| Registration/login/recovery/logout/deletion and A/B separation | Real provider serialization with MockEngine; encrypted journal/coordinator/storage/runtime tests; owner-confirmed Google login on the signed A16 build after provider setup | Real email registration/recovery, logout/deletion, A/B screen isolation, offline restoration and rendered lifecycle/large-text/TalkBack on the new build |
| Consent-only agreed PostHog events, no private payload | Inspected `AccountAnalytics.kt`; exact event/payload tests, cancellation/generation tests and the newly corrected revocation reload case | Actual opt-in/out integration and destination confirmation on an approved test session; no real event has been sent here |
| Samsung Now Bar artwork/timer/mode without developer override or fallback | `live-update-release-2026-09-09.md` source/package evidence; installed September 9 build had the framework promotion flag and chronometer | Visible normal-settings Samsung rendering, timer/readability and lifecycle remain unverified; framework metadata is not a visible Now Bar PASS |
| A16 affected safety checks, no known critical defect left open | `keyboard-window-fix-2026-09-09.md`, prior physical failure and source regression tests | Chrome address-entry with keyboard shown/hidden, restored keyboard, native Home/Settings escape and resulting stop; no ordinary completed Zap cycle repeated |
| Signed APK/AAB/checksums/report | Snapshot below passes local signature/manifest/DEX/bundle/alignment verification; subsequently installed APK matches its SHA-256 | Remaining affected physical validation and secure offline key backup; no upload is performed |

Referenced device evidence is historical and bounded. In particular, prior mode
acceptance must not be discarded, but it does not demonstrate the newly introduced
account transition UI. Old reports describing an earlier unsigned/unwired stage
do not override later implementation reports.

## Defect found during this audit and corrected

If persisting analytics opt-out failed, the old durable value could still be true.
Reloading account state called consent restoration again and could silently enable
capture. Consent restoration is now one-time; a pending revocation blocks capture
and retries only writing false. Failed retries remain visibly reported, while
offline product access remains available. Only a fresh successful explicit opt-in
can re-enable capture. Repeated opt-in requests are idempotent rather than rotating
identity or making an inaccurate off-state claim after a storage failure.

Three new tests cover failed revocation plus repeated restore, runtime reload and
successful durable retry, and repeated opt-in when storage later becomes unavailable.
No real PostHog traffic is used. A failed disk write cannot promise persistence
across process death; the UI explicitly says capture remains off for this process
and provides Reload account state as the retry path.

## Current local verification

- **410 release JVM tests, zero failures/errors/skips; release lint PASS.**
- Full Android pre-device verifier PASS, including 44 artifact regressions.
- Signed artifact verification PASS: existing upload certificate, APK signature,
  exact production manifest/DEX boundary, APK alignment, AAB signature/validation
  and native library alignment.
- Snapshot `android-app/build/candidates/1.0.0-1-ffb1ec06/` contains APK, AAB and
  verification JSON/log; application ID `com.justnoise.app`, version 1.0.0 (1).
- APK SHA-256: `d7ef1c8f5510ed864e693c45f07f0286f3bf9dafd72d456ad813f032d2276e50`.
- AAB SHA-256: `ffb1ec0699648e241568bea38d81098e6b69e5190d1d117cc5a48c3ec6b63e86`.
- Logs under `/private/tmp/`: `justnoise-consent-final-2026-09-09.log`,
  `justnoise-consent-final-readiness-2026-09-09.log`,
  `justnoise-consent-final-verification-2026-09-09.log`.
- No new phone installation, account mutation, real email, analytics event, signing
  key replacement or Play operation occurred. `git diff --check` passes.

## External prerequisites at the original audit checkpoint (historical)

Items 1 and 2 are superseded by the connected-resume report above; they are not
current requests to reconnect or save again.

1. ADB reports no connected device; prior wireless discovery was also empty.
   Reconnect the existing A16. No second phone is required. Installing this newer
   connected candidate needs specific approval and must preserve pairing/data.
2. Supabase Dashboard is accessible. Its Android-only callback pattern is still
   prepared in the Add URL dialog, not saved; browser action-time confirmation is
   pending. Existing `justnoise://open`, `justnoise://reset-password` and the Site
   URL are unchanged. Do not bypass the pending confirmation through another tool.
3. Live account checks require owner-approved test identities and access to their
   confirmation/recovery flow. No disposable account identity is invented and no
   owner account is deleted for testing.
4. Supabase's quota/grace-period warning is a service-availability risk, not proof
   of current outage. No billing setting was changed. The host's recently observed
   1.6 GiB free space is also insufficient headroom for another unplanned emulator
   run; no cleanup or emulator launch was attempted.

## Next bounded acceptance order

The Android callback and exact candidate installation are now verified. Use that
same build for real account A/B/offline and
opt-in/out checks, the distinct Chrome keyboard scenario, and visible Now Bar
acceptance with normal Samsung settings. Record actual outcomes and only rebuild
for an observed defect. Do not redo pairing or already accepted ordinary Zap cycles
without a change that affects them. Privacy/shop publication and Play upload stay
outside this goal.

## Focused source/evidence review after installation

The current AccountCoordinator and MainActivity ownership gate were inspected
alongside the existing XML test results: all 16 coordinator cases pass, including
late refresh rejection, active-session handoff rejection, expired-credential offline
restore, and deletion preserving other workspaces. MainActivity discards old
ViewModels/drafts before displaying a new workspace generation. These are source
and local regression evidence, not real A/B account acceptance.

The current Accessibility event adapter and all nine passing ForegroundWindowRules
cases were also inspected. Preservation of the underlying blocked app is limited
to the current input-method package with the exact framework SoftInputWindow class;
keyboard settings and protected escape surfaces remain actual transitions. The
Samsung event class during the previously failing Chrome scenario remains a device
acceptance dependency. No broader package exemption or content retrieval was added.

# Connected release boundary recheck — September 19, 2026

This is a read-only account/analytics source review plus owner-requested copy
cleanup. No phone install, account change, recovery email, opt-in, telemetry
capture or provider/Play operation was performed. The update/retry permission
question remains outstanding.

## Current source findings

- `AccountAnalytics.kt`: event API is the closed enum `focus_session_started` /
  `focus_session_ended`. Capture begins disabled. Consent restoration is called
  only after successful account reconciliation; new opt-in requires a confirmed
  preference write. Capture rejects old generations and bounds outstanding jobs.
- Its transport sends only project token, allowed event, a random ID and the
  single `$process_person_profile: false` property to the existing US endpoint.
  No account identifier, email, mode name, app selection or NFC value enters that
  payload API. No PostHog SDK, replay, disk queue, redirect following or automatic
  retry is used. This does not prove live dashboard delivery.
- `AccountRuntime` connects actual focus events to that boundary. The normal
  account UI offers a separate switch and confirmation. Cancelling the dialog
  does not call opt-in. Account switching/recovery paths are not used as implicit
  analytics consent.
- `AccountCoordinator` closes product starts during journal transitions, purges
  telemetry before sign-in/sign-out/deletion handoffs and retains pending cleanup
  on failure. Current journal/workspace ownership and operation tokens guard late
  responses. Existing-session stop/recovery is not delegated to remote auth.
- `android-app/app/build.gradle.kts` uses the canonical production, integrated and
  capability sources, with the connected production runtime and no instrumentation
  variant. There is no second copied release engine or PostHog SDK dependency.

No new account/analytics defect was established by this bounded review. Existing
limitations still apply: delivered requests cannot be recalled, receiving servers
see network IP, and a failed opt-out disk write cannot promise durability across
process death. The current UI reports that failure rather than claiming success.

## Remaining permanent-copy corrections

The account runtime still emitted the long successful-import paragraph even after
the static account helper copy was removed. It now emits no success message; the
completed import state and real data remain. The existing regression source gains
an assertion for this; the test was not run in this hardware-first slice.

The schedule ViewModel still emitted permanent success cards after Save, toggle
and Delete. Those success messages are removed. Save failure, uncertain save,
rearm failure and missing setup remain actionable. The real list/switch/editor
state still changes only after the existing persistence result. No schedule
storage, engine or account-ownership rule was changed by this copy follow-up.

## Acceptance still required, in minimal sequence

1. Owner-approved in-place updated candidate; one automatic Chrome/keyboard start
   using only the existing agent-created test schedule. Original schedule/data stay.
2. During that same session, confirm normal-setting Samsung Now Bar, timer/mode and
   safe Home/Settings navigation. A framework promoted flag alone is insufficient.
3. Owner stops once with the physical Zap; inspect real completion/History and
   removal of the system session surface. Do not repeat already-passed pairing.
4. With specifically approved disposable account identities: real registration,
   email confirmation/recovery, sign-out/sign-in A/B isolation and deletion of only
   the disposable account. Never delete or reset the owner's account for testing.
5. Owner-approved analytics opt-in/out with real session events and read-only
   destination inspection; no synthetic production events merely to obtain green
   evidence. Offline/cold restoration must also be physically checked.

These are pending requirements, not passes. The signed build does not close them.
No additional phone, emulator round, privacy publication or Play upload is needed
for this scoped continuation.

## Rebuilt candidate

`build/candidates/1.0.0-1-8b40bd8f/` supersedes `e553541c` for installation, with
the same schedule correction plus the remaining success-copy removal above.
Build passed in 33 seconds, including vital lint; static policy/product contracts
and independent signed APK/AAB package verification passed. Copied snapshot hashes
match the verified outputs. Regression sources were updated, not run. No emulator
or JVM suite was run and the phone remains on `cfa5e637`.

| Artifact | SHA-256 |
| --- | --- |
| APK | `487ad5edc044c72a9661fc934e52ec982b3c0751bc6264ce4ce2cc5a526ad0cf` |
| AAB | `8b40bd8fdbbc0455f8694e57188d5c97574f42cebcec196140544268b0c95a5d` |

The snapshot includes `verification.json`. Installation and targeted A16 retry
await the already-requested approval; no new question or phone mutation was made.
Release HOLD remains. No iOS file was edited in this slice.

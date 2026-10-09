# Android account-owned product storage — 2026-09-09

Scope: canonical local storage implementation supporting the connected release.
No new phone installation, account mutation, telemetry, iOS edit or upload.

## Implemented in the real engine

- Explicit validated local/UUID workspaces. Existing installation data keeps the
  `local` identity and `session_history.db`; first login does not silently adopt it.
- Separate mode catalogs, schedules, execution receipts and History destinations.
  The six-field History schema and existing session UUIDs are unchanged. Account
  database paths come only from a validated identity, never a URL/email/raw token.
- The idle-only handoff archives outgoing configuration and activates incoming
  configuration in one existing preference transaction, after outgoing completion
  delivery. Empty account catalogs remain empty. Pairing, onboarding and current
  consent/service evidence are installation-owned and not copied into profiles.
- Durable handoff barrier blocks mode/History/schedule hydration and new starts.
  A coordinator must confirm matching auth/workspace ownership before releasing it.
  Interrupted handoffs can be retargeted to the durable auth owner without first
  exposing the wrong account. Failed preference commits restore the old owner and
  enter the existing storage-recovery quarantine.
- Session/edit revisions and workspace lifetime tokens reject old mode saves and
  queued schedule work. A schedule create has no ID/revision yet, so its native
  management bridge additionally captures its original workspace lifetime.
- Schedules remain suspended after the storage handoff; existing explicit recovery
  must discard due/missed work before future alarms resume. No catch-up/start is
  invented by account selection.
- Global disclosure withdrawal retains the full History-cleanup owner set until
  every known workspace clears successfully. A failed clear is retryable. The
  existing local-only withdrawal/re-consent path keeps its bridge usable and still
  rejects old consent epochs; it is not treated as a new account lifetime.

## Verification

**331 release JVM tests passed**, zero failures/errors/skips, including **14 new
workspace tests** using real Gate0Preferences/codec/transaction code and synthetic
in-memory preferences/History ports. Cases cover A/B/local isolation, unchanged
pairing and consent, exact outbox delivery, active/recovery rejection, interrupted
handoff and rollback, corrupt profiles/owner IDs, stale lifetimes and multi-owner
withdrawal retry. The 21 encrypted-auth-journal regressions also remain passing.

Release lint and the full Android pre-device verifier passed on the final source
state. The full verifier compiled the existing device-test sources, built the
development artifacts and passed all 42 package regressions. No instrumentation
was executed on the owner's phone or an emulator. iOS tracked-tree diff is empty;
`git diff --check` passes.

An intermediate full verifier stopped with a Kotlin/Lint resolver exception while
source refinement was ongoing. It was not a rule suppression or policy PASS.
Subsequent unchanged-source reruns completed normally; no lint checks were disabled.
Final logs:

- `/private/tmp/justnoise-workspace-checks-2026-09-09.log`
- `/private/tmp/justnoise-workspace-readiness-final-2026-09-09.log`

## Required next work / not proved

1. Production account coordinator joining the encrypted auth journal with the
   workspace handoff; startup must reconcile them before any account UI is shown.
   Clear/invalidate retained ViewModels, drafts and late UI callbacks before
   publishing another owner. Never reuse another account's saved-instance state.
2. Account-specific deletion must erase only that account's product data and
   analytics queues. **Do not call global disclosure withdrawal for account deletion**:
   that operation deliberately clears all workspaces/pairing and is the wrong scope.
3. Real provider/login/registration/recovery/logout/delete screens, transparent
   existing-local-data choice, and separately consented PostHog integration.
4. Native SQLite/Keystore and full A/B/offline/relaunch/deletion acceptance. JVM
   isolation tests do not prove UI lifecycle or actual device filesystem behavior.
5. Build/sign/verify the combined candidate after wiring; current installed and
   preserved db0c2798 artifacts contain neither this slice nor the auth journal.

This is implementation progress toward ACCOUNT-01, not complete sign-in or a
connected-release GO. The original portable/iOS contract remains unchanged.

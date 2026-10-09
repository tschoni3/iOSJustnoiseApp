# Account coordinator integration — 2026-09-09

## Implemented

The production-only `AccountCoordinator` now joins `AuthSessionJournal` to the
real canonical `Gate0Preferences` workspace and targeted-erasure transactions.
Provider/network work is deliberately outside its serialized local handoff.

- Login accepts a captured generation only while signed out and requires the
  explicit keep-local-data-separate choice. It closes the telemetry boundary,
  stages a closed product handoff, commits credentials, then releases that exact
  handoff. Failed writes never return a usable account view.
- Logout returns to the preserved local workspace and invalidates late auth
  responses. Active sessions reject ownership changes without losing Zap stop.
- Restore follows the durable journal, completing or rolling back an abandoned
  handoff without temporarily publishing the wrong owner. Expired credentials
  do not require network access merely to restore offline product access.
- Deletion persists remote-pending intent before the request; only the provider's
  verified success may remove tokens and begin local cleanup. A new session that
  began while the remote request was in flight delays erasure until it stops.
- Product and telemetry cleanup precede the final signed-out journal state.
  Crashes between those commits remain retryable. Existing recovery warnings and
  schedule suspension are not cleared to manufacture a successful transition.
- A same-owner closed handoff permits cleanup after an independent global
  withdrawal without restoring an old local profile over newly saved data.

There is no default no-op telemetry implementation. The future runtime must
provide a real durable close/purge boundary. No SDK, provider request, account UI
entry point or production Application construction was enabled by this slice.
`isCurrent` is worker-only because it reads encrypted storage; retained UI and
main-thread delivery cancellation are still a separate required integration.

## Verification

- **353 release JVM tests, zero failures/errors/skips**, including 16 new
  coordinator regressions using the real journal and product transactions with
  synthetic disk/SQLite/telemetry ports.
- Release lint PASS, no suppressions/test removals.
- Unit/lint log: `/private/tmp/justnoise-account-coordinator-checks-2026-09-09.log`.
- Complete Android verifier PASS: contracts, capability/UI/integrated builds,
  tests/lint, 42 artifact regression tests and integrated release APK inspection.
  Log: `/private/tmp/justnoise-account-coordinator-readiness-2026-09-09.log`.
- iOS tracked source/project/widget diff and `git diff --check`: clean.
- No native instrumentation, phone changes, backend mutations, signing changes
  or distribution were performed. Unit tests do not prove complete account UX.

## Next required end-to-end slice

Implement the native Supabase adapter and account Activity/UI, connect safe
retained-UI invalidation, then connect the opt-in PostHog delivery boundary.
Validate real restoration/recovery/deletion and A/B behavior before signing the
combined candidate. Samsung default-settings Now Bar visibility and the affected
Chrome keyboard-window device check remain outstanding.

Owner separately reports Play developer account verification is complete. That
report is recorded in the technical status; it is not upload authorization or
evidence that the connected Android release is finished.

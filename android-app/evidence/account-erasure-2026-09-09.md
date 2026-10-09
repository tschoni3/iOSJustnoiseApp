# Account-scoped local erasure — 2026-09-09

## Implemented scope

`Gate0Preferences.eraseAccountWorkspace` is a worker-only product-storage operation
for the future production account coordinator. It requires an exact, closed local
workspace handoff and a non-local cleanup owner. The coordinator must authorize
that owner from the encrypted auth journal's confirmed remote-deletion phase,
not from a UI-provided account ID. No runtime caller or auth UI is enabled yet.

The operation removes only that account's archived modes, schedules and execution
receipts. A durable cleanup-owner marker precedes History erasure. Failed database
cleanup or a failed final preference commit keeps the marker and prevents releasing
or retargeting the workspace handoff. Retrying is idempotent. Global disclosure
withdrawal also inventories this owner even after its profile has been removed.

The SQLite adapter removes the account database and its standard sidecars using
Android's database deletion API, then verifies absence. It rejects local-workspace
erasure. This is logical app-storage deletion, not a claim of forensic flash-media
sanitization. Other accounts, local History/configuration, pairing and installation
consent are not targets of account deletion.

Reference: [Android SQLiteDatabase.deleteDatabase](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#deleteDatabase(java.io.File)).

## Verification

- Production release JVM suite: **337 tests, zero failures/errors/skips**.
- Six new cases exercise preservation of local/B data, rejection of wrong/stale
  handoffs, failed database erasure across restart, marker-write failure, final
  commit failure after erasure and global withdrawal during pending cleanup.
- Release lint: PASS. No suppressions or test removals added.
- A native SQLite regression was added to the existing emulator-only History
  suite. It checks real file erasure, retries and local/B preservation. It has
  **not been executed**; its instrumentation APK compiled successfully, which is
  not execution evidence.
- Full pre-device verifier: PASS, including product contracts, capability/UI/
  integrated builds, unit tests, lint, 42 artifact-verifier regressions and the
  integrated release APK inspection. Physical/release decisions remain HOLD.
  Log: `/private/tmp/justnoise-account-erasure-readiness-2026-09-09.log`.
- Unit/lint log: `/private/tmp/justnoise-account-erasure-checks-2026-09-09.log`.
- Tracked iOS source/project/widget diff: empty.

## Remaining integration

Auth coordinator/provider/UI, retained ViewModel invalidation, analytics queue
cleanup and native account A/B acceptance are still required. The encrypted auth
journal must not complete local deletion until both product and analytics cleanup
are confirmed. These storage tests do not prove remote account deletion or a
usable login flow. No backend, owner account, paired phone, installed APK, signing
key or store state was modified by this implementation.

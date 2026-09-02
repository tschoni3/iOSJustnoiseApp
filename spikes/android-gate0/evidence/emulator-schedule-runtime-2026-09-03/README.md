# Schedule runtime — local verification only

Verified 2026-09-03 Europe/Berlin, based on `7c50f9e` plus this change.
The owner-approved behavior is automatic scheduled start and paired-Zap stop,
without automatic end. This is the second implementation slice: the editor,
physical acceptance and release distribution remain unfinished.

## Implemented boundary

- One native coordinator reconciles persisted due candidates in fire-instant then
  stable-ID order. Intent extras never authorize a start. The explicit command is
  start-only; duplicates, edits/deletions, missed delivery and active collisions
  cannot toggle, replace or dismiss an existing session.
- Schema-v1 execution storage retains one pending candidate, independent repeating
  date/one-time instant high-water markers and only the latest outcome per schedule.
  Successful start commits its receipt with the same active UUID, immutable app
  snapshot and session clocks used by Zap. Stop reuses the existing History outbox.
  One-times disable when resolved; rollback cannot re-enable a consumed instant.
- The actual bound Accessibility service performs a bounded main-thread preflight,
  using an endpoint/generation lease and one transient latest package identifier.
  It does not retrieve nodes/window content or store an event history. Unknown
  interactive foreground refuses the start or enters interrupted recovery after
  a lifecycle race. The lease is not an atomic OS-readiness guarantee through disk I/O.
- One explicit non-exported receiver and immutable package-scoped PendingIntent
  use user-granted exact-alarm access, boot revalidation and a 9-second partial
  CPU lease. Work has an 8-second cooperative budget, one worker and one queued
  delivery maximum. There is no inexact fallback, foreground service or network.
- Startup distinguishes known current-process Force Stop evidence where available;
  unknown/legacy evidence is conservative. Only explicit visible app entry resumes
  suspended scheduling. One fixed entry cutoff discards stopped-period work and
  preserves future work; repeating high-water dates cover the full stopped interval.
  Production dispatch samples its clock inside the preference lock, after cleanup.
- Final alarm registration and withdrawal share a short state lock. Withdrawal
  cancels the alarm and clears schedules/executions; late runtime callbacks cannot
  recreate consent, configuration or sessions. Native disclosure is now v4 and
  requires fresh agreement, preserving pairing/selection and eligible v3 History.

## Results

- `verify-product-contracts.sh`: 18 semantic colors, 27 English copy entries and
  89 portable behavior cases passed. No new portable rule or iOS runtime/UI change
  was introduced; no new iOS run is claimed for this Android-only slice.
- Final full `verify-android-predevice-readiness.sh`: **143 JVM tests** (124 Gate 0,
  15 UI, 4 integrated), lint/builds, integrated debug and unsigned release APK/AAB,
  **13 release-checker tests** and actual packaged-release checks passed.
  Up-to-date suites were reused. The 16 execution-codec, 7 startup-guard and 3
  receiver-action JVM methods have saved XML beside this file.
- **69 emulator instrumentation tests**, zero failures/errors/skips, on API-36
  `medium_phone`, selected exclusively by `ANDROID_SERIAL=emulator-5554`.
  Saved XML records 49.658 seconds; final Gradle run completed in 58 seconds.
  XML line endings are normalized to LF; results otherwise remain unchanged.
- The 18 new dispatch tests cover atomic identity/receipt/targets, duplicate History,
  collisions, grace/missed/stale/invalid delivery, unavailable eligibility, replay
  guards, failed commits, withdrawal, fixed recovery cutoff and transaction-time
  clock sampling. Readiness is synthetic in these storage tests.
- Three native-runtime tests cover main-thread readiness generation, withdrawal
  cancellation/late callbacks, and an actual alarm-to-receiver callback. That alarm
  deliberately has fresh persisted liveness but **no bound service**: it records
  `CAPABILITY_UNAVAILABLE`, never a claimed start. This does not prove live blocking.
- Exact-alarm access was shell-granted only to the disposable emulator test package.
  It was not the production permission flow. The emulator ran read-only without
  snapshot saving and was closed afterward; no paired-phone installation, settings,
  pairing, app data or personal content was changed.

Review fixed unknown-foreground recovery, withdrawal/rearm ordering, the stopped-
interval repeating high-water cutoff, and lock-delayed dispatch time. Two earlier
67-test runs also passed before the final cutoff/clock regressions were added; the
69-test report is the final source evidence. A documentation marker check briefly
failed after a wording edit; restoring its required History phrase passed the
contract verifier without relaxing checks.

Logs: `/private/tmp/justnoise-schedule-runtime-readiness-final.log` and
`/private/tmp/justnoise-schedule-runtime-instrumentation-final2.log`.

## Artifact SHA-256

| Artifact | Digest |
| --- | --- |
| Gate 0 debug APK tested | `c90627c766d1cb17ff71036d1b665a67acc5c32252ae1cbb6e04f483341b58b3` |
| Instrumentation APK | `ad1182ca96380bc5a4aeac28d41096c0e41f48192c4a130ee1d0d0a0ea5266da` |
| Integrated debug APK built | `dc066c83d613591a18be46d2b329f2d68f02af4ee2cab5653d5ac3b1f2a6afee` |
| Integrated unsigned release APK | `8e4a37848a4327ab07f20026c66467367d59fc3a2ccd0cc794372c6df93c4b1f` |
| Integrated unsigned AAB | `981233b6670c7cc998d0e363c8592f4b468ec6e63a92aa2eef85670fe12a5385` |
| Saved instrumentation XML | `d50b7d7c0eccac1c28ffb433fa9995c979ef09ff05424e0f85094419ff5db134` |
| Saved execution-codec JVM XML | `a083e9ed2bb0a1f1c71bb76e42ebc74f0643a5dd4170f28c4f40cdae7b91807e` |
| Saved startup-guard JVM XML | `de1aa6926d35a26f3760f961033b5159c9f160ebb958aa31e6b0a6890a95d9bd` |
| Saved alarm-dispatch JVM XML | `25c7dcb36ec16deecc6781246aaa527893e9f7c3ab59c52724f67c4502dfa269` |

Next: connect real create/edit/enable/delete, next occurrence, stored outcome and
the separate exact-access setup flow. Then perform one combined A16 acceptance
flow for app selection, real Zap and scheduled sessions, History and essential
escape/recovery behavior. Screen-off, already-open target, OEM alarm delivery and
permission/Force Stop recovery still require that physical evidence. No matrix
cell, signing/account status or Play decision is promoted by these local results.

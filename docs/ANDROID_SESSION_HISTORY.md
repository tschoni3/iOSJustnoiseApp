# Android local session History

Status: implemented in development source, 2026-09-03; emulator instrumentation
and full local verification passed. A16 History/visual acceptance remains pending.
This does not claim Gate 0 PASS or release readiness.

## Scope and user meaning

The existing Android Zap engine is connected to a real local History list and summary.
This is an Android persistence/recovery adapter extension of the shared
`historyPresentation` rules, not a change to iOS behavior or the shared fixtures.
Keep one application module and the existing native platform boundaries.

- Show completed session dates, durations, selected-app count and a **Blocking interrupted**
  label where applicable. Active sessions are not completed History rows.
- Label aggregate duration **Session time**, never verified protected time. Even an
  uninterrupted record does not prove continuous interception of every target.
- Reuse shared Zap counting, sum-before-minute-truncation and compact duration
  formatting. Show real empty/error states, not preview records.
- Do not add a 90-day/year calendar, midnight splitting, weekly Noise Rewind, cloud
  sync or analytics. These are not prerequisites for this MVP History slice.
- Schedules remain required MVP work but are not implemented by this change. Later
  scheduled starts/stops must use the same session engine and History path.

## Stored data and timing

Active session state stays in private `gate0_local_state` SharedPreferences: one
stable random UUID, start/clock metadata, fixed target snapshot and sticky
interruption/history-eligibility flags alongside existing reconciliation state.
Assign identity with the start, not each time History is read or the UI resumes.

Each completed record contains only:

| Field | Meaning |
| --- | --- |
| Session UUID | Idempotency key; not a tag or account identifier |
| Start/end epoch milliseconds | Recorded session instants for display |
| Duration milliseconds | Non-negative session interval, not protection evidence |
| Selected target count | Count from the fixed active snapshot; no package names |
| Interrupted Boolean | Known continuity loss or uncertain cross-process timing |

Within one process, duration uses the monotonic `elapsedRealtime` difference.
After process replacement, use a non-negative wall-time difference and mark the
record interrupted; do not present that fallback as verified protection. A clock
change must never create a negative duration. Interrupted remains sticky after
process startup/disconnect, an observed stale/reset heartbeat (the existing
eight-second limit), a recovery latch, or unavailable permission/service at stop.
Recovery does not erase that uncertainty.

## Atomic stop and retryable History storage

1. Start atomically records its UUID, clocks, targets and eligibility in the same
   preferences commit as desired-active state. Preserve existing Zap validation,
   consent/capability gates and the three-second accidental-stop guard.
2. A valid stop atomically clears active-session state and writes one pending
   completed record in those preferences. Invalid/duplicate/too-early reads do not
   add a record. The pending record contains only the completed fields above.
3. Drain that pending record to private SQLite `session_history.db`, using UUID as
   the table key. Insertion is idempotent; clear the pending value only after durable
   insertion is confirmed. A crash after insertion but before clearing cannot
   create a duplicate on retry.
4. Retry draining before another start or History read. Never overwrite a
   pending completion. A failed drain blocks a new start until resolved and exposes
   a useful error; it does not restore restrictions after an already committed stop.
5. History failure must not obstruct Home, Settings, disable, Force Stop, uninstall
   or withdrawal. Keep the existing truthful recovery behavior for failed durable
   session writes; do not claim a stop or cleanup succeeded when its required write
   failed.

There is no distributed transaction across preferences and SQLite. The durable
pending record plus idempotent insert is the explicit recovery boundary. No new
event subscription, foreground-app log, package-name history or raw NFC retention
is permitted.

`SharedPreferences.commit(false)` can still change its in-memory map. A failed
session/outbox commit therefore restores the preceding map, marks recovery and
quarantines History delivery until that restored state is durably confirmed. A
failed acknowledgement retains the already-inserted pending record for a safe retry.

## Consent, upgrade and deletion

The History disclosure is `gate0-accessibility-v3`. Earlier consent is not silently
upgraded. The service/start flow requires current affirmative consent; pairing and
saved selection survive an upgrade. Existing v1/v2 active intent remains stoppable,
but does not become a completed History record: history eligibility is set only for
new starts under v3. Do not invent or backfill older completed sessions.

Completed records remain local until withdrawal, Clear storage or uninstall. There
is no network destination and app backup/transfer remain excluded; OEM behavior
still needs device evidence. App-private storage is not a claim of full encryption.

Withdrawal must atomically clear preferences, active intent and pending completion,
while leaving a durable database-deletion-pending marker. It then clears History
and removes that marker only on success. Consent/start/read paths retry an unfinished
deletion before proceeding, so a crash cannot resurrect the outbox or show old
History. Pairing-key cleanup and service disablement remain required; complete
deletion feedback depends on every required cleanup result. Disabling Accessibility
alone does not delete History. Cleanup tests must use disposable test data, not erase
the owner's paired A16.

## Required checks before calling this delivered

- Stable UUID and target count survive UI recreation; same-process monotonic timing
  and interrupted wall-clock fallback remain non-negative across clock changes.
- Legacy v1/v2 state/pairing migrate without silent consent or backfilled History;
  v3 start/valid stop produces one real row.
- Reject duplicate/invalid/guarded scans without rows; repeated stop/read/drain does
  not duplicate a completed UUID.
- Inject failure before stop commit, during database insertion and after insertion
  before pending-clear; verify truthful state, retained retry data and safe escape.
- Reopen the database/process; verify durable rows, sticky interruption, app counts,
  real empty/error UI and the unchanged shared summary/formatting fixtures.
- Inject interrupted withdrawal and database/key cleanup failure; retry deletion
  before consent/start/read and never report complete cleanup prematurely.
- Inspect stored records and packaged boundaries: no app names/event stream/raw tag,
  no network/backup expansion; run the full Android readiness verifier.
- On the A16 candidate, verify real Zap completion, History persistence and recovery
  together without repeating unrelated already-recorded acceptance questions.

The recorded local results are in `ANDROID_PREDEVICE_READINESS.md`. Passing emulator
and repository checks does not complete the A16 or distribution requirements above.

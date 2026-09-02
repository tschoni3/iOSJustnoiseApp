# Android pre-device readiness

Status: **PHYSICAL GATE 0 IN PROGRESS — PRODUCTION IMPLEMENTATION HOLD**

This is the handoff between repository preparation and evidence that only consumer hardware, policy review, and Google Play can provide. It does not authorize `android-app/`.

**Latest MVP scope, 2026-09-02:** the owner requires multiple-app selection, Zap
sessions, schedules and history. This supersedes the earlier proposal to deliver
a single-selected-app build as the MVP. That build remains a foundation/safety
sample, not the requested finished app. The four-feature scope below controls planning.

Release identity decision, 2026-09-02: the owner delegated a conventional Android ID;
use `com.justnoise.app` with public name **Justnoise**, subject to Play Console
availability/registration. The existing development installation stays
`com.justnoise.androiddev`; do not rename or erase it while preparing the release.
The chosen ID is not yet registered, signed, or used by a production build.

## One reproducible repository check

From the repository root, with JDK 17 and Android SDK 36 configured, run:

```sh
bash scripts/verify-android-predevice-readiness.sh
```

The command fails unless all of the following remain true:

- the versioned behavior, copy, design, and pinned API contracts validate;
- the disposable Gate 0 project passes its static policy boundary, JVM tests, lint, debug APK build, and instrumentation APK build;
- the Compose UI prototype passes its contract/reducer tests, lint, and debug APK build;
- the integrated development shell passes its truthful-action state tests and compiles the reviewed Gate 0 capability sources,
  real foreground NFC reader, direct NDEF entry candidate, and polished truth-driven
  Zap surface into one disposable APK;
- the integrated unsigned release APK/App Bundle and release lint also build, and
  packaged-APK checks reject debug preview tooling, unexpected requested permissions,
  or broadened Accessibility metadata; this does not verify release signing or Play approval;
- the UI prototype declares no permission or service and neither its source nor the
  shared native copy/color mapping imports a restricted Android capability API; and
- no production `android-app/` exists during the current internal-beta and full Gate 0 HOLD. After the smaller internal-beta checkpoint is recorded as GO, deliberately replace this phase-specific absence guard with release-app verification during promotion.

GitHub's **Android Gate 0** workflow runs the same command whenever either Android spike, the mobile architecture/gate documents, shared product contracts, or the verifier itself changes.

## Evidence-based readiness table

| Requirement | Current status | Authoritative evidence |
| --- | --- | --- |
| Preserve the shipped iOS client | **Ready** | Android preparation is isolated under `spikes/`, `docs/`, `product/`, and CI/scripts; the production Android module is absent. iOS remains independently verified by `scripts/verify-ios.sh` and the **iOS verification** workflow. |
| Native two-client architecture | **Ready** | `AGENTS.md` and `docs/MOBILE_ARCHITECTURE.md` require SwiftUI plus Jetpack Compose with shared contracts rather than a shared UI runtime. |
| Shared product decisions | **Ready baseline** | `product/behavior/portable-fixtures.v1.json`, `product/copy/en.v1.json`, `product/design/tokens.v1.json`, and `product/api/signal/v1/` are versioned and verified. Direct Zap entry freezes start-the-last-explicit-valid-mode versus stop-active behavior. History density and duration-label rules plus 38 schedule-temporal cases are shared in Swift/Kotlin; approved start-only/Zap-stop defaults and iOS differences are explicit in `docs/ANDROID_SCHEDULE_PLAN.md`. Calendar bucketing and Zap provisioning/envelope parity remain separate work. |
| Android presentation fidelity | **Ready prototype** | `docs/ANDROID_UI_PARITY.md` maps every launch-ready iOS destination and major state to `spikes/android-ui/`, including the first-run coach tour and management editor/confirmation states. Device visual acceptance remains pending. It is presentation evidence only. |
| Consumer restriction candidate | **Buildable, not proven** | `spikes/android-gate0/` contains the disposable capability candidate, static privacy/policy constraints, evidence templates, and native tests. Emulator/CI results cannot satisfy a physical matrix cell. |
| Low-friction NFC activation | **Candidate implemented; physical evidence blocked** | `docs/ANDROID_NFC_ACTIVATION.md` separates direct Android tag routing from authorization and Accessibility enforcement. The integrated disposable APK now contains the live-tag direct NDEF entry Activity and shared mutation gate. G0-19 still requires unlocked Home start/stop without app-open, Scan button, chooser, or second confirmation on the existing required A16; A17 validation is deferred and non-blocking. |
| Physical feasibility | **PARTIAL A16 RUNS / failing gate** | September 1 established setup/pairing and exposed an overlay self-event loop. `spikes/android-gate0/evidence/A16_PARTIAL_RUN_2026-09-02.md` records one real foreground start/block/stop cycle: a short stable Chrome-blocking recording, unchanged screenshots after taps/swipe, owner confirmation of the flicker fix, then idle state and responsive Chrome after the authorized stop. Repeated/negative cases, longer interaction, other targets and remaining safety/lifecycle checks are still pending. No complete matrix cell passes; all 19 required A16 cells remain `BLOCKED`, so the decision remains `HOLD / NO-GO`. The 19 optional A17 cells are excluded from gate arithmetic. |
| Google Play distribution | **HOLD** | The account, policy declaration, testing-track, privacy/deletion, billing, signing, reviewer-access, and final review gates are listed in `docs/ANDROID_GATE_0.md`. Internal checks cannot promise Play approval. |

## Work allowed before a physical PASS

- Maintain the shared contracts and native mappings.
- Improve permission-free Compose presentation states and previews.
- Build and install the disposable APKs on emulators or test phones; use the integrated
  shell for the combined real-NFC and blocked-surface physical test.
- Exercise and harden the existing direct-dispatch Activity under G0-19 without
  promoting it into production code.
- Prepare policy language, privacy inventory, Play account decisions, and test logistics.
- Run the exact physical matrix and attach redacted evidence.

## Work not authorized during the current internal-beta HOLD

- Do not create `android-app/` until the smaller A16 internal-beta checkpoint is recorded as GO (or full Gate 0 passes). The owner-delegated ID decision is already `com.justnoise.app`; verify availability/registration without asking for the same naming decision again.
- Do not promote spike code into production packages.
- Do not connect production accounts, analytics, backend data, signing keys, or store listings to a spike.
- Do not claim working app restriction, NFC parity, background durability, or release readiness from unit tests, screenshots, or an emulator.
- Implement Android schedules from the approved plan and shared temporal fixtures, not an inferred copy of the iOS monitoring interval. Physical delivery/recovery acceptance remains separate.

## Full reliability and distribution gates

1. Complete all 19 required A16 cells defined by `docs/ANDROID_GATE_0.md` on the exact candidate commit, including real authorized/unauthorized NDEF tags, direct unlocked tag dispatch, bypass/escape safety, revocation, process death, reboot, Doze, overnight endurance, privacy observation, and screen recordings. A17 validation is deferred and non-blocking; the owner need not obtain another phone. Evidence is variant-specific, so additional-device reliability stays unverified until separately tested.
2. Obtain product and policy review of the observed experience, Accessibility disclosure/consent, data inventory, and Play declaration. Any technical failure or policy uncertainty keeps the decision on `HOLD / NO-GO`.
3. Complete the applicable Play Console owner/account verification, permanent package identity, signing/OAuth registration, testing-track eligibility, privacy/Data safety/account-deletion disclosures, billing decision, reviewer access, and store review described in the **Google Play and distribution gates** section of `docs/ANDROID_GATE_0.md`.
4. Recheck current Android and Google Play requirements at account creation, first closed-track upload, and production submission; repository documentation is not a substitute for the live console and current policies.

## Unlock sequence for the first internal beta

Budget-conscious sequence agreed 2026-09-02: complete the smaller A16 safety checkpoint in `docs/ANDROID_GATE_0.md`, review the observed product experience and Play policy package, then deliver the smallest useful Play internal-test build. The full endurance matrix can continue during controlled beta work rather than blocking the first learning release. A17 testing is optional follow-up when an existing user or voluntary tester is available; it is not a separate required iteration.

Only after the separate internal-beta decision says `GO` (or full Gate 0 says `PASS`):

1. Confirm Play availability/registration for the selected `com.justnoise.app` application ID.
2. Create one `android-app/app` module beside the existing iOS project.
3. Reuse the working NFC, blocker, Compose and contract components after release-boundary review; avoid a wholesale rewrite or an unreviewed bulk rename of spike packages.
4. Implement the four-feature local MVP below. Define schedule semantics before scheduling implementation, but do not move schedules or history outside the MVP. Broader parity remains a later roadmap.
5. Preserve physical-device and Play Console evidence as release gates, not one-time assumptions.

## Required MVP: apps, Zap sessions, schedules and history

The owner clarified: **“mvp is select apps to block, tap zap to start session, and
schedule, history.”** All four must work with real persisted data. Keep this
local-first on the existing A16; no account system or framework rewrite is required.

| Required feature | Current evidence | Completion needed |
| --- | --- | --- |
| Select apps to block | Development source persists a set with explicit Save/Cancel and an immutable active-session snapshot; all 24 API-36 emulator instrumentation tests pass, including migration/picker and the exact Google Personal Safety exclusion; refreshed full local verification after that fix also passed | Sample multiple eligible targets on the A16 while preserving protected-system exclusions; A16 still has the earlier single-target build |
| Tap Zap for sessions | Real pairing, foreground/direct start-stop and Chrome blocking sampled on A16; development source now implements stable identity/outbox with passing local History tests | Physically verify the History-capable candidate while preserving NFC authorization, stop guards and safe recovery |
| Schedules | Versioned configuration, shared temporal rules, atomic occurrence/start receipts and native alarm runtime now have a real integrated list/editor, explicit exact-access entry and stored outcomes | Verify the combined editor/background-start/History and recovery flow on A16; local tests and UI smoke do not establish OEM reliability |
| History | Real local UUID/outbox/SQLite records and native list/summary; scheduled starts reuse the same completion engine and the new v4 consent boundary | A16 real-Zap and scheduled completion/History plus physical visual acceptance |

Minimum implementation order:

1. **Saved apps and session data:** one saved multi-app selection, stable session
   identity and active target snapshot. Preserve pairing during migration. Multiple
   named modes are not required merely to select several apps.
2. **Real sessions and History:** connect the working Zap/restriction engine to that
   model, save completion exactly once and connect the native History UI to records.
   Do not count periods of unknown enforcement as verified protected time.
3. **Schedules:** define portable timing/collision/recovery cases, persist schedules
   and add native delivery to the same session engine, then connect the schedule UI.
   Do not create a second session implementation for scheduled starts.
4. **MVP release and Play:** verify all four features and essential escape/recovery
   on the A16 release candidate, update privacy/disclosure for actual history and
   schedules, sign the release bundle and use the applicable Play test track after
   account setup and owner upload approval. Do not upload the debug spike.

Multi-app adapter behavior, 2026-09-02: the existing Gate 0/integrated sources keep
one saved set of eligible launchable package identifiers. Save validates the entire
set; Cancel never persists a draft. Empty selection is allowed while idle but cannot
start a session. A start copies the saved set into its active snapshot, and stop
clears that snapshot. Changing/clearing targets while active or in active recovery
is rejected; returning to setup or saving the same set cannot end a session. System
and escape packages remain excluded. The blocker names the actual foreground
selected app, including transitions between two selected apps without recreating
the overlay. Unavailable saved targets stay visible until explicitly removed.

The legacy single-package preference is read as a singleton when the new set is
absent, including a legacy active session. Explicit empty new sets never fall back.
Pairing and session start/revision are preserved. This multi-app baseline introduced
`gate0-accessibility-v2` for selected **apps**; old consent was not silently upgraded.
This is Android adapter implementation of existing portable multi-target selection,
not a change to iOS behavior. It adds no event history, schedules, network access,
new runtime dependency or production module. At this baseline, device acceptance and
stable session identity/real History were separate work; the History update follows.

Local verification for this multi-app baseline, not the later History extension,
is recorded in `spikes/android-gate0/README.md`.
The Mac disk-space block is cleared and all 24 instrumentation tests passed on the
API-36 `medium_phone` emulator (`emulator-5554`), with no failures or skips. Two
initial picker-test failures were corrected by waiting for queued button work and
list layout; the production picker was unchanged for those failures. A separate
emulator discovery led to explicit protection of `com.google.android.apps.safetyhub`,
whose exclusion regression also passed. Log:
`/private/tmp/justnoise-multiapp-instrumentation-recheck.log`.
The initial `d80c4ce` result remains historical. The refreshed full verifier after
the Safety fix passed all 86 JVM tests, lint/builds, 10 release-checker tests and the
packaged release boundary; log: `/private/tmp/justnoise-multiapp-verified-release.log`.
The final combined debug APK checksum is recorded in the README; it was installed
only on the emulator, with no visual acceptance claim from that installation alone.
The paired A16 was not changed, and emulator results do not establish physical
multi-app acceptance or promote any Gate 0 cell.

### History implemented locally — 2026-09-03

[`ANDROID_SESSION_HISTORY.md`](ANDROID_SESSION_HISTORY.md) records the Android-local
contract written before integration and now implemented: stable active UUID/target snapshot,
atomic stop plus one pending completion in SharedPreferences, and retryable,
UUID-idempotent insertion into `session_history.db`. Completed records retain only
session ID, start/end, duration, target count and interruption flag—not package names,
foreground events or raw Zap data. A database retry cannot re-block a completed stop;
new starts wait for pending History to drain. Withdrawal clears History with a durable
deletion marker so an interrupted cleanup cannot restore old records.

The native list/summary uses real data and **Session time**, with **Blocking interrupted**
records labeled; it does not assert verified protected time. Same-process duration
uses monotonic time, while a cross-process wall-clock fallback is non-negative and
marked interrupted. Shared summary/formatting rules and iOS behavior stay unchanged;
calendar/90-day/weekly views are outside this slice.

The disclosure is `gate0-accessibility-v3`, with fresh affirmative consent.
Pairing and selected apps remain. Legacy active sessions can still stop without
backfilling History; only new v3 starts are eligible for completed records. History
stays local until withdrawal, app-data clear or uninstall, with no new network or
backup path. This is development implementation, not A16 or release acceptance.

Local verification passed after the fixes below:

- **36 emulator instrumentation tests**, including 12 new History tests, with zero
  failures/errors/skips on API-36 `medium_phone`. The persisted
  [instrumentation report](../spikes/android-gate0/evidence/emulator-history-2026-09-03/instrumentation-results.xml)
  records 33.974 seconds of tests; Gradle completed in 45 seconds. Log:
  `/private/tmp/justnoise-history-instrumentation-final.log`.
- **92 JVM tests** (73 Gate 0, 15 UI, 4 integrated), all lint/build steps, 10 release-
  checker tests and the packaged release boundary passed in the final full verifier.
  Log: `/private/tmp/justnoise-history-readiness-final.log`.
- The first full run found non-observable `Locale.getDefault()` use in Compose;
  the screen now uses `LocalLocale`. The first instrumentation run exposed database
  initialization/cleanup failure because `PRAGMA secure_delete` returns rows;
  replacing `execSQL` with `rawQuery` corrected that API use.
- The next instrumentation run passed 35 of 36 tests. The remaining legacy forged-
  intent test had seeded an empty target selection; its fixture now supplies a
  non-empty selection while retaining the same no-live-tag rejection assertions.
  The final 36-test run above passed without weakening those assertions.

These checks cover local migration, outbox/duplicate/failure recovery and cleanup,
not actual NFC hardware, A16 interruption durability or physical History visuals.
No device-matrix cell is promoted and no Play/signing approval follows from them.

The owner approved **automatic scheduled start, ending on a paired-Zap tap** on
2026-09-03. Do not ask for this decision again. There is no automatic end in the
Android MVP; the observed iOS 20-minute monitor is not a product duration. The
[schedule plan](ANDROID_SCHEDULE_PLAN.md) records the implementation defaults for
travel/DST, collisions and missed delivery, now represented by 38 temporal fixtures
in portable fixture set 1.5.0. Shipped iOS scheduling is not migrated by this change.

### Schedule foundation — 2026-09-03

Pure Kotlin and Swift rules now resolve one-time input, compute repeating occurrences
and classify delivery without starting sessions. Versioned Android configuration
storage supports create/edit/enable/delete with stable UUIDs, compare-and-set
revisions, malformed-store protection and failed-write rollback. It shares the
existing private preference transaction boundary; active session identity, immutable
targets, pairing and completed History are not rewritten by successful CRUD.

That first slice was **rules and configuration only**, without a UI caller, alarm
receiver, new permission, scheduled execution or persisted occurrence receipt.
The runtime follow-up below adds the next source boundary; the real schedule UI
and A16 flow were unfinished at that checkpoint. The connected-editor follow-up
below now covers the UI source. Earlier v3 consent is not silently treated as
approval for automatic scheduled starts. This progress does not promote a physical gate.

Final local verification passed: **116 JVM tests**, the full Android lint/build/
release-boundary verifier, **48 emulator instrumentation tests** (8 new storage
tests), and the iOS Release build plus **66 XCTest methods**. The 38 temporal
fixtures pass both native consumers. [Scoped evidence and artifact hashes](../spikes/android-gate0/evidence/emulator-schedule-foundation-2026-09-03/README.md)
record the runs. This source is not installed on the paired A16.

### Schedule runtime — 2026-09-03

The development engine now has atomic occurrence/start receipts, replay guards,
latest outcomes and a native exact-alarm receiver. Scheduled starts use the same
session UUID, target snapshot, stop and History path as Zap starts. Bound-service
preflight and a transient latest foreground package support immediate interception;
stale heartbeat data alone cannot authorize a start. The v4 disclosure now describes
automatic starts and local schedule/execution retention, with fresh consent required.

Force Stop/unknown startup remains suspended until explicit visible recovery, using
one entry cutoff and full repeating high-water coverage. Withdrawal cancels alarms
and clears local execution state, protected against late rearm. These are native
adapter changes under the approved shared rules; iOS runtime/UI remain untouched.

Final local checks passed: **143 JVM tests**, **69 emulator instrumentation tests**,
lint/builds, **13 release-checker tests**, actual unsigned release-APK boundary and
product contracts. The real alarm test proves a callback refuses to claim a start
without a bound service, not live A16 enforcement. [Scoped evidence and hashes](../spikes/android-gate0/evidence/emulator-schedule-runtime-2026-09-03/README.md)
record the final source results. The disposable emulator was closed; the phone was
not installed or changed.

At that runtime milestone the usable editor was next; the source integration below
now connects it. No A17 purchase is required, and no physical gate or Play readiness
claim follows from these tests.

### Connected schedule editor — 2026-09-03

The integrated Compose app now routes from Zap to real Schedules. The editor uses
strict shared one-time/repeating rules; the list shows persisted enabled intent,
next planned occurrence and latest outcome. Date/time dialogs, repeat weekdays,
Save/Cancel, explicit conflict reload and delete confirmation have native controls.
Permission/refusal/re-entry states remain truthful: only an explicit action opens
alarm access, and unavailable setup is never labeled confirmed blocking.

`ScheduleManagement` serializes coherent reads and consent-epoch/revision-checked
writes on the existing store, releases the lock before runtime rearm, and reports
saved-but-not-ready separately. A retained ViewModel owns in-flight writes across
rotation. Restored uncertain mutations are not automatically retried, and a queued
create from an older consent epoch cannot survive withdrawal/re-consent.

The editor integration is Android-only; iOS UI/runtime, NFC authorization, session
ownership and completed History fields are unchanged. The four-feature source is
now connected, but A16 acceptance, release identity/signing and Play steps remain.

Final local verification passed **157 JVM tests** (124 Gate 0, 15 UI, 18 integrated,
including 14 editor tests), **83 emulator instrumentation tests** (including 14 new
management tests), all lint/build tasks, **13 Python release-checker tests** and the
packaged unsigned-release boundary. The XML reports have zero failures/errors/skips;
up-to-date JVM suites were reused where applicable. [Exact commands, reports and
artifact hashes](../spikes/android-gate0/evidence/emulator-schedule-ui-2026-09-03/README.md)
record the result. A bounded integrated emulator UI smoke also passed empty/setup
states, draft rotation, explicit alarm Settings denial/return, create/edit-cancel,
disable and delete confirmation. Its screenshots and limits are recorded there;
this does not establish full visual acceptance, A16 alarm delivery or Play readiness.

### Draft-reload guard and A16 update preflight — 2026-09-03

A bounded review after `df5dc05` found that editor fields remained enabled during
an explicit saved-version reload. The same guard now disables those controls and
rejects callbacks in the ViewModel while loading, saving or mutation-uncertain.
A late callback also cannot reopen a cancelled new draft or replace another
schedule/revision. Four added JVM regression cases exercise the actual shared
state-update function, not a duplicate test-only rule.

The full local verifier passed **161 JVM tests** (124 Gate 0, 15 UI prototype,
22 integrated), lint/build tasks, **13 Python release-checker tests** and the
unsigned-release package boundary. The earlier 83 emulator tests and UI smoke
remain historical evidence for the previous integration; they were not rerun
or newly claimed for this small state-guard fix.

The [next A16 candidate record](../spikes/android-gate0/evidence/A16_MVP_CANDIDATE_2026-09-03.md)
contains exact APK hashes and the passive preflight. The connected A16 still has
the old v1-consent single-app candidate, session intent/revision 22 and pairing
retained, with Accessibility off. No phone setting, app installation or session
was changed. An update requires the pending owner approval; current disclosure
and manually enabled capability are required before resuming blocking.

### Rejected-Zap notification safety fix — 2026-09-03

The Android controller no longer broadcasts a state change for an unchanged
rejected/no-op session decision. This matters because that signal dismisses the
service's current overlay. Real starts/stops and persistence-failure recovery still
publish. The portable reducer, authorization, UI and iOS behavior are unchanged.

All **40 emulator instrumentation tests** passed, including four new notification/
History regressions. The full local verifier also passed its 92 JVM tests,
lint/builds, 10 release-checker tests and packaged-release boundary.
[Scoped evidence and artifact hashes](../spikes/android-gate0/evidence/emulator-noop-zap-2026-09-03/README.md)
record the exact results. This update is not installed on the paired A16 and does
not claim physical timing, schedule delivery or Play readiness.

Still outside this MVP: accounts/cloud sync, purchases, widgets, Capture/Signal,
weekly Noise Rewind, advanced reports/polish, A17 hardware acquisition, component
libraries and architecture rewrites. Keep essential permission/privacy controls,
native navigation, disable/Force Stop/uninstall access and truthful recovery.

The smaller core safety checkpoint controls release-app promotion, not MVP feature
scope. Longer endurance/cross-OEM/full-parity work must not justify omitting required
MVP features. These work packages describe dependencies, not a promise of exactly
four iterations or a fixed approval date; local tests cannot guarantee Play approval.

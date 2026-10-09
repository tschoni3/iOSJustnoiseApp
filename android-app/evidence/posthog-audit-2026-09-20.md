# Android PostHog audit — 2026-09-20

## Verdict and scope

**PASS for the bounded release check: real owner-device opt-in start/end receipt and
real opt-out start/end non-ingestion are proven.** The account-transition and
process-restart variants were not run and remain explicitly out of this bounded pass.
See the latest sections below. Earlier audit/test entries are retained as historical
evidence, not the current implementation verdict.
This is an audit, not a feature change or release approval. No app source, iOS files,
phone state, account/provider settings or analytics consent was changed. No synthetic
events were sent to the production PostHog project. Tests used in-memory stores and
mock HTTP transports. The existing APK/AAB was not rebuilt or installed.

## Verified implementation

| Area | Result / evidence |
| --- | --- |
| Destination | Android uses the same public project token and US ingestion host as the read-only iOS source reference. Live project ownership/token validity is not verified. |
| Event allowlist | Exactly `focus_session_started` and `focus_session_ended`. No invented mode/schedule events. This matches the approved connected-release contract, not full iOS analytics parity. |
| Trigger wiring | Successful persisted manual/direct Zap start or stop emits from `Gate0Controller.handleZap`; a successful persisted scheduled start emits from `startScheduled`. Invalid, ignored and failed decisions do not enter those emission branches. This is source evidence, not a new physical NFC test. |
| Consent | Off on construction/default installation. Restoration is gated on account reconciliation. UI enablement asks for a separate confirmation; cancel does not enable. Opt-in persistence must succeed. |
| Payload | Public project token, allowlisted event, random UUID `distinct_id`, and only `$process_person_profile: false` in properties. No account identifier/email, selected apps, mode names, NFC contents, accessibility observations, credentials or session-history records. |
| Transport | HTTPS POST, no Authorization/Cookie from application configuration, redirects disabled, bounded timeouts. No PostHog SDK, replay, autocapture, durable queue, automatic retry or identify calls. |
| Account boundary | Disables capture, rotates generation/ID, cancels and joins pending jobs before acknowledging account mutation. Old-generation callbacks are rejected. Cleanup failure prevents account mutation. Already received events cannot be recalled. |
| Isolation | Analytics exceptions are contained; request work runs on IO workers. At most eight pending jobs are accepted. Analytics is not required for offline Zap operation. No performance benchmark was run. |
| Backup | Production source manifest disables app backup and full backup; consent does not have an analytics-specific external export path. |

Primary source locations: `app/src/main/java/com/justnoise/app/auth/AccountAnalytics.kt`,
`AccountRuntime.kt`, `AccountCoordinator.kt`, `AccountActivity.kt`, and canonical
`spikes/android-gate0/.../feature/Gate0Controller.kt`.

## Findings / limitations

1. **Original opt-out durability risk — fixed in the follow-up below.** If storing `false` fails, capture
   remains off in the current process and account reload retries the write. However,
   the previous durable `true` can be restored after process death because the pending
   revocation marker is only in memory. The existing UI accurately limits its guarantee
   to this process. Current-process regression coverage passes; do not describe this as
   a durable opt-out guarantee across a failed write and restart.
2. **No explicit Android or app-version attribution.** The events share iOS names and
   project but contain no platform, version or schema property. Reliable platform/release
   comparisons cannot be established from this Android payload alone. Recommend a small,
   explicitly reviewed non-user-content platform/version schema rather than broad SDK
   collection. This would expand the present allowlist and has not been implemented.
3. **Identity is process-local, not consent-lifetime durable.** A new process creates a
   new UUID even if consent is restored. Unique-user counts are not actual user counts;
   start/end events across process death cannot reliably be paired. No session identifier
   or stored duration is sent. Do not infer retention or focus duration from these events.
   The connected-release document's “consent-lifetime ID” wording is therefore broader
   than the implementation's actual lifetime.
4. **Best-effort counts only.** Offline/timeouts/server errors, process death, generation
   changes and the eight-job limit can drop events. Independent requests can arrive out
   of order. No event UUID, client occurrence timestamp or retry queue is supplied.
   This is the approved no-replay design, not an exact accounting system. Product History
   remains the separate persisted record. No automatic replay is recommended for this audit.
5. **Anonymous profile setting is not proof of anonymous network data.** PostHog receives
   a network connection/IP; live project IP capture/enrichment and downstream destinations
   remain uninspected. The consent text acknowledges network IP exposure. Turning off
   person profiles does not by itself verify all server-side collection settings.

## Executed local verification

Command: production module `:app:testReleaseUnitTest`, filtered to
`AccountAnalyticsTest`, `AccountRuntimeTest`, `AccountCoordinatorTest`, with
`--offline --no-daemon`, one worker and the existing protected signing configuration.

| Suite | Tests | Failures/errors/skipped |
| --- | ---: | ---: |
| AccountAnalyticsTest | 8 | 0 / 0 / 0 |
| AccountRuntimeTest | 20 | 0 / 0 / 0 |
| AccountCoordinatorTest | 16 | 0 / 0 / 0 |
| Total | 44 | 0 / 0 / 0 |

Fresh XML timestamps: 2026-09-20 03:52:49 UTC. Build successful in 15 seconds.
Reports: `app/build/test-results/testReleaseUnitTest/TEST-com.justnoise.app.auth.*.xml`.
Only eight tests directly target analytics; the other 36 exercise its surrounding
account/runtime boundary. These are not 44 separate PostHog delivery tests.

Verified cases include disabled/stale-generation capture, cancellation and joining,
failed consent writes, retry without resurrection in the same process, identity
rotation, startup reconciliation, exact payload/header allowlist and redirect refusal.
No claim is made of Android-engine cancellation on hardware, UI gesture testing,
network fault injection on the A16, or real server receipt. Dedicated tests for queue
saturation, process-restart identity semantics and actual session-to-event wiring are
not present in the inspected analytics tests; those aspects above are source findings.

## Live check blocked / exact remaining work

The browser reached `https://us.posthog.com/login`; no dashboard session is available.
No credentials were read or entered. The tab is retained for owner login.

After dashboard access, read-only checks should confirm the existing project/token,
recent event arrival and payloads, IP configuration, transformations/destinations and
existing dashboards' assumptions about identity, duration and platform. No production
test event is necessary for that initial inspection.

Final end-to-end consent acceptance later requires the owner-approved A16 flow: no
event before opt-in, real start/stop after opt-in, no subsequent event after opt-out,
and account-transition isolation. Coordinate with an actual session; do not generate
fake telemetry, change consent remotely, or repeat unrelated blocking tests. The owner
is currently away from the phone, so this acceptance is explicitly deferred.

## Official documentation checked

- [PostHog Capture API](https://posthog.com/docs/api/capture): public project-token
  endpoint, required event/distinct ID and anonymous-profile property. Top-level
  `distinct_id` is supported. An HTTP success alone does not establish visible ingestion.
- [PostHog data collection](https://posthog.com/docs/privacy/data-collection): IP capture
  has organization/project configuration; the live setting must be inspected rather
  than inferred from the client's person-profile flag.

Recommendation: retain the narrowly scoped consent-first adapter. Prioritize the
opt-out durability failure case and decide whether counts-only analytics is sufficient
before adding properties or stable identifiers. Do not call PostHog fully accepted
until the dashboard and physical consent/delivery checks are evidenced.

## Follow-up: owner requested testing through completion

Fresh run: **2026-09-20 03:59:49 UTC**, successful Gradle execution in 12 seconds.
Added `AccountAnalyticsFailureModeTest.kt` (tests only; no production behavior change).
This supersedes the earlier statement that queue/restart checks were source-only.

| Suite | Executed | Runner failures/errors/skips |
| --- | ---: | ---: |
| AccountAnalyticsTest | 8 | 0 / 0 / 0 |
| AccountAnalyticsFailureModeTest | 11 | 0 / 0 / 0 |
| AccountRuntimeTest | 20 | 0 / 0 / 0 |
| AccountCoordinatorTest | 16 | 0 / 0 / 0 |
| Total | 55 | 0 / 0 / 0 |

**A green runner is not release acceptance:** one of the 55 is explicitly a
known-risk characterization that succeeds when it reproduces the existing defect.
The desired durable opt-out requirement remains FAILED/UNRESOLVED, not passed.

New isolated checks cover:

- Both allowlisted event payloads, with no extra properties, credentials or cookies.
- Cancellation/join through the actual Ktor client using its mock engine.
- Eight in-flight sends; 32 excess calls dropped without later queued work.
- HTTP 401, 429 and 500 errors rejected with exactly one request each.
- Synthetic offline failure contained and not retried after cleanup.
- Invalid identifiers rejected before any HTTP request.
- Unreadable consent and failed initial opt-in remain disabled.
- Successfully stored opt-out remains disabled in a new analytics instance.
- Restored consent gets a new process-local identity.
- **Reproduction:** failed opt-out storage -> current instance disabled -> fresh
  instance reading the older durable value becomes enabled again.

Restart tests reconstruct the adapter over the same fake durable store; they do not
kill the owner's app and are not physical Android storage-failure evidence. The mock
HTTP tests do not establish Android-engine behavior, server receipt or UI consent
acceptance. Actual session-controller emission wiring is still source-reviewed only.

The A16 is connected (USB and wireless entries represent the same device).
Read-only package inspection found Justnoise 1.0.0/versionCode 1, targetSdk 36,
last update 2026-09-19 23:39:14. No install, launch, session/consent mutation, data
extraction or permission change was performed. A connected phone alone cannot
replace the owner's consent flow and physical Zap actions.

PostHog still displays its login page. Live receipt, project settings and dashboards
therefore remain unverified. No synthetic production event was emitted.

Production Android and iOS source were not edited. The existing tracked iOS diff
fingerprint was identical before and after this test extension:
`e5fbb80973d4464251e9ad13b5cdd5ae1b72c1d902774a87c48ef7534c19282e`.
This preserves pre-existing changes; it is not a claim that the entire repository
was initially clean. Existing signed artifacts were not rebuilt.

Remaining completion conditions: authorize/remediate the opt-out persistence risk
and rerun its desired-behavior regression; sign in to the existing PostHog dashboard;
then perform the scoped real consent/start/stop/opt-out acceptance with the owner.
Platform attribution and stable identity remain optional scope decisions, not changes
silently introduced by this testing request.

## Owner-authorized remediation — September 20

The production consent adapter now scopes a stored grant to a random, process-only
epoch (`ProcessBoundAnalyticsConsent`). The epoch is generated locally once per
OS process and is not restored or transmitted. A new process rejects all older
grants without needing a successful disk write. Old boolean-only grants are also
rejected. Explicit opt-in must still commit successfully, and existing same-process
revocation/generation and account-cleanup barriers are retained. Preferences remain
lazily opened on the existing worker path, not during main-thread construction.

**Intentional tradeoff:** after an app-process restart optional analytics defaults
off until another explicit opt-in. Activity recreation or backgrounding within the
same process does not reset it. Updated consent copy states the lifetime. There are
no changes to payload, events, IDs sent to PostHog, retry policy, authentication,
pairing, modes, schedules, session storage or iOS behavior. This is an Android-only
consent safety correction, not an analytics expansion or a shared iOS refactor.

The earlier positive defect characterization was replaced by a desired-behavior
regression: snapshot the old durable grant, fail the opt-out commit, reconstruct
storage/process from the older snapshot, restore, and assert capture remains OFF.
Five additional adapter tests verify legacy-grant refusal, same/new-process rules,
rejection even with unwritable storage, explicit opt-in/out and failed opt-in despite
SharedPreferences' in-memory mutation. These are simulated persistence faults;
they are not physical flash-failure tests.

Full production Android local suite: **469 tests across 52 suites; zero failures,
errors or skips**, XML run 2026-09-20 04:49:19–20 UTC. Includes 11 analytics failure
mode tests and five process-bound consent tests. All are now desired-behavior checks,
not a passing test that requires the known defect. Full release lint PASS: **0 errors,
74 warnings, 2 hints** (existing warning debt remains). Product contracts PASS: 18
colors, 27 copy keys and 89 portable cases. No emulator or owner-device tests run.

The live PostHog receipt/settings checks remain OPEN. Do not equate this local fix
with production event acceptance or claim it is installed on the A16.

Final packaging: `build/candidates/1.0.0-1-8dc7ba54/`, Justnoise 1.0.0 (1),
`com.justnoise.app`. Signed release build PASS (38 seconds); independent certificate,
release-only package checks, APK alignment and AAB native ELF validation PASS.
The verifier's first attempt selected macOS Python 3.9 and failed on a Python API;
rerunning unchanged with installed Python 3.14 passed. No check was disabled.

- APK SHA-256: `161ddd753295a93759d5727c64c17fe8e862fdd8f61e5b5bd046cb8a033ece48`
- AAB SHA-256: `8dc7ba545257fb34fd4e3e13eba7a37a4c600ea19cb458da35e1f3ed44592c9a`

The snapshot includes signatures/check report, checksum list, lint report and handoff.
The full repository Android pre-device verifier also PASSed: capability/UI/integrated
local tests, lint/build boundaries and 44 verifier regressions. Instrumentation APKs
were compiled by that script but no instrumentation was run on any device/emulator.
Existing iOS diff fingerprint remains unchanged. No installation or upload occurred.

## Later live inspection in Arc — September 20

The earlier dashboard-login blocker is superseded: the owner identified Arc as
the authenticated browser. Read-only inspection reached Justnoise project 239034
in US Cloud. Its public ingestion token matches the Android production adapter;
no secret API key was opened, copied or changed.

The Activity query for `focus_session_started` with Today selected returned no
matching events. The inspected prior 30-day rows were attributed to `posthog-ios`.
Neither result proves Android opt-out behavior or successful Android ingestion.
No synthetic events were sent and the owner's analytics consent was not changed.

Project Privacy visibly has **Discard client IP data = off**. Therefore profile-free
event payloads must not be described as verified IP-free collection. The UI also
states that enabling this switch can still permit enrichment before discarding
the IP. The shared project setting was left unchanged to avoid silently changing
iOS or other clients. Transformations/destinations and real Android consent/event
receipt remain open; live PostHog acceptance is not PASS.

Installation is now separately evidenced in
`a16-update-8dc7ba54-2026-09-20.md`; the earlier no-install statements above describe
their original local verification steps, not the current device state.

## Live pipeline review in Arc — September 20, follow-up

After the owner unlocked the Mac, these project 239034 pages were inspected:

- Destinations > Real-time: `No destinations found` with Show paused off.
- Destinations > Batch exports: `No batch exports found` with Show paused off.
- Destinations > Notifications: `No notifications found` with Show paused off.
  These results establish no currently displayed active forwarding configurations,
  not absence of paused/historical configurations. The onboarding examples were
  explicitly labelled example data and were not mistaken for configured exports.
- Transformations lists **GeoIP**, status **Active**. Its configuration has Enable
  destination on and no event matchers displayed; no Android exemption is shown.
  IP-derived enrichment is therefore a real project-level concern, not merely an
  uninspected possibility. Actual Android event enrichment remains unobserved.
- Event ingestion filtering explicitly reports **Filter is disabled; No events
  are being filtered or counted.** This configured ingestion filter is not an
  explanation for the missing Android event evidence.

No filter, transformation, destination, project privacy setting or consent was
changed. Only navigation/onboarding dismissal was used. No production test event
or payload was submitted. Real device consent/receipt verification remains open.

## Physical opt-in and first Android event — September 20, 19:12 Berlin

The owner reported that Optional analytics had been off and that they had now
enabled it themselves. No protected-screen workaround or remote consent toggle
was used. The owner then physically tapped the paired Zap and replied "gestartet".

The live Today query for `focus_session_started` in project 239034 changed from
zero matches to exactly one entry. Its timestamp is
**2026-09-20T17:12:31.201Z** (19:12:31 Europe/Berlin). The expanded event properties
show a random UUID distinct ID and person-profile processing false. No account
credentials, selected apps, mode names or NFC contents were observed in the
displayed event properties. This is real physical-session receipt, not a synthetic
capture request or a local adapter test. The page and timestamp were rechecked
before recording this result.

**Important finding:** IP and GeoIP-derived properties are present on this actual
Android event. Therefore enrichment is now directly observed, not merely possible
from project configuration. Location/address values and the distinct ID are
deliberately omitted from this report. Shared project settings remain unchanged.
Do not describe this collection as IP-free or fully anonymous.

Bounded result: **explicit owner opt-in followed by one real start event PASS**.
End-event receipt, post-opt-out non-collection, and live account-boundary behavior
remain OPEN. The owner has been asked to stop this same session once with the Zap;
no stop confirmation or matching end event has yet been recorded.

## Matching end event received — September 20, 19:18 Berlin

A subsequent completed Today query for `focus_session_ended` returned exactly one
entry at **2026-09-20T17:18:59.974Z**. Its random distinct ID matches the observed
start event. The properties again show person-profile processing false and actual
IP/GeoIP enrichment. No selected apps, mode names, credentials or NFC contents
were observed. The two event timestamps are about 6 minutes 29 seconds apart;
that is event timing, not an independent assertion of protected duration.

The A16 screenshot shows the ordinary idle Zap screen; passive notification
inspection found the active-list section and **zero active Justnoise entries**.
History subsequently shows **7 Zaps / 215h 36m**, versus 6 / 215h 29m beforehand.
This is consistent with one new completion, not proof of every historical record.
No artificial completion or remote stop was issued, and no owner stop-message was
needed to observe the actual end event and idle state.

Bounded receipt check: **real consented start/end pair PASS**. Opt-out and account
transition checks remain OPEN. The protected account page was opened for the
owner to disable Optional analytics manually; no toggle or protected-screen
inspection was performed.

## Opt-out preparation — September 20, approximately 19:25 Berlin

The owner explicitly replied **"aus"** after manually disabling Optional analytics
on the protected account page. This is owner-reported consent state, not a remote
read of private preferences. Normal Home navigation was issued without restarting
the app process or changing session/account data. The baseline is one observed
start and one matching end event. A fresh completed end query still showed one
entry. A temporary query-only internal/test-user filter toggle was immediately
reverted to off; filtered results do not count as opt-out evidence. No project
ingestion setting was changed.

A subsequent physical session with analytics off is still required before a
no-new-events observation can count as the bounded opt-out acceptance result.
No opt-out PASS is claimed from the switch report or unchanged baseline alone.

### Owner-reported post-opt-out start — approximately 19:29–19:31

The owner replied "gestartet+#" after the requested physical Zap start with
analytics off. USB serial RFGYC3DYKBR was no longer reachable; `adb devices -l`
returned an empty list. No reconnection, settings change or synthetic start was
attempted. Active-session state and current phone connectivity cannot therefore
be independently confirmed in this pass.

The authenticated Arc project was recovered through its existing PostHog favorite.
A completed **Last hour / all events** query with internal/test-user exclusion
**off** displayed **Showing all 2 entries**: only the previously observed start
at 17:12 and end at 17:18, with the same random ID. No new start appeared after
the owner's post-opt-out start report. Both baseline timestamps are within this
window, so changing from Today to Last hour does not hide the comparison pair.

This supports bounded absence of a newly ingested event, not proof of no outbound
request: phone network/runtime state is not verified while disconnected. Stop
with analytics off and a final refreshed query remain required. Do not mark the
full opt-out or account-transition requirement passed yet.

### Wireless reconnection and post-opt-out start observation — 19:34–19:35

The previously stale wireless endpoint refused connection while wireless debugging
was off. The owner subsequently enabled it; the existing trusted A16 pairing
reappeared with a new endpoint. No pairing replacement, authorization revocation,
debugging toggle or app/process restart was performed remotely.

Commands through the newly discovered wireless transport now succeed. A settled
screenshot shows **Phone Zapped / Focus / 00:05:45** with no recovery warning.
Passive network state reports Wi-Fi **INTERNET and VALIDATED**. This supersedes
the earlier inability to inspect runtime/network state; no network traffic or
private app preferences were captured. Screenshot:
`a16-optout-active-2026-09-20.png`.

A new server query (Reload -> Cancel while running -> Reload when complete),
Last hour / all events / test-user exclusion off, still reports **all 2 entries**:
only the original consented start/end pair. Thus the physical post-opt-out start
produced no newly ingested event during this observed window despite confirmed
running UI and validated Internet. This is bounded live start-side evidence,
not an indefinite non-delivery guarantee or a packet-level proof of no request.

The owner was asked to stop once to cover the end side of this same opt-out test.
The start result does not close end-side, process-restart or account-transition
acceptance; no previously passed Chrome test was repeated.

## Bounded owner-device opt-out cycle completed — September 20, 19:46–19:49

The owner replied **"done"** to stopping the opt-out session. A single passive
notification check over the restored wireless connection found the active-list
section and **0 active Justnoise notifications**. No new session or remote stop
was generated. A fresh, completed server query after that confirmation, Last hour
/ all events / internal-and-test-user exclusion off, still returned **all 2
entries**, exclusively the original consented pair at 17:12 and 17:18 UTC. Both
baseline events remain within the query window; no opt-out start or end appeared.

**PASS, bounded physical cycle:** owner explicitly disabled analytics, physically
started and stopped a real session, active UI and validated Internet were observed
during that session, and no additional event was ingested through the final query.
This is not packet-level proof, an unlimited future-delivery guarantee, or live
account-transition/process-restart acceptance. Those distinct checks remain open.
Do not request another unchanged opt-in/opt-out start-stop cycle without a changed
build, an actual failure, or a specifically identified missing assertion.

The owner correctly flagged redundant status polling and repeated instructions.
Those repeated polls did not constitute additional acceptance tests. Future work
must reuse this evidence and ask only for genuinely new checks.

# Gate 0 local data and Accessibility inventory

Status: draft for technical, privacy, and Google Play policy review

Gate 0 and the integrated development app have no Internet permission, backend client, account, analytics SDK, telemetry, crash reporter, or network destination. Their main app sources contain no logging calls. The inventory below is the intended data boundary, including platform metadata outside Accessibility events; device evidence must confirm it with reviewed logs and network observation. See the [September 2 static review](PLAY_DECLARATION_DRAFT.md#static-review-checkpoint--2026-09-02) for the inspected build and limits.

The multi-app baseline contains multiple selected identifiers and a separate active
target snapshot; its dated A16 evidence remains the earlier single-target build.
This inventory also covers the source-level local History integration designed in
[`ANDROID_SESSION_HISTORY.md`](../../../docs/ANDROID_SESSION_HISTORY.md). The final
36-test emulator suite includes 12 passing History tests, and the full local verifier
passed; see the [readiness record](../../../docs/ANDROID_PREDEVICE_READINESS.md).
A16 acceptance, device privacy observation and OEM recovery remain unverified.
Schedule data/delivery remain unimplemented and require a later inventory update.

| Source | Field or derived value | Purpose | Processing | Persistence and retention | Network destination |
| --- | --- | --- | --- | --- | --- |
| Accessibility `TYPE_WINDOW_STATE_CHANGED` | `packageName` and `eventType` | Event type guards the declared callback; package identifier is the only observed app value and is compared with the active target set and Justnoise | Transient, in the service callback | Not persisted, queued, logged, or collected as history | None |
| Accessibility event | Node tree, source, text, content description, password state, notification content, window list | Not used | APIs are not called; `canRetrieveWindowContent=false`; no retrieval flags | None | None |
| Scoped launcher query | App label and package identifier for launchable activities | Display the local multi-app picker and the matched foreground app name on the blocked surface | In memory while the picker or blocked surface is built | Only chosen package identifiers persist; labels are resolved locally | None |
| Platform safety resolution | Home activity package identifiers, resolved Settings/uninstaller package identifiers, and default dialer package; fixed protected-package list and own package | Exclude system/escape apps from selection and enforcement | Transient local PackageManager/TelecomManager queries and set comparison | Not persisted, logged, or collected as history | None |
| Accessibility enablement setting | Flattened enabled-service component list from `ENABLED_ACCESSIBILITY_SERVICES`; derived Boolean for this app's service | Report whether the user enabled Justnoise, separately from service heartbeat | Transient parsing and exact comparison with Justnoise's expected component | Component list is not retained; the Boolean is recomputed for readiness | None |
| User configuration | Selected package identifier set (`selected_packages`); legacy single-package fallback (`selected_package`) | Saved restriction targets for the next session | Private `gate0_local_state` SharedPreferences; explicit Save while idle; Cancel makes no change | Until changed, withdrawal, uninstall, or app-data clear; changed selections are rejected while a session is requested | None |
| Active target snapshot | Package identifier set copied from saved selection at start (`active_packages`) | Keep the session's exact targets stable and identify the matching foreground target | Private `gate0_local_state` SharedPreferences; written with the start state | Removed on successful stop, withdrawal, uninstall, or app-data clear; not appended to a history log | None |
| Consent | Disclosure version and acceptance epoch milliseconds | Prove which prominent disclosure was affirmatively accepted | Local SharedPreferences | Until withdrawal, uninstall, app-data clear, or a new version requires fresh consent | None |
| Session state, including History identity | Desired-active Boolean, stable random session UUID, wall/monotonic start timestamps and random process UUID, sticky interruption and v3-history-eligibility flags, monotonic session revision, fail-closed recovery marker | Start/stop guard and reconciliation; identify one completion; distinguish uncertain duration from verified protection | Private `gate0_local_state` SharedPreferences; same-process duration uses monotonic time, cross-process non-negative wall-time fallback is interrupted | Active identity/clocks/flags cleared at stop; eligible completed fields move to the pending record; revision/recovery follow existing transition/cleanup rules | None |
| Last-finished identity | Last completed/stopped session UUID (`last_finished_session_id`) | Recognize an idempotent repeated stop without ending a newer session | Private `gate0_local_state` SharedPreferences; no target names or observed events | Until a later stop replaces it, withdrawal, app-data clear or uninstall | None |
| Pending completion | Session UUID, start/end epoch milliseconds, non-negative duration milliseconds, selected-target count, interrupted Boolean | Bridge one committed stop to durable History without duplicate or lost retry identity | One pending value in private `gate0_local_state` SharedPreferences, written atomically with active-state removal; drain before later starts/History reads, not during stop | Retained until idempotent database insertion is confirmed or withdrawal/app-data clear/uninstall; no package names or event contents | None |
| Completed History | Only session UUID, start/end epoch milliseconds, duration milliseconds, selected-target count and interrupted Boolean | Real History list and shared summary; labeled Session time, not verified protected time | Private SQLite `session_history.db`, `sessions` table keyed by UUID; no foreground-event collection | Until withdrawal, app-data clear or uninstall; no cloud backup/transfer or network destination | None |
| History cleanup state | Database-deletion-pending Boolean | Retry interrupted withdrawal before consent, session start or History read; prevent old records reappearing | Durable marker in private SharedPreferences, written while clearing active state/outbox/configuration | Removed only after database cleanup succeeds; app-data clear/uninstall also remove it | None |
| Failed-write recovery | Pre-commit preference-map snapshot and unconfirmed-write Boolean | Restore in-memory state after `commit(false)` and quarantine History delivery until durable rollback confirmation | Process-local snapshot/flag; restored map uses the same private preferences and recovery marker | Temporary snapshot per commit; quarantine flag until confirmation or process end | None |
| Service liveness | Connected marker and `elapsedRealtime` heartbeat | Reject stale active claims | Local SharedPreferences; refreshed about every two seconds | Removed on known disconnect; invalidated at process start; cleared on withdrawal/uninstall/data clear | None |
| NFC reader | One NDEF message; transient total-record count; transient well-known Text-record count; TNF/type metadata for each record; payload bytes only for the single Text candidate | Pair or authorize one Zap interaction and classify a local scan failure without exposing payload contents | Exactly one Text candidate is required; non-Text auxiliary records are ignored without reading their payload through the adapter; multiple Text candidates are rejected as ambiguous; the candidate is validated as UTF-8 with a 35-byte language and 512-byte text bound; lifecycle cancellation invalidates a late result; an accepted foreground interaction reserves the shared three-second mutation claim before reader mode restores direct dispatch; owned candidate/canonical byte arrays are zeroed after use | Counts and raw/canonical record bytes are not persisted, logged, or sent to analytics | None |
| Direct NFC dispatch | Intent action/MIME, transient live `EXTRA_TAG` object, and process-local elapsed-time mutation claim | Test tap-to-open activation from an unlocked home screen | Only exact NDEF Text dispatch is eligible; the physical tag is re-read through `Ndef`; copied `EXTRA_NDEF_MESSAGES` are never read or trusted; missing/unusable live tags cannot mutate state; a process-local three-second claim suppresses a second mutation caused by tag bounce without reading a tag ID; a bounded process-start handshake waits only for fresh service evidence and otherwise fails closed | Tag data is not persisted, logged, or sent to analytics; the elapsed-time claim lasts only for the process lifetime | None |
| NFC tag object | Hardware tag identifier | Not used | `Tag.id`/`getId()` is never read | None | None |
| Android Keystore | Non-exportable HMAC-SHA256 key | Compute a keyed digest of the canonical NDEF Text record | Android Keystore | Until withdrawal, uninstall, or app-data/key clear | None |
| Zap configuration | Base64 HMAC-SHA256 digest | Constant-time local authorization comparison | Local SharedPreferences | Until replacement, withdrawal, uninstall, or app-data clear | None |
| Window layout | Current WindowMetrics bounds, visible system-bar insets, stable display-cutout insets, and calculated overlay rectangle | Keep the actual touch-blocking window outside visible system bars and cutouts | Transient local geometry on show/inset/configuration updates; no view-tree or other-app content access | Current rectangle exists in window layout parameters only; not persisted or logged | None |
| Overlay | Justnoise copy template, matched foreground app display label, and buttons | Interrupt a matching active-session target and expose escape routes | In memory while visible | No overlay content is retained | None |

## Explicit emergency-app protection

The fixed protected-package list now explicitly includes Google Personal Safety,
`com.google.android.apps.safetyhub`, after an emulator picker check found it
selectable. Its [official Google Play listing](https://play.google.com/store/apps/details?id=com.google.android.apps.safetyhub)
describes emergency SOS, emergency sharing and medical-information functionality,
so it must be excluded from selection and enforcement. Its exclusion regression
passed in the 24-test API-36 emulator rerun, with zero failures or skips; see
[`../README.md`](../README.md#multi-app-source-verification--2026-09-03) for the run
and earlier test-synchronization failures. This source-level safety correction adds
no event fields, retention or network destination. It is not Google Play approval
or proof that all emergency surfaces on every OEM device are protected. The dated
A16 evidence remains unchanged.

## Selection migration and disclosure

When the set-based selection key is absent, the legacy single package is read as a
one-item selection. A legacy requested session without an active-snapshot key uses
that restored selection; an explicitly stored empty set never falls back to the old
key. Starting a session writes the set-based selection and snapshot together. These
changes do not erase or replace the Zap digest or Keystore key.

The multi-app baseline used `gate0-accessibility-v2`. The History source integration
uses `gate0-accessibility-v3` and explicitly describes completed session fields,
local retention and interruption limits. Older acceptance is not silently upgraded;
new service/start use requires fresh affirmative consent without clearing selection
or pairing. Existing v1/v2 active intent remains stoppable but does not create a
backfilled History record; only new v3 starts are history-eligible.

## Withdrawal and deletion behavior

September 2 baseline: withdrawal first attempts to clear desired enforcement and publishes a state revision so any visible overlay is removed. It attempts to delete the Keystore alias and clear the dedicated Gate 0 SharedPreferences contents, sends an app-scoped non-exported withdrawal signal to the bound service, and asks the service to disable itself. The controller reports success only when both Zap cleanup and preference cleanup return success. Setup uses that result: it displays complete-deletion feedback only on success; a failure message asks the user to retry or use App info to clear storage/uninstall. That diagnostic is shown for 15 seconds instead of the normal 3.5-second success message. The feedback fix was installed as `dbe2469` while idle/revision `19`; see the September 2 record. Installation does not prove deletion under injected storage/Keystore failure.

History extension requirement: atomically clear active intent, pending completion
and configuration while leaving a durable database-deletion marker, then clear
History and remove the marker only on success. Consent/start/read retry pending
cleanup before proceeding. Complete-deletion feedback must also require successful
History cleanup; disabling Accessibility alone does not erase saved History. This
new cleanup path and its injected-failure cases passed emulator tests; this does not
prove every OEM/storage-failure outcome or the paired A16 result.

Re-enabling instructions require a newly accepted current disclosure. Android uninstall or Clear storage is intended to start with consent unchecked because cloud backup and device transfer are explicitly excluded. Failed durable writes and OEM restore behavior remain device-validation cases, not guaranteed deletion outcomes.

## Evidence still required

- Confirm through redacted logs that no foreground package history or NDEF data appears.
- Observe network traffic on the required A16; expected Gate 0 traffic from the app is zero. A17 validation is deferred and non-blocking.
- Confirm OEM backups do not restore consent or pairing; `allowBackup=false` is a source claim until verified.
- Verify History stores only the six declared fields, retries one completion without duplicates, and clears both pending/database records through interrupted withdrawal without claiming premature success.
- Review every dependency and merged manifest from the CI report before policy sign-off.

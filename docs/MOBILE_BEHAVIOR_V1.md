# Mobile behavior contract v1

iOS registered Zap update (November 21): fresh activation and session controls accept both the original sold Zap labels and the new issuer-signed, encrypted record bound to the UID read from the chip. A registered Zap must contain a single valid credential. The iOS reader verifies signed credentials in UTF-8 or UTF-16 Text records. New station writes use UTF-8 and legacy label matching keeps its original encoding rules. Reading a legacy label alongside a registered credential rejects the scan. The same Zap may activate multiple installations. See [iOS Zap registration](IOS_ZAP_REGISTRATION.md) for issuance, migration, and device acceptance. Android keeps its existing pairing policy.

November 6 language decision (supersedes earlier partial-localization notes):
iOS and Android ship English app-authored copy only until another language is
complete across the app. Remove partial German resources and their temporary lint
exception. Keep localization keys for future complete translations. Do not override
the device locale or translate user-entered mode names. OS-owned permission dialogs
and settings continue to follow the device language.


Android presentation update (November 5): welcome uses the same personal-benefit
copy as iOS. The first-session guide uses a black surface, full-frame looping video,
plain numbered steps (no enclosing card), close action and fixed bottom action.
English/German resources cover the welcome and guide. Android keeps its native
setup order, permission consent and unlocked-phone NFC tap-to-start/stop behavior.
No empty mode presets are seeded; the name field offers example names.
The Zap purchase link is https://justnoise.shop/pages/the-zap.


iOS onboarding update (owner, November 5): new installations follow Welcome →
Create your first mode → Connect Zap → Sign in/sign up → Home. Mode setup requests
Screen Time authorization only when the user chooses apps. Save requires a trimmed
nonempty name, at least one blocking target, approved authorization, and no active
session. Do not seed preset modes; persist and select the user-created
mode before marking intro setup complete. Remove legacy presets only when their
original stable ID/name is unchanged, they have no blocking targets, and no
schedule or active-session reference exists. Preserve configured/custom modes. Existing completed onboarding
is not reset. Android onboarding and shared session rules remain unchanged.

Welcome uses “Make space for you.” with a nontechnical personal-benefit subline.
The guide uses a scrollable video and numbered Tap/Hold/Done steps, with a fixed
bottom action and a 44-point close button; text supports Dynamic Type.
The bundled instructional video appears at first Zap activation, not at Welcome.
Its three short instructions describe tapping the in-app button, holding the top
of the iPhone against Zap, and waiting for the tone; start/stop use the same gesture.
The primary action dismisses the video before opening the activation scanner.
Closing without the primary action does not scan. Scan cancellation/failure leaves
activation retryable. Settings and the activation screen can replay the guide.
The mode name starts empty with localized examples (Focus, Sleep, Study, Work);
existing configured mode names stay unchanged. Welcome, mode setup, activation,
and the video guide follow the system/app language (English/German, English fallback).
The video loops until dismissed. Playback controls stay hidden; playback pauses when hidden/backgrounded and resumes
on foreground return. The basic three-tip Home tour remains removed; the optional
first-session walkthrough remains available. Notification/tracking timing is unchanged.

Android session shortcuts (owner, October 11): holding the Home Zap artwork starts
the selected mode without a scan, matching the unchanged iOS gesture. It is start-only,
requires account access, pairing, valid eligible targets, current disclosure and a
ready restriction service, and revalidates the same selection before committing.
Normal taps and NFC entry points retain their existing validation and stop rules.
Settings provides Emergency Unzap with five installation-owned unlocks, confirmation
bound to the current session ID, and atomic token consumption with the normal
stop/completion transaction. Cancel, idle, stale confirmation, exhaustion and failed
writes consume nothing. Recovery sessions can be released without re-enabling a
service. Account changes/deletion and disclosure withdrawal do not replenish unlocks;
uninstall/data-clear starts a new installation. No replenishment is defined.
Identifier-free cases are in `product/behavior/android-session-shortcuts.v1.json`.
The shield reuses the Zap artwork as a decorative image, not an unlock action.
These additions are source behavior, not device acceptance or Play distribution.

Android Zap-label clarification (owner, October 10): initial and replacement
pairing must recognize the unchanged iOS compatibility label list. Arbitrary
valid Text records are no longer enough. Matching is exact decoded UTF-8 text,
without trimming or broad case folding. Android keeps its existing local HMAC
pairing requirement and strict single-Text envelope. An already paired legacy
non-recognized tag retains stop-only escape for an existing requested session;
it cannot start a new one. Identifier-free migration fixtures are in
`product/behavior/android-zap-labels.v1.json`. This is not anti-cloning protection,
full iOS provisioning parity or physical-device acceptance. iOS is unchanged.

Android-only onboarding clarification (owner, October 10): mode creation is not a
first-run prerequisite. After account and blocking setup, Home is accessible with
an empty mode catalog. A new session still requires an explicitly selected valid
mode; no automatic default is created. iOS and shared session rules are unchanged.

Android production account-entry clarification (owner, September 9): sign-in or
registration is required before product access and new manual/NFC/scheduled starts.
A durably restored signed-in workspace remains usable offline, including expired
credentials. Signed-out, restoring and deletion-pending states cannot start a new
session; existing sessions always retain safe stop/recovery. Local unowned data is
preserved, not silently adopted by an account. The Android-only fixture is
`product/behavior/android-account-access.v1.json`; see `ANDROID_CONNECTED_RELEASE.md`
for the transaction boundary. Published iOS and offline development apps are unchanged.

Contract version: 1.4.0

This document describes observable Justnoise behavior that should remain consistent across iOS and Android. It separates portable product rules from platform adapters. It characterizes the published iOS direction without turning framework quirks or unverified schedule behavior into permanent requirements.

The rules below are the target contract. They are not a claim that the current iOS implementation already conforms. The rule-level audit later in this document keeps known gaps visible until native code and device evidence close them.

September 9 Android-only extension: `ANDROID_CONNECTED_RELEASE.md` records the
owner-approved Sign-in, PostHog and Now Bar scope. Live Updates now use the active
mode name on user-enabled system surfaces and no ordinary notification fallback.
This supersedes the older generic-notification-only statement below. iOS and the
original portable fixtures remain unchanged; account/analytics runtime delivery
is not yet complete and data isolation remains a prerequisite.

## Shared language

- **Zap**: the physical NFC item and the primary product interaction.
- **Mode**: a named selection of targets the user wants restricted.
- **Target**: an application, application category, or website/domain where the platform supports that target type.
- **Focus session**: a period during which the selected mode is active.
- **Blocked surface**: the interruption shown when a user tries to access a restricted target. The current iOS shield is system-owned and configured by Justnoise; the Android Gate 0 overlay candidate would be rendered and owned by Justnoise.
- **Capability**: a permission or system facility required to perform an action.

## Portable domain rules

### Modes and target selection

1. A mode has a stable identifier, a user-editable name, and a target selection.
2. A target selection is valid when at least one supported application, category, or website/domain is selected.
3. Create, edit, scheduling, and session-start validation must use that same non-empty rule. A website-only selection is not empty.
4. Removing a mode must not leave a schedule silently pointing to usable but missing mode data. The UI must prevent, repair, or clearly report the invalid schedule.
5. Platform selection tokens stay inside the platform adapter. Portable state stores stable product identifiers or an adapter-owned opaque selection reference.

The `targetSelectionValidity` cases in `product/behavior/portable-fixtures.v1.json` are executable examples of rule 2. Both native domain-test suites must consume or faithfully map every case, including `website-only`.

#### Approved Android named-mode extension — 2026-09-07

The owner now includes real named modes in the Android internal-test MVP. This
supersedes the earlier single-selection scope, not the shipped iOS implementation.
[`android-modes.v1.json`](../product/behavior/android-modes.v1.json), extension
version 1.0.1, contains framework-neutral examples exercised by Android's real mode
rules and codec. It adds no iOS edits or claim that the existing iOS MODE-01 gap is
closed. The original shared fixtures remain unchanged.

- A local mode has a stable ID, revision, trimmed nonempty name (at most 60
  characters) and up to 500 eligible Android app targets; the catalog holds at most
  30 modes. New/edited modes require a nonempty selection. Installed-app eligibility
  remains an Android adapter check, not a string-format rule.
- Manual/direct Zap starts resolve the last explicitly selected mode. A missing or
  empty mode fails truthfully; no first-mode fallback. Owner extension 2026-10-11:
  creating a mode in the Android UI selects it in the same successful storage
  transaction. Editing an existing mode leaves selection unchanged. Non-UI saves
  require an explicit selection request. A scheduled start resolves its own saved mode ID and never changes the user's
  recent selection. Stops do not depend on a current mode lookup.
- All mode mutations and explicit selection reject an active/requested session or
  recovery state. Committed sessions retain their fixed target snapshot.
- Deletion is prevented while any saved schedule references the mode, including
  disabled schedules. Reassign or delete those schedules first. Deleting the recent
  mode clears that reference; zero modes stay zero without repopulated defaults.
- Migration runs once only when no catalog exists. An existing legacy selection,
  including an explicitly empty one, becomes `local-default` / **Noise**, retaining
  legacy schedule references and recent selection. No legacy key means an empty
  catalog. Missing references or intentionally empty saved catalogs never trigger
  fallback migration; initial setup may explicitly create the first Noise mode.
- Mode IDs/names/targets and recent selection stay local and clear with withdrawal,
  app-data clear or uninstall. History keeps its existing six-field schema; generic
  notification content gains no mode name or selected-app list.

These are implementation requirements, not evidence that persistence, device
acceptance, signing or the internal-beta checkpoint has passed.

### Focus-session lifecycle

The portable states are `idle`, `starting`, `active`, `stopping`, and `failed`.

1. Starting requires an existing mode, a non-empty selection, and an available/authorized restriction capability.
2. A successful start records the mode identifier and start instant, applies all supported selected targets, and exposes `active` state.
3. A failed start must not claim that the phone is Zapped. Preserve a recoverable prior state and show a useful error.
4. While active, elapsed time is derived from the recorded start instant rather than accumulated timer ticks.
5. A successful stop clears every restriction applied for the session, ends any active-session system surface, and records a non-negative duration.
6. Relaunching must reconcile persisted product state with the platform adapter before presenting a definitive active/idle claim.
7. The current iOS product has a three-second accidental-stop guard. Treat three seconds as v1 portable behavior unless a product-contract change intentionally removes it.
8. Emergency Unzap is a recovery path: it is available only during an active session, consumes one available token, and must clear restrictions through the same stop path. Token replenishment is not defined by v1.

The boundary cases for rule 7 are frozen in `product/behavior/portable-fixtures.v1.json`; exactly 3,000 elapsed milliseconds is allowed to stop.

### Zap activation and scanning

1. Activation accepts only a recognized Zap payload. Unknown, malformed, canceled, or duplicate reads do not activate or toggle a session.
2. With no active session, an accepted session-toggle scan starts the selected mode after capability validation.
3. With an active session, an accepted session-toggle scan stops it after the accidental-stop guard.
4. A scan session handles at most one accepted read and exposes cancel, unavailable, busy, invalid, and unauthorized outcomes without changing restriction state.
5. Authorized hardware identifiers and payload-validation details are security configuration, not portable copy or source-controlled product examples. Raw Zap payloads must not be sent to analytics. Before platform parity is claimed, each native adapter must document how authorized identifiers are provisioned, migrated, rotated, and kept out of telemetry.
6. A Zap entry point that does not present mode selection uses the most recently explicitly selected mode when starting from idle. If that mode is missing or its target selection is empty, the start fails truthfully and must not silently choose another mode. Stopping an active session does not depend on whether that remembered mode is still available.
7. NFC routing is platform-owned and must not weaken authorization. Android direct tag dispatch may remove the app-open and Scan-button steps, while foreground reader mode remains valid for pairing and recovery; both paths must feed the same authorization decision and session reducer. Intent action, MIME/external type, AAR, and parsed intent-message extras are routing evidence only; direct entry requires a usable live tag read before mutation.

The identifier-free `zapReadHandling` fixtures freeze authorization and duplicate-read outcomes without publishing a real Zap payload. The `sessionLifecycle` fixtures then apply those outcomes to idle/active state, selection validity, capability availability, and the three-second stop guard.

The NDEF envelope and provisioning model are not yet portable. The shipped iOS adapter walks delivered records and may accept a later recognized Text record. The Android Gate 0 safety parser allows non-Text auxiliary records but requires exactly one bounded Text candidate, rejecting multiple Text candidates as ambiguous. iOS uses a compatibility allow-list while the disposable Android spike uses local HMAC pairing. Resolve these differences, add ordered multi-record fixtures, and define ownership/replacement/migration before claiming production authorization parity. `docs/ANDROID_NFC_ACTIVATION.md` records the proposed Android routing architecture separately from this unresolved authorization decision.

### Shield copy and presentation

1. The title is the keyed value `shield.title`.
2. The message uses `shield.item.message` and replaces `{appName}` with the best display name supplied by the platform.
3. Missing or blank display names use the application or website fallback copy as appropriate. The literal text `{appName}` must never be shown to the user.
4. The primary action label uses `shield.primaryAction`.
5. The shared visual intent is a deep-charcoal blocked surface, high-contrast white title, softer secondary message, and a white primary button with dark label. The system-owned iOS shield may constrain layout, font, icon, or action behavior. An Android accessibility-overlay candidate is app-rendered, so Justnoise owns its layout and accessibility even though Android authorizes the overlay window.
6. The shield must remain legible with long names and accessibility text settings. Pixel identity between platforms is not required.

The exact application and fallback substitutions are frozen in the `shieldMessageRendering` fixtures. Trim a candidate display name before deciding whether to use a fallback; the literal placeholder must not survive rendering.

### History presentation contract

The `historyPresentation` fixtures freeze the release History screen’s small, observable presentation rules without making the device calendar part of the portable domain:

1. The summary counts each stored session as one Zap and sums valid non-negative durations before truncating to whole minutes. Two 30-second sessions therefore display as `1m`, not `0m`.
2. A summary duration below one minute displays `0m`; a selected-day detail with positive sub-minute duration displays `<1m`.
3. Duration labels omit a zero minute suffix: `1h`, not `1h 0m`; otherwise they use English compact labels such as `1h 5m` and `13m`.
4. Day density is `empty` below 15 minutes, `light` from 15 minutes, `medium` from 2 hours, and `dense` from 6 hours. Exact threshold seconds and boundary cases live in the fixtures.
5. The characterized density colors are semantic tokens `history.dot.empty`, `history.dot.light`, `history.dot.medium`, and `history.dot.dense`.

Both native rule implementations consume the same fixtures. The shipped iOS summary, selected-day label, and density mapping call the portable rules; the permission-free Compose History prototype uses the same Kotlin rules. Calendar-day bucketing remains platform-owned: the current iOS implementation uses `Calendar.current`, splits sessions at local midnight, discards each split slice shorter than 60 seconds, shows the current calendar year, and builds its “90 Days” window from 60 past through 29 future days. Those calendar, time-zone, DST, corrupt-duration, and cross-midnight slice semantics are observed implementation details, not frozen portable behavior yet.

### Schedules: identity, safety and explicit timing

Current iOS storage has a stable identifier, name, mode identifier, Foundation `Date`, integer weekdays using `Calendar.current` values (`1` for Sunday through `7` for Saturday), enabled state, and an optional last-fired `Date`. Scheduling code later extracts local components from those absolute dates. That mixed representation is an implementation fact, not a cross-platform date/time contract.

A future portable schedule contract must use these named weekday identifiers instead of platform calendar integers: `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`, and `sunday`. The same reserved set appears in `product/behavior/portable-fixtures.v1.json`. The identifiers do not decide locale-specific display order or which day starts a displayed week.

The following rules are safe to share now and are exercised by the `scheduleEligibility` fixtures:

1. A schedule cannot apply restrictions without a resolvable mode and non-empty target selection.
2. Disabled schedules do not start sessions.
3. One-time and repeating schedules remain distinguishable in persisted data and UI.
4. Any eventual time-zone, daylight-saving, reboot, force-stop, and permission-revocation outcomes must be explicit and tested on each platform before schedule parity is claimed.

Eligibility diagnostics use this portable precedence when several gates fail: `disabled`, then `modeUnavailable`, then `targetSelectionEmpty`. Empty named weekdays classify the schedule as `oneTime`; one or more named weekdays classify it as `repeating`. These decisions do not select a fire instant or scheduler.

#### Android MVP temporal rules — 2026-09-03

The owner confirmed **automatic start at the chosen time, paired Zap to stop**.
The implementation defaults from `ANDROID_SCHEDULE_PLAN.md` now have explicit
`scheduleTemporal` fixtures; they do not characterize the shipped iOS runtime.

1. One-time input is a strict Gregorian local date/hour/minute and named IANA zone
   (or UTC), resolved to a fixed instant. Reject invalid, nonexistent and non-future
   input; do not move it to tomorrow. A repeated local time uses its first occurrence.
2. Repeating schedules use named weekdays and hour/minute in the device's current
   zone. Skip a DST gap day; use the first repeated time only, even if calculation
   happens after that first occurrence. A zone change recalculates future instants.
3. Next-occurrence calculation is strictly after the supplied instant. A persisted
   last-resolved local-date high-water mark prevents replay after clock rollback or
   travel. It is not a claim that a callback was delivered or blocking was applied.
4. Delivery first rejects stale/disabled revisions and duplicate occurrences,
   then invalid or early times. More than 300,000 ms late is missed; the boundary
   is inclusive. An already-requested session, including recovery, wins without
   mutation or blocker dismissal. Only the remaining case is a start **candidate**:
   current consent, pairing, resolvable/non-empty selection and real capability
   still need validation before the atomic session start.
5. Consider all enabled schedules, ordering due candidates by instant then stable
   schedule ID. Commit a receipt with each outcome and atomically with a successful
   session start. Do not implement schedule callbacks as NFC toggles. One-time
   schedules become resolved after their single start, skip or missed outcome.
6. Scheduled sessions have no automatic end or midnight cutoff. The same UUID,
   accidental-stop guard, stop/outbox/History and safe recovery paths apply.
7. Reboot, permission changes and user recovery revalidate future alarms. Force
   Stop remains an escape, never bypassed or followed by surprise catch-up. Optional
   notification state must derive from the same committed occurrence/session.

Supported instants are integer Unix milliseconds from 0 through 253402300799999.
The original September 3 Android slice referenced one stable local default selection;
the September 7 named-mode extension above preserves that legacy ID and lets new
schedules reference their chosen mode. Absence is `modeUnavailable`, while a resolved
empty selection is `targetSelectionEmpty`. The temporal rules themselves do not
require mode-management UI. Pure Swift/Kotlin rules consume
the same fixtures, but **the shipped iOS scheduler is not migrated**. Its current
time/ownership behavior below remains a conformance gap, not implied parity.

`docs/SCHEDULE_PORTABILITY_AUDIT.md` records the current iOS source behavior and an interval-end ownership risk separately from portable product intent.

### Persistence, account boundaries, and deletion

1. Signed-in identity and local signed-in UI state must reconcile to one authoritative session outcome.
2. Product data from one account must not appear for a different account on the same device.
3. Account deletion must either remove or deliberately retain every local and remote data category according to the published policy. Partial cleanup cannot be treated as complete deletion.
4. Audio capture ownership must be singular: a successful save leaves one owned recording; cancellation and failure remove unowned temporary files.

These are portable safety rules. The concrete cleanup and storage implementations remain native adapters.

## Platform adapters

| Product capability | iOS-only implementation today | Android contract status |
| --- | --- | --- |
| Select apps/categories/domains | Family Controls | Gate 0 selection mechanism unresolved |
| Apply restrictions and blocked surface | Managed Settings plus a system-owned shield | Consumer-device mechanism unresolved; Gate 0 candidate is app-rendered |
| Background schedule callback | Device Activity extension | OEM-safe scheduling mechanism unresolved |
| Read Zap | Core NFC NDEF session | NFC adapter plausible; device verification required |
| Active session on lock screen | ActivityKit Live Activity/widget | Native ongoing notification likely; presentation may differ |
| Shared extension state | App Group `UserDefaults` | No direct equivalent required; use native persistence/IPC |

Adapters may expose `unsupported`, `permissionRequired`, `permissionDenied`, `temporarilyUnavailable`, and `available`. UI must use those outcomes rather than pretending a capability succeeded.

## Current iOS conformance audit

Baseline: this repository state, including the mobile-contract foundation, portable Zap rules, and fail-closed account-deletion cleanup, audited 2026-07-22. “In code” is not physical-device evidence. Update this table in the same pull request that closes a gap.

| Rule | iOS status | Evidence and remaining work |
| --- | --- | --- |
| SEL-01: applications, categories, and websites all satisfy the same non-empty rule | **Conforms in code; device evidence required** | Create, edit, picker, schedule-save, schedule-candidate, and session-start validation use one rule that includes `webDomainTokens`. Native tests decode every shared `targetSelectionValidity` fixture, including `website-only`; actual website restriction still requires a Family Controls device check. |
| MODE-01: deleting a mode cannot silently orphan a schedule | **Does not conform** | Edit-mode deletion removes the mode directly. Existing schedules may retain that mode identifier and display “Unknown Mode”; no prevent/repair decision is implemented. |
| NFC-00: authorized identifiers are privately configured and raw payloads never enter analytics | **Partial; provisioning unresolved** | Accepted raw payloads are no longer sent to analytics, and tests use synthetic identifiers. The compatibility allow-list still source-controls three shipped payload strings; provisioning, migration, rotation, and secret-storage policy remain required before parity. |
| NFC-01: activation accepts only a recognized Zap payload | **Conforms in code; device evidence required** | Activation now requires a well-known NDEF Text record, UTF-8 payload, and the shared compatibility allow-list. Native tests cover authorized, unauthorized, malformed, wrong-record-type, UTF-16, and canceled outcomes; real authorized/unauthorized Zap evidence remains required. |
| NFC-02: session-toggle scans accept only a recognized Zap payload and handle at most one accepted read | **Conforms in code; device evidence required** | Session toggle uses the same strict parser and allow-list as activation, chooses at most one mutation-eligible result, and ignores duplicate accepted callbacks. Native tests decode every identifier-free `zapReadHandling` fixture; repeated physical scans remain on the device checklist. |
| SESSION-01: accidental stop is blocked before three seconds | **Conforms in code; device evidence required** | Manual scan entry and the toggle path use one `AccidentalStopGuard`. Native tests decode the shared threshold and verify that 2,999 ms is rejected while 3,000 ms is accepted; a real-device Zap check remains required. |
| SHIELD-01: display-name substitution, fallback copy, and white action | **Conforms in code; device evidence required** | The shield configuration substitutes the platform display name or fallback, uses the approved two-line template, and configures a white primary action. Long names and real shield rendering remain on the device checklist. |
| HISTORY-01: summary aggregation, compact duration labels, and density thresholds | **Portable rule frozen; iOS conforms in code** | Swift and Kotlin tests consume every `historyPresentation` fixture. The shipped iOS History summary, selected-day label, and dot-level mapping call the portable rules; the Android UI prototype does the same with representative local data. Calendar-day bucketing and device visual acceptance remain outside this row. |
| ACCOUNT-01: sign-out/account deletion prevent cross-account local-data leakage | **Partial; deletion conforms in code; sign-out and device evidence remain** | Account deletion is remote-first and fail-closed: it durably marks cleanup before local mutation, quiesces restrictions, Live Activities, Signal work, and analytics, clears the inventoried account-owned defaults and capture files, retains only installation-owned activation/onboarding/emergency state, and retries interrupted cleanup before product hydration. Native tests cover failure, retry, late async results, analytics queues, extension races, path containment, and preservation. Ordinary sign-out still leaves globally stored product data, and per-user namespacing is the next required change. |
| AUDIO-01: one owned file after save; no unowned file after cancellation/failure | **Conforms in code; device evidence required** | Capture audio ownership is crash-safe in code after PR #8 (merge `a81c8aa3a6f3903d348e5a5a877f4f64e43f8ddd`): drafts are moved into `CaptureClips`, cancellation removes owned drafts, and durable pending markers recover interrupted commits. The merged verifier passed 22 simulator tests; record/cancel/save/relaunch still requires physical-device and TestFlight evidence. |
| SCHEDULE-01: a schedule requires a resolvable mode with non-empty targets | **Portable rule frozen; iOS partial** | Both native tests consume all shared `scheduleEligibility` fixtures, and iOS bridge candidate selection now calls the portable rule without changing its linear-time filtering behavior. New schedule Save rejects a missing or empty mode. Mode deletion can still leave a persisted orphan and does not synchronously prove that a previously copied arm payload was cleared, so the iOS row is not closed. |
| SCHEDULE-02: disabled schedules do not start | **Partial; device evidence required** | The coordinator filters enabled schedules and rebalances after mutations, but background/extension behavior has not passed the physical matrix. |
| SCHEDULE-03: one-time versus repeating stays distinguishable | **Conforms in persisted shape only** | An empty integer weekday array represents one-time and a non-empty array represents repeating in iOS. The MVP temporal rules are now explicit, but this iOS-only encoding and runtime have not been migrated to them. |
| SCHEDULE-04: date, weekday, duration, collision, missed-fire, and recovery semantics | **MVP rules defined; iOS runtime not migrated** | The owner-approved start/Zap-stop direction and documented timing defaults have shared `scheduleTemporal` fixtures and pure-rule consumers. iOS still uses its existing interval/ownership implementation; Android background delivery and receipt persistence still need implementation and device evidence. No runtime parity is claimed. |
| SCHEDULE-05: unattended interval-end ownership and cleanup | **Unverified risk** | The extension writes a session-start marker at scheduled start and later treats any such marker as a manual-session signal before clearing shields. Source inspection alone cannot prove unattended cleanup; characterize and close this on physical iOS hardware before using it as Android product intent. |

Closing a row requires a native behavior test where feasible and the listed physical-device evidence where the rule crosses NFC, Screen Time, background execution, storage, or system UI.

## Physical-device-only verification

The following cannot be signed off by simulator/unit tests alone:

- Real Zap activation, valid/invalid tag reads, repeated scans, and interruption handling.
- Restriction application and release for apps, categories, and websites.
- Shield display name substitution, fallback names, white primary button, and long-name layout.
- Schedule execution while the app is backgrounded, force-quit, after reboot, and across a local-time change.
- Live Activity/ongoing notification lifecycle and lock-screen presentation.
- Permission denial, later enablement, revocation during a session, and recovery without lockout.
- Account deletion followed by a different account on the same device.
- Recording cancellation, successful save, relaunch playback, and storage growth after repeated captures.

Use `docs/RELEASE_VERIFICATION.md` for the iOS release checklist and `docs/ANDROID_GATE_0.md` before committing to the Android restriction implementation.

## Parity definition

Parity means the same user intent, state transition, safety outcome, keyed copy, and semantic visual hierarchy. It does not mean identical framework APIs, navigation chrome, permission screens, typography metrics, or system-owned surfaces.

A platform is not “implemented” when it only renders the screen. It must also pass its capability, persistence, recovery, and physical-device checks, or visibly report that the capability is unavailable.

# Mobile behavior contract v1

Contract version: 1.0.0

This document describes observable Justnoise behavior that should remain consistent across iOS and Android. It separates portable product rules from platform adapters. It characterizes the published iOS direction without turning framework quirks or unverified schedule behavior into permanent requirements.

The rules below are the target contract. They are not a claim that the current iOS implementation already conforms. The rule-level audit later in this document keeps known gaps visible until native code and device evidence close them.

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

The identifier-free `zapReadHandling` fixtures freeze authorization and duplicate-read outcomes without publishing a real Zap payload. The `sessionLifecycle` fixtures then apply those outcomes to idle/active state, selection validity, capability availability, and the three-second stop guard.

### Shield copy and presentation

1. The title is the keyed value `shield.title`.
2. The message uses `shield.item.message` and replaces `{appName}` with the best display name supplied by the platform.
3. Missing or blank display names use the application or website fallback copy as appropriate. The literal text `{appName}` must never be shown to the user.
4. The primary action label uses `shield.primaryAction`.
5. The shared visual intent is a deep-charcoal blocked surface, high-contrast white title, softer secondary message, and a white primary button with dark label. The system-owned iOS shield may constrain layout, font, icon, or action behavior. An Android accessibility-overlay candidate is app-rendered, so Justnoise owns its layout and accessibility even though Android authorizes the overlay window.
6. The shield must remain legible with long names and accessibility text settings. Pixel identity between platforms is not required.

The exact application and fallback substitutions are frozen in the `shieldMessageRendering` fixtures. Trim a candidate display name before deciding whether to use a fallback; the literal placeholder must not survive rendering.

### Schedules: portable identity and safety only

Current iOS storage has a stable identifier, name, mode identifier, Foundation `Date`, integer weekdays using `Calendar.current` values (`1` for Sunday through `7` for Saturday), enabled state, and an optional last-fired `Date`. Scheduling code later extracts local components from those absolute dates. That mixed representation is an implementation fact, not a cross-platform date/time contract.

A future portable schedule contract must use these named weekday identifiers instead of platform calendar integers: `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`, and `sunday`. The same reserved set appears in `product/behavior/portable-fixtures.v1.json`. The identifiers do not decide locale-specific display order or which day starts a displayed week.

The following rules are safe to share now:

1. A schedule cannot apply restrictions without a resolvable mode and non-empty target selection.
2. Disabled schedules do not start sessions.
3. One-time and repeating schedules remain distinguishable in persisted data and UI.
4. Any eventual time-zone, daylight-saving, reboot, force-stop, and permission-revocation outcomes must be explicit and tested on each platform before schedule parity is claimed.

The following are **not frozen as v1 product behavior** because current iOS behavior is not yet sufficiently characterized:

- Whether multiple enabled schedules are armed simultaneously or only the next candidate.
- The intended scheduled-session duration and who owns the stop action.
- Collision rules between manual and scheduled sessions.
- Missed-fire/catch-up behavior after reboot, force-quit, time-zone changes, or revoked permission.
- Exact lead-time adjustment and the current implementation’s narrow monitoring window.
- Whether a one-time value is an absolute instant or a local wall date/time plus a named time zone.
- Whether a repeating value retains its creation time zone or follows the device’s current time zone.
- How nonexistent and repeated local times at daylight-saving transitions behave.
- Which calendar is used for date input and persistence, and whether week display order follows locale.

Do not reproduce those implementation details on Android as guesses. Resolve each item with tests and a product decision first. This contract intentionally makes no daylight-saving or time-zone choice yet.

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

Baseline: iOS `main` through capture-audio lifecycle merge `a81c8aa3a6f3903d348e5a5a877f4f64e43f8ddd`, audited 2026-07-22. “In code” is not physical-device evidence. Update this table in the same pull request that closes a gap.

| Rule | iOS status | Evidence and remaining work |
| --- | --- | --- |
| SEL-01: applications, categories, and websites all satisfy the same non-empty rule | **Does not conform** | Session-start and picker helpers include `webDomainTokens`, but create/edit Save validation checks only application and category tokens. A website-only mode cannot be created or edited reliably. |
| MODE-01: deleting a mode cannot silently orphan a schedule | **Does not conform** | Edit-mode deletion removes the mode directly. Existing schedules may retain that mode identifier and display “Unknown Mode”; no prevent/repair decision is implemented. |
| NFC-00: authorized identifiers are privately configured and raw payloads never enter analytics | **Does not conform** | `NFCViewModel` source-controls three accepted payload strings and sends an accepted raw payload as the `nfc_tag_id` analytics property. Before parity, choose and implement identifier provisioning, migration/rotation, and privacy-safe telemetry; remove raw payload analytics and add regression tests. |
| NFC-01: activation accepts only a recognized Zap payload | **Partial** | Activation compares the decoded payload with the configured allow-list, but record-shape validation and real authorized/unauthorized tag evidence are not frozen in tests. |
| NFC-02: session-toggle scans accept only a recognized Zap payload and handle at most one accepted read | **Does not conform** | The session-toggle path toggles after any decodable NDEF payload and does not call the activation allow-list validator. Multiple records in one callback are iterated, so the one-accepted-read rule is not guaranteed. |
| SESSION-01: accidental stop is blocked before three seconds | **Conforms in code** | The manual scan entry and toggle path both check elapsed time. Native tests still need to run the shared threshold fixtures and a real-device Zap check remains required. |
| SHIELD-01: display-name substitution, fallback copy, and white action | **Conforms in code; device evidence required** | The shield configuration substitutes the platform display name or fallback, uses the approved two-line template, and configures a white primary action. Long names and real shield rendering remain on the device checklist. |
| ACCOUNT-01: sign-out/account deletion prevent cross-account local-data leakage | **Does not conform** | Sign-out changes authentication/UI state but leaves product stores. Account deletion removes only two defaults keys; modes, schedules, activation/session state, Signal state, and recording files are not comprehensively inventoried or cleared. |
| AUDIO-01: one owned file after save; no unowned file after cancellation/failure | **Conforms in code; device evidence required** | Capture audio ownership is crash-safe in code after PR #8 (merge `a81c8aa3a6f3903d348e5a5a877f4f64e43f8ddd`): drafts are moved into `CaptureClips`, cancellation removes owned drafts, and durable pending markers recover interrupted commits. The merged verifier passed 22 simulator tests; record/cancel/save/relaunch still requires physical-device and TestFlight evidence. |
| SCHEDULE-01: a schedule requires a resolvable mode with non-empty targets | **Does not conform** | Schedule Save resolves a mode but does not validate a non-empty selection. Mode deletion can orphan it, and the bridge may arm timing without a current mode payload. |
| SCHEDULE-02: disabled schedules do not start | **Partial; device evidence required** | The coordinator filters enabled schedules and rebalances after mutations, but background/extension behavior has not passed the physical matrix. |
| SCHEDULE-03: one-time versus repeating stays distinguishable | **Conforms in persisted shape only** | An empty integer weekday array represents one-time and a non-empty array represents repeating. The temporal meaning is still unresolved and the integer encoding is iOS-only. |
| SCHEDULE-04: date, weekday, duration, collision, missed-fire, and recovery semantics | **Unresolved** | iOS currently arms only the earliest enabled candidate and uses a narrow interval. Those behaviors are not accepted portable product decisions and must not be copied to Android yet. |

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

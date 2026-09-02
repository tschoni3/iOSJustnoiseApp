# Google Play Accessibility declaration draft

Status: **draft / HOLD** — not legal advice, not submitted, and not evidence of Google Play approval

## Core functionality

Justnoise is a focus product. At the user's explicit request, the Gate 0 candidate compares the foreground package identifier from Android window-state changes with one app the user selected locally. During a user-started focus session, an exact match displays a Justnoise-owned interruption overlay. The service does not suspend the target package and is not a tool whose primary purpose is assisting people with disabilities.

## Why the Accessibility API is proposed

A normal consumer-installed application does not receive device-owner package-suspension authority. The spike is testing whether a narrowly configured Accessibility service can produce the disclosed user outcome without reading window content or requesting broad installed-app visibility. If Google Play policy review does not support this declared use, the broad consumer Android port remains on HOLD even if device tests pass.

## Data access and handling answer

- Accessibility event type: `TYPE_WINDOW_STATE_CHANGED` only.
- Fields accessed: `eventType` as a callback guard and `packageName` as the only observed app value.
- Derived value: package equality with the selected target or Justnoise, combined with local consent/session/recovery state to show, hide, or retain the overlay.
- Accessibility node/window content, typed text, passwords, notifications, and UI content: not accessed.
- Retention of event data or foreground history: none.
- Sharing or network destination: none. The app has no Internet permission.
- Configuration retained locally: selected package, disclosure version/timestamp, enforcement state/revision/heartbeat, and a Keystore-backed Zap HMAC digest.
- Separate local platform metadata: enabled-service components for Justnoise readiness, protected-package resolution, and window bounds/insets for safe overlay placement. None is used as app-usage history or sent off-device; see `DATA_INVENTORY.md`.

## Prominent in-app disclosure shown before consent

> Justnoise Gate 0 uses Android Accessibility only while you have explicitly enabled this prototype. It observes window-change events and reads the foreground package identifier so it can compare that identifier with the one app you selected. It does not read screen content, typed text, passwords, notifications, or accessibility nodes. Foreground event contents are not retained or sent anywhere. The selected app, consent version, enforcement state, and a keyed Zap digest stay only on this device. You can withdraw below, disable the service in Accessibility settings, Force Stop, or uninstall at any time.

The disclosure is followed by an initially unchecked control reading: “I understand this disclosure and choose to test the Accessibility capability.” Only the user's subsequent button press records consent and opens Accessibility settings. Installation, launch, onboarding, app selection, or NFC scan does not open settings. Accepted consent stays visibly checked until the separate withdrawal action clears it.

## User control and escape

The service is manually enabled in system Accessibility settings and can be manually disabled there. The white **Keep going** primary action returns to Justnoise; separate overlay buttons open Accessibility settings, App info/Force Stop, and Home. Justnoise, launchers, Settings, System UI, permission/package surfaces, installer/store routes, dialer/emergency packages, and resolved Home/dialer packages are excluded from target selection. Withdrawal attempts to remove local Gate 0 state and requests self-disable; the local follow-up reports incomplete cleanup instead of claiming successful deletion. Force Stop and uninstall are preserved as user-controlled escape routes.

## Static review checkpoint — 2026-09-02

Scope: internal source/package review, not Google Play acceptance or a completed
G0-13/G0-18 device cell. The installed integrated APK is source `d09833d`, SHA-256
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
Both spike/integrated source and merged debug manifests were inspected; `aapt2`
also inspected the integrated APK's permissions and packaged service XML.

| Boundary | Static finding | Remaining proof |
| --- | --- | --- |
| Permissions | NFC plus AndroidX's app-scoped signature receiver permission; no Internet, broad package visibility, usage-access, or draw-over-apps permission in the inspected APK | Recheck every release dependency and merged/packaged manifest; existing source checks do not exhaustively cover merged dependency manifests |
| Accessibility | Packaged metadata remains window-state-only, `canRetrieveWindowContent=false`, `isAccessibilityTool=false`, with no retrieval flags | Runtime logs/network observation and complete physical safety matrix |
| Consent | Shared setup shows separate disclosure before an unchecked control; the enable button records consent before opening settings; the service refuses missing/current-version-mismatched consent | Record fresh install, refusal, re-entry, acceptance, revocation, and re-consent on the actual release flow |
| User-defined behavior | Exact selected-package rule during a user-started session; no autonomous planning, content-based decisions, or simulated control of the target app | Product/policy acceptance of the observed interruption behavior and limits |
| Data | Reviewed app source has no logging/network client; inventory now explicitly includes event-type guard, service-enable metadata, protected packages, and overlay geometry | Dependency/runtime review is still required; static absence is not a network trace |
| Withdrawal | Review found that setup ignored the cleanup Boolean and always claimed deletion; local follow-up now maps success/failure to distinct feedback | Fault-injected deletion and recovery remain untested; follow-up is not installed over the navigation-test APK |
| Release identity | This is a debuggable prototype with an exported Compose preview Activity, not a release artifact | Review production ID, signing, release-only dependencies, declarations, and final App Bundle after Gate 0 |

The withdrawal UI wiring regression failed before the fix. Afterward, both pure
feedback tests and all nine static-policy tests passed, followed by the complete
`scripts/verify-android-predevice-readiness.sh` run (contracts, JVM tests, lint,
and all development APK builds). The follow-up integrated APK SHA-256 is
`c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`;
packaged permission/metadata inspection remained unchanged. This APK is **not
installed**: the phone remains on the earlier `d09833d` navigation-test build.
No paired-device instrumentation, consent withdrawal, or iOS changes were made.

Google's [Accessibility guidance](https://support.google.com/googleplay/android-developer/answer/10964491)
permits deterministic user-defined automation but requires a declaration and separate
in-app disclosure/affirmative consent for non-accessibility tools. This supports the
candidate's intended boundary; it is not approval of Justnoise.
The [sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241)
also requires accessible disable/uninstall routes and listing disclosure. Missing
system controls therefore remain a release issue until physically verified, even
when the app offers its own escape buttons.
The [package-visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779)
requires the narrowest justified inventory access. Scoped launchable-app discovery
has a documented selection purpose; removing `QUERY_ALL_PACKAGES` alone does not
establish approval of the complete product.

## Reviewer evidence to attach later

- Completed Galaxy A16/A17 launch-family matrix with exact model, region, RAM, build, and device metadata.
- A short video beginning at app launch and showing the entire disclosure (scroll slowly if needed), initial unchecked consent, refusal/back-out, re-entry, affirmative acceptance, manual settings enablement, and the core selected-app interruption. Add captions where the Accessibility use is not obvious. Include revocation/re-consent and separately demonstrate Force Stop, uninstall, and all overlay escape routes. See [Google's required video flow](https://support.google.com/googleplay/android-developer/answer/10964491).
- Merged-manifest and static-policy reports.
- Redacted logs plus network observation matching `DATA_INVENTORY.md`.
- Exact store listing and privacy disclosures.
- Product explanation of onboarding burden, bypass behavior, battery/OEM caveats, and why the outcome is valuable.

Internal review cannot guarantee acceptance. A policy uncertainty is `HOLD`; a later closed-track Play submission remains mandatory even after an internal Gate 0 PASS.

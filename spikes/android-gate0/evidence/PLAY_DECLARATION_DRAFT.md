# Google Play Accessibility declaration draft

Status: **draft / HOLD** — not legal advice, not submitted, and not evidence of Google Play approval

## Core functionality

Justnoise is a focus product. At the user's explicit request, the Gate 0 candidate compares the foreground package identifier from Android window-state changes with one app the user selected locally. During a user-started focus session, an exact match displays a Justnoise-owned interruption overlay. The service does not suspend the target package and is not a tool whose primary purpose is assisting people with disabilities.

## Why the Accessibility API is proposed

A normal consumer-installed application does not receive device-owner package-suspension authority. The spike is testing whether a narrowly configured Accessibility service can produce the disclosed user outcome without reading window content or requesting broad installed-app visibility. If Google Play policy review does not support this declared use, the broad consumer Android port remains on HOLD even if device tests pass.

## Data access and handling answer

- Accessibility event type: `TYPE_WINDOW_STATE_CHANGED` only.
- Field accessed: `packageName` only.
- Derived value: exact equality with one locally selected package identifier.
- Accessibility node/window content, typed text, passwords, notifications, and UI content: not accessed.
- Retention of event data or foreground history: none.
- Sharing or network destination: none. The app has no Internet permission.
- Configuration retained locally: selected package, disclosure version/timestamp, enforcement state/revision/heartbeat, and a Keystore-backed Zap HMAC digest.

## Prominent in-app disclosure shown before consent

> Justnoise Gate 0 uses Android Accessibility only while you have explicitly enabled this prototype. It observes window-change events and reads the foreground package identifier so it can compare that identifier with the one app you selected. It does not read screen content, typed text, passwords, notifications, or accessibility nodes. Foreground event contents are not retained or sent anywhere. The selected app, consent version, enforcement state, and a keyed Zap digest stay only on this device. You can withdraw below, disable the service in Accessibility settings, Force Stop, or uninstall at any time.

The disclosure is followed by an initially unchecked control reading: “I understand this disclosure and choose to test the Accessibility capability.” Only the user's subsequent button press records consent and opens Accessibility settings. Installation, launch, onboarding, app selection, or NFC scan does not open settings. Accepted consent stays visibly checked until the separate withdrawal action clears it.

## User control and escape

The service is manually enabled in system Accessibility settings and can be manually disabled there. The white **Keep going** primary action returns to Justnoise; separate overlay buttons open Accessibility settings, App info/Force Stop, and Home. Justnoise, launchers, Settings, System UI, permission/package surfaces, installer/store routes, dialer/emergency packages, and resolved Home/dialer packages are excluded from target selection. Withdrawal removes local Gate 0 state and requests self-disable. Force Stop and uninstall are preserved as user-controlled escape routes.

## Reviewer evidence to attach later

- Completed Pixel and Samsung matrix with build/device metadata.
- Video of disclosure, unchecked consent, manual settings enablement, revocation, Force Stop, uninstall, and all overlay escape routes.
- Merged-manifest and static-policy reports.
- Redacted logs plus network observation matching `DATA_INVENTORY.md`.
- Exact store listing and privacy disclosures.
- Product explanation of onboarding burden, bypass behavior, battery/OEM caveats, and why the outcome is valuable.

Internal review cannot guarantee acceptance. A policy uncertainty is `HOLD`; a later closed-track Play submission remains mandatory even after an internal Gate 0 PASS.

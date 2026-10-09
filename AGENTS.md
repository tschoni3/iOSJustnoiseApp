# Justnoise mobile repository guidance

## Product direction

This repository is the client source for two native applications:

- iOS: Swift and SwiftUI, using Apple frameworks through iOS adapters.
- Android: Kotlin and Jetpack Compose, using Android frameworks through Android adapters.

Do not introduce a cross-platform UI runtime merely to share code. Share product decisions as versioned contracts; implement native UI and system integrations on each platform. The backend remains a separate deployable system and repository.

Latest Android MVP scope (owner clarification, 2026-09-02): select multiple apps to
block, tap the paired Zap to start/stop sessions, working schedules, and real session
history. The earlier single-app beta is a foundation step, not the accepted MVP.
Keep accounts, cloud sync, widgets, Capture/Signal and advanced polish out of this
MVP unless separately requested. Follow `docs/ANDROID_PREDEVICE_READINESS.md`; do
not defer schedules/history out of the MVP or count preview screens as implementation.

## Read before changing mobile behavior

Before implementing a mobile feature or refactor, read:

1. `docs/MOBILE_ARCHITECTURE.md`
2. `docs/MOBILE_BEHAVIOR_V1.md`
3. `product/behavior/portable-fixtures.v1.json`
4. `product/design/tokens.v1.json`
5. `product/copy/en.v1.json`
6. `docs/ANDROID_GATE_0.md` for any Android restriction work
7. `docs/ANDROID_NFC_ACTIVATION.md` for Android Zap routing, pairing, or direct activation work
8. `docs/ANDROID_UI_PARITY.md` for Android presentation work
9. `docs/SCHEDULE_PORTABILITY_AUDIT.md` before any schedule implementation

These files describe the intended target architecture. Existing iOS literals are still being migrated, so do not assume the runtime already loads the JSON files.

## Cross-platform change discipline

Owner extension, 2026-09-09 (Sign-in, PostHog, Now Bar, "lets go"):
implement the connected Android release described in `docs/ANDROID_CONNECTED_RELEASE.md`.
The earlier account/analytics/Live Update exclusions no longer define the desired
release. Keep the capability spike local-only; production integrations must be
explicit, reviewed native adapters. iOS remains read-only. A production-only
promoted-notification permission is authorized; do not add foreground-service,
overlay, broad visibility or content-retrieval permissions for this feature.
No automatic telemetry, owner-account mutation, phone installation or distribution
is authorized by this source-implementation extension.

Owner exception, 2026-09-08 ("jetzt bauen"): local production-identity packaging
and signing in `android-app/` are authorized before internal-beta GO. Reuse the
canonical Android sources; do not duplicate engines. This authorizes building
only, not installation, upload or distribution. Both safety decisions remain HOLD
and missing device evidence stays unverified. Replace the old directory-absence
guard with production-layout verification; retain all capability checks.

Subsequent owner approval, 2026-09-08 ("ok done" in reply to the explicit
installation question): install the signed candidate separately on the owner's
A16 and temporarily disable the development package for scoped release acceptance.
Preserve all development data/pairing; no clearing/uninstalling or permission grants
through ADB. The owner performs release pairing and Accessibility consent/enablement.
This is not upload/distribution approval. Generic Gradle install tasks stay blocked;
the approved device operation targets the identified A16 explicitly.

- Start a portable behavior, copy, or visual-token change in the product contract, then update both native implementations in the same change once Android exists.
- Keep stable identifiers, state transitions, validation rules, copy keys, placeholders, semantic colors, and test fixtures portable. Add or update a framework-neutral fixture before duplicating a rule in Swift and Kotlin.
- Put framework types and permissions behind platform adapters. Apple `FamilyActivitySelection`, `ManagedSettings`, `DeviceActivity`, `CoreNFC`, and `ActivityKit` types must not enter portable models.
- Never promise Android parity by naming an Android API without testing it on consumer hardware. Follow `docs/ANDROID_GATE_0.md`: the owner's lean-delivery scope permits a minimal local internal beta after its separate A16 safety checkpoint is recorded as GO; full-product/public-release claims still require the complete gate. Do not require another phone or full parity merely to reach that first beta.
- If a behavior cannot be equivalent, document the platform difference and its user impact before implementation. Do not silently approximate it.
- Keep Android as one application module initially. Add a Gradle module only when a measured build, ownership, reuse, or testing boundary justifies it.
- Prefer small, cohesive files and explicit dependencies. Reducing file count or line count is not a goal by itself.
- Do not move the existing iOS project into a wrapper directory solely for symmetry. Add `android-app/` beside it and keep shared contracts at the repository root.
- Do not add new tracked paths with leading or trailing whitespace. Do not reintroduce legacy widget casing after it is normalized.

## Signal backend contract

The stable backend repository is [`tschoni3/JustNoiseApp`](https://github.com/tschoni3/JustNoiseApp). Its released Signal client contract is `contracts/signal/v1/openapi.yaml`, OpenAPI document version `1.0.0`, with response `contractVersion` 1.

Status: **released and pinned** from backend `main` merge commit `d445e7a45025b59ced388465cff3c84dd9ee86fd`. The byte-for-byte mobile copy lives at `product/api/signal/v1/`; `provenance.json` records the source path and SHA-256 of every vendored file, and mobile CI rejects checksum drift. The current Swift client remains hand-written against this contract; the vendored files are test inputs and do not claim generated-client coverage. Update the backend first, merge it, then deliberately refresh the pinned copy and native contract tests. The mobile repository must not silently create a competing API schema.

## UI and design verification

- On the owner's paired A16, do not run `uiautomator dump` or instrumentation,
  including while idle or inspecting History. The legacy UI automation connection
  can suppress Justnoise's Accessibility service and manufacture recovery warnings.
  Use screenshots/recordings, observed-coordinate navigation when authorized, and
  scoped passive diagnostics. Read the non-interfering inspection protocol in
  `spikes/android-gate0/README.md` before any phone UI inspection. An ADB navigation
  approval does not waive this existing test-isolation requirement.
- Use SwiftUI previews for iOS components and Compose previews for Android components.
- Add screenshot or golden tests for stable shared states when practical.
- Storybook is not required. Adopt a separate component catalog only if native previews and visual tests no longer make component states discoverable.
- Treat iOS Managed Settings shields and Android permission/settings pages as system-owned adapter surfaces. An Android `TYPE_ACCESSIBILITY_OVERLAY` candidate is app-rendered even though the system grants its window type; Justnoise remains responsible for its layout, accessibility, copy, and safe dismissal behavior.
- Decode contract colors as non-premultiplied sRGB `#RRGGBB` or trailing-alpha `#RRGGBBAA`. Android APIs commonly expect leading-alpha ARGB, so map components explicitly instead of passing an eight-digit contract string through unchanged.

## Completion checklist

For a portable mobile change:

- Update the relevant versioned product contract.
- Add or update portable behavior fixtures and native fixture tests.
- Implement or explicitly mark the state on iOS and Android.
- Add native tests for both implementations when both exist.
- Run `bash scripts/verify-product-contracts.sh`.
- Run the affected platform verifier.
- For any pre-device Android contract, UI, Gate 0, or readiness change, run `bash scripts/verify-android-predevice-readiness.sh`.
- Record physical-device-only checks for NFC, restrictions, schedules, notifications, widgets, or background execution.

Platform-only fixes may remain platform-only when the portable behavior is unchanged. Say why in the pull request.

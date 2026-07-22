# Justnoise mobile repository guidance

## Product direction

This repository is the client source for two native applications:

- iOS: Swift and SwiftUI, using Apple frameworks through iOS adapters.
- Android: Kotlin and Jetpack Compose, using Android frameworks through Android adapters.

Do not introduce a cross-platform UI runtime merely to share code. Share product decisions as versioned contracts; implement native UI and system integrations on each platform. The backend remains a separate deployable system and repository.

## Read before changing mobile behavior

Before implementing a mobile feature or refactor, read:

1. `docs/MOBILE_ARCHITECTURE.md`
2. `docs/MOBILE_BEHAVIOR_V1.md`
3. `product/behavior/portable-fixtures.v1.json`
4. `product/design/tokens.v1.json`
5. `product/copy/en.v1.json`
6. `docs/ANDROID_GATE_0.md` for any Android restriction work

These files describe the intended target architecture. Existing iOS literals are still being migrated, so do not assume the runtime already loads the JSON files.

## Cross-platform change discipline

- Start a portable behavior, copy, or visual-token change in the product contract, then update both native implementations in the same change once Android exists.
- Keep stable identifiers, state transitions, validation rules, copy keys, placeholders, semantic colors, and test fixtures portable. Add or update a framework-neutral fixture before duplicating a rule in Swift and Kotlin.
- Put framework types and permissions behind platform adapters. Apple `FamilyActivitySelection`, `ManagedSettings`, `DeviceActivity`, `CoreNFC`, and `ActivityKit` types must not enter portable models.
- Never promise Android parity by naming an Android API without testing it on consumer hardware. App restriction work is blocked by the Gate 0 decision in `docs/ANDROID_GATE_0.md`.
- If a behavior cannot be equivalent, document the platform difference and its user impact before implementation. Do not silently approximate it.
- Keep Android as one application module initially. Add a Gradle module only when a measured build, ownership, reuse, or testing boundary justifies it.
- Prefer small, cohesive files and explicit dependencies. Reducing file count or line count is not a goal by itself.
- Do not move the existing iOS project into a wrapper directory solely for symmetry. Add `android-app/` beside it and keep shared contracts at the repository root.
- Do not add new tracked paths with leading or trailing whitespace. Do not reintroduce legacy widget casing after it is normalized.

## Signal backend contract

The stable backend repository is [`tschoni3/JustNoiseApp`](https://github.com/tschoni3/JustNoiseApp). Its released Signal client contract is `contracts/signal/v1/openapi.yaml`, OpenAPI document version `1.0.0`, with response `contractVersion` 1.

Status: **released and pinned** from backend `main` merge commit `d445e7a45025b59ced388465cff3c84dd9ee86fd`. The byte-for-byte mobile copy lives at `product/api/signal/v1/`; `provenance.json` records the source path and SHA-256 of every vendored file, and mobile CI rejects checksum drift. The current Swift client remains hand-written against this contract; the vendored files are test inputs and do not claim generated-client coverage. Update the backend first, merge it, then deliberately refresh the pinned copy and native contract tests. The mobile repository must not silently create a competing API schema.

## UI and design verification

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
- Record physical-device-only checks for NFC, restrictions, schedules, notifications, widgets, or background execution.

Platform-only fixes may remain platform-only when the portable behavior is unchanged. Say why in the pull request.

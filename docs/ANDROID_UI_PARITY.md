# Android Release UI parity

Status: **PRESENTATION ROUTE/STATE COVERAGE COMPLETE — PHYSICAL VISUAL ACCEPTANCE PENDING**

This audit compares the launch-ready iOS surface (`CAPTURE_ENABLED` absent) with the permission-free Compose project in `spikes/android-ui/`. “Covered” means a native Compose route or deterministic preview state exists and builds; it does not mean Android system integration, stored data, backend behavior, or pixel-perfect device acceptance is complete.

| iOS Release surface or state | Compose evidence | Status and boundary |
| --- | --- | --- |
| Startup hydration, account cleanup, cleanup failure/retry, and auth resolution | `StartupBoundaryPrototype.kt` | **Covered.** Local reducer/presentation only. |
| Two onboarding pages and reused videos | `LaunchFlowPrototype.kt` | **Covered.** Generated Android resources reuse the iOS source assets. |
| Zap activation handoff | `LaunchFlowPrototype.kt` | **Covered.** No NFC scan is started. |
| Sign in, sign up, forgot-password, and reset-link password update | `LaunchFlowPrototype.kt`, `StartupBoundaryPrototype.kt` | **Covered.** No account or credential mutation. |
| Zap idle/active hero, artwork, mode sheet, toolbar, timer, and Unzap CTA | `ZapPrototype.kt` | **Covered.** State transitions are local preview actions. |
| First-run three-step Zap/mode/schedule coach tour | `CoachMarksPrototype.kt` | **Covered.** The order, copy, skip, next, and finish behavior match the Release iOS tour. |
| Mode selection, create, edit, delete confirmation, target counts, and app-picker entry | `ManagementFlowPrototype.kt` | **Covered.** The Android picker remains an explicit Gate 0 boundary. Nothing is stored. |
| History 90-day/year choice, summary cards, dot grid, and Settings entry | `ManagementFlowPrototype.kt`, `HistoryPresentationRules.kt` | **Covered.** Representative local durations flow through the shared Zap-count, duration-label, density-threshold, and semantic dot-color contracts; no session history is read or calendar-bucketed. |
| Schedule list, empty state, enable switch, create form, edit entry, and repeat-day controls | `ManagementFlowPrototype.kt` | **Covered.** No background schedule is registered and portable timing semantics remain deferred. |
| Settings account rows, onboarding, support/about links, emergency flow, account deletion confirmation, and sign out | `ManagementFlowPrototype.kt` | **Covered.** Destructive/external actions terminate in truthful preview notices. |
| Capture-disabled Session Complete | `SessionFeedbackPrototype.kt` | **Covered.** No reflection route is offered in the Release surface. |
| Conditional Noise Rewind ready card and seven-page flow | `NoiseRewindPrototype.kt` | **Covered.** Representative values; no analytics or persistence. |
| Blocked application/website surface, fallback names, long names, and white action | `PlatformBoundaryPrototype.kt` | **Covered.** Activity/preview only; it does not intercept another app. |
| Permission-required, denied, temporarily unavailable, and unsupported recovery | `PlatformBoundaryPrototype.kt` | **Covered.** Recovery never reports unavailable capability as successful. |
| Capture tab and Capture/Signal feature family | iOS compile-time `AppFeatures.captureEnabled` | **Correctly excluded.** The currently published Release configuration hides Capture. |

## What the percentage means

- **100% route/state coverage** means every launch-ready iOS destination and major user-visible branch above has a native Compose representation or an explicit truthful platform boundary.
- It does **not** mean 100% visual acceptance. Typography, insets, animation feel, touch ergonomics, video playback, long/localized copy, and device-specific rendering still require emulator screenshot comparison and then physical checks on the existing A16. A17 visual validation is deferred and non-blocking.
- It does **not** increase the functional Android completion percentage by itself. NFC, restrictions, persistence, authentication/backend adapters, schedules, notifications, billing, and Play distribution remain outside the UI prototype.

## Repeatable evidence

Run:

```sh
bash scripts/verify-android-predevice-readiness.sh
```

The UI test suite rejects shared-copy/color drift, consumes every portable History presentation fixture, verifies launch and management routing, checks the first-run coach sequence, verifies the seven-page Noise Rewind sequence, and protects recovery/startup/account boundaries. Lint and APK assembly cover every Compose source file. The manifest/source policy check rejects permissions, services, and restricted capability APIs in this project.

For deterministic inspection, launch `MainActivity` with `prototype_scene=coach-tour` or `prototype_scene=schedules-empty`, in addition to the scenes listed in `spikes/android-ui/README.md`.

## Acceptance still required

1. Compare captured Compose states against the current App Store iOS build at representative compact, standard, and large phone sizes.
2. Check Dynamic Type/font scaling, screen-reader labels, contrast, focus order, and reduced-motion behavior.
3. Repeat the visual pass on the exact physical A16 used for Gate 0 and record its RAM tier. Other variants, including A17, remain unverified until optional later testing; no additional phone is required for the initial milestone.
4. Record intentional Android differences and their user impact before promoting the prototype into `android-app/`.

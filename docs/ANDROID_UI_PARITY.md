# Android Release UI parity

September 19 later owner clarification ("please remove entirely"): remove
permanent helper paragraphs and historical interruption/timing-warning copy from
normal Android History, day details, completion, modes, selection, schedules and
Settings. This supersedes the earlier requirement below to display those History
warnings. Preserve records/flags and the truthful Session time label; do not call
elapsed duration verified protection. Keep action failures, permission recovery,
essential state labels, empty states and consent/destructive-action explanations.
Successful schedule saves/toggles/deletions and successful local-data imports
must not leave permanent confirmation/helper cards. The updated visible list or
account state confirms success; action failures and setup-required states remain.
No iOS changes.

September 19 owner-requested Android copy cleanup: normal product screens must
not show execution diagnostics or implementation terminology. Remove successful
schedule receipt text and healthy-state revalidation commentary from schedule
cards. Keep next planned starts, actionable setup/failure states, and History
interruption/uncertain-duration warnings. Keep the truthful Session time label
and its detailed explanation; do not relabel elapsed time as protected time.
Live Update settings use consumer-facing wording without claiming Samsung
placement or visibility is under app control. Storage, engine behavior, consent
and published iOS copy remain unchanged.

Status: **PRESENTATION ROUTE/STATE COVERAGE COMPLETE — PHYSICAL VISUAL ACCEPTANCE PENDING**

Current real-app implementation, 2026-09-06: the integrated development app now has
source-matched onboarding/Zap/selection, real calendar History and compact schedules,
grouped Settings and exact-receipt foreground completion. The latest scope/results
are in [ANDROID_PLAY_IMPLEMENTATION.md](ANDROID_PLAY_IMPLEMENTATION.md). The older
prototype and September 3 checkpoints below are historical, not the current real
app's feature inventory. Final iOS rendered/A16/release acceptance remains open.

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
| Schedule list, empty state, enable switch, create form, edit entry, and repeat-day controls | `ManagementFlowPrototype.kt` | **Covered in the permission-free prototype.** These preview controls do not register schedules. Real integrated-app source wiring is recorded below; it uses the now-defined temporal rules. |
| Settings account rows, onboarding, support/about links, emergency flow, account deletion confirmation, and sign out | `ManagementFlowPrototype.kt` | **Covered.** Destructive/external actions terminate in truthful preview notices. |
| Capture-disabled Session Complete | `SessionFeedbackPrototype.kt` | **Covered.** No reflection route is offered in the Release surface. |
| Conditional Noise Rewind ready card and seven-page flow | `NoiseRewindPrototype.kt` | **Covered.** Representative values; no analytics or persistence. |
| Blocked application/website surface, fallback names, long names, and white action | `PlatformBoundaryPrototype.kt` | **Covered.** Activity/preview only; it does not intercept another app. |
| Permission-required, denied, temporarily unavailable, and unsupported recovery | `PlatformBoundaryPrototype.kt` | **Covered.** Recovery never reports unavailable capability as successful. |
| Capture tab and Capture/Signal feature family | iOS compile-time `AppFeatures.captureEnabled` | **Correctly excluded.** The currently published Release configuration hides Capture. |

## Integrated History source — 2026-09-03

Separately from the permission-free prototype above, `spikes/android-integrated/`
now routes to `IntegratedHistorySurface`, backed by `Gate0Preferences` and local
SQLite completion records. It provides loading/error/empty states, a paged session
list, app counts, **Sessions** and **Session time** totals, and **Blocking interrupted**
labels. Shared duration formatting is reused; elapsed session time does not establish
continuous blocking. No real 90-day/year grid or weekly insight is claimed. Local
History instrumentation and the full repository verifier now pass; these are not
physical visual acceptance. A16 History/visual results remain pending.
See `ANDROID_SESSION_HISTORY.md` for persistence, consent and recovery boundaries.

## Integrated schedule source — 2026-09-03

The integrated app now also has `IntegratedScheduleSurface`: real list/empty/error
states, one-time and repeating editor, enable/delete actions, next planned occurrence
and latest persisted outcome. It uses the existing native schedule/session engine,
not the preview list. Native date/time pickers, localized weekday labels, wrapped
controls, safe insets and scrolling handle the platform presentation. Exact-alarm
access and existing Accessibility setup are explicit user actions; missing setup
remains visible. A historical STARTED outcome is not a claim of current blocking.
The retained ViewModel keeps drafts/in-flight mutations across rotation, with
explicit conflict/reload and process-death uncertainty states. Three Compose
previews cover empty, saved and editing surfaces. A [bounded emulator UI smoke](../spikes/android-gate0/evidence/emulator-schedule-ui-2026-09-03/README.md)
passed setup states, draft rotation, Settings denial/return, create/edit-cancel,
disable and delete confirmation; A16 visual and functional acceptance remains
pending. See `ANDROID_SCHEDULE_PLAN.md` for the native boundaries.

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

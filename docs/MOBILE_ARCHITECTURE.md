# Mobile architecture

Status: target architecture, version 1.0.18

## Decision

Justnoise will use native SwiftUI on iOS and native Kotlin with Jetpack Compose on Android. The two applications share product contracts rather than a UI runtime. This preserves the published iOS experience and gives each platform direct access to the system capabilities required for NFC, restrictions, background work, notifications, and platform-owned UI.

The backend stays separately deployed. Its API contract can be consumed by both clients, but backend source code does not belong in either mobile application.

Owner MVP clarification, 2026-09-02: Android must support selecting multiple apps,
real Zap sessions, working schedules and actual session history. The earlier
single-app local beta is only a foundation step, not MVP completion. These four
features remain local-first; authentication/cloud sync, widgets and Capture/Signal
are not prerequisites. The current dependency order and evidence are in
`docs/ANDROID_PREDEVICE_READINESS.md`.

Android MVP implementation update, 2026-09-02: the existing development sources now
support one saved multi-app selection and a fixed active-session target snapshot.
Native Save/Cancel selection cannot change an active session; the legacy single
target and Zap pairing are retained. Updated plural Accessibility disclosure requires
fresh consent. This is an Android adapter extension of the existing shared selection
rule, not a new cross-platform framework or iOS change. It is not yet A16 multi-app
acceptance; stable session identity, persisted History and schedules remain unfinished.

History source integration, 2026-09-03: the development engine now assigns stable
session UUIDs and queues a completion atomically with stop. `Gate0Preferences`
drains this local outbox idempotently into SQLite before starts/History reads, never
as a prerequisite for releasing a committed stop. The integrated Compose app reads
real records through `IntegratedHistorySurface`: a list, **Session time** summary
and interruption labels, not the preview calendar grid. The v3 disclosure, rollback
and cleanup boundaries are in `docs/ANDROID_SESSION_HISTORY.md`. This is native
adapter work with unchanged shared formatting/iOS behavior. The final 36-test
emulator suite (including 12 History tests), 92 JVM tests and full local verifier
passed; A16 History/visual acceptance remains pending and schedules unimplemented.
See `docs/ANDROID_PREDEVICE_READINESS.md` for the scoped results and fixed failures.

Schedule foundation, 2026-09-03: the owner approved automatic scheduled starts with
paired-Zap stops. Shared fixture set 1.5.0 adds 38 temporal cases consumed by pure
Swift/Kotlin rules. Android now has bounded, versioned private schedule configuration
and revision-checked CRUD on the existing preference transaction boundary. This
does not start sessions, register alarms, add permissions or expose schedule controls;
atomic occurrence receipts, native delivery, disclosure and UI wiring remain the
next slices. The published iOS scheduler and UI remain unchanged. See
`docs/ANDROID_SCHEDULE_PLAN.md` for the exact defaults and migration boundary.

## Readiness checkpoint

Audited 2026-08-07 against GitHub `main` merge `4bb4631148a1c5fdab0455c4e09b247c3ca74428`.

**Architecture foundation: READY. Production Android implementation: HOLD.**

The repository has the foundations needed for one Codex task to evolve two native clients: a verified SwiftUI application, versioned behavior/copy/design/API contracts, portable fixture cases, a native-platform adapter map, and a disposable Android feasibility spike. “Ready” means the architecture no longer needs a framework rewrite, Storybook, a mass directory move, or a shared UI runtime before Android work.

The exact audited `main` commit passed the GitHub **iOS verification** and **Product contracts** workflows on 2026-08-04. A fresh **Android Gate 0** workflow dispatch passed on 2026-08-05. On 2026-08-07 the same spike also passed 23 JVM tests, lint/APK assembly, five API-36 emulator instrumentation tests, and a cold-launch smoke check. These checks prove buildability and portable/static boundaries; they do not replace any physical Gate 0 cell.

The separate `spikes/android-ui/` project now proves the Release presentation sequence—startup hydration/account-cleanup gates, two onboarding pages, Zap activation, signed-out authentication and reset-link password update, Zap idle/active, the three-step first-run coach tour, Capture-disabled Session Complete, mode create/edit/delete states, History, schedule list/empty/create/edit states, Settings confirmations, and the conditional seven-page Noise Rewind—in Jetpack Compose. It also renders the shared blocked-surface contract and truthful restriction-capability recovery cards without declaring a service or permission. It maps all current copy and color contracts with drift tests, exposes Compose previews, reuses the existing onboarding videos/icon/Zap artwork through generated resources, and declares no user-facing permission or Android service. History density thresholds and English duration labels now have shared fixtures and pure Swift/Kotlin consumers; calendar bucketing remains deferred. `docs/ANDROID_UI_PARITY.md` is the screen/state coverage audit. All routing, authentication, activation, mode/app selection, schedule controls, settings/account actions, completion, Noise Rewind, blocked-surface, recovery, coach-tour, and Zap state changes are presentation-only; they are not backend, NFC, restriction, interception, background-work, analytics, eligibility, autoplay, persistence, account cleanup/deletion, credential mutation, or deep-link evidence and do not authorize `android-app/`.

It does not authorize a production Android application yet. All 19 required A16 Gate 0 cells are still `BLOCKED`, policy review is incomplete, and `spikes/android-gate0/evidence/DECISION_RECORD.md` remains `HOLD / NO-GO`. Per the owner's 2026-09-02 budget decision, A17 validation is deferred/unverified and non-blocking; no second phone is required. The subsequent lean-delivery decision permits a minimal local internal beta after the smaller A16 safety checkpoint in `docs/ANDROID_GATE_0.md` is recorded as GO. Do not create `android-app/` before that checkpoint or full Gate 0 PASS; an internal-beta GO does not authorize public launch or imply full parity.

`docs/ANDROID_PREDEVICE_READINESS.md` is the operational handoff. `scripts/verify-android-predevice-readiness.sh` validates all three disposable Android projects, their shared/static boundaries, and the absence of `android-app/` in one command; the **Android Gate 0** workflow runs that same command for Gate 0, UI-prototype, integrated-shell, shared-mapping, architecture, and contract changes. A green run proves only the pre-device baseline described there.

The iOS `ACCOUNT-01` ordinary-sign-out storage gap remains visible in `docs/MOBILE_BEHAVIOR_V1.md`. Per-account namespacing is legitimate product hardening, but it is not an Android feasibility prerequisite and must not be used to justify a broad startup/runtime rewrite. Address it later as a separate, measured change with an explicit migration policy and cross-account device evidence.

The owner reported that iOS 1.1.0 build 6 from this release line is live on the App Store. `docs/APP_STORE_1.1.0.md` records the repository evidence and the boundary between that evidence and App Store-side actions.

Device-development update, 2026-09-01: `spikes/android-integrated/` now combines the
reviewed Gate 0 sources with a native Compose Zap surface in one disposable development
APK. The polished surface starts a real lifecycle-scoped foreground NFC scan, and the
same APK includes the live-tag direct NDEF candidate and Accessibility overlay. An
owner-assisted Galaxy A16 run completed setup, local pairing, one foreground start, and
one immediate Chrome interception; it also exposed an overlay self-event loop that was
fixed and covered by pure event-rule tests. The full-screen overlay now explicitly
consumes unused touches. This is implementation progress and partial evidence, not a
Gate 0 pass: all 19 currently required A16 cells remain `BLOCKED`, sustained interaction and direct-Home
testing are pending, and no production `android-app/` is authorized.

Device follow-up, 2026-09-02: the owner-assisted A16 run completed one foreground
physical-Zap start, stable Chrome blocking in a short recording and separate touch
checks, and an authorized stop followed by responsive Chrome. The owner confirmed
that flickering was resolved in this sample. A later task-isolation update completed
one direct Home start/stop cycle without app UI or a chooser. A subsequent direct
start and Chrome check exposed missing normal Android navigation controls; the
installed window-bounds candidate `d09833d` restores navigation visibility and
correct portrait bounds in one sample, with native Home/Back touch routing still
unverified. The exact builds, recording limits,
screenshots, and remaining checks are in
`spikes/android-gate0/evidence/A16_PARTIAL_RUN_2026-09-02.md`; repetitions,
multi-target, and lifecycle/safety evidence still prevent an A16-scoped Gate 0 pass.
A17 validation is deferred and does not block the initial gate.

Complexity is governed by responsibility, not by the smallest file count. Do not collapse legitimate Xcode target folders or combine unrelated features to reduce a number. Conversely, do not add layers, modules, or component catalogs solely to make both platforms look symmetrical. Extract a boundary only when it removes duplicated product decisions, isolates a system capability, or reduces measured change risk.

## Repository shape

The existing iOS project remains at the repository root to avoid a high-churn move. Android starts as one application module:

```text
/
├── AGENTS.md
├── docs/
├── product/
│   ├── behavior/
│   ├── copy/
│   └── design/
├── scripts/
├── spikes/
│   ├── android-gate0/           # disposable capability candidate
│   ├── android-ui/              # permission-free Compose presentation proof
│   ├── android-integrated/      # disposable real-capability + polished Zap shell
│   └── android-shared/          # native copy/color mapping; no Gradle module or app
├── android-app/                 # future Gradle root
│   └── app/                     # one module initially
├── justnoise.xcodeproj          # existing iOS project
├── justnoiseradio/              # existing iOS app target
├── JustNoiseFocusMonitor/       # iOS adapter target
├── JustNoiseWidget/             # canonical target spelling after path cleanup
└── ShieldConfigurationExtension/
```

Gradle and Xcode create legitimate nested directories. Nesting is useful when each level represents a build, feature, or platform boundary. Repeated wrapper folders, trailing-space names, and casing drift are path defects, not architecture.

## Shared sources of truth

| Concern | Source of truth | Native responsibility |
| --- | --- | --- |
| Product state and rules | `docs/MOBILE_BEHAVIOR_V1.md` | Implement and test the same observable outcome |
| Portable rule examples | `product/behavior/portable-fixtures.v1.json` | Run the same cases in Swift and Kotlin domain tests |
| English copy and templates | `product/copy/en.v1.json` | Map keys to native localization resources |
| Semantic visual values | `product/design/tokens.v1.json` | Map tokens to SwiftUI and Compose values |
| Backend requests and responses | `product/api/signal/v1/`, pinned to `tschoni3/JustNoiseApp:contracts/signal/v1` at a merged commit | Generate or hand-write thin native clients |
| UI layout, accessibility, motion | Native components and previews | Follow platform conventions while preserving intent |
| System capabilities | Platform adapter | Report capability and failure honestly |

The JSON files are the product source of truth, not a requirement to parse JSON at application startup. Build-time generation or reviewed native mappings are both acceptable. Runtime UI must not depend on a repository file that is absent from the application bundle.

The Compose UI proof and integrated development shell compile the same native mapping
from `spikes/android-shared/`. Full key/value drift and color-component tests compare it
with the JSON contracts. Parsed native colors are cached once; presentation does not
parse JSON or reparse hex colors per frame. This is source reuse inside disposable
Android apps, not a new Gradle module or a cross-platform UI runtime.

The backend source file `contracts/signal/v1/openapi.yaml` is released at OpenAPI version `1.0.0` and returns `contractVersion: 1`. `product/api/signal/v1/` is a byte-for-byte copy pinned to backend `main` merge commit `d445e7a45025b59ced388465cff3c84dd9ee86fd`; its machine-readable `provenance.json` records a SHA-256 for the schema and all six canonical fixtures. Product-contract CI recomputes every checksum so both native clients can be tested against the same immutable input. This pin does not mean a client was generated: the current Swift DTOs and transport remain hand-written, and Android may use generated or reviewed hand-written mappings later.

## Color-token mapping

`product/design/tokens.v1.json` defines non-premultiplied sRGB colors. Six-digit values are `#RRGGBB` with implicit alpha `FF`; eight-digit values are `#RRGGBBAA`, with alpha last. The alpha byte is the nearest 8-bit representation of the characterized opacity. This deliberately differs from the leading-alpha `0xAARRGGBB` convention used by many Android APIs.

- SwiftUI: split the bytes and construct `Color(.sRGB, red: Double(r) / 255.0, green: Double(g) / 255.0, blue: Double(b) / 255.0, opacity: Double(a) / 255.0)`.
- Jetpack Compose: split the same bytes and use `Color(red = r.toFloat() / 255f, green = g.toFloat() / 255f, blue = b.toFloat() / 255f, alpha = a.toFloat() / 255f)`. If an ARGB integer overload is used, move the trailing alpha byte to the leading byte first.
- Android Views/graphics: call `android.graphics.Color.argb(a, r, g, b)`. Do not send a contract `#RRGGBBAA` string directly to an API that interprets eight digits as `#AARRGGBB`.

Each native token mapper must have a component-level test for one six-digit value and one eight-digit value, including `action.zap.stop.background`. Screenshot/golden tests must render translucent values over the surface named by the component. `scripts/verify-product-contracts.sh` validates the source encoding and the characterized Zap/Noise Rewind distinction before native mapping.

## Native layer boundaries

Use the same conceptual flow in both applications:

```text
Native UI and navigation
        ↓
Feature state / use cases
        ↓
Portable product models and rules
        ↓
Capability ports
        ↓
Platform adapters and backend client
```

Dependencies point downward. Views do not directly coordinate multiple system frameworks. Portable models contain identifiers and values, not `FamilyControls`, `ManagedSettings`, Android `AccessibilityNodeInfo`, or other framework-owned types.

A practical first Android package layout inside the single `app` module is:

```text
app/src/main/java/.../
├── app/          # application and navigation
├── feature/      # zap, modes, schedules, capture, signal, settings
├── domain/       # portable models, validation, use cases
├── data/         # persistence and backend repositories
└── platform/     # NFC, restrictions, scheduling, notifications
```

Package boundaries are sufficient at first. Create additional Gradle modules only after there is evidence that package boundaries are failing.

## Platform adapter map

| Capability | iOS adapter | Android adapter status |
| --- | --- | --- |
| App/site selection | Family Controls picker | Gate 0 must prove a policy-compliant installed-app selection path |
| Restriction surface | System-owned Managed Settings shield configured by Justnoise | Unresolved; an app-rendered `TYPE_ACCESSIBILITY_OVERLAY` candidate must pass Gate 0 |
| Scheduled start | Device Activity extension | Unresolved; exactness and OEM background behavior require a device spike |
| Zap scan and activation routing | Core NFC NDEF reader | Foreground reader mode is the pairing/recovery candidate; direct NDEF tag dispatch is the low-friction session-toggle candidate. Both remain device-gated in `docs/ANDROID_NFC_ACTIVATION.md` |
| Active-session surface | ActivityKit / Live Activity | Ongoing notification is the likely native expression; parity is behavioral, not pixel-identical |
| Persistence | Native stores plus shared app-group state | Native local persistence; schema semantics must match portable models |
| Authentication and Signal | Backend client | Separate native client over the same versioned API |

“Unresolved” is intentional. It prevents an incidental iOS implementation from becoming a false Android promise.

An Android accessibility overlay is not a system-owned equivalent of an iOS shield. Android owns the permission flow, Accessibility settings, and window authorization; Justnoise renders and owns the candidate blocked surface. That means its visual accessibility, touch handling, navigation behavior, privacy, and escape paths are all application responsibilities and must be tested explicitly.

Android NFC routing and restriction enforcement are separate adapters. Direct tag dispatch can remove the app-open and Scan-button steps, but it cannot authorize a Zap or enforce another package. The NFC entry must require a usable live `Tag` read, feed the same portable read decision and session reducer, and use Android 17's NFC-dispatch permission where supported; copied intent extras alone never mutate state. Accessibility remains the disclosed, manually enabled enforcement candidate. Routing records such as MIME/external types and AARs never count as authorization.

## Launch-ready iOS to Android delivery matrix

Audited from the iOS 1.1.0 Release root flow, compile-time feature gates, SwiftUI screens, and extension targets on 2026-08-07. This matrix controls the later full-product scope and sequencing; it is not a claim that an Android screen or capability already exists. The smaller local-only internal beta in `docs/ANDROID_PREDEVICE_READINESS.md` is an explicit sequencing exception: its safety checkpoint, not this entire parity roadmap, controls that first milestone.

| Product surface | iOS 1.1.0 Release source | Portable contract coverage | Android delivery decision |
| --- | --- | --- | --- |
| Startup and account-cleanup gate | Root routing blocks product hydration until cleanup succeeds | Account isolation and fail-closed deletion rules are portable; ordinary sign-out namespacing remains partial | Implement in the first production slice with native persistence and the same truthful loading/blocked outcomes |
| Onboarding and Zap activation | Two launch pages followed by physical Zap activation; Capture page is compiled out | Core brand/Zap copy and activation outcomes are only partially keyed; provisioning and multi-record selection remain unresolved | Use Android NFC language and permission flow, keeping foreground reader mode for pairing/recovery. The internal beta may use direct dispatch after its smaller real-device safety checkpoint passes; the full public reliability promise still requires the complete device criterion |
| Sign-in, sign-up, reset, and session restoration | Supabase email/password, Google sign-in, reset deep link, authoritative auth-session store | Identity/account safety outcomes are portable; provider SDK and redirect handling are adapters | Include in the first production slice; configure Android OAuth/package-signing identity separately and do not treat StoreKit state as entitlement |
| Zap home and focus-session lifecycle | Release opens directly on the single Zap surface; idle/active UI, mode selector, scan, stop guard, and completion sheet | Lifecycle reducer, stop guard, keyed core copy, semantic Zap colors, and last-explicit-mode intent are shared | Primary Android v1 surface after Gate 0, implemented in Compose against the shared reducer fixtures; direct NFC entry must call the same domain path rather than own a second session implementation |
| Modes and target selection | List, create, edit, delete, and Family Controls picker for apps/categories/web domains | Stable mode identity and non-empty selection rule are shared; selection tokens remain platform-owned | Primary Android v1 surface after Gate 0; expose only target types the Android adapter proves and report unsupported types honestly |
| Blocked surface | Managed Settings shield configured with app/site name, fallback copy, and white action | Shield template, fallbacks, action copy, and semantic colors are shared | Gate 0 blocker; Android owns a Compose/View overlay and must pass accessibility, layout, bypass, and escape-path checks |
| Emergency Unzap and recovery | Token-gated recovery plus relaunch/platform-state reconciliation | Recovery intent is portable; token replenishment is intentionally undefined | Include recovery in primary Android v1, but do not invent replenishment or stronger enforcement semantics |
| History and weekly Noise Rewind | Session totals, day grid, 90-day/year views, completion summary, and weekly insight | History Zap count, aggregate-before-minute-truncation, compact labels, density thresholds, and four density colors are shared; calendar bucketing, window/time-zone semantics, weekly insight, and most remaining copy are not frozen | Add after the core focus slice; reuse the History fixtures, then define calendar and weekly-insight fixtures before persistence-backed implementation |
| Schedules and notifications | Create/edit/enable/delete schedule, Device Activity extension, and notification nudges | Schedule identity, kind, named weekdays, and eligibility/failure precedence are shared in fixtures; time-zone, DST, collision, duration, missed-fire, ownership, and recovery semantics remain unresolved in `docs/SCHEDULE_PORTABILITY_AUDIT.md` | Defer Android schedules until those decisions and fixtures are approved; do not copy the current iOS monitoring window |
| Settings, deletion, and support | Account details, language, onboarding reset, support/legal links, Emergency Unzap, deletion, and sign-out | Account deletion/isolation rules are shared; store links, language UI, and provider details remain native | Add account/deletion settings with the first authenticated slice; add secondary support/settings rows incrementally |
| Active-session system surface | WidgetKit extension supplies a Live Activity, Lock Screen, StandBy, and Dynamic Island presentation | Active/idle truth is portable; system presentation is not | Use an Android ongoing notification after the core restriction flow is stable; behavioral truth matters, not pixel identity |
| Capture and Signal | Capture entry points are compiled out of Release; development source remains available | Signal API contract is pinned, but Capture is not in the launch surface | Excluded from Android v1 until the iOS feature is launch-ready and separately scoped |
| Home-screen widget | No home-screen widget ships; the existing WidgetKit target contains only the Live Activity configuration | No widget activation contract is required for Android v1 | Explicitly deferred by product decision; it is not a prerequisite for the Android port |

### Delivery slices

1. **Current pre-device phase:** maintain shared contracts, the disposable Gate 0 and UI spikes, policy evidence, and this matrix. Do not create `android-app/`.
2. **MVP foundation after the separate A16 safety GO:** one Compose application module with local onboarding/pairing, one saved multi-app selection, durable session identity/target snapshots, the existing Zap/restriction engine and truthful recovery. Authentication, multiple named modes and cloud data are not required for this slice.
3. **Required MVP History:** persist real session completion once and connect native History to those records and shared summary rules. Preview data is not delivery; weekly insight and account/settings expansion are later work.
4. **Required MVP schedules:** define temporal/collision/recovery rules and fixtures, persist schedules and deliver them through the same session engine. This is sequenced after the foundation, not deferred outside the MVP. Release acceptance must include all four required features on the A16 candidate.
5. **Later products:** Capture, Signal UI, and an Android widget require separate launch decisions and are not allowed to delay the core Android focus experience.

### Contract expansion rule

Do not bulk-copy every current SwiftUI string, color literal, layout constant, or system phrase into `product/`. The current contract files are intentionally a small stable baseline. At the start of each approved production slice:

1. promote the shared observable states, copy, and semantic visual values used by that slice;
2. add portable fixtures for rules and formatted outcomes;
3. map the same keys in SwiftUI and Compose without changing already-approved iOS behavior accidentally; and
4. keep platform permission, OAuth, store, accessibility-settings, and system-notification language in native resources.

Deferred surfaces do not need speculative contract expansion. This keeps Codex changes simultaneous and reviewable once both native applications exist without turning today’s iOS implementation details into accidental cross-platform promises.

## Component workflow

Reusable components live in native code. Shared product files name states and semantic values; they do not describe view hierarchies.

- iOS: SwiftUI previews covering idle, active, empty, loading, error, long-copy, and accessibility states.
- Android: Compose `@Preview` functions covering the corresponding states.
- Both: screenshot or golden tests for stable screens and components, plus behavior tests below the UI.

Storybook is not a prerequisite. Native previews are closer to the actual typography, accessibility, rendering, and system components. A catalog app can be added later if discovery becomes a measured problem.

## Release feature gates

Unfinished product surfaces stay merged into the main codebase behind centralized, compile-time gates. This avoids a long-lived release branch while ensuring an App Store archive cannot expose work in progress because of a network response or stale local setting.

Capture is controlled by `AppFeatures.captureEnabled`, backed by the `CAPTURE_ENABLED` Swift compilation condition:

- The iOS app target's Debug configuration defines `CAPTURE_ENABLED`, so normal Xcode development keeps the full Capture experience available.
- The Release configuration does not define it, so App Store archives do not present any Capture entry point and open directly on Zap without a one-item tab bar.
- All user-facing entry points must use the same gate: navigation, programmatic routes, post-session actions, onboarding, coach marks, and settings.
- Capture source, local data, and account-cleanup support remain intact while the feature is hidden. Do not create a long-lived branch for Capture development.
- When Capture is launch-ready, enable it intentionally for the shipping configuration and update the release checklist in the same reviewed change.

Remote flags are not the release boundary for unfinished features. They may be added later for rollout control only after the compiled product is safe in both enabled and disabled states.

## Change workflow for Codex

For a feature intended on both platforms:

1. Identify whether the outcome is portable behavior or a platform capability.
2. Update the behavior, portable fixture, copy, token, or API contract first.
3. Implement native state and UI independently against that contract.
4. Run the same portable fixture cases in both native domain-test suites.
5. Add equivalent behavior tests and representative native previews.
6. Run product-contract verification and both platform verifiers.
7. Record real-device evidence for system capabilities.

This lets one Codex task change both applications without forcing the codebases into a shared runtime. It also makes partial parity visible: a contract change cannot be considered complete while one platform is silently unimplemented.

## Explicit non-goals for version 1

- No Flutter, React Native, or Compose Multiplatform rewrite.
- No shared navigation or view-model framework.
- No large “design system” invented ahead of current product needs.
- No Android multi-module clean architecture at project creation.
- No claim that Android can reproduce Apple Family Controls before Gate 0 passes.
- No mass iOS directory move merely to make the tree symmetrical.

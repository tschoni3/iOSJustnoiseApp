# Mobile architecture

Status: target architecture, version 1.0.0

## Decision

Justnoise will use native SwiftUI on iOS and native Kotlin with Jetpack Compose on Android. The two applications share product contracts rather than a UI runtime. This preserves the published iOS experience and gives each platform direct access to the system capabilities required for NFC, restrictions, background work, notifications, and platform-owned UI.

The backend stays separately deployed. Its API contract can be consumed by both clients, but backend source code does not belong in either mobile application.

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
| Zap scan | Core NFC NDEF reader | Android NFC reader mode is plausible but must be device-tested |
| Active-session surface | ActivityKit / Live Activity | Ongoing notification is the likely native expression; parity is behavioral, not pixel-identical |
| Persistence | Native stores plus shared app-group state | Native local persistence; schema semantics must match portable models |
| Authentication and Signal | Backend client | Separate native client over the same versioned API |

“Unresolved” is intentional. It prevents an incidental iOS implementation from becoming a false Android promise.

An Android accessibility overlay is not a system-owned equivalent of an iOS shield. Android owns the permission flow, Accessibility settings, and window authorization; Justnoise renders and owns the candidate blocked surface. That means its visual accessibility, touch handling, navigation behavior, privacy, and escape paths are all application responsibilities and must be tested explicitly.

## Component workflow

Reusable components live in native code. Shared product files name states and semantic values; they do not describe view hierarchies.

- iOS: SwiftUI previews covering idle, active, empty, loading, error, long-copy, and accessibility states.
- Android: Compose `@Preview` functions covering the corresponding states.
- Both: screenshot or golden tests for stable screens and components, plus behavior tests below the UI.

Storybook is not a prerequisite. Native previews are closer to the actual typography, accessibility, rendering, and system components. A catalog app can be added later if discovery becomes a measured problem.

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

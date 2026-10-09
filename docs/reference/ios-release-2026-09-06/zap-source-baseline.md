# Zap source-first baseline — 2026-09-06

The owner approved proceeding with Android Zap presentation from the unchanged
iOS source before the missing idle/active iOS screenshots are available. This
approval permits source-first implementation; it does **not** establish rendered
visual acceptance or 100% parity. The [reference README](README.md) identifies the
three states actually captured. None is the authenticated main Zap idle/active
screen. No iOS source, project, assets, permissions or stored state are changed by
this work.

## Release source measurements

The reference is `ContentView.swift` from the unchanged iOS Release source at
`11d4d98`. Release omits `CAPTURE_ENABLED`, so `MainTabView` renders `ContentView`
without a bottom tab bar; the Capture tab remains excluded. See
[routing](../../../justnoiseradio/Core/ContentView.swift#L30),
[feature gate](../../../justnoiseradio/Core/AppFeatures.swift#L7), and
[Debug-only flag](../../../justnoise.xcodeproj/project.pbxproj#L748).

Measurements below are SwiftUI points, not measured Android pixels. Native font
metrics, navigation-bar geometry and system insets require platform adaptation.

| Element | Observed source baseline |
| --- | --- |
| Toolbar | Native inline navigation bar; no explicit height or side padding. Centered template logo, 15pt high, white idle/black active. Idle-only calendar left and `circle.grid.2x2.fill` History right use `.imageScale(.large)`. [Logo](../../../justnoiseradio/Core/ContentView.swift#L137), [actions](../../../justnoiseradio/Core/ContentView.swift#L688). |
| Hero | 118pt container plus 8pt top padding; horizontal padding 24pt. Vertical text spacing −8pt. Title 50pt system heavy, minimum scale 0.7; subtitle 48pt system black, minimum scale 0.72, up to two lines. [Metrics and typography](../../../justnoiseradio/Core/ContentView.swift#L158). |
| Positioning | Hero block 126pt; artwork slot 240pt; state slot 84pt, starting 26pt after the artwork slot. Bottom reserved space is 132pt plus bottom safe inset. Hero-to-button gap is half the nonnegative remaining height, clamped to 48–88pt. [Layout](../../../justnoiseradio/Core/ContentView.swift#L388). |
| Idle | “Zap to Block” / “Distractions”; background RGB 0.082 (approximately #151515), white title and white 12% subtitle. [Copy and colors](../../../justnoiseradio/Core/ContentView.swift#L904). |
| Active | “Phone Zapped” / elapsed `HH:mm:ss`; white background, black title and black 16% subtitle. Hours have a minimum of two digits, not a two-digit cap. [Timer and colors](../../../justnoiseradio/Core/ContentView.swift#L908). |
| Selection | Approved/idle state: “Select a mode”, 14pt regular, #747474; 8pt gap to a white 220×38pt capsule. Name 16pt medium, black 92%; chevron 14pt regular, black 78%, 2pt gap. Shadows: black 22%, radius 10/y6; white 28%, radius 1/y−1. Border black 8%, 1pt. [Selector](../../../justnoiseradio/Core/ContentView.swift#L629). |
| Active state slot | Approved/active state displays the selected mode name, centered, 24pt semibold black; it is not an editable selector. [State slot](../../../justnoiseradio/Core/ContentView.swift#L612). |
| Unzap | “Unzap Phone”, 18pt semibold black, yellow 90%, corner radius 30pt; horizontal margin 28pt and bottom margin 12pt plus safe inset. Height is intrinsic text plus `.padding()`, not an explicit 56pt. Activation starts an NFC session-toggle scan, not an immediate stop. [CTA](../../../justnoiseradio/Core/ContentView.swift#L440). |

## Existing artwork, not substitute branding

`Justnoise_logo_nav` is a template image with aspect ratio 91:18, yielding about
75.83pt width at the source's 15pt height. The filenames are misleading; use the
[asset manifest](../../../justnoiseradio/Assets.xcassets/Justnoise_logo_nav.imageset/Contents.json)
as the mapping:

| File | Asset scale | Pixel dimensions |
| --- | --- | --- |
| `Subtract@2x.png` | 1× | 91×18 |
| `Subtract-1.png` | 2× | 182×36 |
| `Subtract.png` | 3× | 273×54 |

The existing `zap_button.png` is 1082×1109 pixels, aspect-fit inside 220×220pt,
centered in the 240pt slot. Preserve its transparency and aspect ratio; the source
does not circular-crop it or add an artwork shadow.
[Artwork rendering](../../../justnoiseradio/Core/ContentView.swift#L557).

## Intentional Android adaptations and retained safety

- Reuse the native Compose prototype's layout constants and existing artwork,
  **not** its preview reducer, fake modes, example durations, Signal values or
  “UI PREVIEW” branding. Its 52dp toolbar, 18dp side padding, 56dp CTA, custom
  shadows and fixed text line heights are approximations, not measured iOS
  values. Its title uses black weight rather than the iOS heavy weight.
  [Prototype geometry](../../../spikes/android-ui/app/src/main/java/com/justnoise/uiprototype/ZapPrototype.kt#L285).
- Use dp/sp and native safe insets. Interactive targets have a **48dp minimum**,
  including the selection capsule whose iOS source height is 38pt. Preserve
  readable, non-overlapping content with vertical scrolling on compact windows
  and enlarged text; do not force the fixed source layout to clip content.
- Android has **one saved “Selected apps” selection**, not named modes. Show the
  real selection/count and route to the existing app picker; do not invent a mode
  library to imitate the iOS label. The active selection is not editable.
- Retain Android's active **Session help** escape, recovery guidance, explicit
  disclosure and user-controlled system permissions. Hiding the idle toolbar
  actions during an active session must not hide the safety/help route or Android
  system navigation.
- Artwork tap and “Unzap Phone” invoke the existing scan/authorized-session path.
  They do not optimistically start/stop or bypass pairing, consent, active-session
  or recovery checks. Preserve direct NFC activation and real elapsed state.
- iOS source includes an idle long press that calls `blockApplications()` without
  a tag. It is observed source behavior, **not** authorization to add a no-tag
  long-press start on Android. No scanning policy, HMAC/payload handling,
  restriction engine, history schema or iOS behavior is changed by this visual
  baseline.

Source-backed implementation and emulator checks can proceed now. Rendered
idle/active comparison, typography/inset review and acceptance on the existing A16
remain separate evidence; no additional phone is required for this milestone.

# Settings and completion: source-first baseline

Date: 2026-09-06. Scope: the owner-approved Android-only Settings and completion
continuation in `docs/ANDROID_PLAY_IMPLEMENTATION.md`. The iOS source below was
read only. This document records source observations and implemented Android
adaptations; it is **not** an iOS screenshot, App Store capture, pixel-parity result,
test-pass report or A16 acceptance claim.

## Read-only source provenance

```text
8ce6687386a478933f0de0c68081eb15f5b0c9096652f5d44cfb373c6a3f4074  justnoiseradio/Core/SettingsView.swift
6089979f594f66e1e18ed88078d4c736bd4367139e557fde0f0ce243d7f83fa0  justnoiseradio/Core/ContentView.swift
```

These SHA-256 values identify the inspected source bytes, not an installed build.
The source-first authorization permits implementation before the missing actual
renders are captured; it does not remove final rendered-comparison requirements.

## Settings reference and Android adaptation

`justnoiseradio/Core/SettingsView.swift:46` starts the native NavigationView/Form.
Lines 50–80 contain account/name/email/subscription/language controls; lines 82–94
gate Capture preferences. Lines 97–104 offer the coach-tour reset; lines 107–130
group support and About links. Lines 134–195 contain token-based Emergency Unzap,
account deletion and sign-out. Lines 197–203 establish the Settings title and Done.

Android follows the grouped Form hierarchy with section headings, white rounded
rows on a grouped-gray background, prominent Settings title, readable row details,
trailing navigation indicators and a reachable Done action. The current
`IntegratedSettingsSurface` is presentation-only:

- **Setup:** the real controller truth is supplied by the host; the row opens the
  existing native disclosure/permissions/pairing/recovery screen. Rendering does
  not accept a disclosure, pair a tag or grant a capability.
- **How to use:** replay the existing two-page introduction. Unlike iOS's coach-mark
  reset, this is an explicitly labeled introduction replay, not a claim that the
  missing coach tour exists. Root-owned replay route/page state does not change
  first-run completion, consent, pairing, selected apps, session state or History.
- **Privacy & data:** a readable Android-specific draft policy and a review-only
  local-data-removal entry. The latter opens native Setup; it does not invoke
  withdrawal. Existing native withdrawal remains an explicit user action with
  distinct successful/incomplete-cleanup feedback.
- **Help & recovery:** explicit Android App info navigation, not an automatic Force
  Stop, data clear, permission toggle or emergency-token feature.
- **About Justnoise:** local-only product description and the actual installed
  version supplied through PackageManager. An unavailable version is labeled
  unavailable, not replaced with a fabricated value.

The History gear now routes to grouped Settings rather than directly to technical
Setup. The host pauses History reads and cancels active reader/selection work when
opening Settings, restores the selected History range on return, and retains only
presentation route/page state through recreation. Back from privacy returns to
Settings; Done/Back from Settings returns to History. Errors opening App info are
shown, not treated as successful navigation.

No account/profile, subscription, sign-out/account deletion, language-setting,
Capture preference, emergency-token, Play-review or unsupported support action is
introduced. No external contact is invented and no shop policy is silently
substituted for the Android notice.

### Privacy is explicitly unfinished

`spikes/android-integrated/app/src/main/res/values/privacy_draft.xml` bundles the
complete relevant privacy-policy section of
`spikes/android-gate0/evidence/PLAY_DECLARATION_DRAFT.md:244–383`: 18 paragraphs,
1,415 words at extraction. Only source wrapping and Markdown styling are
normalized for native text. Publisher/contact/public-URL placeholders remain
clearly labeled, nonfunctional plain text; no email or URL is linkified.

The entry and page visibly state that this is a **DRAFT — NOT FINAL**. The policy
does not replace the separate Accessibility disclosure. Final publisher identity,
working privacy contact, actual published Android policy URL and final-package
review remain publication/Play handoff blockers, not a reason to delay building
the read-only screen.

## Completion reference and Android adaptation

`justnoiseradio/Core/ContentView.swift:271` presents `SessionCompleteCard` with a
360-point detent. Lines 786–800 observe a blocked-to-unblocked change and show it
only for a duration of at least 20 minutes. Lines 954–1048 define the card.

| Source element | iOS observation | Android source adaptation |
| --- | --- | --- |
| Layout | 24-point vertical stack spacing; 28-point outer padding | 24 dp spacing, 28 dp content padding; scrolling and expandable sheet instead of a hard height |
| Handle | 42 × 5 capsule, top 8 | 42 × 5 dp capsule, top 8 dp |
| Heading | Uppercase 13-point semibold, tracking 1.2 | 13 sp semibold uppercase, 1.2 sp letter spacing |
| Primary identity | Mode name, 28-point bold | Real completed record's selected-app count, 28 sp bold; no named mode is invented |
| Duration | 34-point bold; source says “protected” | 34 sp real elapsed duration with “Session time”; shared duration formatting |
| Secondary text | 14-point medium protection claim | Explicit elapsed-time meaning; no guarantee of continuous blocking |
| Information card | Radius 22; padding 22 horizontal/14 vertical; 18-point heading | Equivalent hierarchy with real ended/interrupted/timing-uncertain labels |
| Done | 16-point semibold; dark rounded button, radius 18 | 16 sp semibold, black button, radius 18 dp, at least 52 dp high |

The Android `IntegratedCompletionState` preserves the source's 20-minute display
threshold without changing the session-start/stop threshold. A current resumed
foreground Zap STOP can supply its exact newly committed completion receipt.
The UI is not populated by observing idle state, polling the latest History row,
an idempotent stop, or a failed stop. Consumed IDs prevent duplicate presentation
within the current state holder; the completion holder is not serialized.

Backgrounding, withdrawal, a new session or navigation dismissal clears the
presentation. Direct Home NFC continues its unobtrusive behavior and does not
open the completion popup. Dismissing the card does not write History, stop
another session or replay the prior stop.

iOS's computed “stronger signal” percentage and blanket “focus was protected”
copy are not copied: the Android MVP does not establish those measurements.
The count, elapsed duration, interruption flag and timing-uncertainty explanation
come from the real completed record. Capture/Reflect remains absent, matching
the published Capture-disabled scope.

## Native replacement-pairing boundary reviewed read-only

The separate pairing safety change was inspected in
`spikes/android-gate0/app/src/main/java/com/justnoise/gate0/app/MainActivity.kt`.
`requestZapPairing()` first cancels existing reader state and obtains an opaque
snapshot token. Replacing an existing Zap requires the explicit confirmation
before a new scan begins. Dialog Cancel performs neither a scan nor a write.

`onPause()` dismisses the confirmation, cancels the one-shot coordinator and
clears transient pairing authority. A stale scan callback clears its bytes and
returns without pairing. `HmacZapStore` checks the token before work and
`Gate0Preferences.commitZapPairing()` rechecks under the state lock, including
session/recovery, pending-cleanup, frozen consent/session state, pairing generation
and expected digest. No-token compatibility calls are initial-pairing-only.

This read-only review found no actionable automatic-mutation or cancellation
lifecycle bug in that flow. This is not a claim that its newly added tests have
executed or that replacement has passed physical NFC acceptance. The grouped
Settings surface itself adds no replacement control.

## Verification status and remaining checks

Added Settings coverage comprises four pure-render cases and four real-Activity
cases: named navigation/no effects on render, actual version/error presentation,
draft policy/nonfunctional contact, 200% text scrolling, History/year restoration,
privacy recreation/Back, tutorial replay preserving stored authority/History, and
explicit native Setup review without automatic consent or scan. Activity fixtures
guard both emulator hardware and the disposable package before cleanup/seeding;
pure-render fixtures do not establish storage or device behavior.

Completion state/surface/Activity tests and replacement safety tests are tracked
by the main task's verification run. **No test result is asserted here.** Optional
fixture screenshots remain labeled fixtures; source-backed UI work, rendered
fixture checks, real controller/storage tests and physical evidence must remain
distinct in the final report. Required follow-ups include missing real iOS
Settings/completion captures, compact/large-text/reduced-motion visual comparison,
affected A16 navigation/NFC acceptance, and the real privacy publication details.

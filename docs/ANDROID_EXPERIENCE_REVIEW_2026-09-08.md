# Android experience review — 2026-09-08

**Latest implementation:** customer-facing setup wording and a guarded support-email
handoff/copy-address fallback are now implemented locally. 298 JVM / 36 artifact
checks and the final 10 Settings UI cases at each of normal/200% system text passed.
[Support/setup evidence](../spikes/android-integrated/evidence/support-setup-2026-09-08/README.md).
Production system branding, published privacy notice and inbox delivery verification
remain open. The development system/service identity is intentionally retained.

**Subsequent implementation:** findings 1 and 3 below are now implemented locally:
readable Android timer/recovery text and mode-aware onboarding. The final local
verifier passed 296 JVM and 36 artifact checks, and both affected emulator classes
passed 12 cases at standard size and the same 12 at compact/200% system text.
See [build-scoped evidence](../spikes/android-integrated/evidence/experience-polish-2026-09-08/README.md).
The owner phone was not updated. The original review and its other open findings
below remain historical audit evidence, not a claim these fixes shipped.

## Verdict and scope

The implemented Android experience follows the agreed Justnoise hierarchy and
uses real modes, sessions, schedules and History. Preserve that implementation;
this review does not justify a UI rewrite or a new cross-platform runtime.
It is not final visual acceptance, an internal-beta GO, or a signed-release approval.

This pass inspected 12 existing images, the iOS Release source-baseline notes,
current Android onboarding, mode, Zap, Settings and completion code, and previous
verification evidence. No new device session, install, permission change, runtime
test or performance measurement was performed. Only this review document was added.
iOS app/project/test tracked diffs were empty at review time; no iOS files were edited.

## Reference quality

- Actual source-built iOS Release captures exist for two onboarding pages and
  unpaired activation. Page one and activation were visually compared in this pass.
  They are not captures from the installed App Store binary.
- Main Zap, modes, History, schedules, Settings, completion and blocker do not yet
  have a complete corresponding set of current iOS renders here. Their alignment
  is source-backed; do not label it rendered or pixel-perfect parity.
- September 6 Android main-screen captures precede September 7 named modes.
  In particular, their selected-app labels and old schedule-selection row are
  historical, not newly discovered missing-mode defects.
- The mode compact-editor image is a bounded content harness, not a full device
  inset or keyboard acceptance result. The Settings/completion compact evidence
  separately records actual Android system font scale 200%.

References: [iOS captures and baselines](reference/ios-release-2026-09-06/README.md),
[current mode scope](ANDROID_NAMED_MODES.md),
[release handoff](ANDROID_PLAY_INTERNAL_UPLOAD.md).

## Prioritized findings

### 1. Improve the active timer's readability — fix in the next Android UI slice

`IntegratedZapSurface.kt:217–235` uses `text.zap.active.secondary` for the real
elapsed timer. The shared source-characterization token is `#00000029` over white:
approximately `#D6D6D6`, with a calculated contrast ratio of 1.45:1. The saved A16
active image visibly confirms the very pale timer. This follows the iOS source
styling, but the timer conveys meaningful session information.

Use a documented Android-specific readable foreground treatment, retaining the
headline hierarchy, white active background and timer behavior. Do not change the
shared iOS characterization value in a way that silently changes iOS. Verify both
normal and enlarged text, and distinguish active elapsed time from recovery copy.
This finding is a readability issue, not evidence that timer calculation is wrong.

### 2. Finish customer-facing setup wording — required for the release surface

The actual Settings setup path still leads to resources including:

- “Justnoise Gate 0” and “this prototype” in the disclosure;
- “Pair or replace test Zap”;
- “Withdraw consent and clear Gate 0 data”;
- “Consent required. The service will refuse to run.”

The integrated system service label also says “Justnoise Android Dev app blocking.”
Replace engineering terminology with Justnoise branding and clear next actions in
the production package. Keep the disclosure's factual scope, separate consent,
replacement confirmation and recovery/withdrawal behavior intact. Never remove
warning states just to make the screen look cleaner. Update wording-sensitive
checks together with the release copy.

### 3. Explain custom modes in onboarding — small usability improvement

The first page still says “Select Apps / Choose the apps you want to block.” Its
paired continuation also only mentions choosing apps. This is truthful but misses
the newly implemented mode model: users create/select a mode, manual taps use the
last selected mode, and schedules retain their own selection.

Keep the existing two-page structure. Introduce a short create-mode/select-apps
instruction, retaining the important Android distinction that selected apps are
blocked, not allowed. The reused illustration depicts an iPhone; treat replacement
with Android imagery as optional polish, not a reason to delay internal testing.
Do not infer a video failure from the existing reduced-motion still frame.

### 4. Complete privacy and feedback access — already-open release requirement

`IntegratedSettingsSurface` still renders the bundled draft; the provided
Justnoise Media / hello@justnoise.shop details do not make it a final published
notice. There is no dedicated support action in the grouped Settings surface.

Reconcile the bundled and publication versions against the final package; publish
on an authorized Justnoise destination and connect the real policy URL. Add a
working support path using the supplied address, with a useful fallback if no mail
app is installed. Verify the inbox before claiming a working feedback channel.
Do not touch the unrelated connected shop or invent a URL.

### 5. Do not treat incomplete reference coverage as completed acceptance

Finish the missing main-screen comparison when current iOS images are available.
Source-backed Android fixes can proceed meanwhile. Use the final candidate for
remaining keyboard/inset, TalkBack and measured responsiveness checks. Existing
screenshots establish layout observations, not smooth animation, absence of ANRs,
battery efficiency or complete accessibility acceptance.

## Existing work to preserve

| Surface | Evidence and current judgment |
| --- | --- |
| Onboarding / activation | Captured iOS/Android hierarchy, artwork and capsule actions are recognizably aligned; Android adds explicit setup and native navigation. |
| Zap | A16 evidence shows centered branding, large headline, shared artwork, dark/light states and yellow Unzap action; timer contrast is the specific issue above. Current source displays the real mode name. |
| Modes | September 7 A16 catalog clearly distinguishes selected mode, Edit and New Mode. Compact harness shows reachable Cancel/Save. Saving does not silently select a mode. |
| Schedules | Compact grouped editor, separate Cancel/Save, explicit one-time/repeat and native pickers are established. September 7 mode references supersede the earlier one-selection image. Reuse owner-confirmed scheduled/manual behavior. |
| History | A16 summary/calendar presentation is implemented. Keep truthful Session time and legitimate interruption labels. Do not replace them with unsupported protection claims. |
| Settings / completion | Existing 200% system-font evidence shows wrapping/scrolling and reachable Done. Grouping matches the source approach; final privacy and setup wording remain open. |

The completion sheet intentionally shows the completed record's app count, not a
mode name. Completed History currently does not store mode names/IDs. This is an
explicit named-mode scope choice, not a reason to populate receipts from the
currently selected mode or expand the History schema during a polish pass.

The slightly off-center mode sheet title caused by an asymmetric trailing Done
button is cosmetic and lower priority. Do not turn it into a new release gate.

## Next implementation and verification order

1. Make one bounded Android presentation/copy slice for timer readability and
   mode-aware onboarding. Preserve system integration and stored data. Run affected
   regressions and the repository-mandated verifier after application changes.
2. Finish production setup/branding, privacy/support and package work together,
   so the verified user-facing copy matches the candidate and reviewer materials.
3. Record the disposition of the existing internal-beta safety gaps separately;
   this experience review does not close foreign-tag or scoped repeat evidence.
4. Validate affected screens on a disposable emulator first, then only genuinely
   missing or changed checks on the signed A16 candidate. No owner-app
   instrumentation, data clearing, new phone requirement or repeated ordinary
   Chrome/start/stop exercise is authorized by this review.

The last recorded full local verifier remains September 8's 292 JVM / 36 artifact
checks plus builds, lint and contracts. It was not rerun for this documentation-only
review. No fix above is claimed implemented or installed by this document.

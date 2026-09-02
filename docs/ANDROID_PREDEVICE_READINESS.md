# Android pre-device readiness

Status: **PHYSICAL GATE 0 IN PROGRESS — PRODUCTION IMPLEMENTATION HOLD**

This is the handoff between repository preparation and evidence that only consumer hardware, policy review, and Google Play can provide. It does not authorize `android-app/`.

## One reproducible repository check

From the repository root, with JDK 17 and Android SDK 36 configured, run:

```sh
bash scripts/verify-android-predevice-readiness.sh
```

The command fails unless all of the following remain true:

- the versioned behavior, copy, design, and pinned API contracts validate;
- the disposable Gate 0 project passes its static policy boundary, JVM tests, lint, debug APK build, and instrumentation APK build;
- the Compose UI prototype passes its contract/reducer tests, lint, and debug APK build;
- the integrated development shell passes its truthful-action state tests and compiles the reviewed Gate 0 capability sources,
  real foreground NFC reader, direct NDEF entry candidate, and polished truth-driven
  Zap surface into one disposable APK;
- the UI prototype declares no permission or service and neither its source nor the
  shared native copy/color mapping imports a restricted Android capability API; and
- no production `android-app/` exists during the current internal-beta and full Gate 0 HOLD. After the smaller internal-beta checkpoint is recorded as GO, deliberately replace this phase-specific absence guard with release-app verification during promotion.

GitHub's **Android Gate 0** workflow runs the same command whenever either Android spike, the mobile architecture/gate documents, shared product contracts, or the verifier itself changes.

## Evidence-based readiness table

| Requirement | Current status | Authoritative evidence |
| --- | --- | --- |
| Preserve the shipped iOS client | **Ready** | Android preparation is isolated under `spikes/`, `docs/`, `product/`, and CI/scripts; the production Android module is absent. iOS remains independently verified by `scripts/verify-ios.sh` and the **iOS verification** workflow. |
| Native two-client architecture | **Ready** | `AGENTS.md` and `docs/MOBILE_ARCHITECTURE.md` require SwiftUI plus Jetpack Compose with shared contracts rather than a shared UI runtime. |
| Shared product decisions | **Ready baseline** | `product/behavior/portable-fixtures.v1.json`, `product/copy/en.v1.json`, `product/design/tokens.v1.json`, and `product/api/signal/v1/` are versioned and verified. Direct Zap entry now freezes start-the-last-explicit-valid-mode versus stop-active behavior in Swift/Kotlin fixtures. History density and duration-label rules are deterministic in both native rule sets, while calendar bucketing remains explicit platform work. Schedule temporal/recovery semantics and Zap provisioning/envelope parity remain explicitly unresolved rather than guessed. |
| Android presentation fidelity | **Ready prototype** | `docs/ANDROID_UI_PARITY.md` maps every launch-ready iOS destination and major state to `spikes/android-ui/`, including the first-run coach tour and management editor/confirmation states. Device visual acceptance remains pending. It is presentation evidence only. |
| Consumer restriction candidate | **Buildable, not proven** | `spikes/android-gate0/` contains the disposable capability candidate, static privacy/policy constraints, evidence templates, and native tests. Emulator/CI results cannot satisfy a physical matrix cell. |
| Low-friction NFC activation | **Candidate implemented; physical evidence blocked** | `docs/ANDROID_NFC_ACTIVATION.md` separates direct Android tag routing from authorization and Accessibility enforcement. The integrated disposable APK now contains the live-tag direct NDEF entry Activity and shared mutation gate. G0-19 still requires unlocked Home start/stop without app-open, Scan button, chooser, or second confirmation on the existing required A16; A17 validation is deferred and non-blocking. |
| Physical feasibility | **PARTIAL A16 RUNS / failing gate** | September 1 established setup/pairing and exposed an overlay self-event loop. `spikes/android-gate0/evidence/A16_PARTIAL_RUN_2026-09-02.md` records one real foreground start/block/stop cycle: a short stable Chrome-blocking recording, unchanged screenshots after taps/swipe, owner confirmation of the flicker fix, then idle state and responsive Chrome after the authorized stop. Repeated/negative cases, longer interaction, other targets and remaining safety/lifecycle checks are still pending. No complete matrix cell passes; all 19 required A16 cells remain `BLOCKED`, so the decision remains `HOLD / NO-GO`. The 19 optional A17 cells are excluded from gate arithmetic. |
| Google Play distribution | **HOLD** | The account, policy declaration, testing-track, privacy/deletion, billing, signing, reviewer-access, and final review gates are listed in `docs/ANDROID_GATE_0.md`. Internal checks cannot promise Play approval. |

## Work allowed before a physical PASS

- Maintain the shared contracts and native mappings.
- Improve permission-free Compose presentation states and previews.
- Build and install the disposable APKs on emulators or test phones; use the integrated
  shell for the combined real-NFC and blocked-surface physical test.
- Exercise and harden the existing direct-dispatch Activity under G0-19 without
  promoting it into production code.
- Prepare policy language, privacy inventory, Play account decisions, and test logistics.
- Run the exact physical matrix and attach redacted evidence.

## Work not authorized during the current internal-beta HOLD

- Do not create `android-app/` until the smaller A16 internal-beta checkpoint is recorded as GO (or full Gate 0 passes). The permanent production application ID requires an explicit owner decision before use.
- Do not promote spike code into production packages.
- Do not connect production accounts, analytics, backend data, signing keys, or store listings to a spike.
- Do not claim working app restriction, NFC parity, background durability, or release readiness from unit tests, screenshots, or an emulator.
- Do not implement Android schedules from the current iOS timing behavior while the portable time-zone, DST, collision, missed-fire, duration, and recovery rules remain unresolved.

## Full reliability and distribution gates

1. Complete all 19 required A16 cells defined by `docs/ANDROID_GATE_0.md` on the exact candidate commit, including real authorized/unauthorized NDEF tags, direct unlocked tag dispatch, bypass/escape safety, revocation, process death, reboot, Doze, overnight endurance, privacy observation, and screen recordings. A17 validation is deferred and non-blocking; the owner need not obtain another phone. Evidence is variant-specific, so additional-device reliability stays unverified until separately tested.
2. Obtain product and policy review of the observed experience, Accessibility disclosure/consent, data inventory, and Play declaration. Any technical failure or policy uncertainty keeps the decision on `HOLD / NO-GO`.
3. Complete the applicable Play Console owner/account verification, permanent package identity, signing/OAuth registration, testing-track eligibility, privacy/Data safety/account-deletion disclosures, billing decision, reviewer access, and store review described in the **Google Play and distribution gates** section of `docs/ANDROID_GATE_0.md`.
4. Recheck current Android and Google Play requirements at account creation, first closed-track upload, and production submission; repository documentation is not a substitute for the live console and current policies.

## Unlock sequence for the first internal beta

Budget-conscious sequence agreed 2026-09-02: complete the smaller A16 safety checkpoint in `docs/ANDROID_GATE_0.md`, review the observed product experience and Play policy package, then deliver the smallest useful Play internal-test build. The full endurance matrix can continue during controlled beta work rather than blocking the first learning release. A17 testing is optional follow-up when an existing user or voluntary tester is available; it is not a separate required iteration.

Only after the separate internal-beta decision says `GO` (or full Gate 0 says `PASS`):

1. Review and choose the permanent application ID.
2. Create one `android-app/app` module beside the existing iOS project.
3. Reuse the working NFC, blocker, Compose and contract components after release-boundary review; avoid a wholesale rewrite or an unreviewed bulk rename of spike packages.
4. Deliver the minimal local-only test scope below; keep the larger slices in `docs/MOBILE_ARCHITECTURE.md` as a later roadmap and schedules deferred until their portable semantics are approved.
5. Preserve physical-device and Play Console evidence as release gates, not one-time assumptions.

## Immediate milestone: minimal Play internal test, not full parity

The owner's latest instruction is to skip nonessential work and reach Play Console quickly. The next milestone is a small, truthful internal-test app, not a public launch or a complete iOS replica. Keep the full delivery matrix as a later roadmap, not as prerequisites for this first test build.

- **Keep:** local onboarding/pairing, remembered selection of one target app, real authorized Zap start/stop, blocked-app interception and reliable unblock, truthful active/recovery state, essential permission/privacy controls, and safe Home/Back/disable/Force Stop/uninstall routes. The first internal beta deliberately does not promise multi-app modes.
- **Defer:** A17/cross-OEM hardware acquisition, multi-app/multi-mode management, full visual-parity sign-off, cosmetic polish, cloud accounts/sync and purchase flows, History/Noise Rewind, schedules, widgets, Capture/Signal, component-library work, and architecture rewrites. No paid access or account parity is implied by a local-only test build. This is milestone sequencing, not removal from the longer-term product.
- **Before distribution:** pass the smaller A16 safety checkpoint on the actual release candidate and record the separate internal-beta GO; confirm Play account eligibility and permanent package identity; prepare release signing, a non-debuggable release App Bundle without preview/debug tooling, actual-behavior privacy/declaration/reviewer material, and the requirements applicable to the selected Play track. Do not upload or publish without the owner's approval. Internal testing is not an exemption from applicable policy requirements or permission to ship the current debug spike as production.
- **Then:** install through the internal test track on the existing A16, collect focused feedback, fix observed problems, and expand only when the core is usable. Additional tester hardware is opportunistic, not a purchasing requirement.

Track these as three outcome-based work packages: (1) safe/reliable A16 core, (2) minimal release app and Play package, (3) internal-test installation and feedback. These are not promises of exactly three coding iterations or a fixed approval date. The earlier full-parity estimate must not be reused as the estimate for this narrower milestone, and reduced scope does not turn incomplete work into a PASS.

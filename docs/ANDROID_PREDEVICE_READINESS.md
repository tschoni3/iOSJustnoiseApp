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
- no production `android-app/` exists before Gate 0 is recorded as `PASS`.

GitHub's **Android Gate 0** workflow runs the same command whenever either Android spike, the mobile architecture/gate documents, shared product contracts, or the verifier itself changes.

## Evidence-based readiness table

| Requirement | Current status | Authoritative evidence |
| --- | --- | --- |
| Preserve the shipped iOS client | **Ready** | Android preparation is isolated under `spikes/`, `docs/`, `product/`, and CI/scripts; the production Android module is absent. iOS remains independently verified by `scripts/verify-ios.sh` and the **iOS verification** workflow. |
| Native two-client architecture | **Ready** | `AGENTS.md` and `docs/MOBILE_ARCHITECTURE.md` require SwiftUI plus Jetpack Compose with shared contracts rather than a shared UI runtime. |
| Shared product decisions | **Ready baseline** | `product/behavior/portable-fixtures.v1.json`, `product/copy/en.v1.json`, `product/design/tokens.v1.json`, and `product/api/signal/v1/` are versioned and verified. Direct Zap entry now freezes start-the-last-explicit-valid-mode versus stop-active behavior in Swift/Kotlin fixtures. History density and duration-label rules are deterministic in both native rule sets, while calendar bucketing remains explicit platform work. Schedule temporal/recovery semantics and Zap provisioning/envelope parity remain explicitly unresolved rather than guessed. |
| Android presentation fidelity | **Ready prototype** | `docs/ANDROID_UI_PARITY.md` maps every launch-ready iOS destination and major state to `spikes/android-ui/`, including the first-run coach tour and management editor/confirmation states. Device visual acceptance remains pending. It is presentation evidence only. |
| Consumer restriction candidate | **Buildable, not proven** | `spikes/android-gate0/` contains the disposable capability candidate, static privacy/policy constraints, evidence templates, and native tests. Emulator/CI results cannot satisfy a physical matrix cell. |
| Low-friction NFC activation | **Candidate implemented; physical evidence blocked** | `docs/ANDROID_NFC_ACTIVATION.md` separates direct Android tag routing from authorization and Accessibility enforcement. The integrated disposable APK now contains the live-tag direct NDEF entry Activity and shared mutation gate. G0-19 still requires unlocked Home start/stop without app-open, Scan button, chooser, or second confirmation on both required device families. |
| Physical feasibility | **PARTIAL A16 RUN / failing gate** | The owner-assisted Galaxy A16 run recorded in `spikes/android-gate0/evidence/A16_PARTIAL_RUN_2026-09-01.md` proved setup, pairing, one foreground start, and one immediate Chrome interception while exposing and fixing a real overlay self-event loop. It does not pass a matrix cell. Every A16 and A17 cell remains `BLOCKED`, so `spikes/android-gate0/evidence/DECISION_RECORD.md` remains `HOLD / NO-GO`. |
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

## Work not authorized before a physical PASS

- Do not create `android-app/` or choose a permanent production application ID.
- Do not promote spike code into production packages.
- Do not connect production accounts, analytics, backend data, signing keys, or store listings to a spike.
- Do not claim working app restriction, NFC parity, background durability, or release readiness from unit tests, screenshots, or an emulator.
- Do not implement Android schedules from the current iOS timing behavior while the portable time-zone, DST, collision, missed-fire, duration, and recovery rules remain unresolved.

## Unavoidable external gates

1. Complete all 38 A16/A17 launch-family cells defined by `docs/ANDROID_GATE_0.md` on the exact candidate commit, including real authorized/unauthorized NDEF tags, direct unlocked tag dispatch, bypass/escape safety, revocation, process death, reboot, Doze, overnight endurance, privacy observation, and screen recordings. Evidence is variant-specific; add each regional or LTE/5G variant before claiming it.
2. Obtain product and policy review of the observed experience, Accessibility disclosure/consent, data inventory, and Play declaration. Any technical failure or policy uncertainty keeps the decision on `HOLD / NO-GO`.
3. Complete the applicable Play Console owner/account verification, permanent package identity, signing/OAuth registration, testing-track eligibility, privacy/Data safety/account-deletion disclosures, billing decision, reviewer access, and store review described in the **Google Play and distribution gates** section of `docs/ANDROID_GATE_0.md`.
4. Recheck current Android and Google Play requirements at account creation, first closed-track upload, and production submission; repository documentation is not a substitute for the live console and current policies.

## Unlock sequence after Gate 0 PASS

Only after the decision record says `PASS`:

1. Review and choose the permanent application ID.
2. Create one `android-app/app` module beside the existing iOS project.
3. Promote contracts and state models deliberately; reimplement adapters rather than renaming spike packages.
4. Deliver the production slices in `docs/MOBILE_ARCHITECTURE.md`, keeping schedules deferred until their portable semantics are approved.
5. Preserve physical-device and Play Console evidence as release gates, not one-time assumptions.

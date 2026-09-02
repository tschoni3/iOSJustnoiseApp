# Android Gate 0 device evidence matrix

Status: **PARTIAL — A16 native Home/Back, escape-control and phone reachability recorded; Keep going reset fixed and retested; safety coverage incomplete; required A16 `BLOCKED` cells count as `FAIL`**

The exact criteria and interpretation are normative in [`../../../docs/ANDROID_GATE_0.md`](../../../docs/ANDROID_GATE_0.md). Never change a cell to `PASS` without attaching the requested recording/log notes and the device/run metadata below.

Scope decision, 2026-09-02: A16 is required; A17 is deferred/unverified and excluded from gate arithmetic. The owner is not required to obtain another phone. Keeping the optional A17 column does not make it a development prerequisite or establish compatibility. No test result changed because of this scope decision.

## Run metadata

| Field | Galaxy A16 family (required) | Galaxy A17 family (deferred) |
| --- | --- | --- |
| Exact model / radio / region | Observed `SM-A165F` / product `a16nseea`; exact CSC, radio, and sales region still required | No device available; record the exact variant if optional testing becomes available |
| Public OS version/build | Android 16 / API 36 / incremental `A165FXXSADZG3` | NOT RECORDED |
| Security patch | `2026-07-05` | NOT RECORDED |
| Sales region / CSC | NOT RECORDED | NOT RECORDED |
| RAM tier | NOT RECORDED | NOT RECORDED |
| One UI version | Device property `80500` (record the user-visible One UI label before a gate decision) | NOT RECORDED |
| Prototype commit and APK SHA-256 | Foreground cycle: `dbb3ba6` / `f9a2c63cdb1cf3481d5007939bd0b96d02a126c1af6a2f1f709467aacf72b077`. Task-isolation cycle: `d93ae01` / `d2de157599fc77a59fb153f0fde1776bab6c0e26cfa10b9d48e0b4f1abe8ebc0`. Navigation and confirmed stop/unblock candidate: `d09833d` / `6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`. Latest installed cleanup-feedback follow-up: `dbe2469` / `c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`, updated while idle/revision `19` with pairing record/Chrome retained; see the build-scoped September 2 record | NOT RECORDED |
| Install profile | Must be unmanaged personal | Must be unmanaged personal |
| Default battery run | NOT RUN | NOT RUN |
| Unrestricted battery run | NOT RUN | NOT RUN |
| Overnight start/end | NOT RUN | NOT RUN |
| Tester/date | Owner-assisted runs / 2026-09-01 and 2026-09-02; see the corresponding `A16_PARTIAL_RUN` records | NOT RECORDED |

## Results

| ID | Scenario | Galaxy A16 (required) | Galaxy A17 (deferred) | Evidence / exact path / notes |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, unchecked consent, manual enable without privileged setup | BLOCKED | BLOCKED | Partial owner-assisted setup completed; clean-install recording and full escape verification missing |
| G0-02 | Scoped picker includes Chrome, YouTube, and one third-party app; selection survives process death | BLOCKED | BLOCKED | Chrome selected and survived APK update; remaining picker/target coverage missing |
| G0-03 | Authorized real NDEF Zap starts 10/10 once; malformed/unauthorized never starts | BLOCKED | BLOCKED | Authorized foreground starts reported on September 1 and 2; September 2 state changed idle/revision 7 to active/revision 8. Required repetitions and negative cases missing |
| G0-04 | Three launcher targets intercepted 10/10 before usable content | BLOCKED | BLOCKED | September 2 original build: Chrome block stable through a short recorded interval and unchanged before/after separate taps/swipe; owner confirmed the fix, with brief Chrome splash during launch. Updated `d93ae01` build: direct Home start to active/revision 14, owner-reported manual Chrome launcher/tap/scroll blocking, and a blocked-screen still preserved. Time-to-interception, longer/repeated, and multi-target evidence remains missing |
| G0-05 | Recents, notification/deep link, browser link, and Settings Open are intercepted | BLOCKED | BLOCKED | Not run; record exact links/notifications |
| G0-06 | Back, Home, Recents, rotation, split screen, and rapid launch do not bypass | BLOCKED | BLOCKED | Partial on `d09833d`: two ADB-injected touches of the native Home control returned to unblocked Samsung Home; between them a normal Chrome Activity relaunch via ADB was blocked again. State stayed active/revision 16 with a connected, fresh service heartbeat. This is one synthetic Home/re-entry sequence, not owner-operated Home/Back, timing, rotation, split-screen, rapid-launch, or full repetition coverage. See A16_PARTIAL_RUN_2026-09-02.md |
| G0-07 | Screen-off 30-minute durability in default and unrestricted battery modes | BLOCKED | BLOCKED | Not run |
| G0-08 | Reboot restores enforcement or truthfully requires recovery; five launches pass after restore | BLOCKED | BLOCKED | Not run |
| G0-09 | Authorized real Zap stops 10/10; canceled/malformed/unauthorized do not stop | BLOCKED | BLOCKED | September 2: one owner-confirmed foreground stop, active/revision 8 to idle/revision 9, Ready to Zap, and neutral Chrome with responsive menu. Later, one owner-confirmed direct Home stop on `d93ae01`, active/revision 12 to idle/revision 13, without app UI or chooser. A further owner-reported stop changed active/revision 14 to idle/revision 15 before installing `d09833d`; chooser behavior and post-stop Chrome interaction were not separately checked for that sample. On `d09833d`, active/revision 16 changed to idle/revision 17, service connected, and the owner confirmed normal Chrome browsing without the blocker after the requested Zap stop. That completed confirmation must not be requested again; foreground/direct route and chooser behavior were not separately established for this sample. Required repetitions and canceled/malformed/unauthorized cases remain missing. See A16_PARTIAL_RUN_2026-09-02.md |
| G0-10 | Revocation reports unavailable/recovery and preserves non-destructive escape | BLOCKED | BLOCKED | Not run |
| G0-11 | Thirty-minute, ten-app false-positive/privacy script | BLOCKED | BLOCKED | Not run |
| G0-12 | No crash, ANR, persistent overlay, loop, lockout, or visibly usable target flash | BLOCKED | BLOCKED | Owner confirmed no flicker on September 2 after `02f6e7c`/`dbb3ba6`; short video and before/after touch screenshots preserved. Full safety and repeated sustained-interaction coverage still pending |
| G0-13 | Manifest, disclosure, data handling, and Play declaration policy review | BLOCKED | BLOCKED | September 2 internal source/merged-manifest/APK review recorded in PLAY_DECLARATION_DRAFT.md; current narrow metadata/permissions confirmed. Inventory gaps corrected and false withdrawal-success feedback fixed locally. Physical consent/refusal/re-entry and cleanup-failure evidence, final release package, and product/policy acceptance remain missing |
| G0-14 | Justnoise, Home, Settings, System UI, permission/package, phone/emergency surfaces never covered | BLOCKED | BLOCKED | Prior `d93ae01` failure: missing normal navigation controls and overlay overlapping navigation region. Installed `d09833d`: owner reports controls visible; screenshots confirm overlay bottom 2205 leaving the 135-pixel navigation region uncovered. Two ADB-injected touches at native Home coordinate (546,2275) each returned to Home without the blocker, with active/revision 16 unchanged; Chrome re-entry between them was blocked. This is synthetic native-control routing evidence, not owner-operated Home/Back or remaining protected-surface coverage. App GO HOME is not normal system-Home evidence. Use emergency test route only. See A16_PARTIAL_RUN_2026-09-02.md |
| G0-15 | Disable, Force Stop, and uninstall escape via launcher and Settings; reinstall unchecked | BLOCKED | BLOCKED | On `dbe2469`, overlay buttons and ordinary Settings taps reached the enabled Accessibility toggle and visible App info Force Stop/uninstall controls without an overlay. None was activated; actual disable/Force Stop/uninstall and reinstall remain untested. No ADB rescue was used. See the bounded safety sequence in A16_PARTIAL_RUN_2026-09-02.md |
| G0-16 | Service crash/restart, process death, OEM reclaim, and Force Stop recover truthfully | BLOCKED | BLOCKED | Not run |
| G0-17 | Natural Doze and eight-hour overnight pass in both default and unrestricted battery modes | BLOCKED | BLOCKED | Not run; unrestricted-only pass fails gate |
| G0-18 | Minimal metadata plus local-only inventory match reviewed logs/network observation | BLOCKED | BLOCKED | September 2 packaged metadata and app-source review match the updated inventory, including non-content platform metadata. Redacted runtime logs, dependency review, network observation, and withdrawal fault/restore cases remain unverified |
| G0-19 | Unlocked Home direct legacy-Zap start/stop needs no app-open, Scan button, chooser, or second confirmation | BLOCKED | BLOCKED | September 2: initial warm Home start revealed the app; after task-isolation update, one owner-confirmed physical start/stop cycle kept Home foreground without Justnoise or chooser: idle/revision 11 to active/revision 12 to idle/revision 13. Synthetic no-tag rejection passed while idle and active. Repetitions, cold process, lock, competing-handler, remaining negatives, and Force Stop evidence remain pending. Earlier idle/revision 11 has no confirmed cause and is not counted as direct-stop evidence |

## Decision arithmetic

Additional bounded `dbe2469` evidence for G0-06/G0-14: a coordinate touch of native
Back `(850,2275)` returned from blocked Chrome to Home without ending active/revision
`20`. Chrome was blocked again on re-entry; Accessibility settings and App info
were reachable. Keep going then opened setup but unexpectedly cleared the session
to idle/revision `21` when the existing target selection was restored. This was
not a verified Zap stop. The corrected APK `9fe99b7232a135cd6e6e16d4ad105faa3200cd87e516a5101a08bda60c7c4739`
is installed: one Keep going/Chrome re-entry sequence preserved active/revision `22`,
and the empty Samsung dialer remained reachable without an overlay. Dedicated
emergency access and actual recovery remain unverified. No full-matrix cell is
promoted by these partial results; see the build-scoped record for all artifact hashes.

- Required cells: 19.
- Passing cells: 0.
- Blocked/failing cells: 19.
- Deferred A17 cells: 19 (excluded from the three counts above; all unverified).
- Current technical gate result: **NO-GO until testing**.
- Current distribution-policy result: **HOLD until policy review and later closed-track submission**.

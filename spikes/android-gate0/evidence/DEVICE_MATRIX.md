# Android Gate 0 device evidence matrix

Status: **NOT RUN — all `BLOCKED` cells count as `FAIL`**

The exact criteria and interpretation are normative in [`../../../docs/ANDROID_GATE_0.md`](../../../docs/ANDROID_GATE_0.md). Never change a cell to `PASS` without attaching the requested recording/log notes and the device/run metadata below.

## Run metadata

| Field | Galaxy A16 family | Galaxy A17 family |
| --- | --- | --- |
| Exact model / radio / region | Observed `SM-A165F` / product `a16nseea`; exact CSC, radio, and sales region still required | Required baseline: `SM-A175F` LTE, German/EU; add every intended listed A17 variant |
| Public OS version/build | Android 16 / API 36 / incremental `A165FXXSADZG3` | NOT RECORDED |
| Security patch | `2026-07-05` | NOT RECORDED |
| Sales region / CSC | NOT RECORDED | NOT RECORDED |
| RAM tier | NOT RECORDED | NOT RECORDED |
| One UI version | Device property `80500` (record the user-visible One UI label before a gate decision) | NOT RECORDED |
| Prototype commit and APK SHA-256 | Foreground cycle: `dbb3ba6` / `f9a2c63cdb1cf3481d5007939bd0b96d02a126c1af6a2f1f709467aacf72b077`. Later installed navigation candidate: `d93ae01` / `d2de157599fc77a59fb153f0fde1776bab6c0e26cfa10b9d48e0b4f1abe8ebc0`; see the build-scoped September 2 record | NOT RECORDED |
| Install profile | Must be unmanaged personal | Must be unmanaged personal |
| Default battery run | NOT RUN | NOT RUN |
| Unrestricted battery run | NOT RUN | NOT RUN |
| Overnight start/end | NOT RUN | NOT RUN |
| Tester/date | Owner-assisted runs / 2026-09-01 and 2026-09-02; see the corresponding `A16_PARTIAL_RUN` records | NOT RECORDED |

## Results

| ID | Scenario | Galaxy A16 | Galaxy A17 | Evidence / exact path / notes |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, unchecked consent, manual enable without privileged setup | BLOCKED | BLOCKED | Partial owner-assisted setup completed; clean-install recording and full escape verification missing |
| G0-02 | Scoped picker includes Chrome, YouTube, and one third-party app; selection survives process death | BLOCKED | BLOCKED | Chrome selected and survived APK update; remaining picker/target coverage missing |
| G0-03 | Authorized real NDEF Zap starts 10/10 once; malformed/unauthorized never starts | BLOCKED | BLOCKED | Authorized foreground starts reported on September 1 and 2; September 2 state changed idle/revision 7 to active/revision 8. Required repetitions and negative cases missing |
| G0-04 | Three launcher targets intercepted 10/10 before usable content | BLOCKED | BLOCKED | September 2: fresh physical start, Chrome block stable through a short recorded interval and unchanged before/after separate taps/swipe; owner confirmed the fix. Brief Chrome splash during launch is visible. Longer/repeated and multi-target evidence remains missing |
| G0-05 | Recents, notification/deep link, browser link, and Settings Open are intercepted | BLOCKED | BLOCKED | Not run; record exact links/notifications |
| G0-06 | Back, Home, Recents, rotation, split screen, and rapid launch do not bypass | BLOCKED | BLOCKED | Not run |
| G0-07 | Screen-off 30-minute durability in default and unrestricted battery modes | BLOCKED | BLOCKED | Not run |
| G0-08 | Reboot restores enforcement or truthfully requires recovery; five launches pass after restore | BLOCKED | BLOCKED | Not run |
| G0-09 | Authorized real Zap stops 10/10; canceled/malformed/unauthorized do not stop | BLOCKED | BLOCKED | September 2: one owner-confirmed foreground stop, active/revision 8 to idle/revision 9, Ready to Zap, and neutral Chrome with responsive menu. Repetitions and canceled/malformed/unauthorized cases remain missing |
| G0-10 | Revocation reports unavailable/recovery and preserves non-destructive escape | BLOCKED | BLOCKED | Not run |
| G0-11 | Thirty-minute, ten-app false-positive/privacy script | BLOCKED | BLOCKED | Not run |
| G0-12 | No crash, ANR, persistent overlay, loop, lockout, or visibly usable target flash | BLOCKED | BLOCKED | Owner confirmed no flicker on September 2 after `02f6e7c`/`dbb3ba6`; short video and before/after touch screenshots preserved. Full safety and repeated sustained-interaction coverage still pending |
| G0-13 | Manifest, disclosure, data handling, and Play declaration policy review | BLOCKED | BLOCKED | Not reviewed |
| G0-14 | Justnoise, Home, Settings, System UI, permission/package, phone/emergency surfaces never covered | BLOCKED | BLOCKED | Not run; use emergency test route only |
| G0-15 | Disable, Force Stop, and uninstall escape via launcher and Settings; reinstall unchecked | BLOCKED | BLOCKED | Not run; no ADB rescue allowed |
| G0-16 | Service crash/restart, process death, OEM reclaim, and Force Stop recover truthfully | BLOCKED | BLOCKED | Not run |
| G0-17 | Natural Doze and eight-hour overnight pass in both default and unrestricted battery modes | BLOCKED | BLOCKED | Not run; unrestricted-only pass fails gate |
| G0-18 | Minimal metadata plus local-only inventory match reviewed logs/network observation | BLOCKED | BLOCKED | Static claims exist; device observation not run |
| G0-19 | Unlocked Home direct legacy-Zap start/stop needs no app-open, Scan button, chooser, or second confirmation | BLOCKED | BLOCKED | September 2: initial warm Home start revealed the app; after task-isolation update, the owner confirmed a physical start without Justnoise or chooser, idle/revision 11 to active/revision 12, with Home foreground. Synthetic no-tag rejection passed while idle and active. Direct stop, repetitions, cold process, lock, competing-handler, remaining negatives, and Force Stop evidence remain pending. Earlier idle/revision 11 has no confirmed cause and is not counted as direct-stop evidence |

## Decision arithmetic

- Required cells: 38.
- Passing cells: 0.
- Blocked/failing cells: 38.
- Current technical gate result: **NO-GO until testing**.
- Current distribution-policy result: **HOLD until policy review and later closed-track submission**.

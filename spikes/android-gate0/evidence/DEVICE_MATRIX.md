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
| Prototype commit and APK SHA-256 | Integrated dev commit `dbb3ba6`; APK `f9a2c63cdb1cf3481d5007939bd0b96d02a126c1af6a2f1f709467aacf72b077` | NOT RECORDED |
| Install profile | Must be unmanaged personal | Must be unmanaged personal |
| Default battery run | NOT RUN | NOT RUN |
| Unrestricted battery run | NOT RUN | NOT RUN |
| Overnight start/end | NOT RUN | NOT RUN |
| Tester/date | Owner-assisted run / 2026-09-01; see `A16_PARTIAL_RUN_2026-09-01.md` | NOT RECORDED |

## Results

| ID | Scenario | Galaxy A16 | Galaxy A17 | Evidence / exact path / notes |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, unchecked consent, manual enable without privileged setup | BLOCKED | BLOCKED | Partial owner-assisted setup completed; clean-install recording and full escape verification missing |
| G0-02 | Scoped picker includes Chrome, YouTube, and one third-party app; selection survives process death | BLOCKED | BLOCKED | Chrome selected and survived APK update; remaining picker/target coverage missing |
| G0-03 | Authorized real NDEF Zap starts 10/10 once; malformed/unauthorized never starts | BLOCKED | BLOCKED | One owner-reported authorized foreground start; repetition and negative cases missing |
| G0-04 | Three launcher targets intercepted 10/10 before usable content | BLOCKED | BLOCKED | Chrome intercepted after the self-event fix with one immediate captured blocked screen; `dbb3ba6` explicitly consumes unused overlay touches, but a fresh physical start plus sustained and multi-target runs are missing |
| G0-05 | Recents, notification/deep link, browser link, and Settings Open are intercepted | BLOCKED | BLOCKED | Not run; record exact links/notifications |
| G0-06 | Back, Home, Recents, rotation, split screen, and rapid launch do not bypass | BLOCKED | BLOCKED | Not run |
| G0-07 | Screen-off 30-minute durability in default and unrestricted battery modes | BLOCKED | BLOCKED | Not run |
| G0-08 | Reboot restores enforcement or truthfully requires recovery; five launches pass after restore | BLOCKED | BLOCKED | Not run |
| G0-09 | Authorized real Zap stops 10/10; canceled/malformed/unauthorized do not stop | BLOCKED | BLOCKED | Not run |
| G0-10 | Revocation reports unavailable/recovery and preserves non-destructive escape | BLOCKED | BLOCKED | Not run |
| G0-11 | Thirty-minute, ten-app false-positive/privacy script | BLOCKED | BLOCKED | Not run |
| G0-12 | No crash, ANR, persistent overlay, loop, lockout, or visibly usable target flash | BLOCKED | BLOCKED | Original A16 run exposed an overlay show/hide loop; `02f6e7c` fixes the self-event feedback and `dbb3ba6` consumes background touches, but sustained interactive recording is pending |
| G0-13 | Manifest, disclosure, data handling, and Play declaration policy review | BLOCKED | BLOCKED | Not reviewed |
| G0-14 | Justnoise, Home, Settings, System UI, permission/package, phone/emergency surfaces never covered | BLOCKED | BLOCKED | Not run; use emergency test route only |
| G0-15 | Disable, Force Stop, and uninstall escape via launcher and Settings; reinstall unchecked | BLOCKED | BLOCKED | Not run; no ADB rescue allowed |
| G0-16 | Service crash/restart, process death, OEM reclaim, and Force Stop recover truthfully | BLOCKED | BLOCKED | Not run |
| G0-17 | Natural Doze and eight-hour overnight pass in both default and unrestricted battery modes | BLOCKED | BLOCKED | Not run; unrestricted-only pass fails gate |
| G0-18 | Minimal metadata plus local-only inventory match reviewed logs/network observation | BLOCKED | BLOCKED | Static claims exist; device observation not run |
| G0-19 | Unlocked Home direct legacy-Zap start/stop needs no app-open, Scan button, chooser, or second confirmation | BLOCKED | BLOCKED | Live-tag direct-dispatch candidate implemented; physical unlocked-Home routing, chooser, warm/cold process, lock, competing-handler, and Force Stop evidence remains missing |

## Decision arithmetic

- Required cells: 38.
- Passing cells: 0.
- Blocked/failing cells: 38.
- Current technical gate result: **NO-GO until testing**.
- Current distribution-policy result: **HOLD until policy review and later closed-track submission**.

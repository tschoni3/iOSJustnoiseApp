# Android Gate 0 device evidence matrix

Status: **NOT RUN — all `BLOCKED` cells count as `FAIL`**

The exact criteria and interpretation are normative in [`../../../docs/ANDROID_GATE_0.md`](../../../docs/ANDROID_GATE_0.md). Never change a cell to `PASS` without attaching the requested recording/log notes and the device/run metadata below.

## Run metadata

| Field | Galaxy A16 family | Galaxy A17 family |
| --- | --- | --- |
| Exact model / radio / region | Required baseline: `SM-A165F` LTE, German/EU; add every intended listed A16 variant | Required baseline: `SM-A175F` LTE, German/EU; add every intended listed A17 variant |
| Public OS version/build | NOT RECORDED | NOT RECORDED |
| Security patch | NOT RECORDED | NOT RECORDED |
| Sales region / CSC | NOT RECORDED | NOT RECORDED |
| RAM tier | NOT RECORDED | NOT RECORDED |
| One UI version | NOT RECORDED | NOT RECORDED |
| Prototype commit and APK SHA-256 | NOT RECORDED | NOT RECORDED |
| Install profile | Must be unmanaged personal | Must be unmanaged personal |
| Default battery run | NOT RUN | NOT RUN |
| Unrestricted battery run | NOT RUN | NOT RUN |
| Overnight start/end | NOT RUN | NOT RUN |
| Tester/date | NOT RECORDED | NOT RECORDED |

## Results

| ID | Scenario | Galaxy A16 | Galaxy A17 | Evidence / exact path / notes |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, unchecked consent, manual enable without privileged setup | BLOCKED | BLOCKED | Not run |
| G0-02 | Scoped picker includes Chrome, YouTube, and one third-party app; selection survives process death | BLOCKED | BLOCKED | Not run |
| G0-03 | Authorized real NDEF Zap starts 10/10 once; malformed/unauthorized never starts | BLOCKED | BLOCKED | Not run |
| G0-04 | Three launcher targets intercepted 10/10 before usable content | BLOCKED | BLOCKED | Not run |
| G0-05 | Recents, notification/deep link, browser link, and Settings Open are intercepted | BLOCKED | BLOCKED | Not run; record exact links/notifications |
| G0-06 | Back, Home, Recents, rotation, split screen, and rapid launch do not bypass | BLOCKED | BLOCKED | Not run |
| G0-07 | Screen-off 30-minute durability in default and unrestricted battery modes | BLOCKED | BLOCKED | Not run |
| G0-08 | Reboot restores enforcement or truthfully requires recovery; five launches pass after restore | BLOCKED | BLOCKED | Not run |
| G0-09 | Authorized real Zap stops 10/10; canceled/malformed/unauthorized do not stop | BLOCKED | BLOCKED | Not run |
| G0-10 | Revocation reports unavailable/recovery and preserves non-destructive escape | BLOCKED | BLOCKED | Not run |
| G0-11 | Thirty-minute, ten-app false-positive/privacy script | BLOCKED | BLOCKED | Not run |
| G0-12 | No crash, ANR, persistent overlay, loop, lockout, or visibly usable target flash | BLOCKED | BLOCKED | Not run; attach screen video |
| G0-13 | Manifest, disclosure, data handling, and Play declaration policy review | BLOCKED | BLOCKED | Not reviewed |
| G0-14 | Justnoise, Home, Settings, System UI, permission/package, phone/emergency surfaces never covered | BLOCKED | BLOCKED | Not run; use emergency test route only |
| G0-15 | Disable, Force Stop, and uninstall escape via launcher and Settings; reinstall unchecked | BLOCKED | BLOCKED | Not run; no ADB rescue allowed |
| G0-16 | Service crash/restart, process death, OEM reclaim, and Force Stop recover truthfully | BLOCKED | BLOCKED | Not run |
| G0-17 | Natural Doze and eight-hour overnight pass in both default and unrestricted battery modes | BLOCKED | BLOCKED | Not run; unrestricted-only pass fails gate |
| G0-18 | Minimal metadata plus local-only inventory match reviewed logs/network observation | BLOCKED | BLOCKED | Static claims exist; device observation not run |
| G0-19 | Unlocked Home direct legacy-Zap start/stop needs no app-open, Scan button, chooser, or second confirmation | BLOCKED | BLOCKED | Not implemented; require a live Tag, reject forged message-only intents, apply API-appropriate dispatch permission, and record NDEF shape without value, tag-intent preference, competing handlers, warm/cold process, lock, and Force Stop |

## Decision arithmetic

- Required cells: 38.
- Passing cells: 0.
- Blocked/failing cells: 38.
- Current technical gate result: **NO-GO until testing**.
- Current distribution-policy result: **HOLD until policy review and later closed-track submission**.

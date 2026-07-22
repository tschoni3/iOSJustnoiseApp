# Android Gate 0: consumer-device app restriction

Status: required feasibility gate before the full Android port

## Decision to make

Prove that Justnoise can provide a reliable, understandable, and Google Play-policy-compatible restriction experience on ordinary personally owned Android phones. Do not build the full Android product until this gate has a recorded decision.

The spike is not expected to recreate Apple Family Controls. It must prove the user outcome or show that the outcome is not viable enough to promise.

## Known platform constraints

- Android’s strongest package-suspension API, `DevicePolicyManager.setPackagesSuspended`, is for a device owner, profile owner, or delegated package. A normal consumer-installed app is not granted that role. See the official [`DevicePolicyManager` reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html).
- An `AccessibilityService` can observe UI transitions and host a `TYPE_ACCESSIBILITY_OVERLAY`, but it does not truly suspend another package. The candidate blocked surface is rendered by Justnoise; it is not system-owned UI merely because Android authorizes the window type. The service is revocable, sensitive to OEM lifecycle behavior, and subject to disclosure, affirmative consent, and permitted-use review under the [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491) and [Google Play Developer Program Policies](https://support.google.com/googleplay/android-developer/answer/17190352).
- Broad installed-app visibility through `QUERY_ALL_PACKAGES` is restricted. The prototype must use the narrowest package-visibility mechanism and may not assume Play approval for broad visibility. See the official [`QUERY_ALL_PACKAGES` policy](https://support.google.com/googleplay/android-developer/answer/10158779).
- Android supports NFC/NDEF reading, but dispatch, foreground behavior, and device support differ from iOS. See the official [Android NFC guide](https://developer.android.com/develop/connectivity/nfc).

These constraints create both a technical gate and a distribution-policy gate.

## Prototype constraints

Build a disposable native Kotlin prototype with one activity and one application module. It may contain only enough UI to select a launchable app, enable the candidate capability, scan a Zap, show active/idle state, intercept the selected app, and recover.

- Test as a normal, unmanaged consumer install: no root, no device-owner enrollment, no work profile, no ADB-granted production permission, and no OEM partnership API.
- The candidate implementation may use an explicitly disclosed `AccessibilityService` with `TYPE_ACCESSIBILITY_OVERLAY` for the spike.
- Declare `android:isAccessibilityTool="false"` in the accessibility-service metadata. Justnoise is a focus product, not a tool whose primary purpose is assisting people with disabilities.
- Declare `android:canRetrieveWindowContent="false"`. Start with only the narrowly required window-state event type and no flags that enable view-node or interactive-window inspection. Any additional event type or flag is a Gate-level exception: document why package-only observation cannot work without it, add it to the data inventory, and pass policy/privacy review before testing proceeds.
- Do not request `QUERY_ALL_PACKAGES`. Use scoped package visibility for launchable activities and record any missing-app limitation.
- Do not connect production accounts, analytics, or backend data.
- The service must ignore password fields, notification content, typed text, and all UI content not required to identify the foreground package.
- Keep an explicit data inventory covering every accessibility event type, field read, derived value, retention period, local persistence location, and network destination. For Gate 0 the only permitted derived target observation is the foreground package identifier, processing and configuration stay local, event contents are not retained, and accessibility-derived data has no network destination.
- Display an in-app prominent disclosure before an unchecked affirmative consent control. Only the user's explicit enable action may open Accessibility settings; installing, onboarding, or scanning a Zap is not consent. Withdrawal and re-consent must be possible. Record the disclosure version and consent locally only, and record the exact Play declaration language for review.
- The blocked surface must never cover or intercept Justnoise, the launcher/Home surface, Settings, System UI, permission controls, package installation/uninstallation, the default phone/emergency dialer, emergency information, or the routes needed to disable the service, Force Stop, or uninstall Justnoise.
- Treat accessibility service death, app process death, Force Stop, Doze, battery restrictions, and OEM process reclaim as normal states to reconcile. A stale heartbeat or disconnected service must never leave the app claiming enforcement is active.
- Use a real authorized and unauthorized NDEF Zap during testing; do not treat a debug button as NFC evidence.

## Required devices

Run the complete matrix on both device families, using the latest public stable OS available to each device on the test date:

1. Google Pixel 8 or newer, stock public Android, unmanaged personal profile.
2. Samsung Galaxy S23 or newer, public One UI, unmanaged personal profile.

Record model, OS build, security patch, One UI version where applicable, prototype commit, battery mode, and tester. A different device may add evidence but does not replace either family. If an OS update changes a result, rerun the entire matrix.

## Exact pass/fail matrix

Use `PASS`, `FAIL`, or `BLOCKED` in both device columns. `BLOCKED` counts as `FAIL` for the gate.

| ID | Scenario | Exact pass criterion | Pixel | Samsung |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, and consent | Installs normally; prominent disclosure and an unchecked affirmative consent control appear before Accessibility settings; declining leaves the service off; accepting opens the correct settings page; service can be enabled without root, ADB, device owner, work profile, or draw-over-other-apps permission. |  |  |
| G0-02 | Scoped app discovery | Without `QUERY_ALL_PACKAGES`, the picker lists Chrome, YouTube, and one installed third-party launchable test app; selecting and restoring each choice works after process death. |  |  |
| G0-03 | Authorized Zap start | From foreground prototype, 10 of 10 authorized NDEF scans enter active state once; malformed and unauthorized tags never enter active state. |  |  |
| G0-04 | Launcher interception | For each of the three targets, 10 of 10 launches from the launcher show the Justnoise blocked surface before target content accepts input. |  |  |
| G0-05 | Alternate entry paths | The selected target is intercepted from Recents, a notification/deep link, a browser link, and Settings “Open”; no tested path provides usable target content. |  |  |
| G0-06 | Navigation bypass | Back, Home, Recents, rotation, split screen, and repeated rapid launch cannot expose an interactable selected target; non-selected apps remain usable. |  |  |
| G0-07 | Background durability | After screen-off and 30 minutes with the prototype backgrounded, five consecutive selected-target launches are intercepted with no manual service restart. Test once in default battery mode and once after the OEM’s recommended unrestricted mode is applied. |  |  |
| G0-08 | Reboot recovery | After reboot and first unlock, the app either restores enforcement before claiming active or visibly reports that re-enablement is required; it never displays a false active state. Once restored, five launches are intercepted. |  |  |
| G0-09 | Authorized Zap stop | During active state, 10 of 10 authorized scans return to idle and the selected target opens normally; canceled, malformed, and unauthorized scans do not stop the session. |  |  |
| G0-10 | Revocation and recovery | Revoking Accessibility immediately produces a truthful unavailable/permission-required state on next app foreground; recovery steps work and there is always a non-destructive way out of the blocked surface. |  |  |
| G0-11 | False-positive safety | During a 30-minute mixed-use script across at least 10 non-selected apps, no unrelated app is blocked and no text, password, or notification content is stored or logged. |  |  |
| G0-12 | Stability and responsiveness | The full matrix completes without crash, ANR, persistent overlay, navigation loop, or device lockout; the blocked surface appears without a visibly usable flash of target content in recorded screen video. |  |  |
| G0-13 | Policy package | Manifest permissions, accessibility metadata, in-app disclosure, consent flow, data handling, and draft Play declaration are reviewed against the linked current policies; no undeclared restricted permission or hidden data use remains. |  |  |
| G0-14 | Non-blockable system and safety surfaces | While active, Justnoise, launcher/Home, Settings, notification shade/quick settings, System UI permission controls, package installer/uninstaller, default phone/emergency dialer, and emergency information remain usable and are never covered by the blocked overlay. Returning from each surface preserves truthful state. |  |  |
| G0-15 | Disable, Force Stop, and uninstall escape | Starting from an active blocked surface, the tester can reach Accessibility settings and disable the service, reach App info and Force Stop, and uninstall Justnoise through both an available launcher route and Settings. The overlay never obstructs these routes; the selected target becomes usable after enforcement ends; no reboot or ADB rescue is required. Reinstall starts idle with consent unchecked. |  |  |
| G0-16 | Service death and process recovery | Exercise an accessibility-service crash/restart, ordinary app process death, OEM process reclaim, and App-info Force Stop. No stale overlay survives. Before enforcement is restored, the next Justnoise foreground shows unavailable/recovery-required rather than active; after explicit recovery, five target launches are intercepted. Force Stop remains a user-controlled escape: launching a target does not silently restart the service or restore a false active claim. |  |  |
| G0-17 | Doze, battery, and overnight endurance | In default battery mode, leave the screen off long enough for natural idle/Doze and for at least eight continuous overnight hours, then verify five target launches without first opening Justnoise. Repeat after the documented unrestricted-battery setting. Both runs intercept all five, retain no stale overlay, and show truthful state; an unrestricted-only pass is a gate failure. |  |  |
| G0-18 | Minimal accessibility capability and local-only data inventory | Accessibility metadata contains `android:isAccessibilityTool="false"` and `android:canRetrieveWindowContent="false"`. The service subscribes only to the documented package-identification event type, with no view-node or interactive-window retrieval flags unless an approved Gate-level exception is attached. The reviewed inventory lists every event/field/derived value and proves only the foreground package identifier is used, configuration and consent records are local, raw event content is not retained, and no accessibility-derived data leaves the device. Logs plus network observation match the inventory, and withdrawal requires fresh affirmative consent before re-enable instructions. |  |  |

Test G0-05 with links and notifications that are available on the chosen targets; record the exact entry path. G0-14 is an allowlist/safety test, not an invitation to inspect those surfaces. Use the platform-provided emergency test route without placing a live emergency call. For G0-07, any difference between default and unrestricted battery behavior must be recorded; G0-17 still requires the overnight run to pass in both modes.

## Gate decision

The gate is **PASS** only when:

- every G0-01 through G0-18 cell passes on both device families;
- the implementation uses no prohibited or undeclared permission/data access;
- product review accepts the observed bypass resistance, onboarding burden, and battery/OEM caveats;
- policy review finds a supportable Play declaration and disclosure path; and
- screen recordings, logs with private content removed, and the completed matrix are attached to the decision record.

Any technical failure on either family is **NO-GO** for a broad Android port until a revised prototype passes. A policy uncertainty is **HOLD**, not permission to build around it. Google Play approval cannot be guaranteed by internal review, so a later closed-track submission remains a release gate.

## After a pass

Only after Gate 0 passes:

1. Scaffold `android-app/` with one `app` module.
2. Implement modes, Zap state, and recovery around a `RestrictionCapability` port.
3. Add Compose previews and behavior tests from the shared contract.
4. Design schedule behavior after the unresolved decisions in `MOBILE_BEHAVIOR_V1.md` are resolved.
5. Add backend, capture, Signal, and secondary screens incrementally.

If Gate 0 does not pass, reconsider the Android promise: offer a lighter intention/interruption experience, restrict distribution to managed devices, or pause Android rather than presenting cosmetic parity as real blocking.

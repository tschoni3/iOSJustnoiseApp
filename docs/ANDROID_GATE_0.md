# Android Gate 0: consumer-device app restriction

Status: full reliability/public-launch gate; smaller internal-beta checkpoint permitted

September 8 owner exception, "jetzt bauen": local production-identity packaging
and signing are now permitted before internal-beta GO. The directory prohibition
below is superseded for this build-only work. Installation, upload and distribution
remain blocked; no physical result or safety decision changes. The production root
must reuse the canonical sources and pass replacement layout/package checks.

## Decision to make

Prove that Justnoise can provide a reliable, understandable, and Google Play-policy-compatible restriction experience on ordinary personally owned Android phones. The full Android product/public launch remains gated by the complete matrix. Under the owner's 2026-09-02 lean-delivery decision, a constrained internal beta may proceed through the smaller checkpoint below without claiming a full Gate 0 PASS.

The spike is not expected to recreate Apple Family Controls. It must prove the user outcome or show that the outcome is not viable enough to promise.

## Known platform constraints

- Android’s strongest package-suspension API, `DevicePolicyManager.setPackagesSuspended`, is for a device owner, profile owner, or delegated package. A normal consumer-installed app is not granted that role. See the official [`DevicePolicyManager` reference](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html).
- An `AccessibilityService` can observe UI transitions and host a `TYPE_ACCESSIBILITY_OVERLAY`, but it does not truly suspend another package. The candidate blocked surface is rendered by Justnoise; it is not system-owned UI merely because Android authorizes the window type. The service is revocable, sensitive to OEM lifecycle behavior, and subject to disclosure, affirmative consent, and permitted-use review under the [Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491) and [Google Play Developer Program Policies](https://support.google.com/googleplay/android-developer/answer/17190352).
- Broad installed-app visibility through `QUERY_ALL_PACKAGES` is restricted. The prototype must use the narrowest package-visibility mechanism and may not assume Play approval for broad visibility. See the official [`QUERY_ALL_PACKAGES` policy](https://support.google.com/googleplay/android-developer/answer/10158779).
- Android supports NFC/NDEF reading, but dispatch, foreground behavior, and device support differ from iOS. See the official [Android NFC guide](https://developer.android.com/develop/connectivity/nfc).

These constraints create both a technical gate and a distribution-policy gate.

Device inspection must not suppress the service being tested. Do not use
`uiautomator dump` during enforcement/recovery verification; follow the
[non-interfering inspection protocol](../spikes/android-gate0/README.md#do-not-let-inspection-interrupt-the-blocker)
and collect screenshots/recordings plus scoped passive state checks instead.

## Prototype constraints

Build a disposable native Kotlin prototype with one application module. It may contain only enough UI to select a launchable app, enable the candidate capability, pair/scan a Zap, show active/idle state, intercept the selected app, and recover. A direct-dispatch extension may add one minimal NFC entry Activity solely to test the user-approved low-friction route in `docs/ANDROID_NFC_ACTIVATION.md`; it does not authorize a production module.

- Test as a normal, unmanaged consumer install: no root, no device-owner enrollment, no work profile, no ADB-granted production permission, and no OEM partnership API.
- The candidate implementation may use an explicitly disclosed `AccessibilityService` with `TYPE_ACCESSIBILITY_OVERLAY` for the spike.
- Declare `android:isAccessibilityTool="false"` in the accessibility-service metadata. Justnoise is a focus product, not a tool whose primary purpose is assisting people with disabilities.
- Declare `android:canRetrieveWindowContent="false"`. Start with only the narrowly required window-state event type and no flags that enable view-node or interactive-window inspection. Any additional event type or flag is a Gate-level exception: document why package-only observation cannot work without it, add it to the data inventory, and pass policy/privacy review before testing proceeds.
- Do not request `QUERY_ALL_PACKAGES`. Use scoped package visibility for launchable activities and record any missing-app limitation.
- Do not connect production accounts, analytics, or backend data.
- The service must ignore password fields, notification content, typed text, and all UI content not required to identify the foreground package.
- Keep an explicit data inventory covering every accessibility event type, field read, derived value, retention period, local persistence location, and network destination. For Gate 0 the only permitted derived target observation is the foreground package identifier, processing and configuration stay local, event contents are not retained, and accessibility-derived data has no network destination.
- Display an in-app prominent disclosure before an initially uncommitted affirmative Agree action. A prechecked control is not consent; a separate checkbox is not required when the clearly labeled button itself records the explicit choice ([Google guidance](https://support.google.com/googleplay/android-developer/answer/11150561)). Only that action may open Accessibility settings; installing, onboarding, or scanning a Zap is not consent. Withdrawal and re-consent must be possible. Record the disclosure version and consent locally only, and record the exact Play declaration language for review.
- The blocked surface must never cover or intercept Justnoise, the launcher/Home surface, Settings, System UI, permission controls, package installation/uninstallation, the default phone/emergency dialer, emergency information, or the routes needed to disable the service, Force Stop, or uninstall Justnoise.
- Treat accessibility service death, app process death, Force Stop, Doze, battery restrictions, and OEM process reclaim as normal states to reconcile. A stale heartbeat or disconnected service must never leave the app claiming enforcement is active.
- Use a real authorized and unauthorized NDEF Zap during testing; do not treat a debug button as NFC evidence.
- Keep NFC routing separate from authorization. A MIME/external-type filter, Android Application Record, intent action, or parsed intent extra can route a tag but must never authorize it. Foreground reader mode and direct system tag dispatch must feed one parser, authorization decision, and session reducer. Direct dispatch requires a usable live `EXTRA_TAG` re-read; copied `EXTRA_NDEF_MESSAGES` alone never mutate state. Use `android.permission.DISPATCH_NFC_MESSAGE` when the production target/platform combination supports it.

## Required device and deferred compatibility

Scope decision, 2026-09-02: the owner has no time or budget for another phone. The existing Samsung Galaxy A16 `SM-A165F` is the only required initial hardware target. Run all 19 scenarios on that exact device, using its current public stable OS and recording the installed build. No A17 purchase, loan, or tester is required to continue or to reach the initial Gate 0 decision.

Galaxy A17 validation is **deferred, unverified, and non-blocking**. Retain its evidence column for a future opportunistic tester run, not as a second-device prerequisite. This narrows the initial hardware-validation scope, not the Android feature scope; it does not add a model whitelist or claim tested A17 compatibility.

Record the exact model code, sales region/CSC, RAM tier, OS build, security patch, One UI version, prototype commit, battery mode, and tester. Evidence applies only to the tested variant/build; neither shared Samsung branding nor NFC availability proves another variant's enforcement, lifecycle, or navigation behavior.

Wider-device validation can follow through existing users or voluntary beta testers when available. Do not advertise tested A17 or broad cross-OEM reliability from A16-only evidence. Broader compatibility claims require matching evidence, but obtaining additional hardware is not an initial development gate. If an OS update changes a result, rerun the affected A16 checks. Product acceptance, Accessibility disclosure/consent, and Google Play policy review remain required; deferring A17 does not waive them.

## Exact pass/fail matrix

Use `PASS`, `FAIL`, or `BLOCKED` in both device columns. Only the 19 required A16 cells count toward the initial gate; an A16 `BLOCKED` counts as `FAIL`. The optional A17 column stays `BLOCKED` until evidence exists, meaning untested/deferred rather than a blocker for this scoped gate.

| ID | Scenario | Exact pass criterion | Galaxy A16 (required) | Galaxy A17 (deferred) |
| --- | --- | --- | --- | --- |
| G0-01 | Clean install, disclosure, and consent | Installs normally; prominent disclosure and an initially uncommitted affirmative Agree action appear before Accessibility settings; declining leaves the service off; accepting opens the correct settings page; service can be enabled without root, ADB, device owner, work profile, or draw-over-other-apps permission. |  |  |
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
| G0-19 | Direct unlocked NFC activation | After normal setup with a valid remembered mode and Accessibility enabled, start from unlocked Home with Justnoise not foregrounded. Tapping the authorized legacy Zap starts 10/10 and, while active after the stop guard, stops 10/10 without manually opening Justnoise, pressing Scan, choosing an app, or tapping a notification. Repeat after ordinary process death. Each tap produces at most one transition; unauthorized, malformed, empty, ambiguous, or rapidly repeated reads do not mutate state. An explicit intent containing copied authorized `EXTRA_NDEF_MESSAGES` but no usable live `EXTRA_TAG` also produces no mutation. Missing mode, revoked capability, screen lock, and Force Stop remain truthful and never silently resurrect enforcement. A chooser or second-confirmation requirement fails the advertised direct-flow criterion, though product may explicitly fall back to foreground scanning. Apply `DISPATCH_NFC_MESSAGE` on supported API/target combinations. |  |  |

Test G0-05 with links and notifications that are available on the chosen targets; record the exact entry path. G0-14 is an allowlist/safety test, not an invitation to inspect those surfaces. Use the platform-provided emergency test route without placing a live emergency call. For G0-07, any difference between default and unrestricted battery behavior must be recorded; G0-17 still requires the overnight run to pass in both modes. For G0-19, record the exact NDEF envelope without recording the authorized value, the device's tag-intent preference, competing NFC handlers, whether the process was warm or cold, and every system surface shown.

## Gate decision

The gate is **PASS** only when:

- every G0-01 through G0-19 cell passes on the required A16 variant/build, with A17 explicitly excluded from the initial gate arithmetic;
- the implementation uses no prohibited or undeclared permission/data access;
- product review accepts the observed bypass resistance, onboarding burden, and battery/OEM caveats;
- policy review finds a supportable Play declaration and disclosure path; and
- screen recordings, logs with private content removed, and the completed matrix are attached to the decision record.

Any technical failure on the required A16 is **NO-GO** until a revised prototype passes. An optional-device failure blocks a reliability claim for that device; investigate shared causes without treating an absent A17 run as an A16 failure. A policy uncertainty is **HOLD**, not permission to build around it. Google Play approval cannot be guaranteed by internal review, so a later Play test-track submission remains a release gate. An A16-scoped PASS authorizes production development, not a claim of verified reliability on every Android phone.

## Smaller checkpoint for the first internal beta

This core safety checkpoint can use the existing local-only single-target candidate.
It does not define final MVP scope: the owner's later 2026-09-02 clarification
requires multiple-app selection, Zap sessions, schedules and history, as recorded
in `ANDROID_PREDEVICE_READINESS.md`. Do not label the single-app foundation or
preview screens a completed MVP. Full iOS parity, A17 coverage,
and the full endurance matrix are not prerequisites for this core checkpoint.
This is not a public-launch waiver; verify the added MVP behavior before distribution.
The owner approved Android named modes on September 7 as an app-selection extension;
their migration, selection and schedule-reference behavior require affected acceptance
without redefining this existing core safety checkpoint.

Before promoting the existing capability into a signed internal-test app, record one end-to-end A16 safety smoke run on the candidate build covering:

1. Normal disclosure, unchecked consent, manual Accessibility enablement, local pairing, and restored app selection.
2. Authorized real-Zap start, stable interception with target touches blocked, authorized stop, and the target actually usable afterward; exercise the advertised direct route as well as the foreground recovery scan.
3. Invalid/unauthorized input and a rapid repeated tap do not cause unintended state changes.
4. Native Home/Back, Justnoise, Settings, and the phone/emergency route remain reachable; the overlay must not obstruct system navigation or emergency access. Never place a live emergency call for testing.
5. Revocation/service loss reports recovery truthfully; disable, Force Stop, and uninstall remain reachable without ADB rescue, with no stale blocker. Reinstall requires fresh consent.
6. Withdrawal/cleanup feedback is truthful, and the reviewed packaged permissions, disclosure and local data handling have no unresolved policy issue for the beta's actual behavior.

Record the build, evidence, limitations, and owner acceptance as a separate **Internal beta decision: GO/HOLD** in `DECISION_RECORD.md`. GO authorizes the constrained release-app implementation, not a Play upload or a full Gate 0 PASS. After package/signing changes, repeat this smoke check on the release build before distribution and on the Play-delivered build. Play account eligibility, signing, privacy/declarations, reviewer access, and owner approval still apply. Longer repetitions, multi-target/alternate-entry coverage and overnight/default-battery evidence stay open for controlled beta testing and public-release acceptance. A known safety defect blocks beta distribution; do not defer it as polish.

The pre-device verifier intentionally continues to reject `android-app/` during the current HOLD. When the internal-beta decision becomes GO, replace that phase-specific absence check with release-app verification as part of the explicit promotion change; do not keep it as a demand for full-matrix completion or merely delete the safety checks.

## After the appropriate checkpoint

The four-feature MVP is already implemented in the integrated development app;
schedule timing is covered by the approved contract. After recorded internal-beta
GO, promote that reviewed implementation rather than rebuilding it. Full Gate 0
remains the later reliability/public-release decision. The next boundaries are:

1. Promote to `android-app/` with one canonical `app` module and the approved
   `com.justnoise.app` identity, subject to Play registration.
2. Preserve real app selection, Zap sessions, schedules, History and recovery,
   keeping UI separate from NFC, restrictions, storage and alarm adapters.
3. Retain the existing contracts and regression tests; replace the phase-specific
   production-directory absence guard with release verification.
4. Reconcile production labels, privacy/disclosure and signing, then run the
   affected signed-release checks without erasing development data.
5. Keep backend/accounts, Capture, widgets and other excluded features out of this
   internal-test milestone. Named modes are now separately approved (September 7),
   not an inferred prerequisite; verify that extension before distributing it.

If Gate 0 does not pass, reconsider the Android promise: offer a lighter intention/interruption experience, restrict distribution to managed devices, or pause Android rather than presenting cosmetic parity as real blocking.

## Google Play and distribution gates

Policy checkpoint audited 2026-09-01 against the linked official Google and Android documentation. Recheck it before account creation, before the first test-track upload, and before production submission because Play requirements can change. These distribution tasks may be prepared in parallel. The limited internal-beta route above does not turn the full technical Gate 0 `HOLD` into a public-release approval.

1. **Choose and verify the Play Console owner deliberately.** Google offers Personal and Organization accounts; an Organization account requires a D-U-N-S number. A new Personal account may require verification through the Play Console mobile app on a non-rooted physical Android 10-or-newer phone. See [account types](https://support.google.com/googleplay/android-developer/answer/13634885), [required account information](https://support.google.com/googleplay/android-developer/answer/13628312), and [device verification](https://support.google.com/googleplay/android-developer/answer/14316361).
2. **Choose the production application ID once.** Play package names are unique and permanent, so do not reuse the disposable `com.justnoise.gate0` identifier or create the Play app record before the production name is reviewed. Play package registration is required by September 30, 2026. See [app setup](https://support.google.com/googleplay/android-developer/answer/9859152) and [package registration](https://support.google.com/googleplay/android-developer/answer/16984799).
3. **Keep the release toolchain compliant.** Starting August 31, 2026, new phone/tablet apps and updates must target Android 16/API 36 or newer. The spike already targets 36, but every future production dependency and App Bundle must be revalidated. See the [target API requirement](https://developer.android.com/google/play/requirements/target-sdk).
4. **Submit the Accessibility use for review.** Because Justnoise is not an accessibility tool, the production app must keep `isAccessibilityTool=false`, provide a separate in-app prominent disclosure and affirmative consent before settings enablement, complete the Play Accessibility declaration, and attach a short video showing consent, refusal/re-entry, enablement, and the core restriction behavior. See the [AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491) and [disclosure/video guidance](https://support.google.com/googleplay/android-developer/answer/11150561).
5. **Plan the required testing track.** A Personal developer account created after November 13, 2023 must complete a closed test with at least 12 continuously opted-in testers for at least 14 days before applying for production access. This is separate from the A16-scoped Gate 0 matrix. See the [new-account testing requirement](https://support.google.com/googleplay/android-developer/answer/14151465).
6. **Complete applicable privacy and deletion disclosures from actual behavior.** Apps exclusively active on internal testing are exempt from the Data safety form; complete it accurately before moving to a closed/open/production track, including third-party SDK behavior. Applicable privacy-policy, user-data and Accessibility disclosure duties still apply during internal testing. If Android later allows account creation, it must expose the required in-app deletion path and public web deletion-request path; do not add accounts merely to satisfy a checklist for the local-only beta. See [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), and [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111).
7. **Decide Android entitlement and billing behavior before implementing purchases.** StoreKit receipts are not a portable entitlement source. If the Android app sells digital access, use the applicable Play billing path and verify purchase state through a secure backend before granting or synchronizing entitlement. If the first Android release is consumption-only, document that limitation explicitly. See the [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738), [Play Billing integration](https://developer.android.com/google/play/billing/integrate), and [backend lifecycle guidance](https://developer.android.com/google/play/billing/backend).
8. **Create and protect the release identity.** Publish an Android App Bundle, enroll in Play App Signing, keep the upload key outside source control, and register Play's app-signing certificate fingerprints with every OAuth/API provider that authenticates the Android package. See [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756) and [app setup](https://support.google.com/googleplay/android-developer/answer/9859152).
9. **Give reviewers durable access.** App content declarations must include instructions for restricted functionality. Provide a reusable review account and required entitlement only if the actual app uses login/access restrictions; the local-only internal beta does not require inventing an account system. The physical-Zap requirement and Accessibility flow still need explicit reviewer instructions and the applicable declaration video; do not create a hidden production bypass merely to satisfy review. See [prepare an app for review](https://support.google.com/googleplay/android-developer/answer/9859455).

A production Android release remains blocked until both sets of evidence exist: the full physical Gate 0 matrix and the applicable Play account, policy, testing, privacy, billing, signing, and closed-track approvals.

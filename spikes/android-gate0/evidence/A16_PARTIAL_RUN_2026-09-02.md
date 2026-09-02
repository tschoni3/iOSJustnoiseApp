# Galaxy A16 partial run — 2026-09-02

Status: **FOREGROUND AND WARM DIRECT CYCLES OBSERVED; LATER SYNTHETIC HOME AND OWNER-CONFIRMED UNBLOCK SAMPLES RECORDED — no complete Gate 0 cell passes**

## Device and tested build

- Same owner-assisted Galaxy A16 `SM-A165F` / product `a16nseea` as
  [the September 1 run](A16_PARTIAL_RUN_2026-09-01.md).
- Android 16 / API 36; build `A165FXXSADZG3`; security patch `2026-07-05`.
  Full SKU/CSC, RAM tier, and user-visible One UI metadata remain incomplete.
- Application ID: `com.justnoise.androiddev`.
- Foreground-cycle and initial warm-Home tested source:
  `dbb3ba6b1c03d222ba7e06623b904fb610d56379`.
- That phase's installed APK SHA-256, rechecked from the device:
  `f9a2c63cdb1cf3481d5007939bd0b96d02a126c1af6a2f1f709467aacf72b077`.
- No APK update, state clearing, re-pairing, or permission toggle was performed
  during the original foreground cycle. The later update is separately scoped below;
  the eight preserved artifacts still show this original build.

## Readiness diagnostic

Earlier accessibility-tree dumps showed **Reconnect Accessibility**, while passive
checks afterward found the service enabled/bound, `desired_active=false`,
`service_connected=true`, and a fresh heartbeat. Android's exit history recorded an
overnight low-memory reclaim, not an application crash. This is not a durability pass.

Source review identified a test-interference hypothesis: UiAutomation suppresses other
Accessibility services by default, and a paused screen can retain recovery text after
the service reconnects. See the
[non-interfering inspection protocol](../README.md#do-not-let-inspection-interrupt-the-blocker).
The hypothesis was not deliberately reproduced on the paired phone.

The readiness observation used screenshots and passive checks only. With the phone manually
unlocked and Justnoise resumed, the screen showed **Ready to Zap** and target **Chrome**.
Opening the app with an ordinary task bring-to-front initially retained Samsung settings
above it; returning to the existing Justnoise Activity with `--activity-clear-top`
exposed the actual app screen. No permission reset was necessary.

## Physical observations

1. The real foreground scanner was opened. The owner tapped the paired physical Zap
   and reported **Session started**. Sanitized persisted state changed from idle,
   revision `7`, to `desired_active=true`, revision `8`.
2. Chrome was launched through its normal Activity. Justnoise displayed the full-screen
   blocked surface with dynamic Chrome copy and the white **Keep going** action.
3. Two ADB-injected taps and a swipe were applied to the overlay's blank, non-button
   area. The before/after screenshots were byte-identical; the session remained active
   at revision `8`. These are synthetic touch checks, not a substitute for the full
   manual bypass/escape matrix.
4. Asked whether Chrome was now steady/unusable underneath or still flickering, the
   owner replied **“its working thanks.”** This corroborates one successful post-fix
   sample; it is not repeated-run or multi-target coverage.
5. Returning to Justnoise showed **Phone Zapped / Protection active** with the same
   Chrome target. The foreground stop scanner was opened; the owner subsequently
   reported **“stopped.”** Sanitized state changed to `desired_active=false`, revision
   `9`, with the service still connected. Justnoise displayed **Ready to Zap**.
6. Chrome reopened without the overlay. A neutral `about:blank` page was opened for
   preserved evidence, and an ADB-injected tap on Chrome's menu opened the normal menu.
   This confirms post-stop touch responsiveness for this sample. The menu was dismissed;
   the session remained idle throughout this post-stop check. Only neutral Chrome screenshots were retained in the
   repository; no browsing suggestions were archived.

## Recording limits

The recording command requested a 30-second limit. The actual MP4 contains **9.456222
seconds and 176 video frames**; do not label it a 30-second stability recording.

- The first approximately 4.98 seconds show Justnoise's active screen.
- The app-switch animation briefly exposes Chrome's splash/logo at approximately
  5.0755 seconds. No Chrome browsing content was observed in the inspected transition.
- The full blocker appears at approximately 5.0939 seconds and remains through the
  end: approximately **4.36 seconds of recorded post-interception stability**.
- No disappear/reappear loop was observed after interception. The recording alone
  does not establish whether Chrome accepted input before interception or prove the
  exact timing of the separate tap/swipe commands.

## Warm Home / navigation follow-up

### Original build: one physical start and a navigation defect

- From unlocked Samsung Home, with Justnoise's existing task in the background,
  the owner tapped the physical Zap and confirmed **“i tapped qithout opwn justnoise app.”**
  Sanitized state changed from idle/revision `9` to active/revision `10`.
- Justnoise's existing `MainActivity` became foreground afterward. This was not the
  intended return-to-Home behavior. A narrow `text/plain` NDEF resolver query returned
  only `DirectNfcActivity`, but the owner did not explicitly confirm whether a chooser
  appeared; do not infer complete chooser-free coverage from the query.
- A later read found idle/revision `11`. The owner has not confirmed the action that
  caused that transition, so it is **not evidence of a physical direct stop**.
- A separate synthetic `ACTION_NDEF_DISCOVERED` / `text/plain` / `NEW_TASK` launch,
  containing neither a live tag nor copied NDEF messages, reproduced the navigation
  defect: Home was replaced by the existing `MainActivity`. Its immediate baseline and
  after-state were both idle/revision `11`, confirming no session mutation in that
  no-tag sample. An earlier diagnostic lacked an immediate baseline and is not used
  to attribute any state transition.

### Task-isolation build: navigation and physical start/stop follow-up

- Source: `d93ae010024101fdd93c9246a94dc146ca0593f2`.
- APK SHA-256, matched against the installed `base.apk`:
  `d2de157599fc77a59fb153f0fde1776bab6c0e26cfa10b9d48e0b4f1abe8ebc0`.
- Installed using `adb install -r`; no app-data clearing, uninstall, or re-pairing.
  The Chrome target and idle/revision `11` state were retained.
- Only the transient NFC entry's task affinity changed in the two manifests. Its
  explicit empty affinity is covered by a regression that failed before the change
  and passed afterward. The full Android pre-device verifier passed, and the packaged
  integrated manifest contains the attribute. This build also includes the previously
  verified shared visual mappings from `113b660`.
- After the update, the app truthfully showed **Enable Accessibility**, and Android's
  enabled-service setting returned `null`. The cause of that disabled state has not
  been established. No ADB permission grant or setting write was used; the owner was
  asked to manually re-enable the service before further physical session checks.
- With the existing main task primed, the phone was returned to unlocked Home. The
  same synthetic no-tag `NEW_TASK` launch then left Samsung Home foreground. Immediate
  before/after state remained idle/revision `11`. This is a successful **navigation
  and no-tag rejection check**, not evidence of a physical NFC start or stop.
- The owner subsequently reported **“enabled.”** Android listed the expected
  `Gate0AccessibilityService`; sanitized state showed `service_connected=true` and
  an advancing heartbeat. The app showed **Ready to Zap** with Chrome selected and
  idle/revision `11`. The phone was returned to unlocked Samsung Home.
- Asked to tap the physical Zap once without opening Justnoise or pressing Scan,
  the owner confirmed **“session started without justnoise or app chooser.”**
  Sanitized state changed to active/revision `12`, with the service connected;
  a passive window check found Samsung Home foreground and unlocked. This is one
  owner-confirmed successful warm direct-start/navigation sample on the updated APK,
  not repetition, cold-process, or other-device coverage.
- After the physical entry had finished, with the owner asked to keep the Zap away, a synthetic
  `ACTION_NDEF_DISCOVERED` / `text/plain` / `NEW_TASK` request containing no live tag
  and no copied NDEF messages was rejected while the session was active. Immediate
  before/after state remained active/revision `12`; the heartbeat advanced and Home
  remained foreground. Together with the idle sample above, this verifies no-tag
  rejection in both sampled states. It does not cover copied-authorized-message
  forgery or unauthorized physical tags.
- Asked to tap once from Home to stop, the owner confirmed **“Session stopped” while
  staying on Home, without a chooser**. Sanitized state changed from active/revision
  `12` to idle/revision `13`; a second read remained idle/revision `13` with a connected,
  advancing service heartbeat. Samsung Home remained foreground and unlocked.
  This completes one owner-confirmed warm direct start/stop cycle on the updated APK.
  No further APK installation, foreground scanner, pairing change, or permission
  toggle occurred between this cycle's start and stop.

### Updated-build launcher-blocking follow-up (partial)

On the same `d93ae01` / `d2de157...` APK, the owner was asked to start once from
unlocked Home with the physical Zap, open Chrome using its launcher icon, and try
tapping and scrolling. The owner reported **“chrome stays blocked after doing that.”**
Sanitized state changed from idle/revision `13` to active/revision `14`, with the
service connected and its heartbeat advancing. A passive window check identified
Chrome's Activity; the screenshot showed Justnoise's app-specific Chrome interruption
surface and white **Keep going** action, not usable browser content.

This is a second post-fix warm start sample and an owner-reported manual
launcher/touch-blocking sample on the updated build. It is not a launch recording,
does not measure time-to-interception or pre-overlay input, and does not establish
10/10 repetitions or multi-target coverage. Asked specifically about normal system
Home, the owner reported using the prototype's **Go Home** button and not seeing any
other controls. The supplied phone photo corroborates missing normal navigation
buttons. The app's escape action must not be credited as a normal system-Home pass.
After being asked to return Home and stop with the physical Zap before an update,
the owner reported **“stopped.”** A passive check confirmed idle/revision `15`, a
connected service, and unlocked Samsung Home. This corroborates the second stop,
but the owner did not separately confirm chooser-free navigation for it. Post-stop
normal Chrome interaction was not checked for this second cycle.

The following artifact belongs to the updated build, unlike the original foreground
artifacts below. It contains only the interruption UI and generic device status icons.

| Artifact | SHA-256 |
| --- | --- |
| [Chrome blocked after direct Home start](a16-2026-09-02/direct-chrome-blocked.png) | `b32d412a48ac3b22e1ab7516c44ab547d49b6004d1616825d45d2df61b2caaa6` |

### Navigation-controls defect and candidate fix

On the previously installed `d93ae01` build, passive window diagnostics placed the opaque
accessibility overlay at `[0,100][1080,2340]`, above a shown 135-pixel navigation-bar
window. Together with the screenshot and owner report, this establishes a visible
system-navigation defect on that build, not merely an unperformed G0-14 check.
The newer visibility sample below does not complete the remaining G0-14 coverage.

The new candidate sizes the actual overlay window using `currentWindowMetrics`,
visible system-bar insets, and the per-edge maximum of stable display-cutout insets.
It does not merely add padding to a still-touchable full-screen window. Physical
top/left coordinates and explicit dimensions avoid covering the navigation region;
framework inset fitting is disabled to avoid subtracting insets twice. The clickable
root and non-focusable touch-blocking window remain in place. Failed layout marks
recovery required rather than claiming healthy enforcement.

This choice is supported by Android's [WindowMetrics contract](https://developer.android.com/reference/android/view/WindowMetrics#getWindowInsets())
and [AOSP's distinction between above-window insets and display-metrics insets](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/services/core/java/com/android/server/wm/WindowState.java).
The AOSP behavior is consistent with the observed window geometry; it is not a
claim to have inspected Samsung's private window-manager implementation.

Six pure geometry tests cover measured A16 portrait dimensions, side navigation,
zero visible navigation inset, offset bounds, and invalid sizes. A source-wiring
regression failed before the controller change. The full Android pre-device verifier
passed afterward, including all six geometry and eight static-policy tests. The
integrated candidate APK SHA-256 is
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
These tests do not establish actual
Home/Back touch routing. Configuration and inset callbacks refresh changed bounds
without polling; immersive/transient bar changes may not reach an overlay above
the bars and remain explicitly unverified.

After the confirmed stop to idle/revision `15`, source
`d09833de0ebbe5652bde590b2e3418942ed3db36` was installed using `adb install -r`.
The installed `base.apk` matched the candidate checksum above. No app-data clearing,
uninstall, re-pairing, or permission toggle was performed. Accessibility remained
enabled; sanitized state retained idle/revision `15`, `service_connected=true`,
and an advancing heartbeat. The resumed app showed **Ready to Zap** with Chrome
selected and normal Android navigation controls visible on the main app screen.
That main-screen observation does not verify navigation under the blocker.

### Installed window-bounds candidate: navigation visibility retest

The phone was returned to Home. Following a request to tap the Zap once to start,
open Chrome, and inspect the bottom navigation buttons, the owner replied
**“yes its visible now.”** Sanitized state changed from idle/revision `15` to
active/revision `16`, with the service connected and a fresh heartbeat. This is
one post-install start and owner-reported navigation-visibility sample; chooser
behavior was not separately reconfirmed for this start.

The first subsequent screenshot/window read found Samsung Home, not the blocker.
The action that returned Home was not established and is not credited as a normal
Home-button test. With state still active/revision `16`, Chrome was relaunched by
ADB using its normal Activity. The next screenshot showed the Chrome-specific
blocker and visible native Recents, Home, Back, and Accessibility controls.
Passive window diagnostics measured the actual overlay at `[0,100][1080,2205]`
(`1080 x 2105`), leaving the navigation window's `[0,2205][1080,2340]` region
uncovered. The old failing build's overlay extended to `2340`.

| Artifact, source `d09833d` | SHA-256 |
| --- | --- |
| [Chrome blocker with restored navigation controls](a16-2026-09-02/navigation-controls-restored.png) | `85560b8ccc16a2458bc5190d565071e40b3bd79c240821ae4478e155e777daae` |

The artifact contains the blocked UI and generic status icons only. It follows
the synthetic warm Chrome relaunch, not a recorded manual-launch sequence.
Visibility and portrait bounds are confirmed in this sample. The later synthetic
Home-routing check below adds limited touch-routing evidence; owner-operated native
Home/Back, sustained touch blocking, and rotation/immersive behavior remain unverified
on `d09833d`.

### Synthetic native Home and Chrome re-entry on the same candidate

With the same `d09833d` APK still installed and the session active/revision `16`,
Chrome's blocker was confirmed at `[0,100][1080,2205]`. An ADB-injected touch at
`(546,2275)` targeted the native bottom-centre Android Home control, below the overlay.
This was a coordinate touch, not a Home key event or the app's **Go Home** action.
Samsung Home became foreground with no blocker. Chrome was then reopened through
its normal Activity using ADB, and the blocker appeared again. A second touch at
the same native Home coordinate returned to Home without the blocker. Scoped reads
remained active/revision `16`, with the service connected and a fresh heartbeat.

This is one synthetic Home/re-entry sequence on the physical A16, including two
injected native-Home touches. It is not an owner-operated Home/Back test, a launch
timing measurement, or repetition, rotation, split-screen, or durability coverage.
Only neutral blocker screenshots are preserved; the Home screenshot containing the
owner's launcher layout and location widget is not archived.

| Artifact, source `d09833d` | SHA-256 |
| --- | --- |
| [Chrome blocker before native Home touch](a16-2026-09-02/native-home-before.png) | `8e5fea10be872f44d97cacee5cc900174372c73b5f1c6043375af796568fe0f4` |
| [Chrome blocked again after native Home](a16-2026-09-02/chrome-after-native-home.png) | `2d9efdd21cf71804c7685ef48554ea1fc44f9d8253d550db821175c474237661` |

### Owner-confirmed stop and normal Chrome access

After being asked to tap the physical Zap once to stop and open Chrome, the owner
confirmed **“yes i can!”** in response to whether browsing worked normally without
the blocker. The scoped state read found `desired_active=false`, `session_revision=17`,
`service_connected=true`, and `service_heartbeat_elapsed_millis=172659620`.
The installed build at this checkpoint was still `d09833d`, APK SHA-256
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
Together with the preceding active/revision `16` state, this records a stop to
idle/revision `17` and owner-confirmed post-stop Chrome usability on that candidate.
The stop's foreground/direct route and chooser behavior were not separately
established, and this does not establish 10/10 stopping reliability.

The owner explicitly asked not to repeat this already-completed confirmation.
Retain this result rather than asking the same stop-and-browse question again;
the reference to earlier confirmations is not counted as additional test runs.
A later pre-install read found idle/revision `19`. The intervening transitions
were not observed, so revisions `18` and `19` are not credited as another verified
scan or cycle and do not replace the separately confirmed revision `17` outcome.

### Cleanup-feedback update installed while idle

After a fresh read confirmed idle/revision `19`, `adb install -r` successfully
updated the existing development app to source `dbe2469`. The installed APK hash
was verified as `c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`.
No uninstall, data clearing, re-pairing, or permission grant/toggle was performed.
Quiet presence/value checks confirmed that the paired-credential record remained
present and Chrome remained selected; no credential value was printed.

Post-update reads retained idle/revision `19` and `service_connected=true`.
The heartbeat advanced from `172751752` to `172822224`, with the latter read at
device uptime `172824.07` seconds. This verifies update/data retention and a live
service sample, not a new physical NFC cycle or fault-injected cleanup test.
The earlier owner-confirmed stop/unblock result remains recorded on `d09833d`;
do not ask the owner to repeat that same confirmation merely for bookkeeping.

### Native Back and escape-route check; unexpected setup stop

At approximately 23:15–23:19 device-local time, the installed `dbe2469` APK was
rechecked against SHA-256
`c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`.
The owner replied **“ok done”** after a request for one Zap start specifically to
check system escape routes, not repeat the completed stop/unblock question.
Scoped state changed from idle/revision `19` to active/revision `20`, service
connected. Home was visible; chooser behavior was not separately credited.

Chrome was opened with its normal launcher intent. An immediate capture contained
its launch animation; the settled capture showed the blocker at
`[0,100][1080,2205]`. This is not launch-time or zero-flash proof.

- An ADB coordinate touch at `(850,2275)` pressed visible native **Back**. Samsung
  Home became foreground without a blocker; state remained active/revision `20`,
  service connected. This is a physical-screen synthetic touch, not a key event
  or app-provided escape button.
- After Chrome re-entry, **Disable Accessibility** opened Samsung Accessibility
  settings. Normal taps through **Installed apps** and **Justnoise Android Dev app
  blocking** reached its enabled toggle without an overlay. No toggle was changed.
- After Chrome re-entry, **App info / Force Stop** opened the app's system page.
  **Uninstall** and **Force Stop** were visible and unobstructed. Neither was
  activated: this proves reachability, not completed disable/uninstall/recovery.
- After Chrome re-entry, **Keep going** opened setup without an overlay but
  unexpectedly showed idle. The next read confirmed idle/revision `21`, service
  connected. No scan or intentional target change was performed. This unintended
  session end is **not** another successful Zap-stop result.

Source inspection found a matching cause: the spinner's asynchronous restoration
callback reselected Chrome after its synchronous binding guard reset;
`selectTarget` wrote the same package, cleared the session and broadcast the change.
The run stopped here and the idle phone returned to Home. Pairing, consent and
Accessibility were not cleared or changed. Phone/emergency and actual revocation
remain untested. The beta stays HOLD pending the fix and relevant verification.
Existing synthetic native Home evidence remains valid; a finger-only repetition
is not an additional prerequisite for the smaller beta checkpoint.

| Artifact, installed source `dbe2469` | SHA-256 |
| --- | --- |
| [Blocker before native Back](a16-2026-09-02/safety-blocked-before-back.png) | `95d3b3c44aaebe2319e0db2490204d5ea24b3b0bac76245710071a04b026fe19` |
| [Reachable Accessibility toggle, unchanged](a16-2026-09-02/safety-accessibility-control.png) | `bb00a94f9a8047b56946eaff596aa07050bf0a83d4b2fd592be68b6d561187b9` |
| [Reachable App info controls](a16-2026-09-02/safety-app-info-controls.png) | `1b9e765e3ce00f039f732c904e678069f00eeeb284aff2b943f04446a57369c7` |
| [Unexpected idle after Keep going](a16-2026-09-02/keep-going-unexpected-idle.png) | `df2a791589b52912ca0c186ce120d3db2ef5f7750b29d11a15b7bf0644ebbc3c` |

Only app-specific settings and neutral blocker/setup captures are retained. Home
and launch-animation captures with launcher/location/profile details are excluded.
No `uiautomator dump`, instrumentation, permission grant, live call, or ADB state
injection was used on the paired phone.

### Corrected restoration and interruption handling

The correction makes reselecting the existing package a no-op in both the
controller and persistence boundary. It preserves session/recovery/start/revision
and sends no state-change broadcast. Real target changes still validate eligibility
and deliberately clear the old session. No spinner timing workaround or UI/layout
change was introduced.

A separate source review found that `onInterrupt()` treated feedback interruption
as service teardown, dropping the stop/withdraw receiver and heartbeat while later
package events could still render the overlay. The correction hides the overlay and
sets recovery required while keeping those recovery channels alive; actual unbind
and destruction still tear down the service. This follows Android's
[`onInterrupt` feedback contract](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onInterrupt()).
The recovery latch updates process-local preferences immediately, but asynchronous
durability and actual OEM interruption behavior are not proved by these tests.

Verification in this change:

- Seven existing/new storage and consent/setup instrumentation tests ran only on
  the API-36 `medium_phone` emulator. Before the target fix, three regressions
  failed for same-target persistence, controller state/broadcast behavior, and
  Activity creation. After correction all seven passed, including app-list refresh,
  recreation and retained recovery state. Synthetic emulator sessions are not NFC
  or physical-device restriction evidence.
- The new interruption callback wiring regression failed before its fix. All 81
  JVM tests across the three Android projects passed afterward; the pure recovery
  test confirms a fresh heartbeat does not clear the recovery latch.
- The full pre-device verifier passed: product/static contracts, JVM checks, lint,
  debug/instrumentation APK builds, integrated unsigned release APK/AAB and ten
  packaged-boundary verifier regressions. Packaged release checks passed for
  `com.justnoise.androiddev`, not the future `com.justnoise.app` identity.

After a fresh idle/revision `21` read, `adb install -r` installed the corrected
integrated APK without clearing data. Installed SHA-256 matches
`9fe99b7232a135cd6e6e16d4ad105faa3200cd87e516a5101a08bda60c7c4739`.
Quiet checks confirmed the pairing record and Chrome selection remain present;
state stayed idle/revision `21`, service connected, with heartbeat advancing from
`174346911` to `174387168`. The declared integrated main Activity opened and showed
**Ready to Zap** for Chrome; the phone then returned to Home. An initial implicit
ADB launcher intent did not resolve, so no launcher-icon test is claimed here.
No iOS source, permissions, signing identity, pairing or Play state was changed.
Installation alone did not establish the active Keep going behavior; the separate
physical regression below does.

### Corrected Keep going verified on the A16

After the owner acknowledged a new, explicitly defect-specific Zap start request,
scoped state was active/revision `22`, service connected, on the corrected
`9fe99b…c4739` APK. Chrome's blocker was visually confirmed. An ADB touch at
`(540,1520)` pressed **Keep going**; setup opened without an overlay, displayed
**Active**, and the state remained active/revision `22` with a fresh heartbeat.
Reopening Chrome displayed the blocker again. This verifies one corrected
Keep going/re-entry sequence on the phone, not a new stop/unblock campaign.

Native Home was then tapped and the ordinary empty `ACTION_DIAL` route opened
Samsung's `com.samsung.android.dialer/.DialtactsActivity`. Its empty keypad was
visible without a blocker and state remained active/revision `22`. No number was
entered and no call was placed. The dedicated emergency route, lock-screen access,
actual revocation, Force Stop, withdrawal and uninstall/reinstall remain untested.
The phone was finally returned to Home with the same active/revision `22` state;
the owner can stop normally, without repeating the already-confirmed browsing check.

| Artifact, corrected APK `9fe99b…c4739` | SHA-256 |
| --- | --- |
| [Keep going preserves active session](a16-2026-09-02/keep-going-preserves-active.png) | `0f776b816cb4ccf69f743ecf6e031584680b7cd48baa0c7389ccf09fa5a68d51` |
| [Chrome remains blocked after Keep going](a16-2026-09-02/chrome-blocked-after-keep-going.png) | `96c779fcdf8dc000e246f988243f9947c2de540ef3218bbe12444c2a19588473` |
| [Empty phone keypad reachable while active](a16-2026-09-02/phone-reachable-while-active.png) | `1f69ff25d4a32e28aae2b80c3d4cbb317f49250e00dc87ec61d83296d7dee3bc` |

## Preserved foreground-cycle artifacts

Artifacts below contain the test UI, not Zap payloads/digests. Checksums are SHA-256.

| Artifact | Checksum |
| --- | --- |
| [Ready screen](a16-2026-09-02/ready.png) | `37240ce80f3f990b8f5d9ce129e0873ba6a7f616a1745f6e8e38366ef01603f9` |
| [Initial Chrome block](a16-2026-09-02/chrome-blocked.png) | `220db304e665351e77487b5289dc0815056738d1ebcc7f46cc9d3c7f03cf5d48` |
| [After taps/swipe](a16-2026-09-02/chrome-after-touch.png) | `220db304e665351e77487b5289dc0815056738d1ebcc7f46cc9d3c7f03cf5d48` |
| [Active after check](a16-2026-09-02/active-after-check.png) | `7ebc154ca58139df83358301a56a54370d6d01c1282e9611a1a5a82ff5ee9239` |
| [Launch/block recording](a16-2026-09-02/chrome-stability.mp4) | `bcf6b74929e3444e1b00591658bf2e2e75896aa5ec795146ffab42442c8bafc7` |
| [Stopped / Ready to Zap](a16-2026-09-02/stopped.png) | `46f567af879b19f7ab0ada061e5893cd3df9dc7fbd152e6d2657ec1d867c9bb4` |
| [Neutral Chrome after stop](a16-2026-09-02/chrome-after-stop.png) | `51408b70ec2c2f9640ca3213b608c771c17fc48d01d3c5b86a9326e5a94b0d4c` |
| [Chrome responds to menu tap](a16-2026-09-02/chrome-menu-after-stop.png) | `dd0aa7ec30d84585960c9a5d1dc3b0e5f55d10004461d67bd8d24b3e27b574e3` |

## Remaining checks

- Repeated authorized physical starts/stops and unauthorized/malformed/canceled-tag
  cases; this run records one complete foreground cycle and one complete post-fix
  warm direct cycle, plus the later `d09833d` stop/unblock sample above, not the
  required 10/10 repetitions. The latest owner-confirmed normal Chrome access is
  complete evidence for that sample, not a pending question.
- Longer recorded touch, Back, Home, Recents, rotation, split-screen, and rapid-launch
  checks; the synthetic native-Home/re-entry sample does not complete those scenarios
  or all required target/entry-path coverage.
- Repeated post-update direct unlocked-Home starts/stops and navigation,
  cold-process and remaining negative direct-entry cases; lifecycle/reboot/battery/Doze/overnight,
  privacy/false-positive, escape, and policy evidence. Galaxy A17 validation is
  deferred/unverified and non-blocking under the owner's later 2026-09-02 scope decision.

All 19 currently required A16 matrix cells remain `BLOCKED`; the optional A17 column
is excluded from gate arithmetic. No observed result changed with the scope decision.
This run establishes meaningful progress on the
reported flicker defect and direct NFC navigation without changing the Gate 0 or
production-implementation hold.

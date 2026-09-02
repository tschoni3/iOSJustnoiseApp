# Galaxy A16 partial run — 2026-09-02

Status: **ONE FOREGROUND CYCLE AND ONE POST-FIX WARM HOME START/STOP CYCLE OBSERVED — no complete Gate 0 cell passes**

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
Direct stop and post-stop normal Chrome interaction for this second cycle remain
pending; the last observed state is active/revision `14` with Chrome foreground.

The following artifact belongs to the updated build, unlike the original foreground
artifacts below. It contains only the interruption UI and generic device status icons.

| Artifact | SHA-256 |
| --- | --- |
| [Chrome blocked after direct Home start](a16-2026-09-02/direct-chrome-blocked.png) | `b32d412a48ac3b22e1ab7516c44ab547d49b6004d1616825d45d2df61b2caaa6` |

### Navigation-controls defect and candidate fix

On the installed `d93ae01` build, passive window diagnostics placed the opaque
accessibility overlay at `[0,100][1080,2340]`, above a shown 135-pixel navigation-bar
window. Together with the screenshot and owner report, this establishes a visible
system-navigation defect. G0-14 remains blocked by this known failure, not merely
an unperformed check.

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
pre-install integrated candidate APK SHA-256 is
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
These tests do not establish actual
Home/Back touch routing. Configuration and inset callbacks refresh changed bounds
without polling; immersive/transient bar changes may not reach an overlay above
the bars and remain explicitly unverified. The candidate has not been installed
or physically retested; the owner was asked to stop the active session first.

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
  warm direct cycle, not the required 10/10 repetitions.
- Longer recorded touch, Back, Home, Recents, rotation, split-screen, and rapid-launch
  checks; all required target/entry-path coverage.
- Repeated post-update direct unlocked-Home starts/stops and navigation,
  cold-process and remaining negative direct-entry cases; lifecycle/reboot/battery/Doze/overnight,
  privacy/false-positive, escape, policy, and Galaxy A17 evidence.

All 38 matrix cells remain `BLOCKED`. This run establishes meaningful progress on the
reported flicker defect and direct NFC navigation without changing the Gate 0 or
production-implementation hold.

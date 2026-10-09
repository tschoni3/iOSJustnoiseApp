# Optional Android session notification — 2026-09-06

Status: the ordinary notification passed A16 permission, panel timer and tap-to-open
checks, followed by passive stopped-state/cancellation evidence. The StandardStyle
**debug-only Live Update experiment** produced an actual system promoted-ongoing
flag: **API promotion passed for that sample**. The owner's change from lock-screen
Icons to Cards made the ordinary session card/timer visible, but no Now Bar or
status-bar timer chip was observed. That sample is now stopped at revision 48,
with notification cancellation observed. The **debug-only BigText comparison** at
revision 49 confirmed the actual BigText template and
system promoted flag, but **no Now Bar or Home timer chip was
observed**. That sample subsequently stopped at revision 50 with cancellation
observed. With the same APK and Samsung's temporary developer override enabled,
the new real session at revision **51** displayed the **actual compact and expanded
lock-screen Now Bar, plus the Home status-chip timer**. This is physical rendering
evidence, not an inference from the promoted flag or a mockup. It proves this
override-enabled A16 sample, not default-user compatibility or shipping eligibility.
Release behavior remains ordinary, iOS is unchanged, and no Play GO is claimed.

Current device condition: Samsung **Live-Benachrichtig. für alle Apps** was restored
to its original **OFF** state at about **23:36 local**, visually confirmed. Session
**51** remains active and connected, without a recovery flag. After restoration,
the own notification was absent and its dismissal marker matched the active UUID;
neither Now Bar nor the ordinary session card remained visible. The marker was not
cleared and the notification was not forced back. The phone was returned to Home.

## Behavior and boundary

- Generic **Zap session** / **Session time · Open Justnoise to check blocking**,
  with the system chronometer based on the existing same-process elapsed start.
- Tap opens the launcher Activity only. No notification action stops a session;
  valid Zap handling remains the session authority.
- Settings → Session notification opens Android notification Settings only after
  explicit tap. App permission and channel availability are checked. Refusal does
  not change or prevent a session; there is no automatic permission prompt/grant.
- Projection reconciles committed manual/scheduled changes, service heartbeat,
  recovery/disconnection, foreground return and process startup. Same-state refresh
  does not repost; system UI ticks the timer, with no new timer or foreground service.
- Known recovery/uncertain clock shows **Blocking needs attention**, without a
  running clock. Known stop, withdrawal and missing consent cancel the notification.
- Android can retain an ordinary notification while the app process is dead.
  Reconciliation occurs when the process returns; there is deliberately no liveness
  or uninterrupted-protection claim, timeout or keepalive service.
- Dismissal saves only the matching session UUID in existing private preferences,
  with best-effort persistence/process fallback. It does not change the session
  revision, stop state or History. An old immutable delete PendingIntent cannot
  suppress the next session. Withdrawal clears this presentation marker.
- Main/release manifest boundary adds exactly POST_NOTIFICATIONS and one non-exported,
  filterless dismissal receiver. Promoted-update/foreground-service permissions and
  additional Accessibility privileges remain prohibited. No Internet permission.
- Privacy draft/in-app text and data inventory include this optional system display;
  publisher placeholders remain a distribution blocker, not verified contacts.

## Ordinary notification local verification — before the debug experiment

- Full Android pre-device verifier PASS: **229 JVM tests**, Android debug/release
  builds and lint, development APK/AAB packaging, static policy and **36 Python
  APK/AAB checker tests**. A built development bundle is not a signed production
  candidate or upload approval.
- Product contracts PASS: 18 semantic colors, 27 English entries and 89 portable
  behavior cases. No shared contract or iOS source/project/asset changes.
- **8 Gate 0 emulator cases PASS:** native chronometer/no stop or foreground-service
  flag; attention/hidden forms; UUID-scoped persistence without core mutation;
  private receiver/action filtering; actual notification permission denial without
  mutation; actual OS posting/cache/stop cancellation; withdrawal cancellation;
  real delete-PendingIntent dispatch and stale-session isolation.
- **2 integrated Settings emulator cases PASS:** host-owned On/Off state, explicit
  callback only, 360×640 layout with 200% text and reachable existing escape/settings
  controls. Rendered fixture evidence is separate from physical acceptance.
  The [enlarged-text screenshot](settings-360x640-font200.png) was visually reviewed:
  the full notification row and Done action are readable; the dark unused area is
  the emulator fixture's intentionally bounded 360×640 viewport, not a phone layout.
- Posting tests use an attention projection because no Accessibility grant is made
  by these tests. The timed notification construction/projection is tested, but this
  is not physical active-session/lock-screen rendering evidence.
- Initial app-op simulation did not represent Android 16 notification permission;
  that failed harness was removed. The final denial test uses actual revoked
  POST_NOTIFICATIONS **before** its isolated instrumentation process, with no
  skipped assertion. Other seven cases run separately and grant only in the guarded
  disposable Gate 0 emulator. This proves denied-permission behavior, not a human
  tapping Refuse in a runtime dialog (the product uses explicit system Settings).

## Reproduction and safety

Build using `scripts/verify-android-predevice-readiness.sh`; explicitly rebuild
integrated `assembleDebugAndroidTest` as well. All instrumentation is restricted
to `ranchu`/`goldfish` hardware and the expected disposable package before mutation.
Never run these tests on the owner's physical paired package.

For notification tests, install Gate 0 app/test APKs on the explicit emulator serial.
Revoke `android.permission.POST_NOTIFICATIONS` for `com.justnoise.gate0` while no
instrumentation is running. Invoke only
`SessionNotificationInstrumentationTest#systemNotificationDenialDoesNotPreventOrMutateThePersistedSession`
first, then the other seven methods in a separate invocation. The denial test
intentionally fails if the harness has not established actual permission denial;
do not run it after a granting test in the same process or substitute app-op denial.
Run `IntegratedSessionNotificationSettingsUiInstrumentationTest` on that emulator
with `captureZapEvidence=true` for the enlarged-text screenshot.

At the source-test checkpoint, the physical A16 was read passively and remained in the prior owner-started session
(revision 43). No update, instrumentation, permission grant, cleanup, force-stop,
pairing change or stop injection was performed. The owner was asked to stop normally
before installation. Prior Chrome/navigation/schedule evidence is not repeated or
claimed as evidence for this notification change. Required new A16 check: explicit
notification enablement, one real start/timer/tap-to-open and Zap stop/cancellation;
check Samsung's actual lock-screen presentation without promising a Now Bar.

## Subsequent A16 installation — 2026-09-06, 21:29 local

The owner confirmed **Stopped**. Passive preflight confirmed idle revision **44**,
current v4 disclosure, connected service, paired Zap and two selected apps. A second
idle check guarded `adb install -r`; installation returned Success. The installed
base APK checksum exactly matches the reviewed integrated debug APK below.

After opening Justnoise, revision 44 remained idle, pairing and two selected apps
were retained, service connected was true and no recovery-required flag was present.
No cleanup, uninstall, force-stop, re-pairing, instrumentation or permission grant
was performed. iOS/product diff remains empty. This is the development update,
not a production-signed or Play-delivered installation.

The real History → Settings → Session notification path was used. Its row reports
Off and Android package metadata confirms POST_NOTIFICATIONS is denied. The row
opens Samsung's app-notification Settings for manual user enablement. The settled
app Settings screen has readable dark system/navigation icons; the first screenshot
was captured during the light/dark transition and is not a settled-state defect.
The permission grant and a new real notification start/timer/tap/stop cycle are not
yet claimed as passed. Previously completed Chrome/unblock tests were not repeated.

## Ordinary notification artifact SHA-256 — before the debug experiment

| Artifact | SHA-256 |
| --- | --- |
| Gate 0 debug APK | `1e9e700576fc78b4e024b7f913db0433cf3b028fcd6022339dc9ac43f9909ccf` |
| Gate 0 test APK | `91c222b8a35d8c34512ab94e6b017feea68f0dccbee9dcd15b4373904d8e8482` |
| Integrated debug APK, installed on owner A16 while idle revision 44 | `bbff927301217830c1782eedbf4da6b5295f0f96f4137dd1034133d29aa3ecf8` |
| Integrated test APK, emulator only | `d1e908027388fa426e6169f28d51170d7146dc3a3c4aec5bcaeabc5f44ef980d` |
| Development release AAB, not production-signed/upload-ready | `61238943ff47605778eb0142f072abfa3acfcda2595c035a06403f81b7510e5e` |

## Subsequent A16 active-session checkpoint

The owner reported **started**. The passive, allowlisted state observation showed
revision **45**, `desired_active=true`, `service_connected=true` and no
recovery-required flag. The active session UUID was
`b7628740-aaaf-4920-87ea-94be4ac0c112`, with stored start epoch **1788724167807 ms**
and elapsed-realtime start **514145405 ms**. The integrated MainActivity was the
current focus; this observation does not establish how the owner routed the start.

Android package metadata now reports **POST_NOTIFICATIONS granted=true, USER_SET**.
The app's own NotificationRecord was present with ID **7101**, channel
`zap_session`, LOW importance, ongoing/only-alert-once/silent flags, STATUS category,
PUBLIC visibility and `showChronometer=true`. Its title was **Zap session** and its
body **Session time · Open Justnoise to check blocking**.

This confirms user-set permission, a real active session and the system-posted
notification record. Actual panel/lock-screen appearance, a visibly advancing
timer, tap-to-open and subsequent Zap-stop cancellation were still being checked
at this checkpoint and are **not yet claimed as passed**. No additional NFC routing,
blocking or Play-readiness claim follows from this observation.

## Subsequent A16 panel timer and tap-to-open checkpoint

The real Samsung notification panel displayed **Zap session** with the generic
session-time copy. Its visible chronometer advanced from **02:05** to **02:52**.
After returning to Home, the actual notification body was tapped in the shade.
Focus then became `com.justnoise.integrated.MainActivity`; the app displayed
**Phone Zapped**, timer **00:03:25** and **2 apps**. The own-app screenshot is
[recorded here](a16-notification-opens-active-session.png). Full-panel screenshots
remain in `/private/tmp` only because they include unrelated notifications.

The following passive observation still showed revision **45**, the same session
UUID `b7628740-aaaf-4920-87ea-94be4ac0c112`, `desired_active=true`,
`service_connected=true` and no recovery-required flag. Opening the notification
therefore did not end or replace this session. **Live panel timer and tap-to-open
pass for this session.** This does not certify continuous protection or establish
Samsung lock-screen/Now Bar presentation.

The next physical step is one owner Zap tap from Home to stop the still-active
session. Physical stop and notification cancellation remain **pending**, not
completed evidence at this checkpoint.

## Subsequent passive stop/cancellation observation

The session previously observed at revision 45 was subsequently observed stopped:
revision **46**, `desired_active=false`, `service_connected=true` and no
recovery-required flag. The count of the app's own notification ID **7101** was
**0**. This establishes stopped state and notification cancellation on the A16.
The physical stop gesture was not observed in this turn, so this is **not** a new
directly witnessed Zap-gesture or routing test. Earlier timer/tap evidence remains
valid for the preceding active session; no unrelated phone tests are repeated.

## Owner-approved debug-only Live Update compatibility experiment

The owner supplied the official
[Android Live Update guidance](https://developer.android.com/develop/ui/compose/notifications/live-update)
and approved a bounded compatibility experiment. Whether an open-ended focus
session meets the intended Live Update eligibility remains unresolved. This is
technical compatibility work, not permission to ship promotion or claim Play GO.

The experiment uses the public Android **API 36.1+** request-based compatibility
path through **AndroidX Core 1.18.0**. Each app's `src/debug/AndroidManifest.xml`
adds exactly `android.permission.POST_PROMOTED_NOTIFICATIONS`, with no application,
components or other changes. Main/release permissions remain unchanged; static and
release-package guards continue to reject shipping this permission. Runtime
requests additionally require a debuggable app, the supported OS level, granted
permission and system promotion capability.

Only a valid timed-session projection may request promotion. Attention/recovery
notifications remain ordinary. Failed/ineligible promotion retains the ordinary
notification; no fake category, colorized presentation, new service, action or
session state is added. Existing tap-to-open, dismissal and Zap authority remain.

Request/probe extras and promotable characteristics alone do **not** prove actual
promotion. Evidence must record the system's actual promoted-ongoing flag and the
observed system UI; Samsung Now Bar/status-chip/lock-screen rendering cannot be
inferred from an API return value. At this experiment-definition checkpoint, build,
installation and device-probe outcomes were pending. The earlier passes and artifact hashes
above belong to the ordinary-notification baseline, not this experiment. Release
continues to use the ordinary fallback; iOS is unchanged and no Play approval,
production signing or upload is claimed.

## Debug experiment local verification and idle A16 installation

The subsequent full pre-device verifier passed **232 JVM tests**, with **0 failures,
errors or skips**, plus **36 Python package-checker tests**. Thirteen focused
notification emulator cases passed: **5 new construction cases**, the **1 isolated
denied-permission case**, and the **7 other existing notification cases**. The
emulator runs **API 36.0**: this verifies its ordinary fallback, not promoted Live
Update behavior on compatible hardware.

The release APK boundary passed. The development release bundle's merged manifest
contains no promoted-notification permission; release behavior remains ordinary.
No production signing, Play eligibility or upload approval is inferred.

At **22:13:48 local**, `adb install -r` returned **Success** on the A16 while it was
idle at revision **46**. The installed base APK hash matches the integrated debug
artifact below. The post-install passive state remained idle revision 46,
`service_connected=true`, pairing present, **2 selected apps** and no recovery flag.
`POST_NOTIFICATIONS` remains granted with `USER_SET`; the debug-only normal
`POST_PROMOTED_NOTIFICATIONS` manifest permission is also granted. Neither permission
grant establishes that Android actually promoted a notification.

At this installation checkpoint, a new physical session and its actual promotion
flag/system UI remained pending.
This installation is not a new start/stop or cancellation test and does not replace
the earlier ordinary-notification evidence.

### Current debug-experiment artifact SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Gate 0 debug APK | `336baa634f9e6599321f678a1ce229ecd060ec30fa71b69b91b28b8497d8290d` |
| Gate 0 test APK | `470672d1e676e6b1356c123edab3f7b97576058b46eaa0aa9393063b3c334462` |
| Integrated debug APK, installed on idle A16 revision 46 | `7ace0d62a65e511361d2ae0956a352a096d2a27e5778007058f1b485e47ffee0` |
| Development release AAB, ordinary notification only, not production-signed/upload-ready | `2c9a024c61b994b3c40ce21d88b5c455533def4558a7337f6950bc3beee8de12` |

## A16 live debug-probe result — API promotion observed, enhanced surfaces not observed

The owner confirmed a new start. Passive state showed active revision **47**, UUID
`37d52a24-cbff-4828-aedd-bc6a7aab50a9`, start epoch **1788725771031 ms**, elapsed
start **515748863 ms**, `service_connected=true` and no recovery flag.

The app's own notification **7101** carried the actual system flags
`ONGOING_EVENT | ONLY_ALERT_ONCE | PROMOTED_ONGOING | SILENT`. The debug requested,
promotable and system-allows probes were all true. Its chronometer and Zap session
title were correct. The category remained STATUS, color was 0, and no foreground-
service flag was present. **Actual API-level promotion passes for this sample**;
this conclusion comes from the system flag, not merely the request/probe extras.

Observed Samsung presentation was more limited:

- Home showed the ordinary lightning status icon, with **no timer chip**.
- The real notification panel showed **Zap session** at the top, generic copy and
  a **01:45** timer.
- A settled lock-screen capture showed the clock and normal status icons, but
  **no Now Bar or session card**.
- Read-only app-notification Settings showed notifications allowed, lock-screen
  display eligible and the default always-show-content behavior. No separate
  Live Update option was visible. No setting was toggled for this inspection.

The actual promoted flag therefore does **not** establish a visible Samsung Now
Bar/status-chip surface. These surfaces were not observed with this build and
current settings; this does not prove them impossible on every setting, Samsung
version or device. Screenshots remain in `/private/tmp` only because they include
unrelated notifications. The experiment remains technical compatibility evidence:
open-ended-session eligibility is still unresolved, and release behavior remains
the ordinary notification with no Play GO.

After the checks the phone was returned to Home. The same UUID and revision **47**
remained active, connected and without a recovery flag. The owner was asked for one
Zap tap to stop this session; its stop/notification-cancellation check is **pending**.
No new stop or cancellation is claimed by this active-session result.

## Lock-screen settings result and bounded BigText comparison

The owner changed Samsung's global lock-screen notification presentation from
**Icons (Symbole)** to **Cards (Karten)**. A subsequent locked-screen capture showed
the ordinary **Zap session** card and a **21:47** timer. No compact Now Bar was
visible; the actual promoted-ongoing flag remained set. Cards therefore resolves
the missing ordinary lock-screen card, but does not establish Now Bar rendering.
Samsung's full Now Bar live-notification app list did not include Justnoise in this
sample. The public per-app promotion-settings intent resolved to the general app
notification page, without a separate Live Update switch. No setting was changed
by automation. Private screenshots remain only in `/private/tmp`.

The owner approved one public-template comparison. The next **debug-only**
promotion candidate will use `NotificationCompat.BigTextStyle` with the exact
existing body. The title, chronometer, STATUS category, notification ID and
content/delete intents remain unchanged. Ordinary, attention/recovery, release
and failed/ineligible-promotion notifications retain StandardStyle. A debug-only
template diagnostic will distinguish the returned candidate from the fallback.
This is an intentional Android debug presentation experiment, not a release UI
change or a claim of Samsung compatibility. No fake progress, foreground service,
hidden Samsung API, developer override or new permission is introduced.

At this definition checkpoint, the comparison build, installation and visible
A16 result are pending. Installation must wait for an idle session; the active
revision 47 must not be replaced or ended by automation. Existing StandardStyle
evidence and hashes above remain the baseline, not results for the new template.

## BigText comparison — local verification and baseline cancellation

The full Android pre-device verifier passed: **232 JVM tests** with **0 failures,
errors or skips**, debug/release lint and builds, **36 Python package-checker
tests**, and the release APK boundary. Product contracts also passed: **18 semantic
colors, 27 English copy entries and 89 portable behavior cases**. Packaged APK and
bundle release manifests contain no promoted-notification permission and are not
debuggable. These development release artifacts are not production-signed or
upload-ready.

All **6** focused notification construction tests passed on the disposable API
36.0 emulator. They cover the ordinary fallback, attention, invalid clock mapping,
non-debug exclusion and public BigText construction with the original timer and
intents. API 36.0 does not exercise the guarded 36.1 promotion branch. Prior
unchanged dismissal/stop/permission-lifecycle test evidence is reused rather than
repeating it on the owner's phone. The emulator was closed afterward.

Local logs: `/private/tmp/justnoise-nowbar-bigtext-readiness.log` and
`/private/tmp/justnoise-nowbar-bigtext-construction-tests.txt`. Independent review
found no concrete debug/release/fallback boundary issue. Git comparison with
`11d4d9816809ff99b1d2720c8eff5fe4fa3d9d82` showed no tracked changes outside
Android spikes and documentation; iOS and shared product contracts remain unchanged.

Before installation, a passive A16 read observed **idle revision 48**, connected
service, no recovery flag and **zero** Justnoise notification 7101 records. This
closes the pending StandardStyle sample's cancellation observation; the physical
stop gesture was not observed by automation. An immediate installation guard
confirmed idle state, pairing present and **2 selected apps**.

### BigText comparison artifact SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Gate 0 debug APK | `6d02e8da74f02f98453f5214ec8c58cfd9e358b4b0ef9991b931d6d66ab8d6f5` |
| Gate 0 test APK | `ff30a521fd75622bc4687733287eb6e4d549e7fd6cd1b2c9d89332c228dbb62c` |
| Integrated debug comparison APK | `5ef848daa5c891cdd383d942d8eb6088b0754f57f44057dd5808aa9e9ce16b76` |
| Development release AAB, ordinary notification only | `6fd2e40151374e9cb8a776c797b93cc5c9093c427820f0c045f7974b92cd9f61` |

The integrated debug certificate SHA-256 remains
`b84ff4b2beddd12c299f32f3482cf4b1461223f4697c9af082107173cb3d2a59`.
Installation completion and actual A16 template/promotion/Now Bar presentation
are not established by these local test results.

The subsequent in-place A16 installation returned **Success**. The installed base
APK SHA-256 matches `5ef848daa5c891cdd383d942d8eb6088b0754f57f44057dd5808aa9e9ce16b76`.
Post-install passive state remained **idle revision 48**, service connected, no
recovery flag, pairing present and **2 selected apps**. No permission or Samsung
setting was changed, no data was cleared and no physical instrumentation ran.
The phone was returned to Home and the owner was asked for one physical Zap start.
Actual BigText candidate, promoted flag and visible Now Bar remain **pending**.

## A16 BigText result — correct template and promotion, still no Now Bar

The owner replied **gestartet**. The same installed comparison APK documented above
produced active revision **49**, UUID `cc4153d5-f222-40f9-8412-b0035bcc8e85`, start
epoch **1788728633898 ms** and elapsed start **518611923 ms**. The service was
connected and no recovery flag was present.

Justnoise's own notification **7101** reported the actual system flags
`ONGOING_EVENT | ONLY_ALERT_ONCE | PROMOTED_ONGOING | SILENT`. Its native
`android.template` was `android.app.Notification$BigTextStyle`; the debug template
was `big_text`, with requested/promotable/system-allows probes all true. The real
chronometer was enabled; `android.text` and `android.bigText` both retained the
existing body. Category stayed STATUS, color stayed 0, and no foreground-service
flag appeared. This establishes a returned and posted BigText candidate with
actual API promotion, not merely a build or diagnostic-request success.

Actual visible Samsung results:

- Home showed the ordinary lightning icon, **no status-bar timer chip**.
- The settled lock screen showed the Justnoise card with timer **01:52**, but
  **no compact Now Bar**.
- The notification panel showed the session card at the top with timer **02:25**.
  The time advanced from the lock-screen sample without changing the session UUID.

The public BigText template comparison therefore **did not resolve the missing
Now Bar on this A16/software/settings sample**. It does not prove that every
Samsung device or public implementation is incompatible, identify Samsung's
internal eligibility rule, or justify hidden APIs/fake categories. Release remains
ordinary; this experiment is not a Play eligibility or shipping-promotion decision.
Further template guessing is not justified by these two matching observations.

Private screenshots remain only in `/private/tmp`: `justnoise-bigtext-initial.png`,
`justnoise-bigtext-lockscreen.png`, `justnoise-bigtext-panel.png` and
`justnoise-bigtext-final-home.png`. They are not committed because they include
unrelated device content. No settings, app data or permissions were changed; no
physical instrumentation, NFC injection or stop action was performed. Passive
post-inspection state retained the same active UUID/revision **49**, connected
service and no recovery flag. Its later owner stop/cancellation remains unobserved;
the earlier baseline cancellation evidence is not relabeled as this sample's pass.

## Samsung developer-switch comparison — actual Now Bar observed

The owner explicitly requested the real Samsung Now Bar on the A16 after the
online-research proposal for a reversible developer-switch comparison. The scope
was announced: temporarily change only the Samsung live-notification setting,
compare the existing reviewed APK and restore the original setting afterward.
There is no authorization for hidden Samsung APIs, fake media/navigation/progress,
new session authority, data clearing or unrelated developer-option changes.

At the beginning of this turn, passive state was **idle revision 50**, connected,
without recovery and with **zero** own notification 7101 records. This closes the
previous BigText revision-49 sample's stopped-state/cancellation observation;
automation did not observe or generate the physical stop gesture.

The expected main Developer options Apps section did not show the switch. Normal
Samsung Settings search for **Live** located **Live-Benachrichtig. für alle Apps**
under **Entwickleroptionen → Weitere Einstellungen**. Its visible subtitle was
**Real Time Notification Test.** The settled UI confirmed it was **OFF**, then
confirmed **ON** after the specific switch was tapped at about **23:25 local**.
No other switch was changed; in particular **Disable app compatibility policies**
and screen-sharing protection remained untouched. No settings-provider write,
permission grant, Accessibility instrumentation or app reinstall was used.

The phone was returned to Home. Passive session state still read idle revision
50, connected, with no recovery flag. The owner was asked for one physical Zap
start; this ensures a genuine newly posted notification under the override,
rather than relying on same-projection refresh caching to repost an old session.
The existing APK remains
`5ef848daa5c891cdd383d942d8eb6088b0754f57f44057dd5808aa9e9ce16b76`.

Private UI captures remain in `/private/tmp`, notably
`justnoise-nowbar-settings-live-results-portrait.png`,
`justnoise-nowbar-developer-switch-after.png` (settled OFF) and
`justnoise-nowbar-developer-switch-enabled.png` (ON). A first tap made during the
search-result scrolling transition left the switch OFF; only the subsequent
settled-position tap is the confirmed toggle.

### Physical result under the override

After the physical-start request, passive state advanced from idle revision 50 to
active revision **51**, UUID `6a75509a-a340-4635-84a7-4a36899d767c`, with the service
connected and no recovery flag. No synthetic NFC or session-start command was
sent. An explicit written owner reply was not received; the real state transition
was observed instead. Start timestamps were epoch `1788730002453` and elapsed
`519980139` milliseconds.

Own notification 7101 used native `Notification$BigTextStyle`, category `status`,
`ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING|SILENT`, no custom content view and
the unchanged chronometer. Requested/potential/system-allowed promotion diagnostics
were true. Existing text remained **Zap session** / **Session time · Open Justnoise
to check blocking**. No code, copy, artwork, permission or build change was needed
for this comparison.

Actual system UI, inspected visually:

- Home: blue Samsung status chip with the lightning icon and elapsed **01:51**.
- Lock screen: compact white Now Bar near the bottom, with the Justnoise icon,
  **Zap session** and the beginning of the existing body text.
- A normal tap expanded that capsule into Samsung's white Now Bar card showing
  the app's development label, title and complete existing body text.

The same active revision/UUID and connected state persisted after expansion.
This establishes real Samsung rendering under the override, not merely framework
eligibility. It does not establish ordinary customer settings, universal Samsung
support or approval to ship the debug-only promotion path.

Private screenshots are `/private/tmp/justnoise-nowbar-override-session-home.png`,
`/private/tmp/justnoise-nowbar-override-session-lockscreen.png` and
`/private/tmp/justnoise-nowbar-override-session-expanded.png`. They include unrelated
device content and are intentionally not committed.

### Original switch restored; session retained

Rather than ask the owner to repeat a completed stop check, restoration while the
session remained active was announced and only the same Samsung UI switch was
returned to **OFF**, visually confirmed at about **23:36 local**. Neighboring
developer switches remained unchanged. No setting-provider write, app reinstall,
permission mutation, notification cancellation command or session command was used.

Both the immediate and later passive checks retained active revision **51**, the
same UUID, connected service and no recovery flag. After restoration, Home showed
no Justnoise chip/icon and the lock screen showed neither Justnoise Now Bar nor
ordinary session card. A scoped notification read returned **zero** records for
7101; the app's `dismissed_notification_session_id` equaled the current session UUID.
This is a dismissal outcome observed after the toggle, not proof of the exact OEM
internal callback sequence. The dismissal was respected: no marker clearing or
forced repost. No stop/cancellation pass is claimed for this still-active session.

Restoration evidence is private at
`/private/tmp/justnoise-nowbar-developer-switch-restored-off.png`,
`/private/tmp/justnoise-nowbar-after-restore-home.png` and
`/private/tmp/justnoise-nowbar-after-restore-lockscreen.png`. Final normal-UI return
to Home was captured in `/private/tmp/justnoise-nowbar-comparison-final-home.png`.
The test override is no longer enabled. Default-user Now Bar availability remains
unresolved; this result is not a requirement for users to enable Developer options.

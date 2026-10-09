# A16 four-feature MVP candidate — preflight and next run

Status: **candidate installed and identity verified; physical MVP acceptance pending**.
The historical preflight below preceded the authorized in-place update. On
2026-09-06, the installed base APK was read directly on the connected A16 and its
SHA-256 matched the candidate below. No further install is needed.

## Post-install verification — 2026-09-06

- Device: Samsung `SM-A165F`, connected through wireless ADB.
- Installed package: `com.justnoise.androiddev`; base APK SHA-256
  `ecd76456ef21d010de4e59db8650ab61e8bf3e4b974f8ab387807a565c873edd`.
- Scoped private-state read: pairing digest present (value not printed),
  `desired_active=false`, revision `25`, accepted disclosure still v1,
  `schedule_suspended=true`.
- Android enabled Accessibility services setting: `null`.
- Opened the integrated MainActivity. A non-interfering screenshot showed Chrome
  retained, History/Schedules/Setup entry points, **Consent required**, and
  **Open setup**. Android navigation controls were visible.
- The owner must review the current disclosure and manually enable Accessibility
  before physical blocking/schedule acceptance. No consent or permission was
  granted through ADB. No data was cleared and no session was started.

This proves installation, retained pairing presence and the visible consent gate;
it does not prove multi-app blocking, scheduled delivery or History completion.
The remaining physical flow below is still pending.

### Owner setup completed; alarm access pending — 2026-09-06

A subsequent passive check found v4 disclosure accepted, the Justnoise Accessibility
component enabled, `service_connected=true` and `desired_active=false`. Main showed
**Ready to Zap** with Chrome retained. The Schedules list and one-time editor opened
on the A16; the editor reported the saved selection as one app. The latest screenshot
showed Android's **Wecker und Erinnerungen** page for Justnoise with **Berechtigung
erteilen** off. No alarm grant or successful scheduled start is claimed. The earlier
consent/Accessibility blocker is resolved; exact-alarm setup and the physical flow
remain pending.

### First real scheduled start — 2026-09-06, 09:40 CEST

The owner enabled exact-alarm access (`SCHEDULE_EXACT_ALARM: allow`). Through the
visible editor, saved one enabled one-time schedule, `A16-schedule-check`, for
2026-09-06 09:40 Europe/Berlin, using the retained one-app Chrome selection.
Android `dumpsys alarm` confirmed a package-scoped RTC_WAKEUP alarm with
`origWhen=1788680400000`, zero window and `exactAllowReason=permission`.
Chrome was brought to the foreground at 09:39:02; no synthetic NFC input or alarm
broadcast was sent.

After due time, scoped state showed `desired_active=true`, revision `26`,
`service_connected=true`, schedule ID `1a503c50-bcf7-42ad-95c9-31a35c11500a`, and
session start epoch `1788680400059` (09:40:00.059 CEST). A non-interfering screenshot
showed the Chrome blocker with its app-specific copy and visible Android navigation
buttons. This is one observed scheduled foreground interception on this A16; it
does not prove Doze, reboot, repeated delivery, multi-app or endurance behavior.

The owner was asked to stop this scheduled session with one physical paired-Zap
tap. Stop and exactly-once History completion remain pending at this checkpoint.

### Completed History observed — 2026-09-06, 10:06 CEST

The next scoped read found `desired_active=false`, revision `27`. Main showed
**Enable Accessibility**; Android's enabled-service setting was `null`. History
showed exactly one completed entry: 06.09.2026 09:40, 25m, one app, **Blocking
interrupted**. The aggregate also showed one session and 25m. This establishes the
visible completed History record for the observed scheduled start, with truthful
interruption labeling; it does not establish uninterrupted blocking or a confirmed
physical Zap-stop path. No stop was simulated or permission changed by the agent.

The owner's stop method and any manual disable/Force Stop during the interval are
awaiting clarification. Do not attribute the disabled service to an app defect or
count a clean scheduled-start/Zap-stop acceptance until that is resolved.

Scoped follow-up: `dumpsys activity exit-info com.justnoise.androiddev` reported
the latest process exit at 09:28:33 for **PACKAGE UPDATED**, before this session.
No later crash or Force Stop was present in that returned history. This does not
explain a service-only disable or prove that no unrecorded interruption occurred.
Source inspection found service self-disable only on disclosure withdrawal or
missing/current-version-invalid consent; normal state-change handling hides the
overlay without calling `disableSelf()`. Owner clarification remains necessary.

### Service-disable cause identified — 2026-09-06

The owner reports only tapping the Zap again, without deliberately disabling the
service. Samsung's `dumpsys accessibility` supplies stronger event-level evidence:
service call-stack history entry 18 at **10:05:41** sets the enabled-service value
to `null` from package `android`, through
`performAccessibilityShortcutInternal` → `performAccessibilityShortcutTargetService`
→ `disableAccessibilityServiceLocked`. Shortcut history entry 17 at the same time
targets Justnoise, shortcut type 1. The Justnoise service is assigned to the Android
Accessibility button/gesture. This establishes a system-shortcut disable, not a
normal NFC-stop call to `disableSelf()`; whether the shortcut activation was
accidental is unknown. Do not characterize it as deliberate owner revocation.

Entry 19 records manual Settings re-enable at **10:09:47**. Current service state
is enabled and bound, with an empty crashed-service set. No agent permission toggle
was performed. Keep the service enabled; recommend removing only its optional
Accessibility shortcut to avoid inadvertent toggles. The 25-minute interrupted
History entry remains valid evidence, while a clean scheduled/Zap-stop cycle is
still unverified.

### Shortcut removed and two-app selection saved — 2026-09-06

Through Samsung's visible Justnoise service page, turned off only the optional
shortcut switch. The main service remained enabled; the secure button-target value
was empty and the next app screenshot showed no Accessibility shortcut button.
Main reported **Ready to Zap**, and Setup reported idle enforcement available.

Using the app picker, selected Browser alongside Chrome. The first rapid
checkbox/Save attempt retained only Chrome; a separate row selection, visual
confirmation of both checkmarks, then Save persisted
`com.sec.android.app.sbrowser` and `com.android.chrome`, revision `28`, while idle
and service-connected. No picker defect is established by the first automated
attempt alone. Returned to Home and requested one real paired-Zap start to check
both targets. Physical multi-app enforcement and completion remain pending.

### Two-app active session sampled — 2026-09-06, 10:27–10:29 CEST

Passive state subsequently showed an active session at revision `29`, with the
service connected. Opened Chrome, then Samsung Browser via their launcher intents.
Non-interfering screenshots showed each app's blocker with the correct foreground
app name and visible Android navigation controls. Pressed the visible native Home
control; subsequent state retained revision `29`, `desired_active=true`, two active
targets and a connected service. No recovery-required flag was returned.

This establishes both selected targets' visible interception in one real active
session, not exhaustive bypass/endurance coverage. Requested one physical Zap stop
to verify a new two-app History completion and retained Accessibility; that step
remains pending.

### Two-app completion observed — 2026-09-06, 10:37 CEST

Passive state advanced to revision `30`, `desired_active=false`, with
`service_connected=true`. Main showed **2 apps selected** and **Ready to Zap**.
History showed two total sessions: the earlier 09:40 one-app entry and exactly one
new 10:26 entry, displayed as **6m · 2 apps**, also **Blocking interrupted**. The
aggregate displayed 32m; shared sum-before-truncation means individually truncated
25m and 6m rows need not add to that aggregate. No new source change or reinstall
occurred. Android's latest recorded process exit still predates both sessions.

The two-app start/interception/stop and visible completion are sampled. The new
interruption flag needs investigation despite the service being connected at the
sampled reads. Existing storage retains a sticky Boolean, not a cause/timestamp;
current observations cannot distinguish an earlier stale heartbeat, feedback
interruption, transient disconnect or other conservative recovery path. Do not
erase the flag, widen the heartbeat threshold or assert a clean run without evidence.
The next engineering task is to establish the interruption reason with bounded
diagnostics, avoiding another blind physical test or a claimed NFC-disable defect.

## Actual passive preflight — 2026-09-03

| Observed field | Value |
| --- | --- |
| Device / transport | Samsung Galaxy A16 `SM-A165F`, USB `RFGYC3DYKBR`; awake and powered |
| Existing app | `com.justnoise.androiddev`, older source `909b41b`, version `0.1-integrated-dev` |
| Installed APK SHA-256 | `9fe99b7232a135cd6e6e16d4ad105faa3200cd87e516a5101a08bda60c7c4739`; read directly from the current installed base APK and matched to the prior build record |
| Device-reported last update | `2026-09-02 23:25:50` |
| Saved consent | v1, acceptance epoch `1788291104133` |
| Saved session intent | `desired_active=true`, revision `22`, start epoch `1788384447003` |
| Saved targets / pairing | Legacy Chrome selection; pairing digest **present**, value not recorded |
| Android enabled-service setting | `enabled_accessibility_services=null` |

The saved active intent is **not evidence of current enforcement**: Accessibility
is not listed as enabled. This matches the outstanding manual recovery after the
older [Force Stop check](A16_PARTIAL_RUN_2026-09-02.md#owner-approved-force-stop-and-emulator-withdrawal-regression).
It does not establish how the new candidate migrates or behaves.

## Candidate identity and local verification

Candidate source: the draft-race guard fix committed with this record, on top of `df5dc05`.
The final verifier passed **161 JVM tests** (124 Gate 0, 15 UI, 22 integrated:
18 editor and 4 Zap), with zero failures/errors/skips, plus **13 Python checks**,
lint/builds and the packaged unsigned-release boundary. The four added editor
regressions cover stale reload/save/uncertainty, canceled-draft and identity guards.
[Latest editor XML](emulator-schedule-ui-2026-09-03/editor-draft-race-jvm-results.xml)
is preserved separately from the earlier report; its SHA-256 is
`f4aeee70a1e44516c1cbadfdabb20fbffc15cf5ae3403e8ab5f00d2d45241ff2`.

Recorded command, from the repository root:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/tj/Library/Android/sdk \
bash scripts/verify-android-predevice-readiness.sh
```

Log: `/private/tmp/justnoise-schedule-draft-race-readiness.log`.
The earlier **83 emulator tests and bounded UI smoke** belong to the
[`df5dc05` editor evidence](emulator-schedule-ui-2026-09-03/README.md); they were not
rerun for this guard-only follow-up. No new physical or UI result is claimed.

| Artifact | SHA-256 |
| --- | --- |
| Integrated debug APK to update in place | `ecd76456ef21d010de4e59db8650ab61e8bf3e4b974f8ab387807a565c873edd` |
| Integrated unsigned release APK | `eb8d915c366434b1fb5df6c5b2105cb6ea3f846db8b81e652e0d753c6c939730` |
| Integrated unsigned AAB | `9eca27f3bdf9ff45665bc0f2aa16ec0b6c0fcd9b9ceb7ff3e50df1816f74a0ef` |

These retain development ID `com.justnoise.androiddev`, not a signed Play release.

## Original minimal run plan — see subsequent physical evidence above

1. **Authorized in-place update and migration.** Preserve app data and pairing;
   do not uninstall or clear storage. Check retained Chrome/pairing and truthful
   recovery. Require explicit current v4 consent and manual Accessibility enablement,
   never an ADB grant. If the legacy active intent remains, use the normal Setup
   **Scan**/paired-Zap stop before editing targets. That v1 session must not gain
   backfilled History; only new current-consent starts are eligible.
2. **Saved apps + one new Zap/History cycle.** Save several eligible installed apps,
   use the real paired Zap to start, sample those targets, then stop. Verify exactly
   one new History completion and retained selection/pairing. This validates the new
   multi-app/History candidate; do not ask the already-answered Chrome-unblock question
   again or recast the [earlier acceptance](A16_PARTIAL_RUN_2026-09-02.md#owner-confirmed-stop-and-normal-chrome-access) as a new run.
3. **One scheduled start + History.** Through the real editor, save a near-future
   one-time start and explicitly grant alarm access in Android Settings. Leave a
   selected target already open when due. Observe automatic start/interception,
   stop with the paired Zap, and verify one further completion with its own identity.
   A stored outcome or alarm callback alone is not proof of blocking.
4. **Essential escape/recovery during those flows.** Check native Home/Back, Keep
   going, Settings and phone/emergency routes remain reachable without ending a
   session unintentionally. Observe truthful unavailable/recovery state if capability
   is lost. Any deliberate disable/Force Stop needs its own authorization; do not
   uninstall, erase pairing or place an emergency call for this run.

Use screenshots/recording and scoped passive state only; no `uiautomator dump` or
instrumentation on the paired phone. Follow the [non-interfering protocol](../README.md#do-not-let-inspection-interrupt-the-blocker).
Record actual outcomes against this APK; stop on a safety defect rather than expand
into repeated trials. No A17, broad repetition campaign or full matrix is added here.

This plan is not acceptance, a comprehensive Gate 0 PASS or an internal-beta GO.
The [decision record](DECISION_RECORD.md) and [gate requirements](../../../docs/ANDROID_GATE_0.md)
remain unchanged; signing, account/policy requirements and Play upload authorization
are separate from this bounded new-candidate check.

## September 6 — readiness clock-order correction (local verification only)

Readiness checks in `Gate0Controller.truth` and `handleZap` now sample the default
elapsed-realtime clock after obtaining the stored session snapshot. Previously a
heartbeat committed while the snapshot waited could appear newer than the sampled
clock, producing a false unavailable result and a sticky interrupted-History flag.
Explicit test timestamps and the eight-second heartbeat threshold are unchanged.
This is a code-confirmed race, not proof of the cause of the A16's latest warning;
existing History is not rewritten.

`scripts/verify-android-predevice-readiness.sh` completed successfully after the
change: Android unit-test/build/lint checks, 13 Python release-checker tests, shared
contract validation and release-APK boundary verification passed. Output is recorded
locally at `/private/tmp/justnoise-clock-order-verification.log`. No emulator or
physical tests were run for this correction, and the rebuilt APK has not been
installed on the paired phone. The preceding device evidence still belongs to the
installed `ecd76456…` candidate, not this new build. Production remains HOLD.

### Subsequent in-place installation

The clock-order build was then installed successfully with `adb install -r` on the
connected SM-A165F while idle. APK SHA-256:
`4fee383141d2977ec9f693b9d4d3ec27a32e43e688723f2713a682f3ddd166d3`.
Post-update state retained revision 30, two selected apps, inactive session and
connected service. The explicit integrated launcher opened successfully and the
screen displayed **2 apps selected**, **Ready to Zap**, and visible Android
navigation controls. An initial implicit MAIN/LAUNCHER command did not resolve;
the explicit component did. No uninstall, data clear, permission grant or synthetic
NFC was performed. No new physical Zap cycle or resolved History-warning claim is
made for this build. Local screenshot: `/private/tmp/justnoise-clock-fix-installed.png`.

## Next bounded acceptance run — current UI candidate, 2026-09-06

The current tested development APK is
`spikes/android-integrated/app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`3d9ff301d41895509387c4f6758c7b53004e2ec538e13e3d19e6d0681c8fc85b`.
Its [Settings/completion evidence](../../android-integrated/evidence/settings-completion-2026-09-06/README.md)
records the local checks. No physical install or acceptance of this candidate is
claimed: ADB device listing and wireless discovery were both empty at this check.
The owner was asked only to reconnect/unlock the phone, not to repeat a Zap test.

Before an in-place update, confirm the connected device is the existing A16 and
observe only scoped installed-build/session/recovery/service state. Update while
idle; do not interrupt an active session, erase data, re-pair the Zap, grant a
permission through ADB or run instrumentation/UI-tree dumping on the owner app.
Preserve pairing, selected apps, schedules and History. Compare their counts/state
without printing private payloads, digests or unrelated device content.

Only the remaining distinct checks are planned:

1. Check the new real screens and a bounded current-build completion: preserved
   selection, Zap, schedules, History and Settings; native navigation and visible
   warning metadata. Reuse the prior two-app interception and owner stop/unblock
   evidence rather than requesting the same confirmation again.
2. Observe a physical invalid/unauthorized-tag sample and rapid-repeat sample;
   synthetic no-live-tag tests are not substituted. Never rewrite or replace the
   owner's paired Zap to manufacture a rejected input.
3. Check the dedicated emergency route remains reachable while active, without
   entering a number or making a call. Ordinary dialer/Home/Back/Settings access,
   Keep going, revocation/recovery and Force Stop already have scoped evidence.
4. Record one clean scheduled start → paired-Zap stop → one History entry on the
   current candidate. Existing v4 consent, alarm access and on-time delivery stand;
   no fresh setup, long endurance or full-matrix campaign is added here.
5. Obtain physical withdrawal/reset/fresh-consent evidence only on a separately
   authorized disposable/release installation. Existing development data must not
   be deleted; emulator cleanup coverage alone is not physical completion.
6. Reconcile current package/disclosure/data review and owner acceptance, then
   record the separate internal-beta decision. Signed release/Play-delivered smoke
   checks and actual upload authorization remain separate.

The old second interruption's exact cause cannot be recovered from a sticky Boolean.
A clean current-build cycle can establish present acceptance without claiming to
explain that historical row. Keep its warning; investigate only if a new warning
recurs. No currently reproduced unfixed safety defect is established in this audit.
Missing evidence is not silently converted to a pass.

No A17, second phone, ten-of-ten sets, overnight/Doze or full 19-cell approval is
added to this smaller internal-beta milestone. The decision remains HOLD until
the scoped evidence above supports GO.

### Reconnected phone: current UI installed in place

The owner reconnected the A16 on 2026-09-06. The tested `3d9ff301…` candidate was
installed successfully while idle, preserving pairing, two selected apps, the
one saved schedule and History. A pre-existing pending 7,464 ms completion was
delivered exactly once by ordinary History loading; the two older interrupted
records were unchanged. This is not a new physical cycle on the updated build.

[Current A16 installation and UI evidence](../../android-integrated/evidence/a16-current-ui-2026-09-06/README.md)
records the screenshots, preservation checks and one newly observed visual issue:
dark status-bar icons over the selection sheet's dark background scrim. Native
navigation and sheet controls remain visible. The affected fix and remaining
bounded physical evidence are still pending; internal-beta status remains HOLD.

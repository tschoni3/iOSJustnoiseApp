# Android Gate 0 decision record

Decision: **HOLD / NO-GO for the full Android port**

Internal beta decision: **HOLD — smaller A16 safety checkpoint incomplete**

September 8 build-only authorization: the owner explicitly approved "jetzt bauen"
after being asked to permit local production packaging/signing while installation
and distribution stay blocked. Local build decision: **GO for packaging only**.
This supersedes historical production-directory prohibitions, not either safety
HOLD above. Negative-input/repeat limitations remain unverified. No owner phone
operation or store action is authorized by this exception.

September 8 update: the authorized isolated cleanup exercise is finished. Actual
withdrawal cleared the disposable pairing, consent, modes, schedule and History;
its service disabled itself. Uninstall/reinstall showed fresh onboarding and
unchecked consent. The owner confirmed re-pairing during the inspection, explaining
the new pairing record. The test copy was removed; regular pairing, **power / 1
app**, idle revision 68 and manually restored connected service were verified.
See [the scoped evidence](../../android-integrated/evidence/a16-cleanup-2026-09-08/README.md)
for the unarmed-alarm and pre-pairing-observation limits. Do not repeat this cleanup
exercise or ordinary Chrome/scan flows merely because older paragraphs say pending.

The stale Schedule saved banner found after withdrawal is corrected locally in
the schedule-state reload paths. Three new regressions preserve error/uncertain
save messages on failed reads and recovery notices with accepted consent. Full
local verification passed: 292 JVM tests, 36 package-checker tests, debug/release
lint and build, and product contracts. This presentation-only fix is not installed
on the owner phone and does not change NFC, storage, alarms or restrictions.

Remaining internal-beta decision work: the scoped negative-input/rapid-repeat
evidence gaps and final reviewed candidate boundary. Only the existing paired Zap
is available; do not request another phone/tag again or rewrite it. A public
foreign-tag sample cannot be credited from parser tests. Final production
identity/signing, release smoke, privacy publication, declaration/video/reviewer
access and live Console eligibility remain separate distribution steps.

Subsequent September 7 named-mode acceptance: the tested modes APK
`9d5efacf4eb4a06aca96374253c374078ef045b26ef5f70fc4cc29f889c0a36c`
was installed on the A16 without clearing data. The owner created **power** and
confirmed that both scheduled and manual Zap sessions work. This closes the
owner-reported ordinary feature-flow check; it is not evidence of the remaining
negative-input, cleanup/reinstall or signed-release checks. The owner has now
requested the Play build. Release preparation may continue, but no GO or signed
production artifact is claimed. See [the scoped modes evidence](../../../docs/ANDROID_NAMED_MODES.md)
and [release preflight](../../../docs/ANDROID_PLAY_INTERNAL_UPLOAD.md).

Latest owner-assisted follow-up, 2026-09-07: the owner reports that the rapid second
paired-Zap presentation was ignored. Two passive observations confirm idle revision
58, connected service and no recovery flag. The earlier baseline was active revision
55; the intervening three increments are not independently timed or attributed.
This is bounded owner-reported success, not a proven defect or an isolated
one-transition/timed rapid-repeat PASS. Do not blindly repeat completed checks;
close the observation gap during the next necessary NFC workflow. Only the paired
Zap is available, so real foreign-tag rejection remains unverified. No pairing,
History, consent or phone setting was changed. See the
[current upload handoff](../../../docs/ANDROID_PLAY_INTERNAL_UPLOAD.md#subsequent-owner-reported-rapid-repeat-result--2026-09-07).

Latest owner scope clarification, 2026-09-02: the MVP requires multiple-app
selection, Zap sessions, working schedules and real History. This supersedes the
earlier single-app MVP proposal below. A single-app foundation remains useful for
safety work but is not completion of the requested MVP. See the current delivery
order in `docs/ANDROID_PREDEVICE_READINESS.md`; no new device purchase or account/cloud
feature is implied.

Latest follow-up, 2026-09-06: **clean ordinary scheduled cycle passed**. A separate
20:20 alarm started while Chrome was open, applied two saved targets, and paired-Zap
stop recorded its UUID exactly once: 131,764 ms, no interruption/timing warning.
History now has six rows and the same two old warnings. The subsequently installed
contrast-only APK `e217eb1abbbe93e75a5f1919622750b967ba2e518d5acd9367fb7aeaa586166e`
fixes selection/completion modal status-icon styling; A16 selection contrast is
physically confirmed. Pairing, both disabled test schedules, selection and all rows
survived the idle-only update. NFC/restriction/schedule behavior did not change.
See [schedule/contrast evidence](../../android-integrated/evidence/schedule-modal-contrast-2026-09-06/README.md).

The remaining smaller-checkpoint gaps are physical rejected/rapid-repeat input,
separately authorized cleanup/fresh consent and final review. Neither the manual
nor the scheduled clean-History sample needs blind repetition. Both decisions
remain HOLD pending these distinct requirements and the later release boundary.

Subsequent held-Zap sample on that unchanged build: the owner reports that keeping
the paired Zap against the phone while starting leaves the session on. Passive
state moved once from idle revision 42 to active revision 43, with connected service
and no interruption flag. This bounded held-presentation result is recorded in
[the follow-up evidence](../../android-integrated/evidence/schedule-modal-contrast-2026-09-06/README.md#subsequent-held-zap-sample).
It is not the still-missing rapid remove/re-present or physical rejection sample;
the historical eight-second event's exact cause remains unknown. No new permission,
runtime change, cleanup, internal-beta GO or release installation occurred.

Preceding current-candidate checkpoint, 2026-09-06: APK
`3d9ff301d41895509387c4f6758c7b53004e2ec538e13e3d19e6d0681c8fc85b`
was installed in place while idle. Pairing, two selected apps, one saved schedule
and prior History were preserved. A real owner-assisted session was observed
active, then stopped at revision 36 and saved exactly once: 210,307 ms, two targets,
no interruption or timing uncertainty. History contains five completions and the
same two old warnings. Current active/idle, selection, History/day, Settings and
schedule surfaces were observed. Samsung's empty dedicated emergency keypad was
unobstructed while active and exited with native Back/Home; no digits or call.
See [current physical UI/session evidence](../../android-integrated/evidence/a16-current-ui-2026-09-06/README.md).

This closes the current-build clean completion gap, not the historical warning's
unknown cause. Physical rejected/rapid-repeat input (including unresolved owner
clarification about an earlier eight-second start/stop), separately authorized
cleanup/fresh-consent evidence and final review remain open. The subsequent clean
scheduled cycle and modal contrast correction are above. Both decisions remain
HOLD; no signed release or upload is implied.

Preceding installed checkpoint, 2026-09-06: verified clock-order correction APK
`4fee383141d2977ec9f693b9d4d3ec27a32e43e688723f2713a682f3ddd166d3`
was installed in place while idle. Two selected apps, revision 30 and connected
Accessibility were retained; Main showed **Ready to Zap**. The preceding candidate
sampled both Chrome and Samsung Browser interception, then an idle state with
Accessibility still connected and exactly one new two-app History entry. That entry
also carried **Blocking interrupted**. A real readiness clock-order race was fixed
and local verification passed, but it is not proven to have caused that warning;
no physical cycle was claimed at that earlier checkpoint. The later current-build
clean completion is recorded above; it does not prove the old warning's cause.
See [the current build-scoped evidence](A16_MVP_CANDIDATE_2026-09-03.md).

Earlier diagnostic result, 2026-09-06: the owner reports only tapping the Zap.
Samsung service call-stack and shortcut histories identify a system Accessibility
shortcut disable at 10:05:41, followed by Settings re-enable at 10:09:47. The service
is now enabled/bound with no crashed-service entry. The interruption is explained
by that shortcut event; intentional versus accidental activation is unknown.
This is not evidence that NFC stop disables the service. See the detailed candidate
record below; the later clean scheduled cycle is recorded above, without changing
that historical interruption's attribution.

Preceding physical checkpoint, 2026-09-06: after owner setup, one real 09:40 exact
alarm started a session while Chrome was already open and the Chrome blocker was
observed. By 10:06, the session was inactive and History showed one 25-minute entry
marked **Blocking interrupted**. Accessibility was off. The stop method and cause
of service loss were clarified by the later shortcut diagnostic above; this is not clean-cycle acceptance or
a change to either decision above. See the candidate record linked below.

Preceding installation checkpoint, 2026-09-06: the four-feature development candidate
is installed on the A16. Its installed APK hash matches
`ecd76456ef21d010de4e59db8650ab61e8bf3e4b974f8ab387807a565c873edd`.
Pairing is present, Chrome remains displayed, session intent is inactive at revision
25, and the main screen truthfully shows **Consent required**. Consent is still v1,
Accessibility is off, and scheduling is suspended. Current disclosure/manual setup
and physical multi-app/schedule/History acceptance remain pending. See
[the candidate record](A16_MVP_CANDIDATE_2026-09-03.md). This does not change either
decision above or establish Play readiness.

Historical safety follow-up: an owner-approved App-info Force Stop terminated the app,
removed the blocker and remained stopped after opening Chrome. Explicit Justnoise
reopen truthfully showed recovery; pairing/selection and session intent `22` survived.
Samsung set Accessibility off; the phone is on that switch awaiting owner re-enable.
The actual Withdraw-button success/reset regression passed with synthetic state on
an emulator, not the paired phone. No new complete gate or MVP feature is claimed.

Preceding recovery run: on installed `909b41b`, the owner-authorized system-UI
Accessibility disable retained pairing, Chrome selection and session intent/revision
`22`. Setup and main truthfully reported recovery; opening Chrome did not restore a
stale overlay. After the owner's manual re-enable, service heartbeat resumed, main
showed active protection and Chrome was blocked again. Native Home remained usable.
This recovery smoke passed without clearing data or another Zap scan. It is not
cleanup/reinstall or a complete Gate 0 PASS. The separate Force Stop result is above.
The earlier Keep going reset
is fixed and physically retested; do not credit that earlier unintended end as a
Zap stop. At that historical checkpoint, dedicated emergency access, physical
negative input and cleanup remained open; the later emergency-surface observation
is above. Prior stop/unblock results stand. See the September 2 record. The latest
Play Console status is in `docs/ANDROID_PLAY_INTERNAL_UPLOAD.md`; no upload occurred.

Date opened: 2026-07-22

Last updated: 2026-09-08

The repository now contains a reviewable candidate and CI design, but the complete required consumer-device and policy evidence does not exist. All 19 required A16 cells remain `BLOCKED`, which is a gate failure. No production `android-app/` should be created from this spike yet.

Owner scope decision, 2026-09-02: use the existing A16 as the only required initial hardware target. A17 validation is deferred/unverified and does not block development or this scoped gate; no additional phone purchase or tester is required. All 19 A16 criteria and product/policy review remain intact. No device result or approval was granted by narrowing the scope.

Subsequent lean-delivery instruction, 2026-09-02: first target a local-only, single-selected-app Play internal beta. The smaller safety checkpoint in `docs/ANDROID_GATE_0.md` may authorize that implementation separately, without waiting for the full endurance matrix or pretending it passed. The owner has confirmed normal Chrome access after stopping; that confirmation is complete and must not be requested again. The cleanup-feedback fix `dbe2469` and later `909b41b` restoration fix were installed while idle, with pairing record and selection retained. Remaining safety evidence still does not complete the smaller checkpoint. No internal-beta GO, upload approval, or public-release approval has been granted.

The September 2 A16 follow-up records one foreground cycle and one warm direct
Home start/stop cycle. A further direct-start/Chrome-blocking check exposed missing
normal Android navigation controls on `d93ae01`. The window-bounds candidate
`d09833d` passed the local verifier and was tested on the phone. A portrait retest
confirms visible navigation controls and corrected window bounds; later coordinate
touches of native Home/Back confirm limited touch routing. Remaining safety coverage
is still incomplete. The app's own Go Home button is not a substitute for system
navigation. That September 2 installed follow-up was `909b41b`, not a new full safety PASS.
See [the build-scoped evidence](A16_PARTIAL_RUN_2026-09-02.md). No safety PASS is implied.

## Pre-device evidence now recorded

At repository commit `4bb4631148a1c5fdab0455c4e09b247c3ca74428`, product-contract and static-policy verification passed; the local prototype passed 23 JVM tests, lint and APK assembly; five hardware-independent instrumentation tests passed on an API 36 Google Play emulator; and the APK cold-launched. GitHub Android Gate 0 run [`31009963974`](https://github.com/tschoni3/iOSJustnoiseApp/actions/runs/31009963974) also passed on the exact commit.

This evidence changes no device-matrix cell. It validates only the build, declared static boundary, portable fixtures, local state/Keystore behavior, synthetic NDEF parsing, and basic launchability.

## What the green prototype evidence proves

- The disposable project compiles with its pinned toolchain.
- Portable reducer fixtures pass in Kotlin.
- Static source/manifest claims match the minimal declared boundary.
- Lint and instrumentation-test APK compilation pass.
- The five hardware-independent instrumentation tests execute successfully on an API 36 emulator.

## What it cannot prove

- Interception happens before target content is usable.
- Navigation, split-screen, alternate entry, or OEM behavior cannot bypass it.
- No persistent overlay or lockout can occur under real lifecycle stress.
- Default-battery and overnight durability pass on the exact required A16 variant/build. A17 compatibility remains unverified and deferred.
- Real authorized/unauthorized NFC behavior is reliable.
- An unlocked direct Zap tap routes to Justnoise and toggles once without app-open, Scan-button, chooser, or confirmation friction.
- Google Play accepts the declared Accessibility use.
- Product accepts the onboarding, battery, OEM, and escape-path tradeoffs.

## Decision owner checklist

Only change this record to `PASS` when every `G0-01` through `G0-19` cell is `PASS` on the required A16 variant/build, all evidence is attached, and product plus policy reviewers sign off. Any required A16 `FAIL` or `BLOCKED` remains `NO-GO`; policy uncertainty remains `HOLD`. Optional A17 results do not enter this gate's arithmetic, and an A16 PASS must not be presented as tested A17 or universal Android compatibility.

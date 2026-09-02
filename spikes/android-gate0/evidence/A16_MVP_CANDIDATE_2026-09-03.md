# A16 four-feature MVP candidate — preflight and next run

Status: **passive preflight only; candidate installation/acceptance pending**.
The update request has already been sent to the owner and remains unanswered at
this checkpoint. No phone installation, app launch, setting, session or data was
changed in this turn. Do not repeat the authorization question or install before
the response is received.

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

## Minimal next run — all pending

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

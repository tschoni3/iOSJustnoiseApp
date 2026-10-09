# A16 scheduled cycle and modal contrast — 2026-09-06

## Results and boundary

- **PASS, bounded physical workflow:** real automatic scheduled start while Chrome
  was open → paired-Zap stop → one clean History completion on the existing A16.
- **PASS, affected A16 UI:** selection-sheet status icons are readable again;
  bottom navigation and Cancel remain visible and functional after the in-place fix.
- **PASS, local checks:** 217 JVM tests (147 capability, 15 prototype, 55 integrated),
  lint/debug/release builds, 17 package-checker tests and development-package boundary.
- **PASS, focused emulator checks:** two actual modal-window tests; the expanded
  selection/completion Activity regression run passed all 11 tests.
- **Still HOLD for internal beta:** physical rejected/rapid-repeat NFC acceptance,
  separately authorized cleanup/fresh consent, final review and release dependencies
  remain. This is not signed-release, Play approval or full endurance evidence.

No iOS/shared-contract change, permission change, owner-app instrumentation, NFC
injection, data wipe, re-pairing or commit/upload occurred. Only the emulator ran
instrumentation. All phone observations used scoped passive state and screenshots.

## Real schedule → Zap → History

The owner continued the plan. Starting from idle revision 36, two selected apps,
current consent and connected Accessibility, a **separate** one-time schedule was
created through the normal Compose UI:

- Name `A16-current-schedule`; ID `e00202f1-bdda-4cc5-a6fd-81136758c3df`, revision 1.
- September 6, 2026, **20:20 Europe/Berlin**; fixed epoch `1788718800000`.
- The original `A16-schedule-check` ID `1a503c50-bcf7-42ad-95c9-31a35c11500a`
  retained its name, revision 1, disabled state, timing and prior execution record.
- Saved selection was used; no selected package, time-zone, device clock or
  alarm/service permission was altered. A blank Chrome page was opened before due
  time; the scoped foreground observation at 20:19:31 confirmed Chrome.

The alarm's durable STARTED receipt and session start were recorded at
`1788718800058` (**58 ms after due time in this sample**), revision 37, with active
schedule ID above and session UUID `74fba178-c0bf-468f-8c89-81f52ab7b9d6`.
Both active targets were present. At the 20:20:30 observation, heartbeat age was
approximately 0.81 seconds and no interruption/recovery flag was recorded.
[Chrome's blocker](justnoise-a16-schedule-start.png) was visible, with normal Android
navigation. No NFC scan or programmatic start was used to manufacture the alarm.

The owner was asked to tap Zap once to stop. A later passive snapshot recorded
idle revision 38 and the exact same UUID's completion:

- End epoch `1788718932158`; stored duration **131,764 ms** (about 2m 12s).
- Two targets; `interrupted=false`; wall/monotonic difference **336 ms**, below
  the existing independent 1,000 ms uncertainty threshold.
- Normal History loading delivered the UUID exactly once and cleared the outbox.
- Totals increased from five rows / 2,165,580 ms to **six rows / 2,297,344 ms**.
  Interrupted rows stayed at **two**. [The real History screen](justnoise-a16-schedule-history.png)
  showed **6 Zaps / 38m**, preserving the old warnings.
- The new one-time schedule correctly became disabled after its resolved start.
  It remains in the schedule list as a completed test; no repeating or future alarm
  was left enabled. The original schedule was not deleted or edited.

This workflow used installed APK `3d9ff301…` below. The subsequent contrast-only
patch does not touch alarms, NFC, session state or storage, so this result is reused
for that unchanged runtime. It does not cover Doze/reboot/overnight, every alternate
entry, or the unresolved physical rejected/held/rapid-repeat input cases.

## Modal-only fix and verification

`IntegratedSelectionSurface.kt` and `IntegratedCompletionSurface.kt` now use the
pinned Material3 API's separate `ModalBottomSheetProperties`: light status icons
above dark idle Zap (`isAppearanceLightStatusBars=false`) and dark navigation icons
below the white sheet (`isAppearanceLightNavigationBars=true`). Selection is
idle-only and closes if a session starts; completion follows committed stop. The
selection saving/back-dismiss guard is preserved. No dependency or module added.

`IntegratedModalSystemBarInstrumentationTest` checks the **actual modal window**
via `DialogWindowProvider`/`WindowInspector`, not only Activity flags. It verifies
independent status/navigation appearance, no host-window mutation and restoration
after Cancel/Done. It guards emulator hardware and the disposable package before
launching fixtures; it writes no synthetic session records.

- `:app:compileDebugAndroidTestKotlin` and explicit `:app:assembleDebugAndroidTest`
  passed. The integrated test APK was explicitly rebuilt to include the new class.
- [Full local verifier](justnoise-modal-contrast-verifier.log): PASS, exit 0.
- [Initial focused window run](justnoise-modal-system-bars-tests.log): **2 PASS**.
- [Window + selection/completion Activity regressions](justnoise-modal-contrast-regression-suite.log):
  **11 PASS** (2 modal + 4 selection + 5 completion), no failures.

The first emulator screenshot attempt was obscured by a **Digital Wellbeing**
system-app ANR. Those images are not accepted visual evidence. Only that unrelated
emulator app was force-stopped; no phone service/app setting changed. A final
[two-test rerun](justnoise-modal-clean-captures-tests.log) passed and produced an
unobstructed [completion fixture capture](justnoise-modal-completion-clean.png).
The selection capture caught the idle screen rather than the sheet and is excluded
as visual evidence; the actual modal-window assertions and physical A16 selection
capture below provide that scoped verification. These are repeated executions of
the same two cases, not extra unique test coverage.

### Physical in-place contrast verification

Before updating: idle revision 38, connected service, current consent, paired,
two selected apps, both one-time schedules disabled, no pending History receipt.
`adb install -r` returned Success and the installed APK hash matched the new
`e217eb1a…` artifact. The redacted pairing/selection/schedule-content fingerprint
matched before/after. Read-only SQLite comparison retained all six complete rows
unchanged (`prior EXCEPT current` count zero), totals and the two old warnings.

The real A16 selection sheet then showed **white readable clock/status icons over
the dark upper background**, with dark bottom navigation over the white sheet.
Cancel returned without saving. Screenshot is kept at
`/private/tmp/justnoise-a16-selection-contrast-fixed.png`, not committed here because
it includes the owner's broader installed-app list. The phone was left idle on
Home. Completion's physical >=20-minute foreground presentation remains separate;
no additional long phone session was manufactured for a system-bar-only change.

## Subsequent held-Zap sample

The owner continued with a distinct held-tag check on the same installed build.
The new baseline was idle revision **42**, not the earlier 38; intervening owner
use is outside this sample. Pairing, two selected apps, current v4 consent and
connected service were present. No phone state was written by the test tools.

The owner was asked to hold the paired Zap continuously for approximately ten
seconds while starting, then remove it. The owner reported that the session stayed
on without changing status while the Zap was held. A passive observation confirmed
exactly one revision increase to **43**, desired active, connected service and
session UUID `0012624e-8afe-4a0f-ad40-b6c4686f4782`, start epoch
`1788720555391`. About 100 seconds had elapsed at that observation; no interruption
flag was stored. This is a **bounded owner-assisted held-presentation pass**.

RF contact duration was not independently timed or recorded, and this sample does
not establish a specific foreground/Home dispatch route. It neither reconstructs
the earlier eight-second session nor proves all repeated inputs safe. A deliberate
remove/re-present within the three-second guard and physical non-paired rejection
are still open. Further phone testing paused for the owner's Live Updates question;
the owner was told they could end this session normally with Zap. No stop or new
History completion is inferred from that instruction.

## Artifact identities

| Artifact | SHA-256 |
| --- | --- |
| Physical scheduled-cycle APK before contrast-only update | `3d9ff301d41895509387c4f6758c7b53004e2ec538e13e3d19e6d0681c8fc85b` |
| New debug APK, installed and hash-verified on A16 | `e217eb1abbbe93e75a5f1919622750b967ba2e518d5acd9367fb7aeaa586166e` |
| New integrated test APK, emulator only | `1e77a588a42ab22604744ab3f06ccf3d0142ded882947e59971c9eddaf25adc9` |
| Unsigned development release APK | `cfcbd9c991e5f6ba5e2c47e74a6d86d343a4ce809983d95e74dae3d92d37c4dc` |
| Unsigned development AAB — **do not upload** | `2c1fd33d22461168022260f382388ebf2ddc28650228b68da5165e0690c10f71` |

All artifacts still use `com.justnoise.androiddev`. Production identity/signing,
release-device checks, final privacy/contact/reviewer materials and live Console
eligibility remain separate. No new internal-beta GO is implied.

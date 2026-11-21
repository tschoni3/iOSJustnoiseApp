# A16 passive follow-up — October 10, 2026, approximately 18:48 Europe/Berlin

The owner approved USB debugging. Device SM-A165F, Android 16, is connected.
Only passive package/service inspection, a bounded system-log filter and navigation
to the existing Justnoise Play listing were performed. No update was installed,
session started, permission changed, data cleared or instrumentation run.

## Verified facts

- Installed production package `shop.justnoise.app` is **1.0.3 (4)**, installed by
  `com.android.vending`, last updated October 10 at 15:04:01 device time.
- This corrects the earlier assumption that the owner's sheet/copy confirmation
  referred to the newly published 1.0.4 (5). The reported improvement is valid as
  an owner observation, but not release-specific 1.0.4 acceptance yet.
- The production Accessibility service is enabled and bound. Binding-services
  and crashed-services sets are empty at inspection time.
- Scoped app service dump reports schedule readiness `NOT_ATTEMPTED`; this is not
  proof of historical schedule success or failure, nor a session heartbeat check.
- The old `com.justnoise.androiddev` package is installed but disabled (enabled=3).
  Only the production service is enabled/bound. Its stale shortcut target does not
  establish that a competing development service is running.
- The bounded retained system-log filter returned no matching Justnoise service
  crash/disconnect record. This cannot rule out older events outside retained logs.
- The Play listing visibly offers **Aktualisieren** and displays the 1.0.4 release
  notes. Screenshot: `/private/tmp/justnoise-play-update-20261010.png`.

## Boundaries and next action

Current service connectivity is healthy at this sample. The photographed prior
interruption is not reproduced or historically explained; no timeout, recovery
flag or History warning was cleared to manufacture success.

Have the owner tap Aktualisieren on the already-open Play listing, then verify
version code 5 before collecting release-specific evidence. Do not repeat accepted
NFC/Chrome/Now Bar/PostHog tests. Fresh first-run still requires an isolated approved
installation/profile rather than resetting the owner's paired Play app.

## Follow-up at approximately 19:01

- Package inspection still reports 1.0.3 (4), not the internal-track 1.0.4 (5).
- A subsequent scoped service dump timed out, while the system still reported the
  production Accessibility service as enabled/bound. A dump timeout alone is not
  evidence of an app crash or the cause of the earlier interruption.
- Opening the existing main activity and capturing pixels showed the old
  `Session needs attention / Check setup` surface with a `Scan Zap to stop` action.
  Screenshot: `/private/tmp/justnoise-preupdate-state-20261010.png`.
- This is an unresolved requested-session/recovery presentation, not a verified
  idle state. Do not update across it or clear recovery state to hide the issue.
  Ask the owner to stop the existing session with the paired Zap before updating.
- No session, permissions, pairing, or data were changed by this inspection.

## Play update completed at 19:05–19:06

- The owner stopped the existing session with Zap. The pre-update screenshot
  showed normal idle Home with the existing `focus` selection; no recovery hero.
- The already-authorized update was initiated through the visible Play Store
  `Aktualisieren` button. No local APK or upload-key installation was used.
- Package inspection confirms **1.0.4 (5)**, installer `com.android.vending`,
  `lastUpdateTime=2026-10-10 19:05:27`.
- After opening the updated app, Home remained idle with `focus` and one app
  selected. No setup text or new onboarding appeared. Screenshot:
  `/private/tmp/justnoise-1.0.4-after-play-update.png`.
- Android reports the production Accessibility service enabled/bound, with empty
  binding/crashed sets. This confirms connectivity at the inspection sample,
  not continuous protection or the historical interruption trigger.
- No uninstall, data clear, permission change, fresh session, instrumentation,
  Chrome test, or NFC retest was performed. Existing-selection retention is
  observed; pairing and all History records were not independently re-tested.
- Release-specific scrolling acceptance and fresh onboarding are still separate
  from this successful update check. The old interruption's trigger remains
  unknown; ending that requested session removed its visible recovery state.

## Subsequent read-only process-history investigation

Package-scoped exit history retains only the old process's October 10, 19:05:28.063
exit, reason 16 PACKAGE UPDATED. Retained package start records report
wasForceStopped=false throughout. Neither supplies a positive historical crash or
Force-Stop explanation for the original warning. Retention is bounded; absence is
not proof that an older event never occurred. No further owner-device mutation or
passed hardware-test repeat was performed. Isolated onboarding progress and the
remaining causal-data gap are recorded in `onboarding-emulator-2026-10-10/README.md`.

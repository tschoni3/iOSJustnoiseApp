# A16 isolated cleanup exercise — September 7–8, 2026

Status: **isolated exercise finished with documented limits; test copy removed; regular service restored**.

The owner explicitly approved installing and later removing the separate disposable
copy, with coordinated manual Accessibility switching. The first September 7
preflight found the regular app active at revision 67; installation was deferred.
After the owner stopped it, a passive sample confirmed idle revision 68.

## Installed artifact

- Device: existing Galaxy A16 `SM-A165F`, explicitly targeted over ADB.
- Regular app preserved: `com.justnoise.androiddev`.
- Disposable app: `com.justnoise.androiddev.cleanupcheck`.
- Label: **Justnoise Cleanup Check**.
- APK SHA-256:
  `a15342da4aa3ff177a0daa5b32304607f5b6e5100b6d54a76bf45b6efea10d54`.
- September 7 `adb install -t` returned **Success** and the installed APK hash
  matched. This is test-only/debug-signed, not a Play release. Direct NFC is disabled
  only for this variant; pairing/scanning uses its foreground reader.

The initial settings list showed regular Justnoise **Ein** and Cleanup Check
**Aus**. A subsequent navigation call was blocked by the tool approval service's
usage limit; neither that call nor its queued documentation edits executed.

## September 8 fresh setup

The owner reported turning the regular service off. Following a temporary wireless
connection loss, the already-paired A16 reconnected. A scoped enabled-service query
returned no Justnoise entries; regular preferences remained idle at revision 68.
No permission was changed by ADB. Duplicate wireless discovery entries represented
the same phone; only the current explicit transport was targeted.

The isolated app opened to fresh onboarding, then Activate Zap. The agent opened
the foreground pairing scanner and the owner scanned the existing physical Zap.
The owner reported **paired**. A presence-only check confirmed the test pairing
record exists and no disclosure-consent record exists; no digest or payload was
printed. The visible main screen required disclosure/setup and had no mode/app
selection. No data was copied from the regular app.

Opening setup and scrolling displayed the disclosure followed by an **unchecked**
consent checkbox and **disabled Continue to Accessibility settings** action.
This is initial consent-state evidence, not a withdrawal/reinstall result or an
independent refusal/re-entry test. The checkbox was left untouched for the owner
to read and affirm, followed by manual enablement of only the cleanup service.

The regular app's service is intentionally off during this exercise. Restore it
with owner coordination afterward; do not leave both services enabled. Test app
selection, session/history/schedule creation, withdrawal, uninstall/reinstall and
restoration remain pending. No regular-app uninstall, clear-data, permission write,
full private data export, synthetic NFC/session injection or instrumentation was
performed. No internal-beta GO, production signing or Play upload is claimed.

## Manual enablement and disposable data preparation

After the owner reported **enabled**, the scoped Android setting listed only
`com.justnoise.androiddev.cleanupcheck`'s Accessibility service. The regular
Justnoise service remained excluded. The test app progressed from consent-required
to target-required, consistent with manual setup; no permission was granted by ADB.

Through the real UI, the agent created and explicitly selected **Cleanup**, with
Chrome as its single target. A **Cleanup** one-time schedule for September 8 at
15:56 Europe/Berlin was saved **disabled**, and the list visibly confirmed Disabled.
The schedule therefore supplies a persisted record for deletion verification,
not an armed alarm. Exact-alarm access was not granted and no fresh scheduled
delivery or pending-alarm cancellation evidence is claimed from this record.

No existing regular-app mode or schedule was changed. A real completed session
in the test copy is the next step, to provide History data for the cleanup check;
the agent has not injected or synthesized a completion.

## First real disposable session

The owner reported **started** after the foreground Zap scan. The test copy's
scoped state confirmed `desired_active=true`, mode **Cleanup**, revision 3 and
service connected, without a stored recovery flag in that output. The real UI
showed Phone Zapped, Cleanup and an advancing elapsed timer (50 seconds in the
sample). Native Android navigation and Unzap Phone were visible. No repeated
Chrome-blocking check was requested. The agent opened Unzap Phone's foreground
scanner; the physical stop and resulting History entry were subsequently verified below.

## Physical stop and withdrawal results

The owner reported **stopped**. Scoped state confirmed idle revision 4 with the
service connected. History visibly showed **1 Zap / 6m** before withdrawal.
The resumed activity was verified as the cleanup package before using its actual
**Withdraw consent and clear Gate 0 data** button; no `pm clear` was used.

Observed afterward:

- Pairing and accepted-disclosure records absent (presence-only checks).
- Neither Justnoise service listed as enabled; no permission setting was written.
- Consent checkbox unchecked, Continue disabled, and setup says consent required.
- No apps selected; modes and schedules preference records absent.
- History visibly **0 Zaps / 0m**; schedules visibly **No schedules yet**.
- No cleanup-package entry in the alarm dump. The test schedule was disabled, so
  this does not establish cancellation of a previously armed alarm.
- Native navigation and Settings remained usable with no visible stale blocker.

The transient withdrawal success/failure message was not captured; the persistent
state and storage outcomes above were observed directly. A presentation defect
was found: the old **Schedule saved** banner remains when returning to the now-empty
schedule list after withdrawal. Record this for correction, not as restored data.

## Uninstall checkpoint and phone interruption

Android App Info and its confirmation dialog both identified **Justnoise Cleanup
Check**. The authorized Uninstall confirmation was tapped, but removal is **not
verified**: subsequent package queries still list both packages. Screenshots then
became black; passive state showed Dozing, battery **5%**, not charging, with Android's
uninstall activity behind NotificationShade. One wake request did not restore a
readable screen. No blind follow-up taps, lock bypass, reinstall or second deletion
were attempted. Owner wake/unlock and charging are needed to finish the UI route.

The regular app remains installed and its data was never cleared or uninstalled.
Its service remains intentionally **off** pending restoration after the disposable
exercise. No internal-beta GO or signed-release/upload readiness is claimed.

## Reconnection and reinstall — 15:28–15:31

After the owner reported the phone ready, USB transport 16 identified the same A16.
The battery indicator showed charging. A package query listed only the regular
app, confirming that the prior disposable uninstall ultimately completed.
The same SHA-256-verified cleanup APK was installed again, returning Success.
Its first launcher screenshot showed the initial **How to Use / Select Apps** page.

Before the agent advanced that page, a subsequent presence check unexpectedly
found a pairing record. Redacted-key inspection also found onboarding completed,
and a later screenshot showed the main screen with no mode and consent required.
No digest was exposed, no pairing was injected and no onboarding control was
operated by the agent in this interval. The owner was asked whether they had just
completed onboarding and scanned again. Until clarified, this is ambiguous between
concurrent owner setup and unexpected data restoration; **do not claim fresh
unpaired reinstall acceptance or a confirmed restoration defect**.

Consent, modes and schedules records were absent. The actual setup screen showed
an unchecked consent box and disabled Continue action; neither Justnoise service
was enabled. The disposable app remains installed while the ambiguity is resolved.
The regular app's data is untouched and its service remains off pending restoration.

## Owner clarification and final removal — 15:32–15:34

The owner answered **yes i did**, confirming they had just completed Get Started
and physically paired the Zap again after reinstall. This explains the newly
created onboarding/pairing records; there is no established restore defect.
Initial onboarding and fresh unchecked consent were directly observed; the
pre-pairing storage state was not sampled before the owner's interaction.

The agent removed only Cleanup Check through Android App Info and its explicitly
labeled confirmation dialog. A subsequent package query listed only
`com.justnoise.androiddev`, confirming final removal of the disposable copy.
The regular app was opened without starting a session: **power / 1 app selected**
was preserved, as were pairing and consent presence; state remained idle revision
68. No regular storage was cleared, exported in full or uninstalled.

Android's installed-service list showed only the regular Justnoise entry, **Aus**.
Its detail page was opened for owner-controlled manual re-enablement. Restoration
is still pending, not presumed complete. The stale schedule banner remains a
documented presentation correction; this exercise does not by itself grant the
internal-beta GO or close unrelated direct-NFC evidence gaps.

## Regular-service restoration — 16:07

The owner reported **enabled**. A scoped system query listed only the regular
Justnoise Accessibility service; preferences confirmed service connected and
idle revision 68, with pairing and consent records still present. Only the regular
Justnoise package remained installed. Opening its main screen showed **power /
1 app selected**, with no enablement/recovery prompt. No session was started, no
Zap scan requested, and no permission was granted by ADB.

Owner-device cleanup coordination is now finished. The observation limitations
above and stale schedule-status presentation issue remain documented; this is
not a production-signed candidate acceptance or a Play upload claim.

## Follow-up local presentation correction

The stale **Schedule saved** message was retained by the ViewModel after its
snapshot had correctly become empty/unconsented. The list and editor reload paths
now clear stale notices only after a successful read confirms consent is absent.
Failed/unknown reads retain existing errors and save-uncertainty warnings; current
consent preserves ordinary success and recovery notices. Three JVM regressions
cover these boundaries, with all 292 JVM tests, 36 package-checker tests, Android
lint/builds and shared contracts passing. No NFC, alarm, storage or restriction
logic changed. No new owner-device test/install occurred; final rendered acceptance
belongs to the eventual candidate rather than repeating destructive owner cleanup.

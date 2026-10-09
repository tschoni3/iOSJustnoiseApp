# Android named modes — 2026-09-07

Owner-approved extension of the local Android MVP. iOS remains unchanged. This is
the real integrated development application, not the permission-free UI prototype.

Current device status: the tested development APK is installed on the A16. The
owner has confirmed that both scheduled and manual Zap sessions work with modes;
the dated observations and their evidence limits are recorded below. This is
feature-flow acceptance, not a signed-release or full safety-gate decision.

## Implemented behavior

- Create, rename, edit and delete named modes with independent app selections.
  Save/Cancel operate on drafts; saving a mode does not silently select it.
- Explicitly choosing a mode remembers it for foreground and direct Zap starts.
  Missing/empty selections fail without falling back to another mode.
- Each schedule saves its own mode ID and resolves that mode's current targets at
  start. A scheduled start never changes the remembered manual choice.
- Active sessions retain their mode name/ID and exact target snapshot. Mode edits,
  deletion and selection are disabled during active sessions or recovery.
- A mode referenced by any schedule, including a disabled one, cannot be deleted
  until that schedule is reassigned or removed. Deleting the recent mode clears
  the choice; deleting all modes never regenerates defaults.
- Names are trimmed, nonblank and bounded to 60 characters; there are at most 30
  modes and 500 eligible app targets per mode. Android offers apps, not Apple-only
  category or website tokens. Protected/unavailable apps cannot be newly selected.

## Data and runtime boundaries

The old saved selection becomes **Noise**, retaining the stable `local-default`
ID. Existing schedules therefore need no reference/receipt rewrite. An explicitly
empty old selection stays incomplete. Without legacy data the catalog starts
empty. Unknown or corrupt catalog versions are reported, never replaced with
defaults. Existing pairing, consent, onboarding, active UUID/clocks/targets, pending
completion and completed History are retained.

`ModeStoreCodec` bounds the local catalog; `Gate0Preferences` caches decoded values
and owns the single transaction lock. Mode edits use the existing process/consent/revision
token. `ModeManagement` handles current launchable-app eligibility outside that
lock. Compose owns transient drafts, not NFC or alarm execution. No module,
backend, network permission, analytics, or second session engine was added.

`ValidatedSessionWriter` binds NFC validation to the checked state. A late start
cannot select different targets or quarantine a scheduled session that won the
race. A late stop, including a migrated session with no UUID, cannot stop a newer
session. Non-mutating conflicts neither report a storage failure nor broadcast an
overlay-dismissal signal. Genuine failed writes retain rollback/recovery behavior.

Mode names/IDs are not added to completed History or generic notifications.
Withdrawal clears mode configuration through the existing complete local cleanup.
Consent v4 remains unchanged because app grouping introduces no new Accessibility
event/content access, purpose or network use; privacy/inventory drafts now describe
the added local configuration explicitly. Live Updates/Now Bar remains paused.

## Verification and remaining device check

The Android extension fixtures are in
[`android-modes.v1.json`](../product/behavior/android-modes.v1.json); the shared iOS
fixture baseline and all Swift/Xcode/extension/asset files are unchanged.

Local verification covers migration/idempotence/corrupt versions, rollback,
independent selections, stale edits, active/recovery refusal, referenced deletion,
manual/scheduled races, completion preservation, current app eligibility and UI
state. The focused emulator pass covers nine real-store mode flows and eight
schedule UI/restoration flows. The compact 360×640 / 200% text sample is a constrained
content-layout harness, not an A16 screenshot or a system-navigation acceptance.
Both focused passes passed all **17 emulator tests**, including the final APK.
[Build-scoped screenshots and evidence](../spikes/android-integrated/evidence/named-modes-2026-09-07/README.md)
record the exact scope and test-harness correction.

The final repository verifier passed **289 JVM tests** (zero failures/errors/skips),
**36 Python package-checker tests**, all three Android projects' lint/build checks,
and the unsigned development release-package boundary. Product contracts passed
18 colors, 27 copy entries and the unchanged 89 shared behavior cases; the added
Android mode fixtures are exercised separately by eight of the JVM tests.

Current development artifacts (not Play-uploadable; debug APK installation recorded below):

- Debug APK: `spikes/android-integrated/app/build/outputs/apk/debug/app-debug.apk`;
  SHA-256 `9d5efacf4eb4a06aca96374253c374078ef045b26ef5f70fc4cc29f889c0a36c`.
- Unsigned development AAB: `spikes/android-integrated/app/build/outputs/bundle/release/app-release.aab`;
  SHA-256 `ee54b60b37d305982ccd9c0b5334bf33b101d67c370572aa6196d29904e06014`.

Both still use `com.justnoise.androiddev`. Earlier September 7 checksums identify
the pre-mode builds, not these replaced output paths. Signing and production
promotion remain separate work.

Logs are local to this working session:

- `/private/tmp/justnoise-named-modes-final-2026-09-07.log`
- `/private/tmp/justnoise-named-modes-emulator-tests-2026-09-07.log`
- `/private/tmp/justnoise-named-modes-emulator-final-2026-09-07.log`

Before accepting the feature on the A16, install the new development build while
idle without clearing data. Confirm the existing app selection appears as Noise,
create a second mode with different apps, explicitly select it and use the paired
Zap once to start/stop. Check that an existing schedule still has Noise and a new
schedule can retain the second mode independently. Reuse prior unrelated NFC,
navigation and recovery evidence; no extra phone or repeated full gate is required
for this feature check.

The initial implementation/verification above did not change the owner's phone.
It does not constitute a signed Play release, internal-beta GO, upload, or
public-launch approval.

## A16 development update — 2026-09-07

The owner approved continuing with the modes build. The existing Galaxy A16
`SM-A165F` was explicitly targeted, with no emulator connected. Immediately before
installation, a passive check confirmed `desired_active=false`, revision 58,
`service_connected=true`, and the existing v4 disclosure acceptance.

- `adb install -r` returned **Success**. The installed `base.apk` SHA-256 exactly
  matched `9d5efacf4eb4a06aca96374253c374078ef045b26ef5f70fc4cc29f889c0a36c`.
- A normal launch intent was accepted. A subsequent passive sample retained idle
  state, revision 58, connected service, and v4 consent.
- Presence-only checks confirmed the pairing and schedule records remain stored.
  These checks do not prove byte-for-byte record integrity or a working physical
  Zap interaction. No pairing digest, payload, target list or History rows were
  printed.
- The display was dozing and its screenshot was black. The mode catalog had not
  yet been persisted, so neither visible migration nor A16 mode UI acceptance is
  claimed. Unlocking and opening the mode sheet is the next step.
- No uninstall, data clear, permission/settings change, service toggle,
  instrumentation, UI-tree dump or programmatic session mutation occurred.
  The iOS tree remains unchanged.

At this installation checkpoint, the bounded new-mode/schedule-reference acceptance
described above was still pending. The full local test suite was not rerun for this
unchanged-artifact installation.

### Visible mode catalog after owner unlock

The owner opened Justnoise. The real idle Zap screen displayed **Noise** and
**2 apps selected**. Opening the capsule displayed one selected Noise row with
two apps, an Edit action, and **+ New Mode**. The dark-screen and modal status
icons, Done control and native Android navigation were visible. The unedited
[A16 catalog screenshot](../spikes/android-integrated/evidence/named-modes-2026-09-07/a16-catalog.png)
is physical-device evidence, separate from the emulator screenshots.

The app remained idle at revision 58 with the service connected before opening
the mode sheet. This confirms the visible migrated name/count/selection, not
byte-for-byte target identity or a completed mode-specific NFC/schedule check.
The new-mode editor was opened without entering or saving any user configuration;
the owner's custom mode choice is pending.

### Owner-created mode and explicit selection

After the owner reported Save, the A16 catalog showed **power / 1 app** beside
**Noise / 2 apps**. Noise remained selected: saving the new mode did not change
the manual selection. The passive snapshot was idle, revision 59, service
connected. The agent then explicitly tapped power; the black selected row and
checkmark moved to power while both app counts remained visible.
[Physical selection screenshot](../spikes/android-integrated/evidence/named-modes-2026-09-07/a16-power-selected.png).

No name or app selection was authored by the agent. Closing the sheet returned
to the idle Zap screen. A real Zap start/stop using this mode and the physical
schedule-reference check remain pending; neither is inferred from a saved row.

### First physical start using the custom mode

Following the instruction to tap the paired Zap once from Home and open the
selected app, the owner reported **“Yes blocked.”** A passive sample confirmed
`desired_active=true`, `active_mode_name=power`, revision 61 and the service
connected, with no stored recovery flag in the scoped output. This is a real
owner-reported custom-mode start/block observation, not synthetic NFC input.

The subsequent screenshot was black, so no new visual-blocker evidence is
claimed from it. An app outside this mode remaining usable, the physical stop,
and schedule-reference acceptance are still pending. No mode edit, permission
change, app launch, session mutation or repeat full-gate test was performed by
the agent during this active sample.

### Owner confirmation: scheduled and manual Zap routes

The owner subsequently reported: **“👍 good job worked both scheduled and normal
on tap.”** Record this as owner-reported acceptance of the intended mode flow on
the A16 for both schedule-driven sessions and manual paired-Zap sessions. Do not
request another ordinary scheduled/manual cycle merely to repeat this confirmation.

The agent did not independently capture the schedule's exact trigger time, saved
mode reference, stop receipt or excluded-app result in this follow-up. Preserve
that distinction from the earlier passive power-mode start and local automated
reference/isolation tests; do not fabricate those additional observations or
infer the phone's current active/idle state. No device commands or code changes
were performed in response to this confirmation. Remaining production identity,
signing, release-safety and Play requirements are unchanged.

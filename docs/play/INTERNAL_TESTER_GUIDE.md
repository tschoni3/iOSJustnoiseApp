# Justnoise Android — internal tester guide

Updated 2026-09-20. A signed production AAB exists; no Play opt-in link has yet been
issued. The steps below are based on the implemented app;
reconcile the release labels before sending. They are not new test results.
Named modes were approved and implemented in the development build on September 7.
Local/emulator checks and the owner's confirmation of manual/scheduled mode use
on the A16 are recorded in `ANDROID_NAMED_MODES.md`. The subsequent local
timer/onboarding/setup/support changes are not installed on that phone. No eventual
signed-release acceptance is implied by the development evidence.

Publisher: **Justnoise Media**. Feedback: **hello@justnoise.shop**.
Planned app: **Justnoise**, `shop.justnoise.app` (availability confirmed; registration
pending). Build 1.0.0 (1), signed AAB SHA-256
`94741f2afee9319b25e5cdca11ac126baf768d1dc755ec74ccb27e1cd86e697d`.
Opt-in link: **not issued**.

## Before you install

You need an NFC-capable Android 12+ phone, a compatible physical Zap and a Google
account included in the internal test. The current physical evidence is from the
Samsung Galaxy A16 SM-A165F; it does not establish reliability on all Android phones.
Keep your Zap nearby whenever a session or scheduled start may be active.

Use only the issued Play opt-in link and confirm the installed version. Justnoise
requires account sign-in before product setup; modes and History remain local to
that account workspace. There is no purchase. Do not enable developer
options or give ADB access to use the app. If you already have the paired development
app, stop here and coordinate the separate installation; two competing Accessibility
services or NFC routes are not the intended test setup. Do not uninstall it or
clear its data to resolve that conflict.

## First setup

1. Open Justnoise and follow the two introduction pages. On **Activate Zap**, use
   **Scan your Zap** and hold the Zap at the phone's NFC antenna until pairing is
   confirmed. Move it away. Pairing alone does not start blocking.
2. Use **Continue setup**. Read the separate **Accessibility disclosure**. You may
   leave without checking the consent box; blocking stays unavailable. Only if you
   agree, check the box and press **Continue to Accessibility settings**, then enable
   Justnoise manually in Android Settings. Do not enable a different development app.
3. Return to the main screen and open the mode selector. Create or edit a named mode,
   choose the launchable apps you want to block, and save. Cancel keeps the previous
   saved choices. Explicitly select the mode you want the next Zap tap to use;
   saving alone does not select it. Protected system/escape apps cannot be added.
   A session needs a selected mode with at least one eligible app.

## Use the four MVP features

### Select apps in named modes

Use the main screen's mode selector while the session is stopped and recovery is
resolved. Create a named selection or edit an existing one, then explicitly choose
the mode for your next Zap start. Names can contain up to 60 characters; the app
supports up to 30 modes and 500 eligible apps per mode. If an app is marked
unavailable/protected, it can only be removed. Stop an active session with the paired
Zap before changing modes or targets.

Older saved targets appear in **Noise** after migration, with pairing and History
retained. Deleting the currently selected mode leaves no selected mode; choose one
explicitly before starting again. A mode cannot be deleted while any schedule
references it, even if disabled: reassign or delete those schedules first. Deleting
all modes does not recreate defaults. Cancel an edit or deletion to keep saved data.

### Start and stop with Zap

From unlocked Home, tap your paired Zap once and move it away. An eligible session
starts using your last explicitly selected mode; selected apps show the Justnoise
blocking screen. A missing or empty mode cannot start and never falls back to another.
After more than three seconds, a new tap stops the session. Rapid duplicate reads
are ignored. Keep the tag away between deliberate start and stop taps.

If direct tapping does not work or Android shows a chooser, report what appeared.
For a foreground scan, open Justnoise and tap the large Zap artwork to start, or
**Unzap Phone** while active to stop. Use the same paired Zap. Do not replace the
pairing just because a scan failed, and do not rewrite the Zap.

### Schedule automatic starts

Open the calendar control, tap **Add schedule** (+), name it and choose **One time**
or **Repeat weekly**. Set a future date/time or the required weekdays, choose the
mode this schedule should start, review its app count and enabled choice, then tap
**Save**. The schedule keeps that mode reference even if you later select a different
mode for manual Zap starts. Scheduled starts do not change your explicit choice.

When requested, use **Allow alarms & reminders** and make your own choice in Android
Settings. Refusal does not silently grant access; a saved enabled schedule can still
need setup. Once setup and the referenced mode are ready, the schedule starts automatically. **Tap the paired
Zap to stop**; there is no scheduled automatic end. Disabling or deleting the
schedule removes future starts, not an already-active session.

After Force Stop, open Justnoise and follow its recovery instructions. Missed
starts do not catch up automatically. Delivery can be delayed or missed by Android.

### Review History

Open History using the main screen's History control. Switch between **90 Days**
and **Year**, then select a day for details. **Session time** is recorded duration,
not guaranteed continuously protected time. Interruption/timing warnings stay
visible. There are no reflection counts, accounts or cloud-synced records.

## Help, privacy and safe escape

History's gear opens **Settings**. **Blocking & Zap setup** provides setup/recovery;
**Privacy Policy** opens the notice. Tutorial replay does not reset your pairing.
**Review local-data removal** only opens setup; it does not itself erase anything.

Normal Home and Back should remain usable. **Keep going** on the blocker returns
to Justnoise; it does not end the session. If needed, use **Go Home**, **Disable
Accessibility**, or **App info / Force Stop**. Disabling Accessibility leaves local
data intact. Withdrawal/clear storage/uninstall can remove pairing, schedules and
History, so use them only when you intend to delete that installation's data.
Never call an emergency number as a test.

Notifications are optional. This beta does not promise Samsung Now Bar placement
or a particular lock-screen design. Accounts, cloud sync, Capture,
widgets and weekly insights are outside this MVP.

## Send useful feedback

Email **hello@justnoise.shop** with subject **Android beta — short issue summary**.
The latest local build also offers **Settings → Help & recovery → Email support**.
It opens a composer addressed to us without a message body, diagnostic data or
attachments; it does not send anything automatically. If no email app opens, press
and hold the displayed address to copy it. Add the subject and details yourself.
Include phone model, Android/One UI version, Justnoise version, what you did, what
you expected and what happened. For schedules, include the intended local time
and time zone; for NFC, say foreground scan or direct Home tap and any chooser/error.

Do not send raw Zap text, passwords, private browsing content, a complete app list
or a database export. Send only a cropped/redacted screenshot of Justnoise if useful.
There is no automatic diagnostic upload or remote access to your local History.
If navigation becomes obstructed, stop the test, use an available escape route and
report the problem rather than repeatedly launching the target.

## Before this guide is sent

Release owner supplies the actual version/checksum/opt-in link, verifies the mailbox,
checks final service/setup labels, arranges real Zap access and confirms the signed
release safety decision. Sending tester invitations is a separate action; this file
does not invite anyone or start a test-track rollout.

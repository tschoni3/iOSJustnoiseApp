# Justnoise for Android — Privacy Policy

**Publication candidate, updated 2026-09-08 — not published or release-approved.** This
plain-language draft describes the current Android implementation. It does not
replace the bundled engineering draft yet. Reconcile both with the final signed
release before distribution. Publication checks follow the proposed notice.
Named modes are implemented; the owner confirmed manual and scheduled use on the
A16. The September 8 support-composer handoff and setup-copy changes are locally
verified but not installed on the owner phone. Neither fact establishes signed
release acceptance or makes this unpublished notice final.

## Publisher and contact

Justnoise for Android is provided by **Justnoise Media**.
For privacy questions, contact **hello@justnoise.shop**.

This notice covers the Android app, not the iOS app, the Justnoise shop, Google Play
or your phone manufacturer's services. The Android MVP has no app account, cloud
sync, in-app purchases, advertising, analytics or automatic crash-reporting service.

## App blocking and Accessibility

You choose which apps to block and manually enable Justnoise's Accessibility
service after a separate disclosure and your affirmative consent. Pairing a Zap,
reading this policy or completing the introduction does not grant that consent.

While you keep the service enabled, it receives window-change event types and the
identifier of the app in the foreground, including when no focus session is active.
It keeps only the most recent identifier temporarily in memory so a scheduled
session can also interrupt a selected app that is already open. That identifier is
cleared when the screen turns off, the service is interrupted or disconnected, or
its current foreground state is uncertain. It is not saved as an app-usage log.

During an eligible active session, an exact match with your saved session selection
displays a Justnoise blocking screen. Justnoise does not read screen contents,
typed text, passwords, notification contents or Accessibility view trees. It does
not save a browsing or foreground-app history. No Accessibility-derived data is
sent to us or other parties.

Justnoise also checks whether its own service is available and responsive, screen
lock state and system-navigation boundaries. Those checks support truthful recovery
messages and safe placement of the blocking screen. Android's Home, Settings,
service-disable and uninstall routes remain available.

## Modes, selected apps and local setup

The picker reads names and identifiers of launchable apps locally. Only selected
app identifiers are saved; app names are resolved on the phone when displayed.
Protected system and safety apps, including Home, Settings and the default dialer,
are excluded from blocking. Each started session keeps its own copy of the selected
identifiers until that session ends.

You can save named modes containing different app selections. The app keeps each
mode's name, stable identifier, revision and selected app identifiers locally,
along with the last mode you explicitly selected. There can be up to 30 modes,
with names up to 60 characters and up to 500 selected apps per mode. Mode names
are not sent to us or placed in session notifications or completed History.

Saving changes does not automatically select that mode. A manual Zap start uses
your last explicit choice; a missing or empty mode cannot start a session and is
not silently replaced with another. Mode changes are unavailable during a requested
session or recovery. Deleting your selected mode clears the choice without choosing
another; deletion is prevented while a saved schedule still refers to that mode,
even if the schedule is disabled. Reassign or delete those schedules first.

When upgrading from the earlier single-selection Android build, the saved apps
become a local mode called **Noise** without changing their selection or pairing.
An intentionally empty catalog is not repopulated after deletion. Modes remain
until you delete them or remove the app's local data.

The app also saves your setup-completed flag, disclosure version and acceptance
time, pairing, session state and recovery information in its private storage.
Android may temporarily preserve screen navigation and unsaved editor fields when
recreating a screen. This does not create another account or transmit those fields.

## Zap pairing and NFC

Justnoise temporarily reads the Zap's NFC Text record to pair it or check a tap.
It stores a keyed digest used to recognize the paired text, with a non-exportable
key held by Android Keystore. Raw NFC text is not stored or logged. The tag's
hardware identifier (UID) is read only in memory, to check that a registered Zap's
signature belongs to that chip, and is never stored, logged or sent. Pairing remains until you replace it or remove
the app's local data. A canceled replacement keeps your current pairing.

## Sessions and History

During a session, the app stores a random session ID, the mode's ID/name and selected-app identifiers,
start clocks and state needed to stop or recover the session. Stopping removes the
active selection and session clocks and queues the completed record for local
History storage. A failed History write is retried without intentionally adding
the same session twice.

Each completed History record contains only a random session ID, start and end
times, elapsed duration, selected-app count and whether the session was interrupted.
It contains no app names, selected-app identifiers, NFC text or foreground-event log.
Calendar summaries are calculated locally from those records.

History displays **Session time**, not proof of uninterrupted blocking. Interrupted
or uncertain timing is indicated. Records have no automatic expiry; they stay on
the device until you remove the local data as described below.

## Schedules

Schedules save their name, random identifier, revision, enabled choice, chosen
date/time or weekdays, time-zone information and the identifier of the mode you
choose for that schedule. A scheduled start uses that mode's then-current saved
apps, not a different mode selected later on the main screen. It does not change
your most recent explicit mode selection. Missing or empty referenced modes cannot
start a session. Older schedules keep their reference to the migrated Noise mode.
The app also keeps bounded delivery/replay-prevention information and the latest
outcome for each schedule. It does not keep an unbounded schedule-event log.

Automatic starts require separate Android alarms-and-reminders access. Android
alarm, boot and time-change callbacks and a short wake lock let Justnoise check
the saved rules. Delivery may be delayed or missed. After Force Stop or uncertain
process recovery, open the app and follow its recovery instructions; missed starts
are not automatically caught up. Temporary checks of the app's own process-start
or exit state are not a record of other apps' process activity.

Deleting a schedule removes its configuration and execution information, but does
not end an active session or erase its completed History. Tap your paired Zap to
end a session.

## Optional session notification

If you separately allow notifications, Android can display generic session time or
an attention message. Lock-screen visibility depends on your system settings. The
notification contains no selected-app names or Zap text. Tapping opens Justnoise;
dismissing it does not stop the session. The app saves one matching session ID to
keep a dismissed display hidden for that session. Refusing notifications does not
prevent sessions. If Android abruptly ends the process, the last display may remain
until reconciliation; it is not proof of continuous blocking.

## Storage, sharing and security

The app's configuration and History stay in private Android app storage. The
Android implementation has no Internet permission and does not automatically
upload the data described above. It has no advertising, analytics or crash-reporting
SDK. We cannot remotely view or erase your on-device History.

The pairing key uses Android Keystore. We do not claim that all app files are
individually encrypted. Android cloud backup and device transfer are disabled for
the app; this is not a guarantee about every manufacturer's restore behavior.
Protect your phone with its normal device security controls.

If you choose an external shop link, your browser opens that website under its
own privacy terms. The Settings support button opens an email composer addressed
to hello@justnoise.shop. Justnoise does not attach app data or a message body and
does not send the email automatically. If no email app opens, you can copy the
displayed address. If you email us, your sender address, message and any attachments
are transmitted through email rather than by this app. Do not include passwords,
raw Zap text, private browsing content or unnecessary personal screenshots.

## Your choices and local-data removal

Disabling Accessibility stops service access but does not erase your saved pairing,
selected apps, schedules or History. You may also use Android's Force Stop and
uninstall controls; Justnoise does not turn those choices into consent to restart.

The app's local-data withdrawal action attempts to remove saved configuration,
pairing and its key, modes and their remembered selection, pending and completed
History, schedules and recovery state.
It also cancels its scheduled alarm and notification and requests service disablement.
Successful and incomplete cleanup have different messages. If deletion cannot be
confirmed, retry or use Android App info to clear storage or uninstall. A pending
History deletion is retried before new consent, starts or History access.

After local-data removal or a fresh installation, setup and affirmative disclosure
consent are required again. There is no server-side app account to delete. Clearing
the app does not delete email you separately sent to support or data held by an
external website or Android/Google Play.

## Publication checks — internal, not part of the proposed notice

- Compare this notice with the final signed package and the bundled in-app policy.
  This readable version is not yet wired into the app; do not publish mismatched
  versions. The exact implementation inventory and current bundled text remain in
  [DATA_INVENTORY.md](../../spikes/android-gate0/evidence/DATA_INVENTORY.md) and
  [PLAY_DECLARATION_DRAFT.md](../../spikes/android-gate0/evidence/PLAY_DECLARATION_DRAFT.md).
- Publisher/contact were supplied by the owner. Do not invent a registered legal
  identity, address, inbox provider, support-email retention period or other legal
  terms. Confirm applicable operator/support/website details before publication.
- Publish on the correct Justnoise website as a separate Android policy, leaving
  the existing shop/iOS policy unchanged. The currently connected Shopify store
  does not match Justnoise; no page was created or changed there.
- Record the real accessible URL and effective date, verify the contact receives
  messages, and add the final URL to Play Console. No public URL exists in this
  document and no publication is claimed.
- Keep the separate Accessibility disclosure and consent flow. A privacy page does
  not substitute for it. These publication checks follow
  [Google's User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).

This is a product/technical draft, not a legal-compliance certification.

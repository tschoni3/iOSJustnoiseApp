# Scoped account and first-run evidence — 2026-10-11

Scope: close missing Android acceptance evidence only. Do not repeat accepted
session, NFC, blocking, scheduling, mode-selection or sheet tests. Do not delete
the owner's account/data, sign out, reset passwords, clear app data or revoke
working permissions. Privacy and public publication remain outside this work.

## Observed on the owner's A16

- USB ADB connection is available to the Samsung SM-A165F. The initial sandbox
  daemon failure was a host permission problem, not proof of phone disconnection.
- Before this check the installed production app was `shop.justnoise.app`,
  1.0.14 (16), installed by `com.android.vending`.
- Opening the app displayed the ordinary idle Zap Home with the existing `focus`
  mode and one selected app. This is an already configured installation, not a
  fresh-first-run test.
- Google Play offered **Update**, not Install. The update was applied in place.
  Afterwards the package manager and Settings footer both reported **1.0.16 (18)**,
  still installed by `com.android.vending`.
- The updated app returned to idle Home with the same visible mode/selection.
  Its normal account page displayed the existing signed-in owner account.
- `adb exec-out screencap -p` captured the ordinary account page on build 18.
  This closes the missing normal-account screenshot-policy hardware observation.
  Password forms were not opened or captured.
- Settings -> Replay introduction displayed the education page and embedded
  media frame. This is replay evidence only, **not** proof of the separate
  first-session guide or a complete fresh-first-run journey.

No owner account operation, session start, instrumentation, UiAutomation,
permission change, uninstall, data clear or existing test-suite rerun occurred.
No production source or iOS source was edited in this check.

## Evidence boundary still open

1. A genuinely fresh production journey through permission grant, actual Settings
   return/progression, the separate first-session video, dismissal and Home.
   The configured owner installation must not be reset to manufacture this proof.
2. Live email-confirmation return and password-reset completion using a separate
   test account. Previous mail delivery and Google acceptance are retained;
   they are not equivalent to these missing paths.
3. Live disposable-account deletion and cross-account isolation. No destructive
   account test may target the owner's real account. Test-account deletion needs
   explicit authorization, and private credentials remain with the user.

Requested a separate test installation and disposable account/mailbox authorization.
Until that context is available, these cases remain NOT RUN, not PASS. Existing
local regression and prior owner-accepted evidence are retained without reruns.

## Explicitly authorized owner-account reset in Supabase

The owner subsequently revoked the earlier no-account-deletion boundary for the
specific account `tschoni.nguyen@gmail.com`, expressly confirming irreversible
deletion including associated data. Local A16 data preservation remains in force.

On returning to the Supabase UI, the Auth account was already absent; a fresh
page load showed zero matching users. The agent did not perform that Auth deletion.
A narrowly scoped read-only query identified one orphaned `public.profiles` row
whose primary ID exactly matched the former Auth UID
`36047997-5e0b-4619-a76a-c577aa2fcc37`. No matching promo-code reference was found.

After a separate explicit owner confirmation to delete only this remaining profile,
the agent executed the exact-ID deletion with an additional guard requiring the
old Auth UID to remain absent. `DELETE ... RETURNING id` returned precisely that
one old profile ID. A subsequent independent read-only query verified:

- old Auth UID: 0 rows;
- old profile ID: 0 rows;
- Auth accounts matching the requested email: 0 rows.

No other user record or local phone data was deleted. This is an administrative
Supabase cleanup, not acceptance evidence for the app's self-service deletion
flow. Re-registration, email-link return, password recovery and a genuine fresh
onboarding journey remain unobserved. Backend-account removal does not reset the
existing phone's installation/onboarding state.

## New email signup and A16 confirmation — PASS

The owner reported a new registration, opening the confirmation link on the A16,
and successful sign-in: "Bin angemeldet — funktioniert alles". This is owner
hardware acceptance for signup -> email confirmation on the originating Android
device -> signed-in app, not a claim that every remaining account path passed.

A read-only Supabase Users overview independently confirmed a genuinely new
Email-provider account with UID `3745b558-700e-4b8e-9679-763c91a97c10`, distinct
from the deleted account. The dashboard showed:

- Created: 11 October 2026, 18:43 (Europe/Berlin).
- Confirmation sent: 16:43:52 UTC.
- Confirmed: 18:44 (Europe/Berlin).
- Last signed in: 18:44 (Europe/Berlin).

This closes the signup/email-confirmation-return evidence gap above. The agent
did not repeat the flow or change account/device state. Password-reset completion,
app self-service deletion, cross-account isolation, and the complete fresh
permission/video/Home journey are not established by this report and remain
separate evidence boundaries.

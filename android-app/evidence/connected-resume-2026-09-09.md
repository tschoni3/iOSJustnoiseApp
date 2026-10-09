# Connected acceptance resumed — September 9, 2026

## External state changed

Read-only inspection after the prior blocked checkpoint found:

- USB ADB device `RFGYC3DYKBR` is connected and authorized, product `a16nseea`,
  model `SM-A165F`, transport 71 at observation time.
- Supabase URL Configuration now lists three saved redirects:
  `justnoise://open`, `justnoise://reset-password`, and
  `com.justnoise.app://auth/callback\?state=*`. The Add URL dialog is closed and
  the list reports Total URLs: 3. Site URL remains `https://example.com`.
  The agent did not perform the save; this records observed external state.
  Both existing iOS redirect strings are unchanged.
- The prior blocker report is therefore historical: connection and Android
  redirect persistence are now verified. Live authentication is not yet verified.

## Installed build and screen observation

Read-only package metadata still identifies Justnoise 1.0.0 (1), last updated
September 9 at 09:20:36, first installed September 8 at 18:15:34. Both ordinary
notification permission (USER_SET) and promoted-notification permission are granted.
The recorded installed build is still the older `db0c2798` candidate, not the
new account-enabled `ffb1ec06` candidate. No installation was performed here.

Passive screenshot `/private/tmp/justnoise-resumed-a16-2026-09-09.png` shows the
Samsung lock screen at 13:02 and a Justnoise notification card with:

- timer `3:20:34`;
- `LOCKED IN FOR`;
- `Session time · hunk`;
- the lime app icon and dark Zap artwork.

This proves a rendered lock-screen card on the installed old build. It does not
prove a separate Samsung Now Bar, actual timer advancement from one image,
developer-override status, or acceptance of the newer account build. The visible
active session must not be interrupted by installing an update.

## Pending owner action

One combined question requests a normal Zap stop before approving an in-place
update to the exact tested `ffb1ec06` candidate. Pairing, modes and History must
remain intact; no uninstall, data clear or instrumentation on the owner app is
authorized. Do not infer approval from the automatic goal continuation.

An optional read of private Auth email-template contents was rejected by the
permission reviewer as outside the explicit redirect/testing authorization. No
template body was inspected or modified, and no alternate access method was used.
This optional inspection is not required to test the actual Android confirmation
and recovery return flows once test-account access is approved.

No backend setting, live account, password, analytics preference or phone setting
was changed by this resumed inspection. Release acceptance remains HOLD.

## Subsequent owner-approved update — 13:05

The owner replied **"mach einfach"** to the explicit in-place update question.
Before installation, the visible screen showed the idle headline
`Zap to Block Distractions`, mode `hunk`, and `1 app selected`. Thus the update
did not interrupt the previously observed active session.

The exact preserved `1.0.0-1-ffb1ec06` APK was installed on `RFGYC3DYKBR` using
`adb install -r`; the result was **Success**. No uninstall, data clear,
instrumentation, permission grant or pairing replacement was performed.

- Installed package: `com.justnoise.app`, version 1.0.0 (1), min SDK 31 / target 36.
- Last update: September 9, 2026, 13:05:41. First installation remains
  September 8, 2026, 18:15:34.
- CE/DE data directory inodes remain 71241 / 71025 respectively.
- SHA-256 read from the actual installed `base.apk`:
  `d7ef1c8f5510ed864e693c45f07f0286f3bf9dafd72d456ad813f032d2276e50`.
  This matches the approved local candidate exactly.
- Launch returned `Status: ok`, WARM, total time 343 ms. This single observation
  is not a performance benchmark.
- The production Accessibility service remains enabled. Ordinary notification
  permission remains USER_SET/granted; promoted-notification permission is granted.

Visible post-update evidence:

- `/private/tmp/justnoise-after-account-update-2026-09-09.png`: idle Zap screen,
  `hunk`, one selected app, no onboarding or recovery requirement.
- `/private/tmp/justnoise-account-update-history-2026-09-09.png`: Timeline shows
  3 Zaps and 3h32m, with the truthful elapsed-time qualification. One blocking
  interruption and one timing-uncertainty warning remain visible. Their causes
  have not been established here; they were not erased or declared false alarms.
- `/private/tmp/justnoise-account-update-settings-2026-09-09.png`: Your account
  entry, ready/idle blocking setup and Live Update permission On.
- `/private/tmp/justnoise-account-screen-2026-09-09.png`: black protected content
  after opening Your account, consistent with the Account Activity's FLAG_SECURE.
  No screenshot-protection bypass or blind credential-field interaction was used.

These observations support retained visible mode/selection/History and unchanged
installation storage. They are not a full private-store comparison or a fresh
NFC acceptance test. The original candidate verification report remains unchanged
as historical build evidence; this checkpoint supersedes its installation status,
not its distribution HOLD.

The account screen is left open for the owner to enter credentials directly,
confirm separate local data and leave Analytics off. Live sign-in, A/B isolation,
recovery/deletion, consented telemetry, normal-settings Now Bar and the affected
Chrome keyboard sequence remain unverified. No account mutation or analytics
preference change was performed by the agent. iOS source remains untouched.

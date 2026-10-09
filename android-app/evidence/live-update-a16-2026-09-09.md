# A16 Live Update candidate installation — 2026-09-09

## Authorization and exact candidate

Owner replied **JA** to the explicit request to update the A16 with the new signed
build while retaining pairing and saved data. The later connected-development goal
does not itself authorize arbitrary further phone updates or distribution.

- Device: Galaxy A16, SM-A165F, serial RFGYC3DYKBR, USB transport observed directly.
- Package/version: `com.justnoise.app`, Justnoise, 1.0.0 (1).
- Source snapshot: `android-app/build/candidates/1.0.0-1-db0c2798/`.
- APK SHA-256 before install: `52606ff0dd27d67deec4a09f593847a21492053b70793983d71e2725744cd7e5`.
- Android public properties: SDK 36, full version 36.1.

## Observed installation result

Before updating, the real Settings UI said ready for the next session, app blocking
currently off. Notification permission was off. The scoped `adb -s RFGYC3DYKBR
install -r` command returned **Success**. No uninstall, clear-data, instrumentation,
permission grant or pairing replacement was run.

Package metadata changed lastUpdateTime to **2026-09-09 09:20:36**; firstInstallTime
remained **2026-09-08 18:15:34**. Credential/device encrypted data-directory inode
values remained 71241/71025. The existing Justnoise Accessibility service remained
enabled. After launch, the real Zap screen was idle and retained mode **hunk** and
**1 app selected**. No fresh onboarding/pairing screen appeared. This supports
in-place preservation; it is not a new NFC or complete History acceptance claim.
The installed `base.apk` SHA-256 was independently read and exactly matched the
approved APK above. `com.justnoise.androiddev` remained disabled-user (`enabled=3`)
with its existing data-directory inodes; no competing development service was enabled.

Owner then manually enabled notification permission and replied **aktiviert**.
Package metadata confirmed POST_NOTIFICATIONS granted with USER_SET and the
production POST_PROMOTED_NOTIFICATIONS permission granted. The updated Settings
screen showed **Live Update permission: On** and **Live Update requests are allowed**.
That proves permission/support state, not Samsung Now Bar rendering.

Screenshots retained locally (not private preference extraction):

- `/private/tmp/justnoise-session-before-update-2026-09-09.png`
- `/private/tmp/justnoise-after-approved-update-2026-09-09.png`
- `/private/tmp/justnoise-live-permission-owner-enabled-2026-09-09.png`

## Pending

At approximately 09:59, a passive screenshot showed a running session on the real
Zap screen: **Phone Zapped**, mode **hunk**, timer **00:17:22**. No additional Zap
start is needed. Package metadata still confirmed notification permission granted
and the same installed candidate. Screenshot:
`/private/tmp/justnoise-nowbar-current-2026-09-09.png`.

The current notification record for `com.justnoise.app`, ID 7101, channel
`zap_session`, contained `PROMOTED_ONGOING`, `android.showChronometer=true`,
`android.chronometerCountDown=false`, a large resource icon and color `0xffd7fa00`.
This establishes framework promotion and configured timer/artwork, **not visible
Samsung Now Bar acceptance**. A requested `dumpsys notification --help` unexpectedly
returned the complete service dump rather than help; do not repeat that unfiltered
diagnostic. Only the Justnoise record is relevant to these findings.

The session is already active. Lock-screen rendering and normal-settings visual
acceptance are still pending; no additional start or stop is requested.
Samsung developer override must remain off. The known Chrome keyboard-window
correction is included, but its affected device check remains outstanding.
No internal-beta GO, completed-connected-release claim or upload is recorded.

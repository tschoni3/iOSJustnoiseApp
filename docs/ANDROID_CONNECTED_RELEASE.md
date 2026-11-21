# Android connected-release extension — 2026-09-09

October 11 fresh Play signup diagnosis: both owner signup requests reached the
shared Auth backend and returned HTTP 500 because Resend rejected the confirmation
email: the SMTP key's associated domain was not verified. Custom SMTP is enabled
with smtp.resend.com and sender hello@justnoise.app. This is a shared mail-delivery
configuration defect, not a proven Android connectivity/password/callback defect.
Do not disable email confirmation or grant a local session as a workaround.
Android now maps HTTP 5xx and 429 to sanitized server/rate-limit failures and
shows operation-specific account errors without requesting a nonexistent link or
mentioning offline Zap state. Provider response bodies remain private. The shared
Resend configuration and a real confirmation/sign-in still require completion;
neither source changes nor mock tests establish working customer registration.

Owner approved Sign-in, PostHog and Samsung Now Bar, then requested implementation.
This extends the local MVP; it does not make older signed bundles complete, waive
the Chrome keyboard-window acceptance failure, or authorize upload/distribution.
iOS source, assets, project and extensions remain unchanged.

## Required account entry — owner clarification, September 9

September 19 owner extension: add an explicit one-time local-data transfer after
sign-in and in Account settings. `product/behavior/android-local-import.v1.json`
defines consent, merge, single-account ownership, crash recovery and disabled
imported schedules. This supersedes the separation-only UI, not account isolation.
No automatic adoption, cloud sync or iOS changes are authorized.

Production Android requires a durably signed-in account before product access or
any new manual/direct-NFC/scheduled session. There is no guest mode. This corrects
the earlier optional account entry to match the owner's iOS experience. Development
capability apps remain local-only. See `product/behavior/android-account-access.v1.json`.

After local account restoration, expired credentials or absent network do not
prevent offline sessions. A process-local start permit begins closed and is opened
only for the reconciled signed-in workspace. Permit changes and session-start
commits share the product lock; authentication transitions close it before changing
the journal. No NFC callback takes the auth/Keystore lock or waits for the network.
Sign-out, deletion intent and failed restoration close new starts, not safe stops.
Existing sessions (including an upgraded local session) retain physical-Zap stop
and system recovery/escape routes. Local unowned modes/history remain preserved,
but signing out no longer exposes them as a usable guest app. No implicit migration.

Entry refinement: Android auth forms use the read-only iOS SignInView reference
(charcoal background, centered existing app icon/title, blue primary action and
white Google action). Android keeps its native fields, navigation and explicit
local-data separation choice. A completed required sign-in returns to the real
product route automatically; password-recovery forms never auto-dismiss before
their confirmed update. The first direct NFC callback may wait for local account
restoration within the existing 1.5-second service-readiness deadline, on its reader
worker only. Cancellation/deadline stop the wait; the actual commit still validates
the current account permit, and an existing session does not wait for account auth.
Read-only iOS root routing confirms introduction → activation → required login →
product. Android permits only introduction/physical pairing before login, after
storage reconciliation. Skipping/closing those pages still lands at the account
gate, never a guest product. Restored/active installations keep their safety route.

## Delivery order and acceptance

Account recovery follow-up: expired credentials may refresh only for the same
account; rejection offers same-account password/Google reauthentication, including
while deletion is remote-pending. Reauthentication must not switch workspaces or
clear deletion intent. Recovery links for an existing account must verify that
same account before changing its password. Abandoning a recovered-password form
forgets temporary credentials and cancels only its exact pending flow. A claimed
link after process loss is retried only explicitly, never automatically replayed.

Android registration also supplies its own generation-bound PKCE callback, using
the same exact Android return route as Google/recovery. The existing shared Site
URL is a placeholder and must not be changed as a workaround. An unconfirmed
signup retains the local workspace and a pending encrypted verifier; only a valid
confirmation-code exchange may accept the account. The confirmation UI explains
same-phone return and the manual sign-in fallback after expiry. Supabase redirect
and email-template behavior still need live acceptance.

1. **Native session Live Update:** promote the existing single, silent session
   notification in the production manifest; expose truthful settings/support state.
   Use the persisted monotonic timer, clear on stop, hide the surface in recovery,
   respect dismissal and permission refusal. No fake progress, media playback,
   foreground service, private Samsung API or developer-toggle dependency.
   Samsung Now Bar rendering requires the actual signed build/device check.
2. **Supabase account integration:** reuse the existing project, not a new auth
   provider. Implement email/password, registration/confirmation, recovery,
   restoration, Google sign-in, sign-out and account deletion as one coherent
   slice. Establish account-owned storage and crash-safe migration before exposing
   account switching. Never attach the current unowned local history/modes to an
   account silently, clear owner data, or block Zap stops on network availability.
3. **PostHog integration:** reuse the existing ingestion project only after an
   explicit event allowlist and separate user opt-in. No autocapture, screen replay,
   UI text, credentials, emails, mode names, selected packages, tag data or
   Accessibility-derived observations. Account transitions must stop/purge pending
   analytics before changing identity. Failure/opt-out never affects core sessions.

Owner clarified on September 9 to reuse existing PostHog events. Read-only iOS
source confirms `focus_session_started` and `focus_session_ended`; Android reuses
those exact names for successful manual/scheduled starts and Zap stops. No new
mode/schedule event names are invented. iOS's mode/session/duration properties,
activation and generic error events are not copied. Android sends only a random,
unidentified process-local ID and `$process_person_profile: false`, after a
separate explicit opt-in. A native direct capture adapter has no SDK autocapture,
disk queue or retry/replay. Account changes revoke consent, cancel/join outstanding
requests and rotate its in-memory generation before switching ownership. Events
already delivered cannot be recalled; ordinary networking exposes an IP address to
the receiving service. This source audit is not inspection of the live dashboard.

September 20 opt-out hardening: Android optional analytics consent is bound to a
random app-process epoch, never the account ID or session ID. A stored grant is
valid only for that same process after account reconciliation. A new process
defaults off even if the previous opt-out write failed and left its old grant on
disk. Legacy boolean grants are not restored. Explicit opt-in must still commit
successfully; same-process account changes retain the existing purge/generation
barrier. This deliberately trades cross-restart analytics coverage for fail-closed
revocation: after Android restarts the app process, a new optional opt-in is needed.
Activity recreation/backgrounding alone does not end the grant. Account, pairing,
mode, History and session persistence are unaffected; iOS is unchanged. Consent
copy must state this lifetime. The process epoch is local-only, never sent to PostHog.

4. **Combined acceptance:** isolate account A/B, offline restore/stop, expired and
   revoked credentials, deletion retry, opt-in/out and event destination/contents;
   check the signed A16 Live Update lifecycle and the existing keyboard-window fix.
   Rebuild and reverify the final APK/AAB only after the included scope is explicit.

## Read-only reference audit

### Account persistence boundary — implementation contract

Local product storage gains explicit workspace ownership: the existing `local`
workspace retains all legacy rows/IDs and is never silently assigned to a login.
Account workspace IDs are validated provider UUIDs. Modes, schedule definitions,
execution receipts and History are isolated; pairing, onboarding and the current
Accessibility consent/service evidence remain installation-owned. Idle-only
workspace changes preserve the outgoing profile and select the incoming profile
in one preference commit after the outgoing History outbox is drained. The same
commit invalidates stale edit tokens and durably blocks new starts until the
account coordinator acknowledges the matching auth/workspace handoff. Schedule
recovery discards missed work before future alarms can resume. Global disclosure
withdrawal retains a durable cleanup-owner list until every workspace History
store is cleared. No auth UI may expose switching before coordinator integration.

Account deletion uses a different operation from global disclosure withdrawal.
After confirmed remote deletion, the coordinator switches to the preserved local
workspace but keeps its handoff closed. Targeted erasure atomically removes only
the deleted account's archived profile and records its cleanup owner before
removing that account's History database. A failed erase or final preference write
retains the owner for retry and prevents releasing or retargeting the handoff.
Only successful cleanup removes the marker. Local data, other account profiles,
pairing and installation consent are retained. The auth journal must remain in
local-cleanup-pending until this and the analytics cleanup both finish; this
storage operation does not itself prove remote account deletion.

The production-only auth adapter owns a versioned, encrypted session journal in
Android's no-backup directory, protected by its own Android Keystore AES-GCM key
(never the Zap pairing key). Passwords are never persisted. No plaintext fallback,
automatic deletion of unreadable credentials, or successful-login UI before a
confirmed write is allowed. Reads/mutations run off the UI/session-event thread.

Every asynchronous auth operation captures the journal generation. Successful
sign-in, refresh, sign-out and deletion transitions rotate it; a late response
cannot restore credentials after sign-out or cross into a different account.
Deletion has durable remote-pending and local-cleanup-pending phases. Remote
success removes tokens but retains the cleanup owner; only completed local cleanup
may remove that owner. Sign-out cannot bypass pending deletion.

This journal is a prerequisite, not account isolation by itself: the account
coordinator must quiesce work, switch the product namespace and clear pending
analytics before publishing a new account. The current local workspace remains
unowned and intact until an explicit migration choice. Auth UI/provider and
product-store wiring are still required; no accounts or network traffic are
introduced just by compiling these native storage adapters.

`AccountCoordinator` now connects the durable journal to the real product-store
handoff and targeted erasure operations. It serializes local auth outcomes against
refresh/deletion callbacks, requires explicit `KEEP_SEPARATE` for existing local
data and closes/purges the telemetry boundary before changing account ownership.
An active session rejects account switching; expired credentials do not prevent
offline restoration or Zap stop. Remote deletion success drops credentials first;
if a new session started while the request was in flight, local erasure waits for
that session to stop. Startup reconciliation follows the durable journal owner,
not an abandoned provider callback. Retrying a failed final handoff retains the
existing recovery warning and schedule suspension.

The coordinator is worker-only, including `isCurrent`, because journal reads use
encrypted disk storage. The production UI still needs its own cancellation and
delivery-generation boundary and must discard retained product ViewModels/drafts
before exposing another account. Provider/Activity wiring and a real telemetry
boundary are not yet installed; no no-op analytics cleanup is supplied as a
production default. The canonical offline capability app does not construct this
production-only coordinator.

**September 9 runtime update:** the production `AccountApplication` now constructs
the real coordinator and performs local restore off the main thread. A dedicated
native Account Activity is reached from grouped Settings and handles the exact
PKCE callback route. Product UI waits for reconciliation and replaces retained
ViewModels/drafts when the runtime generation changes. Email/registration/recovery,
Google browser handoff, password update, logout, confirmed deletion and separate
analytics opt-in now have real actions; no preview reducer is imported. The native
PostHog capture adapter is the actual cancellation/purge boundary. The earlier
unwired statements above describe the preceding foundation checkpoint, not this
new source slice. Live provider configuration, rendered/device acceptance and
expired/revoked-session recovery still require explicit verification.

The production provider now uses pinned Supabase Kotlin **3.6.0** (Auth and
Functions) with Ktor Android **3.4.3**, matching the existing Kotlin 2.3.21 toolchain.
Upstream 3.8.0 targets Kotlin 2.4; no unrelated toolchain upgrade is introduced.
Email login/registration, token refresh, recovery requests/code exchange, verified
password updates and the existing delete-account edge function have native adapter
methods. They use short-lived clients with in-memory SDK session storage, no
automatic load/save/refresh, no SDK logging, finite request timeout and no HTTP
redirect following. Returned credentials still require the coordinator's commit.
Only the production manifest gains INTERNET; offline capability/development
permissions stay unchanged.

Recovery and Google OAuth use PKCE with the Play-release redirect
`shop.justnoise.app://auth/callback?state=<random-flow-UUID>`. The encrypted,
generation-bound pending-flow owner now persists the exact verifier, purpose,
30-minute expiry and one claimed callback. Strict URI validation rejects foreign
origins, ambiguous query parameters, raw token fragments and stale account/flow
generations. Claim rotation permits explicit process-loss retry while invalidating
older workers. The exported callback/Activity integration is still required, as is
checking the Supabase redirect allowlist. SDK session disposal must not delete that external
pending-flow verifier. The provider's cache adapter delegates save/read while
leaving consume/expiry to the pending-flow owner. No implicit access-token fragment
login is implemented. Registration confirmation can complete with the project's
existing browser link, followed by password sign-in; confirmation alone is not
treated as a session. No live account or provider setting was changed for this work.

The pinned SDK generates a padded base64url verifier and launches its cache write
asynchronously. The adapter preserves the exact verifier bytes and awaits a confirmed
save/readback before publishing a Google URL or recovery-request success. Storage
failure is sanitized rather than escaping an unobserved SDK coroutine. Recovery
HTTP may already have been submitted when storage fails; no working recovery link
is claimed in that case. Automatic SDK lifecycle callbacks are disabled as well as
refresh/persistence; the production coordinator owns restoration. Pending-flow
consumption/purging still needs runtime wiring. A server-consumed code whose response
was lost may require a new link; local persistence cannot make server exchange atomic.

- iOS `SupabaseManager.swift`: Supabase email/password and existing delete-account
  edge function; native SDK owns session persistence. Its hand-written password
  reset endpoint must be checked against current provider APIs, not copied blindly.
- `AuthSessionStore.swift`: ordered authoritative SDK session events, including a
  deletion barrier against late signed-in callbacks.
- `SignInView.swift`: email/password, registration and Google/Apple adapters.
  The iOS Google client ID is not proof of an Android OAuth client registration.
- `PostHogAccountBoundary.swift`: queued telemetry cleanup is part of deletion,
  not merely a call to reset identity. Current iOS lifecycle/screen autocapture
  is not automatically approved for Android.
- Android: actual product stores are currently local/unowned. Auth routes in
  `android-ui` are previews, not a usable backend implementation.

## Intentional Now Bar limits

Android Live Updates are promoted notifications, not a second independent lock
screen widget. Use one notification ID/channel. System settings may show that same
item in both the notification panel and lock screen; the app cannot promise Now
Bar while deleting its underlying notification, or position it in screen center.

The existing compatibility boundary requires Android 16 QPR2 / API 36.1 for the
request-based promotion semantics. Base API 36/OEM legacy eligibility is not
implemented by pretending a focus session is a call, navigation or media player.
`canPostPromotedNotifications` and `hasPromotableCharacteristics` are necessary
checks, not proof of actual promotion. Only the system's posted promoted flag is
reported as a promoted update, and even that does not prove Samsung lock-screen
visibility. The settings screen explains unsupported cases. The owner's follow-up
requires **Live Update only**, with no ordinary fallback. Unsupported/denied/
unhealthy states post nothing. After a valid request, check the own posted flag
once after one second and cancel a demoted ordinary item without heartbeat reposts.
An OEM may briefly render that item before the check; zero transient appearance
cannot be guaranteed because promotion is decided asynchronously by the system.

Design reference: `JustNoiseWidget/JustnoiseWidgetLiveActivity.swift` shows Zap
artwork, "LOCKED IN FOR", a count-up timer and the mode name, on charcoal with
lime text. Android reuses the headline, real timer, active-mode name and lime
accent (`#D7FA00`); the system owns font/size/background/layout. Production reuses
the unchanged iOS Zap artwork through its release resource alias for the large
icon; the compact icon remains the monochrome Zap symbol. No custom `RemoteViews`, fake
media session or fabricated progress is used to force the iOS layout. The body
labels this as **Session time**, not verified uninterrupted protection. No mode
name or selected-app data is sent to analytics.

Primary references checked 2026-09-09:
- [Android Live Updates](https://developer.android.com/develop/ui/compose/notifications/live-update)
- [Android notification capability](https://developer.android.com/reference/android/app/NotificationManager#canPostPromotedNotifications())
- [AndroidX promotion request](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder#setRequestPromotedOngoing(boolean))
- [Supabase password sign-in](https://supabase.com/docs/reference/kotlin/auth-signinwithpassword)
- [Supabase PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow)
- [PostHog Android](https://posthog.com/docs/libraries/android)

No provider settings, accounts, analytics events, phone data or permissions were
changed during the reference audit. Store/privacy publication remains paused;
technical in-app transparency and secure data handling are required by the new
runtime scope, not a separate shop-publication task.

Historical A16 evidence remains important: the September 6 experiment showed real
Now Bar rendering only with Samsung's temporary developer override. The same APK
could have a promoted framework flag without a visible Now Bar under normal
settings. That does not become a default-user PASS from a production permission,
new artwork or new copy. See `spikes/android-integrated/evidence/session-notification-2026-09-06/README.md`.

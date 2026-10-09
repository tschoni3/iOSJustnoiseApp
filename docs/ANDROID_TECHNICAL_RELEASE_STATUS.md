# Android stability and technical release status

Updated September 20, 2026. Owner expanded the Android release to Sign-in, PostHog
and Samsung Now Bar alongside stability and technical packaging. See
`ANDROID_CONNECTED_RELEASE.md`. Privacy drafting/publication and shop work remain
outside scope; the authorized internal Play operation is now complete.

## Current result

**Authoritative September 20 Play candidate:** signed candidate `94741f2a` is the
current upload build under the available permanent identity `shop.justnoise.app`.
Google Play reported `com.justnoise.app` as already in use. Both new binary checksums
and all package/signing checks pass. The affected Chrome overlay/navigation, physical
Zap stop and History path pass on the A16. A real consented PostHog start/end pair
was received; after owner opt-out, a real start/stop cycle with validated Internet
produced no new events. Do not repeat these unchanged cases.

The final local account closure adds a complete synthetic A→B→A runtime regression.
All 142 account tests and the full 470-test production suite pass; release lint,
contracts, complete pre-device verification and signed artifact checks pass. The
behavior remains unchanged from the installed and owner-accepted candidate
`8dc7ba54`. The new release identity is intentionally reserved for a fresh internal-
track install; do not replace or clear the owner's accepted installation.

Read-only Google Cloud inspection confirms OAuth app name **Justnoise** and an app
logo, but unverified branding, only the Supabase authorized domain and no Android
OAuth client. The current functional browser OAuth flow therefore remains in the
final-test candidate. A native Credential Manager migration would require deliberate
Cloud/provider setup and a new tested binary; it is not a safe cosmetic hotfix.

The owner explicitly skipped A→B→A, recovery, registration and deletion because
the required password is unknown; no sign-out, reset or account mutation occurred.
The later consolidated final journey passed offline multi-app blocking, Zap stop,
History, automatic schedule start and the owner-visible Samsung Now Bar on the A16.
This newer real-device result supersedes the earlier absent-list/partial-rendering
observation. Play Console accepted `shop.justnoise.app`, version code 1 and the
signed AAB. Internal release `1.0.0 (1) – Internal` is active and available to the
selected tester list. There are no Play validation errors; only two non-blocking
diagnostic-symbol warnings. This paragraph supersedes the chronological intermediate
states below; see the final closure audit.

**September 20 — corrective candidate installed:** after the owner explicitly
confirmed the session was off, `8dc7ba54` was installed as an in-place A16 update.
Device package timestamp is 14:52:07; on-device APK SHA-256 matches the signed
candidate. Accessibility service is bound; no stale session notification is present.
No data clearing, permission change, account action or upload occurred. The targeted
Chrome-to-Justnoise switch test has been requested, not yet reported as passed.
Live account/PostHog and exact Now Bar acceptance remain open. This supersedes the
earlier not-installed/owner-away status below, not their historical test results.
See `android-app/evidence/a16-update-8dc7ba54-2026-09-20.md`. Release HOLD.

**September 20 — PostHog opt-out restart correction:** the reproduced failed-write
restart defect is fixed in local candidate `8dc7ba54`. Optional analytics grants
are process-bound; new processes and legacy unscoped grants default off until a
new explicit opt-in. Consent copy explains the intentional lifetime. No new event
properties or account/product-state changes. 469 production JVM tests pass, full
release lint passes (0 errors, 74 warnings, 2 hints), contracts and signed APK/AAB
verification pass. Not installed/uploaded. Live PostHog receipt/settings and A16
acceptance remain open; release HOLD. See
`android-app/evidence/posthog-audit-2026-09-20.md`.

**September 20 — owner away, physical tests deferred:** continue local work only;
no further phone interaction or update. Full release lint PASS (0 errors,
74 warnings, 2 hints), warnings triaged, candidate hashes independently rechecked,
and `75b4c6dd` handoff/checksums/lint report prepared. Physical stop/History and
overlay-fix acceptance remain DEFERRED, not passed. No iOS or provider/Play changes.
See `android-app/evidence/local-release-review-2026-09-20.md`. Release HOLD.

**September 20 — real hardware run in progress:** `8b40bd8f` automatically started
the 23:54 Focus/Chrome session with the Chrome keyboard open; diagnostic READY,
blocker, native Home, Settings and lock/unlock return passed this bounded sample.
A direct return from Chrome to Justnoise exposed a stale overlay; focused own-UI
event correction `75b4c6dd` has passed build/vital lint/static and signed artifact
checks, but awaits the owner's physical stop before update/device acceptance. Live Update is
PROMOTED_ONGOING with a moving lock-screen timer while Samsung's developer override
is OFF, but the requested bottom Now Bar capsule is not observed. Owner physical
stop/History checks remain pending. Release HOLD. See
`android-app/evidence/a16-hardware-pass-2026-09-20.md`.

**September 19 — owner-approved update installed:** `8b40bd8f` installed in place
on the idle A16 at 23:39:14; installed SHA-256 matches the signed candidate.
Accessibility remains enabled/bound. Focus / 1 app, History 4 Zaps / 200h 22m and
both disabled schedules are visibly retained. History/schedule helper paragraphs
are absent. No new session, schedule edit, account operation or permission change.
This closes the update step only; affected schedule/keyboard and other connected
acceptance remain pending. See `android-app/evidence/a16-update-8b40bd8f-2026-09-19.md`.

**September 19 — final helper-message cleanup and connected-boundary recheck:**
candidate `8b40bd8f` includes the lease-order correction and also removes permanent
schedule save/toggle/delete success cards and the remaining import-success text.
Real errors/setup recovery/consent remain. Build (33 seconds), vital lint, static
contracts and signed APK/AAB verification pass. Account/analytics wiring inspected;
no new defect established, no real event or account mutation performed. No emulator
or JVM suite run. Phone untouched on `cfa5e637`; already-requested update/retry
approval remains outstanding. Release HOLD. See
`android-app/evidence/connected-boundary-review-2026-09-19.md`.

**September 19 — lease-expiry correction built, hardware acceptance pending:**
the second A16 attempt at 23:24 on `cfa5e637` returned `LEASE_EXPIRED`. At 23:31,
normal UI confirms a failed receipt, disabled one-time test schedule and idle Zap.
Candidate `e553541c` moves package discovery before the original one-second lease
and uses package IDs without label loading/sorting; current mode/targets and
protected roles still validate at dispatch. Includes all helper-copy cleanup.
Signed build, vital lint, static policy/contracts and APK/AAB checks pass. Not
installed; owner approval requested for the targeted update/retry. No emulator or
JVM suite run. No device pass claimed; release HOLD. See
`android-app/evidence/schedule-discovery-order-2026-09-19.md`.

**September 19 — readiness diagnosis installed; helper-copy cleanup in progress:**
owner approved the in-place diagnostic update `cfa5e637`; installation succeeded
on the idle A16, and its read-only service dump returns `NOT_ATTEMPTED`. Signed
artifact/static checks pass. No runtime behavior was loosened. The owner then
requested removal of permanent helper copy, including historical interruption/
timing warnings. The presentation-only cleanup is built as `766a1b5b` with signed
APK/AAB checks and build-integrated vital lint passing; not installed yet.
No second test schedule has been armed. The scheduled-start failure remains open.
See `android-app/evidence/schedule-readiness-diagnostic-2026-09-19.md`.

**September 19 — approved A16 scheduled-keyboard test FAILED to start:** the
owner approved `test starten`. A one-time Focus/Chrome schedule at 22:54 ran its
dispatch but recorded `App blocking needs setup` and disabled itself without
starting a session. Chrome's keyboard remained visible. Subsequent Android
metadata showed Accessibility enabled/bound, and app Settings reported ready.
The exact readiness failure is not yet isolated; keyboard interception and Now
Bar are not accepted by this attempt. Existing Release Check and visible History
remain unchanged. See `android-app/evidence/a16-scheduled-keyboard-2026-09-19.md`.
The test schedule is now off; no owner Zap stop is required. Release HOLD.

**September 19 — Now Bar prerequisite recheck (read-only):** the actual A16 reports
Android 16, SDK 36, full SDK 36.1, One UI property 80500, security patch 2026-07-05.
It meets the source's 36.1 version guard. Normal Justnoise Samsung notification
settings visibly allow notifications, lock-screen display and content. No separate
Live notifications control was visible. The SDK-defined promotion-settings intent
opens that same app-notification page. No setting was changed or session started;
the Samsung developer override was not inspected in this slice and is not assumed
OFF. Active-session rendering with it confirmed OFF remains required.
Android's current documentation explicitly permits BigTextStyle/chronometers but
allows extra OEM eligibility criteria; Samsung describes supported-app controls.
These sources do not establish Justnoise Now Bar support. At that prerequisite
checkpoint, test-schedule approval was still outstanding. The later authorized
attempt and its failure are recorded above; no active Now Bar test resulted.

Sources checked September 19:
[Android Live Updates](https://developer.android.com/develop/ui/compose/notifications/live-update)
(updated September 16) and
[Samsung Now Bar settings](https://www.samsung.com/ca/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/).

**September 19 — UI-copy cleanup installed and visually checked:** candidate
`25098600` removes successful schedule receipts and implementation commentary
from normal screens, simplifies Live Update settings, and preserves real History
warnings in plain language. Installed in place at 22:31:27 on the idle A16;
installed APK hash matches. Actual schedules, History and ordinary Settings
screens inspected. No session/permission/data mutation; no emulator or JVM rerun
for this presentation-only change. Signed build/package checks pass. See
`android-app/evidence/ui-copy-cleanup-2026-09-19.md`; broader release HOLD remains.

**September 19 — real import visibility PASS:** after the owner confirmed the
import, the A16 product UI showed Focus with 1 selected app, History with 4
sessions and retained interruption/timing warnings, and the disabled Release
Check schedule referring to Focus. No session or schedule was activated during
inspection. Basic owner-device import visibility is verified; full row equality,
cross-account isolation and the other physical release gates remain unverified.

**September 19 — import candidate now on the A16:** owner requested hardware
testing. The idle phone was updated in place to `7ee1df3c` at 22:22:47; installed
APK hash matches the preserved candidate. No uninstall/data clear, permission
grant or owner-data import occurred. AccountActivity is foreground for the
owner's explicit import confirmation. Real mode/History continuity remains
unverified; no further emulator tests ran in this hardware-test turn.

**September 19 — explicit local-data import built, not installed:** the owner
approved an opt-in, one-time transfer after the signed-in mode picker was
confirmed empty. Candidate `1.0.0-1-7ee1df3c` offers modes/app selections,
schedules (imported disabled) and History. It preserves existing account data,
deduplicates exact records, rejects conflicting History UUIDs before claiming
the source, and journals interrupted transfers for the same account only.
Declining preserves the source and keeps the option in Account settings.
**452 release JVM tests, release lint, full development verifier, two targeted
SQLite tests on a disposable API 36 emulator and signed artifact checks PASS.**
The A16 still has `1e0b85fd`; no owner data was imported and no new installation
was performed. A16 import/UI acceptance and full-release gates remain open.
See `android-app/evidence/local-data-import-2026-09-19.md`. No iOS changes were
made by this task; an unrelated Shield source diff remains untouched.

**September 19 — post-Google entry observed on updated build:** after the owner
reported pressing Google sign-in, foreground inspection and a screenshot show
the actual MainActivity Zap screen, not the account gate. It is idle and shows
"Select a Mode" / "Choose apps to block". This proves product entry after the
reported login action, not independently the complete browser sequence or every
account operation. Subsequent inspection of the actual mode picker and the
owner's confirmation established that this account's visible mode list is empty;
the earlier local Focus mode is not displayed there. This is not proof that
local data was deleted. Registration/recovery, account isolation and other device
gates remain open.

**September 19 — approved update installed:** owner message "update freigabe"
authorized `1.0.0-1-1e0b85fd`. Before installation the visible idle Zap screen
showed mode Focus and one selected app. `adb install -r` returned Success;
package last-update time is 21:48:40. Installed `base.apk` SHA-256 is
`4d38aa82eb72342c57938a0f399bceba7a144aff3db529dcb25adb797fc9e99f`, matching
the approved snapshot. CE/DE inodes remain 71241/71025; production Accessibility
remains enabled. No uninstall, data clear, permission grant or account mutation
was performed. Opening MainActivity automatically routes to AccountActivity.
Owner-entered Google sign-in and return-to-product acceptance are next; pairing,
mode/History visibility after login and the remaining device checks are not yet
revalidated. Secure account-screen capture was not attempted. Distribution HOLD.

**September 19 — A16 available again; update approval pending:** read-only ADB
checks identify the authorized USB-connected SM-A165F, Android 16 / API 36, with
the lock screen not showing at inspection. Justnoise remains version 1.0.0 (1),
last updated September 9 at 13:05:41; CE/DE data inodes remain 71241/71025.
The preserved `1.0.0-1-1e0b85fd` APK and AAB hashes still match their signed report.
The owner has been asked to approve this exact in-place update, conditional on
an idle session. No approval has arrived, no update was performed, and current
session state has not yet been established. Device availability alone does not
close any outstanding functional acceptance check. The build remains frozen.

**September 12 — personal-test candidate frozen:** snapshot `1.0.0-1-1e0b85fd`
contains the required-account entry refinement, guarded pairing-to-setup route and
bounded cold-NFC account-restoration wait. **431 release JVM tests (zero failures,
errors or skips), release lint, full Android development verifier and signed
APK/AAB checks pass.** The final full verifier completed with exit 0; its earlier
obsolete run was cancelled, not counted as a pass. Snapshot hashes were rechecked.
At that checkpoint no phone was visible to ADB, and this candidate was not installed.
The September 10 target was missed; device/auth/Now Bar acceptance still prevents
full-goal completion. No new features are being added before owner testing. See
`android-app/evidence/account-entry-refinement-2026-09-12.md`.

**September 9 — required account entry correction built:** production Android now
gates product navigation and manual/direct/scheduled starts on the reconciled
signed-in workspace. Offline signed-in use and safe stops remain; unowned local
data is preserved without a guest route. **422 release JVM tests, release lint,
full Android verifier (non-incremental Kotlin), and signed APK/AAB checks pass.**
Snapshot `1.0.0-1-f3004e27` is preserved, not installed or distributed. A16 entry,
offline/cold-start and visual acceptance remain pending. See
`android-app/evidence/required-account-entry-2026-09-09.md`. Older optional-login
round-trip evidence below does not prove this corrected mandatory-entry behavior.

**September 9 — logout/local-mode round trip confirmed:** the owner reports no
modes in the newly signed-in account and `hunk` visible again after normal sign-out.
This confirms the bounded account/local-mode separation and logout sequence on
the A16; History restoration and distinct A/B accounts remain unverified.

**September 9 — real Google sign-in confirmed:** the owner reports successful
login on the installed A16 candidate after the backend correction. The saved
Supabase client-ID list contains both the intended Web client and the unchanged
iOS client. No new APK was needed. Account/local isolation and the remaining
recovery/logout/deletion checks are still open; no overall release GO is asserted.

**September 9 — Google OAuth entry unblocked:** after the owner's Supabase save,
the synthetic authorization probe returns HTTP 302 to Google with the intended
existing Web client and exact Supabase callback. The missing-secret error is gone
at entry; real Google consent, token exchange and Android account acceptance still
require the owner's retry on the installed build. No new APK was installed.
See `android-app/evidence/google-oauth-provider-failure-2026-09-09.md`.

**September 9 — real Google login blocker identified:** the owner reports the
Supabase browser entry fails. A synthetic, no-redirect authorization probe returns
HTTP 400 `Unsupported provider: missing OAuth secret`, despite Google reporting
enabled in public Auth settings. This requires approved shared-provider setup,
not a password reset or speculative APK rebuild. No backend setting was changed.
See `android-app/evidence/google-oauth-provider-failure-2026-09-09.md`.

**September 9 — access resumed:** the A16 is connected again and Supabase visibly
contains the saved Android-only callback alongside the two unchanged iOS entries.
The old build showed a real lock-screen timer/mode/Zap card, not a separate Now Bar
PASS. After the owner's explicit approval and an observed idle session,
`ffb1ec06` was installed in place at 13:05:41. The installed APK hash matches the
verified candidate; visible mode/selection/History and Accessibility are retained.
The account screen is open for owner-entered sign-in, with Analytics left off.
No account mutation has been performed by the agent. See
`android-app/evidence/connected-resume-2026-09-09.md`.

**September 9 — full-goal audit and analytics revocation fix:** account reload no
longer restores an older opt-in after an opt-out storage failure; it retries the
revocation with capture off. **410 release JVM tests, release lint, full Android
verifier and signed APK/AAB checks pass.** Snapshot `1.0.0-1-ffb1ec06` is preserved
and subsequently installed as recorded above, not distributed. Full goal remains unproved: live auth/A-B/analytics,
normal-settings Now Bar and the affected A16 Chrome sequence remain open. See the
requirement-by-requirement audit in
`android-app/evidence/connected-goal-audit-2026-09-09.md`.

**September 9 — recovery Activity lifecycle fix:** closing during an in-flight
exchange now queues cleanup using the validated callback ID, including before the
first rendered frame. Account switches have explicit TalkBack labels. **407 release
JVM tests, release lint, full Android verifier and signed APK/AAB checks pass.**
Snapshot `1.0.0-1-e42c39ad` is preserved, not installed/distributed. ADB and wireless
discovery currently show no devices; host free space was about 1.6 GiB. Supabase
save confirmation and live account/A16 acceptance remain open. See
`android-app/evidence/account-lifecycle-2026-09-09.md`.

**September 9 — Android registration return fixed:** signup now explicitly uses
the encrypted Android PKCE return flow instead of inheriting the shared placeholder
Site URL. Unconfirmed signup retains local data; valid confirmation is accepted
once. **405 release JVM tests, release lint, full Android verifier and signed
APK/AAB verification pass.** Latest snapshot `1.0.0-1-6b14c24a` is not installed
or distributed. Backend save confirmation and real account/A16 acceptance remain
open. See `android-app/evidence/registration-return-2026-09-09.md`.

**September 9 — signed recovery follow-up:** **404 release JVM tests, release lint,
full Android verifier and signed APK/AAB checks pass.** Snapshot
`1.0.0-1-2cb5a5e7` is preserved with hashes/report, not installed or distributed.
Supabase Dashboard is now accessible; the Android redirect addition is prepared,
not saved. Existing iOS entries are intact. Newly confirmed: the shared Site URL
is a placeholder, so Android registration needs its own confirmation-return flow;
the project also displays a quota/grace-period warning. Both are recorded in
`android-app/evidence/account-recovery-2026-09-09.md`. No release GO is asserted.

**September 9 — account recovery hardening:** same-account reauthentication now
preserves deletion intent, expired deletion credentials refresh, abandoned
password-update credentials are cleared, and interrupted claimed links have an
explicit retry. **401 release JVM tests, release lint and the full Android
pre-device verifier pass (including 44 artifact regressions).** Live public Auth
settings report Google/email/signup enabled and email confirmation required;
this is not end-to-end account acceptance. The owner approved only an Android
redirect addition, but the Dashboard requires login and no backend setting has
changed. The prior signed integration snapshot predates this follow-up. See
`android-app/evidence/account-recovery-2026-09-09.md`.

**September 9 — account UI/runtime and existing PostHog events connected:** grouped
Settings now opens native account actions backed by the real coordinator and
Supabase adapter. Workspace changes invalidate retained product views. Separate
analytics opt-in uses existing iOS start/end event names with no private event
properties and a real cancellation/purge boundary. **392 release JVM tests,
release lint and the full Android verifier pass; 44 artifact regressions pass.**
These use mock HTTP, not real account/device acceptance. Expired/revoked-session
UX, broader recovery/recreation checks, live provider configuration and rendered
UI acceptance remain required. Signed integration snapshot `1.0.0-1-0bd6b56c`
passes APK/AAB package checks but remains uninstalled and distribution HOLD.
See `android-app/evidence/account-runtime-ui-2026-09-09.md`.

**September 9 — encrypted pending login links:** recovery/Google flows now have
durable verifier storage, origin/query validation, expiry and account-generation
guards. A real-SDK test found and fixed an asynchronous verifier-write race and
padded-verifier compatibility issue. **380 release JVM tests, release lint and the
full Android verifier pass.** Account UI/callback delivery, live provider
configuration and PostHog remain unfinished; no new phone build was installed.
See `android-app/evidence/auth-pending-flow-2026-09-09.md`.

**September 9 — native Supabase adapter:** production now compiles the existing
project's email login/registration, refresh, PKCE recovery/code exchange, password
update and deletion requests through a deliberately non-persisting SDK adapter.
**365 JVM tests, release lint and the complete Android verifier pass.** Twelve
new SDK tests use intercepted HTTP, not real accounts. The account Activity/UI,
pending-flow security/callback wiring, Google login and PostHog remain unfinished.
Only production gains INTERNET; the paired A16 build is unchanged. See
`android-app/evidence/supabase-provider-2026-09-09.md`.

**September 9 — account coordinator connected to local stores:** login acceptance,
logout, restore and confirmed-deletion cleanup now coordinate the encrypted journal
with real product workspaces, including crash rollback and active-session safety.
**353 release JVM tests pass**, including 16 coordinator regressions; release lint
passes. Provider/UI, retained-view invalidation and the real telemetry boundary
remain required before exposing accounts in the app. See
`android-app/evidence/account-coordinator-2026-09-09.md` for scope and verification.

**September 9 — Play developer verification:** owner reports that the developer
account is now verified. This is owner-provided status, not an independent Console
inspection. No upload, package registration or distribution was performed. The
remaining connected-release implementation/device checks are unaffected.

**September 9 — targeted account erasure implemented:** the local persistence
layer can now remove one account's archived configuration and History database
without global withdrawal or deleting local/other-account data and pairing.
Durable cleanup ownership blocks unfinished handoffs and supports crash/failure
retries. Production unit tests: **337 passed**, release lint PASS. Full verifier
result is recorded in `android-app/evidence/account-erasure-2026-09-09.md`.
This is not server deletion or a completed login: the production coordinator,
provider/UI, analytics boundary and native account acceptance remain unfinished.

**September 9 — account-owned product storage implemented:** canonical local
storage now isolates modes, schedules/receipts and History into local/UUID
workspaces, retains legacy data without silent assignment and uses a durable,
idle-only handoff barrier. Account-scoped schedule bridges reject queued writes
from a previous workspace. 331 release JVM tests (14 new workspace cases), release
lint and the complete Android verifier pass. No new phone installation or actual
account UI/provider wiring is claimed. The combined coordinator, retained-UI
invalidation, account-specific deletion, PostHog and affected device acceptance
remain required. See `android-app/evidence/account-workspaces-2026-09-09.md`.

**September 9 — installed candidate and account-storage foundation:** the owner-
approved db0c2798 candidate was updated in place on the connected A16; its installed
APK hash exactly matches 52606ff0. The existing mode/selection and Accessibility
enablement remain visible; the development package stays disabled. Owner manually
enabled notifications. The new physical start/Now Bar and keyboard-window acceptance
are still pending, not passed. See `android-app/evidence/live-update-a16-2026-09-09.md`.

Subsequent source work adds production-only encrypted auth-session storage with
late-callback generations and durable deletion phases. 317 release JVM tests,
release lint and the full pre-device verifier passed. It is not yet connected to
provider/UI or product namespaces and is not included in the installed snapshot.
See `android-app/evidence/auth-journal-2026-09-09.md`. Sign-in, safe product-account
separation and PostHog still require implementation and acceptance.

**September 9 — Live Update implementation:** production can request native
promotion; the owner now requires no ordinary notification fallback. The native
timer uses the persisted active-mode name, iOS headline/artwork/lime accent within
Android's template. Recovery/unsupported/denied states stay silent. New Settings
rows distinguish permission, eligibility and actual framework promotion without
claiming Samsung rendering. Account/analytics adapters are audited/planned, not
yet wired into the app. Current build/evidence follows in
`android-app/evidence/live-update-release-2026-09-09.md`.

Default-user Now Bar acceptance remains open: the historical A16 rendering PASS
required a temporary Samsung developer override. No phone setting was changed in
this implementation. The Chrome keyboard-window failure also remains open until
its affected physical check passes. Distribution stays HOLD.

**September 9 — keyboard-window correction built, not installed:** native event
handling now preserves the underlying app only for the current IME's exact framework
SoftInputWindow; protected surfaces and keyboard activity/settings transitions
remain ordinary app transitions. No new permission, event subscription, node/window
retrieval or persisted data was introduced. Transient class/default-IME metadata is
documented in the technical inventory. Nine new regressions and all 292 release
tests pass; full Android verifier, release lint and signed APK/AAB package checks
pass. New AAB SHA prefix 7c49dce6 / APK 1bd44cd6, prior snapshot preserved.
No phone/iOS changes. The observed Samsung failure remains open until the affected
device check passes; no release/distribution GO. See
[candidate evidence](../android-app/evidence/keyboard-window-fix-2026-09-09.md).

**Latest release finding — scheduled Chrome address-entry gap (20:13 CEST):**
the owner enabled exact-alarm access; the one-time hunk schedule was registered,
delivered and recorded a real active session. Chrome's already-focused address
field nevertheless stayed accessible, including keyboard interaction and reopening
while that state remained focused. Exiting address entry and foregrounding Chrome
again showed the blocker. Scheduled dispatch/start is verified; this interception
case failed and blocks release acceptance pending diagnosis, a targeted fix and
recheck. The one-time entry disabled itself after delivery. The phone was returned
Home, session active awaiting owner Zap stop. No runtime/build/iOS changes were
made during this check. See the physical evidence below for exact scope.

**Follow-up after owner "gestoppt":** release idle confirmed; History/day detail
now show 2 completions / 10m total, no visible interruption warning. This closes
the scheduled stop/completion observation, not the Chrome interception failure.
Read-only diagnosis found no IME/auxiliary-window distinction in the foreground
tracking/overlay rules; a targeted correction and affected recheck remain next.

**Latest A16 release sample:** manual setup complete; the release showed active
mode hunk and blocked Chrome through a tap/swipe. Native Home worked. After the
owner's physical Zap stop, Chrome accepted address-bar focus again, Justnoise
showed idle with hunk / 1 app, and History showed one completion / 3m without a
visible interruption warning. The scoped release cycle is complete; do not repeat
it. Scheduled-start, negative-input/repeat and broader release acceptance remain
distinct. See [physical evidence](../android-app/evidence/a16-release-2026-09-08/README.md).

**Subsequent A16 checkpoint:** after explicit owner approval, the preserved signed
APK was installed separately and its installed checksum verified. Development was
idle (revision 68), then temporarily disabled without data deletion to prevent
competing NFC/services. Release visible setup and manual pairing/consent remain
pending; the phone was screen-off at launch inspection. See
[scoped installation evidence](../android-app/evidence/a16-release-2026-09-08/README.md).
Distribution and safety decisions remain HOLD.

**Later September 8 update — signed local candidate built:** the owner explicitly
approved "jetzt bauen", allowing build/signing before the safety GO while keeping
installation/distribution blocked. `android-app/` now packages the same canonical
sources as **Justnoise / com.justnoise.app / 1.0.0 (1)**. The signed AAB and APK,
283 passing release tests, artifact/signature checks and signing backup boundary
are documented in [the production handoff](../android-app/README.md).
No phone or iOS changes occurred. Historical unsigned-only statements below describe
the preceding development-verifier slice, not the newly built production candidate.

The integrated development candidate builds and passes local checks. It is **not
a signed production release**. No app-runtime, UI, iOS or phone changes were made
in this verification slice. No new feature or additional device is required here.

### Completed in this slice

- Hardened the packaged-APK checker to reject instrumentation, test-only builds,
  cleartext opt-in, enabled/unspecified backups, a disabled/unknown application and
  application/component process overrides. These align APK safeguards with checks
  already present in the AAB verifier. This fixes a verification gap, not an
  observed app crash or change to phone behavior.
- Added five regression tests. All **41 artifact-checker tests pass**.
- Full required local verifier passed: product contracts (18 colors, 27 copy
  entries, 89 cases), existing static checks, debug/release builds and lint.
  Lint success does not mean zero warnings; unused-resource warnings remain.
- Explicitly reran `testDebugUnitTest --rerun` in all three existing Android
  projects after the full verifier. **298 tests, zero failures/errors/skips**;
  these are actual executions, not just Gradle up-to-date results. Coverage includes
  modes/persistence, schedule dispatch/startup, History timing, state reconciliation,
  NFC parsing/mutation coordination and protected-package/window rules.
- The actual development release APK passed the strengthened packaged check.
  Read-only JDK inspection confirms the current AAB is unsigned.
- Existing physical feature/safety acceptance is retained. No A16 command, install,
  permission change, Zap scan, cleanup or instrumentation occurred. No new physical
  NFC, performance, TalkBack or signed-release acceptance is claimed.
- iOS app/project/test tracked diff was empty; `git diff --check` passed.

### Artifact identity

All artifacts below still use `com.justnoise.androiddev`, version code 1,
version name `0.1-integrated-dev`. No production promotion or signing key was created.

| Artifact under `spikes/android-integrated/app/build/outputs/` | SHA-256 |
| --- | --- |
| `apk/debug/app-debug.apk` | `8c04d759ac6696684d1cefbb41460f4eb727ebc761de0015af5a0199ae45e4eb` |
| `apk/release/app-release-unsigned.apk` | `30d242ec47a5f7c09d2e443aa6758dd68e6a374a3b8ecc65a6704f936c010bfd` |
| `bundle/release/app-release.aab` | `4513dbf6275e67229fff141d6a6c66fcffe31a65efbd495b2d7ca1e9173a5abd` |

The hashes are unchanged by checker-only edits. The owner's A16 retains the
previously accepted modes build, not the newer locally verified presentation build.

## Remaining technical sequence

1. **Resolve the existing internal-beta decision, without repeating completed
   acceptance.** Real foreign/unpaired-tag rejection is unverified; only the paired
   owner Zap is available. Rapid re-presentation has owner-reported success but lacks
   an isolated timed transition record. Keep both limitations explicit. Do not ask
   for another phone/tag or rewrite the paired tag. A synthetic test is not physical
   evidence. The [decision record](../spikes/android-gate0/evidence/DECISION_RECORD.md)
   remains HOLD; any changed acceptance scope requires an explicit owner decision,
   not silently marking missing results PASS. This is the repository's sequencing
   rule, not a claim that Google forbids local compilation/signing.
2. **After the required GO, promote the canonical app once.** Use public name
   Justnoise, approved production ID `com.justnoise.app`, a deliberate release
   version and one canonical implementation. Preserve modes, pairing, consent,
   schedules and History. Do not duplicate restriction/NFC engines or overwrite
   the owner's development install.
3. **Configure and verify signing.** Protect the upload key outside Git, establish
   its certificate fingerprint and safe backup, and reject missing credentials
   instead of falling back to a debug key or an unsigned output. Produce and verify
   the production AAB and its corresponding installable APK. Check packaged
   components, release dependencies/tooling and native alignment against that exact
   candidate; existing development evidence is not final-release proof.
4. **Run only final-candidate acceptance.** Coordinate the separate release install
   and manual service switching so development/release NFC routes and services do
   not compete. Preserve owner data. Cover the package-change smoke plus genuinely
   missing keyboard/inset, accessibility and responsiveness checks; do not repeat
   ordinary mode/schedule/Chrome or cleanup tests merely for new counters.
5. **Hand off an immutable technical candidate.** Record AAB/APK checksum, package,
   version, signing-certificate fingerprint and exact acceptance results. Actual
   distribution is outside this development-only slice.

## Evidence

Logs for this slice:

- `/private/tmp/justnoise-release-stability-2026-09-08.log`
- `/private/tmp/justnoise-stability-gate-tests-2026-09-08.log`
- `/private/tmp/justnoise-stability-ui-tests-2026-09-08.log`
- `/private/tmp/justnoise-stability-integrated-tests-2026-09-08.log`

Prior rendered/phone evidence remains linked from the
[experience review](ANDROID_EXPERIENCE_REVIEW_2026-09-08.md) and
[release handoff](ANDROID_PLAY_INTERNAL_UPLOAD.md). Their dated older entries are
historical, not new requests to repeat completed work.

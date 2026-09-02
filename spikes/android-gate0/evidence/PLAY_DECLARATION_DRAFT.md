# Google Play Accessibility declaration draft

Status: **draft / HOLD** — not legal advice, not submitted, and not evidence of Google Play approval

Scope update, 2026-09-03: this draft covers the multi-app development baseline and
the local History design recorded before integration in
[`ANDROID_SESSION_HISTORY.md`](../../../docs/ANDROID_SESSION_HISTORY.md). History
storage, v3 consent, list/summary and cleanup are implemented, with passing local
History instrumentation and full verification recorded in
[`ANDROID_PREDEVICE_READINESS.md`](../../../docs/ANDROID_PREDEVICE_READINESS.md).
These are not A16, policy or release acceptance. The dated single-target A16 checkpoint
remains unchanged and build-specific. Working schedules remain required MVP work.
Do not submit this draft unchanged: reconcile it with the implemented release,
including schedule-triggered behavior, retention, labels and reviewer steps. Preview
screens are not implemented features.

## Core functionality

Justnoise is a focus product. At the user's explicit request, the Gate 0 candidate compares the foreground package identifier from Android window-state changes with the locally selected apps copied into the active session's target snapshot. During a user-started focus session, an exact match displays a Justnoise-owned interruption overlay naming that foreground app. The service does not suspend the target package and is not a tool whose primary purpose is assisting people with disabilities.

## Why the Accessibility API is proposed

A normal consumer-installed application does not receive device-owner package-suspension authority. The spike is testing whether a narrowly configured Accessibility service can produce the disclosed user outcome without reading window content or requesting broad installed-app visibility. If Google Play policy review does not support this declared use, the broad consumer Android port remains on HOLD even if device tests pass.

## Data access and handling answer

- Accessibility event type: `TYPE_WINDOW_STATE_CHANGED` only.
- Fields accessed: `eventType` as a callback guard and `packageName` as the only observed app value.
- Derived value: exact package membership in the active-session target set or equality with Justnoise, combined with local consent/session/recovery state to show, hide, or retain the overlay.
- Accessibility node/window content, typed text, passwords, notifications, and UI content: not accessed.
- Retention of event data or foreground history: none.
- Sharing or network destination: none. The app has no Internet permission.
- Configuration retained locally: selected packages, active-session UUID/target snapshot/clocks/process identity and interruption/eligibility flags, last-finished session ID, disclosure version/timestamp, enforcement state/revision/heartbeat, and a Keystore-backed Zap HMAC digest. Successful stop removes active state; saved selection and pairing remain.
- Local History retention in source: only session UUID, start/end times, non-negative duration, selected-target count and interruption flag. A pending completion in private preferences drains idempotently to `session_history.db` before later starts/History reads, not during stop; no package names, foreground-event stream or raw tag data enter History. Records remain until withdrawal, app-data clear or uninstall. No schedule data is stored by this extension.
- Separate local platform metadata: enabled-service components for Justnoise readiness, protected-package resolution, and window bounds/insets for safe overlay placement. None is used as app-usage history or sent off-device; see `DATA_INVENTORY.md`.

## Prominent in-app disclosure shown before consent

> Justnoise Gate 0 uses Android Accessibility only while you have explicitly enabled this prototype. It observes window-change events and reads the foreground package identifier so it can compare that identifier with the apps you selected. It does not read screen content, typed text, passwords, notifications, or accessibility nodes. Foreground event contents are not retained or sent anywhere. The selected apps, consent version, enforcement state, and a keyed Zap digest stay only on this device. For History, it saves a random session ID, start and end times, duration, number of selected apps, and whether the session was interrupted. History contains no app names or foreground event log. Session time does not prove continuous app blocking. History stays only on this device until you withdraw, clear app storage, or uninstall. You can withdraw below, disable the service in Accessibility settings, Force Stop, or uninstall at any time.

The disclosure is followed by an initially unchecked control reading: “I understand this disclosure and choose to test the Accessibility capability.” Only the user's subsequent button press records consent and opens Accessibility settings. Installation, launch, onboarding, app selection, or NFC scan does not open settings. Current-version consent stays visibly checked until withdrawal. The History wording is `gate0-accessibility-v3`: v1/v2 acceptance is not silently upgraded. Saved selection and Zap pairing remain; legacy active sessions can still stop, but are not backfilled into History. Only new starts under v3 are History-eligible.

## User control and escape

The service is manually enabled in system Accessibility settings and can be manually disabled there. The white **Keep going** primary action returns to Justnoise; separate overlay buttons open Accessibility settings, App info/Force Stop, and Home. Justnoise, launchers, Settings, System UI, permission/package surfaces, installer/store routes, dialer/emergency packages, and resolved Home/dialer packages are excluded from target selection. Withdrawal attempts to remove local Gate 0 state and requests self-disable; the local follow-up reports incomplete cleanup instead of claiming successful deletion. The History extension must additionally clear its pending record/database through a durable deletion marker and report success only after all cleanup succeeds. Force Stop and uninstall remain user-controlled escape routes; a failed History insertion must not re-block a committed stop.

## Static review checkpoint — 2026-09-02

Scope: internal source/package review, not Google Play acceptance or a completed
G0-13/G0-18 device cell. The integrated APK inspected at this checkpoint was source
`d09833d`, SHA-256
`6487e3f462328b9a78d8bc767c36b4c7ee1e0fdaf483e93235725e35a1fb3d77`.
Both spike/integrated source and merged debug manifests were inspected; `aapt2`
also inspected the integrated APK's permissions and packaged service XML.

| Boundary | Static finding | Remaining proof |
| --- | --- | --- |
| Permissions | NFC plus AndroidX's app-scoped signature receiver permission; no Internet, broad package visibility, usage-access, or draw-over-apps permission in the inspected APK | Recheck every release dependency and merged/packaged manifest; existing source checks do not exhaustively cover merged dependency manifests |
| Accessibility | Packaged metadata remains window-state-only, `canRetrieveWindowContent=false`, `isAccessibilityTool=false`, with no retrieval flags | Runtime logs/network observation and complete physical safety matrix |
| Consent | Shared setup shows separate disclosure before an unchecked control; the enable button records consent before opening settings; the service refuses missing/current-version-mismatched consent | Record fresh install, refusal, re-entry, acceptance, revocation, and re-consent on the actual release flow |
| User-defined behavior | Exact selected-package rule during a user-started session; no autonomous planning, content-based decisions, or simulated control of the target app | Product/policy acceptance of the observed interruption behavior and limits |
| Data | Reviewed app source has no logging/network client; inventory now explicitly includes event-type guard, service-enable metadata, protected packages, and overlay geometry | Dependency/runtime review is still required; static absence is not a network trace |
| Withdrawal | Review found that setup ignored the cleanup Boolean and always claimed deletion; follow-up now maps success/failure to distinct feedback and was later installed while idle | Fault-injected deletion and recovery remain untested; installation is not proof of deletion behavior |
| Release identity | This checkpoint inspected a debuggable prototype with an exported Compose preview Activity, not a release artifact | Review production ID, signing, release-only dependencies, declarations, and final App Bundle after the applicable beta checkpoint |

The withdrawal UI wiring regression failed before the fix. Afterward, both pure
feedback tests and all nine static-policy tests passed, followed by the complete
`scripts/verify-android-predevice-readiness.sh` run (contracts, JVM tests, lint,
and all development APK builds). The follow-up integrated APK SHA-256 is
`c0d7d926630ed1c0cd40a76515ca7f09d9b7ea94d09c8725bb75050f6cacbc5d`;
packaged permission/metadata inspection remained unchanged. It was initially kept
off the phone during navigation testing, then installed with `adb install -r` after
idle/revision `19` was confirmed. The installed checksum matches; pairing record,
Chrome selection, idle state and a connected/advancing service heartbeat were retained.
See the build-scoped September 2 record. No paired-device instrumentation, consent
withdrawal, or iOS changes were made.

Google's [Accessibility guidance](https://support.google.com/googleplay/android-developer/answer/10964491)
permits deterministic user-defined automation but requires a declaration and separate
in-app disclosure/affirmative consent for non-accessibility tools. This supports the
candidate's intended boundary; it is not approval of Justnoise.
The [sensitive API policy](https://support.google.com/googleplay/android-developer/answer/16558241)
also requires accessible disable/uninstall routes and listing disclosure. Missing
system controls therefore remain a release issue until physically verified, even
when the app offers its own escape buttons.
The [package-visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779)
requires the narrowest justified inventory access. Scoped launchable-app discovery
has a documented selection purpose; removing `QUERY_ALL_PACKAGES` alone does not
establish approval of the complete product.

## Minimal internal-beta reviewer walkthrough — draft

Updated for the source-integrated local-only History extension on 2026-09-03. These are review
instructions and expected outcomes, not a claim that every step
has passed or that this source is installed on the A16. Build-specific single-target results remain in
[the September 2 evidence](A16_PARTIAL_RUN_2026-09-02.md).

- **Current development app:** `com.justnoise.androiddev`, label **Justnoise Android Dev**.
  The setup screen still uses Gate 0 wording. Do not upload its debug or unsigned
  development artifacts as the release.
- **Planned internal-test release:** `com.justnoise.app`, public name **Justnoise**.
  Registration, signing and a Play-delivered release are not established by this draft.
  Reconcile the labels below with the final release UI before supplying instructions.
- **Access:** no app login, subscription or review account is needed. An unlocked
  NFC-capable Android 12+ phone and a compatible physical tag containing one valid
  UTF-8 NDEF Text record are needed. Pair it through the visible local pairing flow;
  no private production payload, factory allow-list or hidden review bypass is used.

1. Begin with a fresh review installation on the agreed test device, not by clearing
   the owner's existing paired app. Open **Open setup** or **SETUP**. Under **Apps to
   block**, press **Select apps to block**, select multiple launchable apps such as Chrome and
   YouTube, then **Save**. Show that reopening restores the saved selection and
   **Cancel** discards unsaved edits. This source has one saved selection, not multiple
   named modes. An empty selection cannot start; unavailable saved apps are shown and
   must be removed while idle before starting.
2. Read the separate Accessibility disclosure. Initially leave the consent box
   unchecked: **Continue to Accessibility settings** is disabled. Leave and return
   to demonstrate refusal/re-entry. Then explicitly check the box, press that button,
   and manually enable **Justnoise Android Dev app blocking** in Android settings.
   For a separate upgrade check, verify that v1/v2 consent does not bypass acceptance
   of v3, while the legacy selection and pairing remain present. A legacy active
   session must remain stoppable without creating a backfilled History entry.
3. Return to setup, press **Pair or replace test Zap**, and scan the physical tag.
   Pairing is device-local; it does not create an online account or verify a purchase.
4. Use **Scan Zap to start** on the main surface and scan the paired tag. Open the
   selected apps in turn: each should show its own name on the Justnoise interruption.
   **Keep going** returns to Justnoise without ending the session; opening a selected
   app again should block. While the session is requested, **Select apps to block**
   should be disabled, with guidance to stop using the Zap before editing targets.
   Non-selected and protected
   system apps must remain usable.
5. After at least three seconds, use **Scan Zap to stop** and scan the paired tag;
   all selected apps should become usable and selection editing should return. Also demonstrate the advertised direct
   route from unlocked Home: tap the paired tag to start or stop without first opening
   Justnoise. Report any chooser or failure honestly; do not describe a foreground
   scan as a successful direct tap.
6. Open the real **History** view. The new v3 session should appear once with its
   start date/time, duration and selected-app count; reopening must not duplicate it. The
   summary must say **Session time**, not verified protected time. Exercise a
   deliberate recovery interruption on disposable test data and verify its
   **Blocking interrupted** label. No app names, foreground-event log, 90-day calendar or
   weekly insight should be implied by this list.
7. Show the ordinary Home/Back and escape routes. **Disable Accessibility** opens
   Android settings; **App info / Force Stop** opens app controls. In a deliberate
   cleanup test, **Withdraw consent and clear Gate 0 data** requests local cleanup and
   service disablement, including pending/completed History deletion. Show its result,
   real empty History after successful cleanup and fresh consent requirement. Do not place an
   emergency call. Do not repeat an already-recorded owner stop/browse confirmation
   merely to prepare these instructions.

For the declaration video, capture launch, the entire disclosure, refusal/re-entry,
affirmative consent, manual enablement and the core interruption. Add short captions
where needed. These elements follow [Google's Accessibility video guidance](https://support.google.com/googleplay/android-developer/answer/10964491).
Give reviewers workable physical-tag access instructions/material; a video must not
be assumed to replace access to restricted functionality. See [app review access](https://support.google.com/googleplay/android-developer/answer/9859455).

## Justnoise Android internal beta — Privacy Policy draft

Draft updated: 2026-09-03. This text is for the planned local-only `com.justnoise.app`
beta with the locally tested History extension, not a claim that device or release
acceptance has finished. It does not describe the iOS app, web shop, or Android/Google Play's
own services. Validate it against the release package before publication.

**Publisher and privacy contact:** [OWNER TO SUPPLY: publishing identity and public privacy-contact email or URL].

Justnoise helps you interrupt apps you select during a focus session. This candidate
has no app accounts, purchases, cloud sync, advertising, analytics or crash-reporting
service. It does not transmit the data below to us or third parties or sell them.

While you have enabled its Accessibility service, Justnoise receives window-change
event types and foreground app identifiers, including when no focus session is active.
It processes those identifiers locally; the interruption is shown only for an app in
the active session's saved target snapshot during an eligible active session. It does not read screen contents, typed text,
passwords, notifications or accessibility nodes, or save a foreground-app history.

The local app picker reads launchable app names and identifiers. Justnoise also reads
system app identifiers, service-enablement metadata and screen/inset geometry for
readiness and safe navigation. These observations are temporary. It saves only your
selected targets, active-session identity/target snapshot/clocks/process identity,
last-finished session ID, consent version/time, session state/revision,
interruption/recovery and service-liveness state, and the
keyed digest used to recognize your paired tag.
NFC Text data is processed temporarily, not saved or logged; the tag's hardware
identifier is not read. Configuration uses private app storage, and the digest's
non-exportable key is managed by Android Keystore. Configuration is not claimed to
be wholly encrypted. The app excludes its data from Android backup and transfer;
this is not a guarantee about every manufacturer's restore behavior.

For History, Justnoise saves a random session ID, start and end times, non-negative
duration, number of selected apps and whether continuity was interrupted. Completed
History contains no app names or foreground-event log. It shows **Session time**, not
verified continuously protected time; cross-process timing is marked interrupted.
Stopping clears the active target snapshot and clocks and queues one local completed
record for retryable insertion into a private SQLite database before a later start
or History read. Stopping itself does not wait for database delivery. Repeated insertion of
the same session ID must not add another record. History stays on this device until
withdrawal, app-data clear or uninstall; there is no automatic expiry in this slice.

**Withdraw consent and clear Gate 0 data** attempts to remove saved configuration,
pending/completed History and the pairing key and disable the service. A durable
cleanup marker retries an interrupted database deletion before new consent, session
start or History access. Success and incomplete cleanup have different feedback;
if cleanup cannot be confirmed, retry or use
Android App info to clear storage or uninstall. Disabling Accessibility alone stops
service access but does not erase saved configuration or History. There is no server
account to delete. Raw foreground events and NFC payloads are not retained as History.
New v3 disclosure consent is required for History-eligible starts; earlier active
sessions are not backfilled. Schedules remain required MVP work outside this extension
and need reviewed behavior/retention disclosures before that release.

The detailed implementation inventory is [DATA_INVENTORY.md](DATA_INVENTORY.md).
Before publishing, provide a working contact, review the final release's actual data
handling, host this notice at a public non-geofenced HTML URL, and expose the policy
in the app and Play Console. The current separate Accessibility disclosure is not a
substitute for a full privacy policy. These publication requirements follow [Google's User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311).
Apps exclusively on internal testing are exempt from the Data safety form, not the
applicable privacy/disclosure duties; see [Google's Data safety guidance](https://support.google.com/googleplay/android-developer/answer/10787469).

## Facts needed before using these drafts externally

- Owner-confirmed publisher/privacy contact for the single field above and a live
  Android-specific policy URL. The repository's existing shop-policy link is not
  evidence that this new notice is published or covers the Android beta.
- Verified Play account/track eligibility, registration and signed release identity,
  final UI labels, and the actual reviewer-access/video URLs and physical-tag provision.
- Verified History implementation, v3 migration, interrupted-time labels, duplicate/
  persistence-failure recovery and complete withdrawal across preferences/database/key.
- The smaller A16 beta safety decision, any policy issues resolved for the actual
  release, and explicit owner approval to upload. A17 and the full 19-cell public
  reliability matrix are not additional prerequisites for this beta draft.

## Reviewer evidence to attach later

- For the first internal beta: the smaller A16 safety-checkpoint evidence and separate beta decision from `docs/ANDROID_GATE_0.md`, including the exact model/build, source and APK identity, and known limitations. The complete 19-cell A16 matrix is required for the later full-product/public-release decision, not this first learning milestone. A17 validation is deferred/unverified and non-blocking; a second phone is not required. Do not present A16 evidence as tested A17 compatibility.
- A short video beginning at app launch and showing the entire disclosure (scroll slowly if needed), initial unchecked consent, refusal/back-out, re-entry, affirmative acceptance, manual settings enablement, and the core selected-app interruption. Add captions where the Accessibility use is not obvious. Include revocation/re-consent and separately demonstrate Force Stop, uninstall, and all overlay escape routes. See [Google's required video flow](https://support.google.com/googleplay/android-developer/answer/10964491).
- Merged-manifest and static-policy reports.
- Redacted logs plus network observation matching `DATA_INVENTORY.md`.
- Exact store listing and privacy disclosures.
- Product explanation of onboarding burden, bypass behavior, battery/OEM caveats, and why the outcome is valuable.

Internal review cannot guarantee acceptance. A policy uncertainty is `HOLD`; complete the requirements of the applicable Play test track and account before distribution. The initial milestone is internal testing. A separate closed test is required only where the account's production-access rules or the later release plan require it; internal testing does not itself grant public-release approval.

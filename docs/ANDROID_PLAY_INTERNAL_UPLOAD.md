# Justnoise — Play internal-test handoff

## Authoritative handoff — September 20, 2026

**Signed candidate accepted by Google Play; internal track active; consolidated A16
MVP, bounded PostHog checks and the owner-reported Play-install smoke test pass.**
This section supersedes every older status, authorization and product description
below. The dated archive is retained as historical evidence, not instructions for
the current release. Do not submit its listing/privacy/reviewer copy unchanged.

### Exact current deliverable

Canonical production module: `android-app/app`, reusing the reviewed Android
implementations. Public name **Justnoise**, application ID `shop.justnoise.app`,
version **1.0.0 (1)**, min SDK 31, target SDK 36.

Candidate directory: `android-app/build/candidates/1.0.0-1-94741f2a/`.

| Signed artifact | SHA-256 |
| --- | --- |
| `app-release.aab` | `94741f2afee9319b25e5cdca11ac126baf768d1dc755ec74ccb27e1cd86e697d` |
| `app-release.apk` | `fba6969b635554d0c2984fe5d45cd53230660a7e5b626330c91f24a61e31225f` |

The candidate folder also contains `verification.json`, `SHA256SUMS.txt`,
`HANDOFF.md` and the full release lint report. Upload-certificate SHA-256:
`2b51da4e2d3fc6345480db87fdc15a185b12a1480358faf8e00e6d2991b26ebd`.
The upload key is external to Git; owner backup confirmation is still pending.

Latest recorded local checks: **470 production unit tests, zero failures/errors/
skips; release lint 0 errors, 74 warnings, 2 hints; complete Android pre-device
verification and 44 artifact-verifier regressions passed.** Signatures, packaged
release boundaries and native alignment checks are recorded in the candidate
report. These results are not physical-device or external-service acceptance.
Google Play reported `com.justnoise.app` as already in use and confirmed
`shop.justnoise.app` was available. The full checks above were rerun against the new
identity. Supabase now permits `shop.justnoise.app://auth/callback\?state=*` while
the existing redirects remain unchanged.

### Current product, not the old account-free MVP

- Named modes and app selections, reused by automatic-start schedules; Zap stops
  the session. Real completed sessions feed History.
- Required account entry with Google/email authentication; registration, recovery,
  sign-out, deletion and account separation are implemented but not all live
  acceptance cases are closed. Previously authenticated offline Zap operation
  must remain available. There is no cloud synchronization of modes/History.
- Optional PostHog session-start/session-end events require explicit consent.
  The latest candidate makes consent process-bound: a fresh app process starts
  opted out. Payloads exclude account credentials, app selections and NFC data.
  The authenticated dashboard received one real consented start/end pair; after
  owner opt-out a real start/stop cycle with validated Internet produced no new
  events. Account-transition/process-restart variants were not run.
- A promoted Live Update with artwork, mode and timer exists. The final owner test
  visibly confirmed the Samsung Now Bar on the Galaxy A16; this claim is based on
  the rendered device result, not Android's promotion flag alone.

The immediately preceding behavior-equivalent candidate was installed on the
owner-confirmed idle A16 September 20 at 14:52:07. Its on-device APK hash matches
that archived candidate. The affected Chrome
overlay, offline multi-app blocking, timer/Now Bar, stop, History and automatic
scheduled start passed in the consolidated journey. Preserve pairing, modes and
History; do not clear/uninstall the owner's app or run instrumentation against it.
The new Play identity intentionally remains a fresh app until internal-track install.
See [installation evidence](../android-app/evidence/a16-update-8dc7ba54-2026-09-20.md).

### Finite technical release gates

- [x] Signed production APK/AAB and bounded local verification exist.
- [x] Owner confirmed session off; data-preserving A16 update and installed hash
      verified. Service bound after update. This is not a complete data/content check.
- [x] Owner-attended current-candidate A16 pass for the affected overlay/navigation,
      Zap stop and History path. Reuse unaffected manual/scheduled Zap evidence.
- [x] Offline direct start and multi-app blocking on the owner A16.
- [x] Bounded consent-only PostHog opt-in and opt-out acceptance on the A16.
- [ ] Live test-account lifecycle/separation. The owner skipped A→B→A because the
      password is unknown; never reset or delete the owner's account to close it.
      A complete synthetic A→B→A runtime regression and all 142 account tests pass.
- [ ] Final rendered iOS-reference/Android comparison; published iOS stays unchanged.
- [x] Owner-visible Samsung Now Bar and moving timer on the final candidate.
- [ ] Correct the bundled draft's false account/analytics statements before public
      distribution. Policy/publication ownership remains outside this task.
- [x] Freeze signed source/build identity and register `shop.justnoise.app` in
      Console; versionCode 1 was accepted in the internal track.
- [ ] Confirm secure signing-key backup.
- [x] Deliver accepted binaries, hashes, signing identity, test evidence and known
      limitations together. Any subsequent code change requires affected checks
      and refreshed binary identity; do not relabel this snapshot as that build.

Developer-account verification was **confirmed by the owner**. Play app
`4972093831482851244` now exists for `shop.justnoise.app`; Play App Signing was
accepted and version code 1 processed without validation errors. Internal release
`1.0.0 (1) – Internal` is active on track `4701314183475386469`. Play reports two
non-blocking diagnostic warnings (deobfuscation mapping and native debug symbols).
Do not ask the owner to redo identity verification.

### Scope and current handoff

The owner explicitly authorized app creation and internal-track upload on September
20 and explicitly accepted Play's policy, App Signing and export declarations. The
internal track is active; privacy/shop publication and public rollout remain excluded.

For future internal-track updates, build a new version code from the frozen source;
never re-upload or relabel version code 1. A
Play-signed install may use a different certificate from the sideloaded upload-key
APK; do not erase owner data to force a signature transition. Reviewer/tester
instructions must include the current required login and workable physical-Zap
access, not the archived assertion that login is unnecessary. Public release is
a separate milestone; no Google approval is implied.

Selected tester: `tschoni.nguyen@gmail.com`. Opt-in URL:
`https://play.google.com/apps/internaltest/4701314183475386469`. Google currently
shows the temporary internal name `shop.justnoise.app (unreviewed)` pending broader
app setup/review.

Current acceptance authority: [final closure audit](../android-app/evidence/final-closure-audit-2026-09-20.md),
[A16 evidence](../android-app/evidence/a16-hardware-pass-2026-09-20.md),
[PostHog audit](../android-app/evidence/posthog-audit-2026-09-20.md).
Publisher/contact already supplied: **Justnoise Media**, **hello@justnoise.shop**.
Mailbox delivery and policy publication are not verified by these values.

## Historical archive — September 6–8, 2026 (not current instructions)

All remaining sections preserve the old checkpoints and drafts verbatim. Words
such as "current", "latest", "required" and old unchecked boxes refer only to
their historical dates. For today's gate and scope use the section above.

Updated 2026-09-08. **DRAFT / NOT UPLOAD-READY.** This is the first controlled
internal-test milestone, not public launch approval. The owner has authorized the
internal-track upload once its prerequisites pass; no Console mutation, signing-key
creation, upload or tester invitation has been performed by this preparation.

## Current artifact boundary

**Latest: signed local candidate, NOT approved for installation/distribution.**
The owner's September 8 "jetzt bauen" permits local packaging/signing before GO.
`android-app/` builds Justnoise, `com.justnoise.app`, version 1.0.0 (1), from the
canonical sources. Release JVM tests (283), lint/build, APK/AAB signature/package
and native alignment checks passed. See [exact files, hashes and limits](../android-app/README.md).
No upload, phone install, Console operation or privacy publication occurred.
The owner's development app and the unsigned development artifacts below remain
separate; they are no longer the only locally available artifacts.

**Development-only focus, September 8:** privacy/shop publication and Console work
are paused at the owner's direction. The latest technical slice strengthened APK
release safeguards: 41 artifact tests passed, the full local verifier passed, and
all 298 JVM tests were explicitly rerun without failures/errors/skips. Artifact
hashes below are unchanged; no runtime change, phone install or signing occurred.
Use [the technical status and remaining sequence](ANDROID_TECHNICAL_RELEASE_STATUS.md)
for current development work rather than historical publication/cleanup entries.

**Latest September 8 support/setup build:** normal setup copy is customer-facing;
Settings now has an explicit recipient-only support composer handoff and selectable
address fallback. No email sent, inbox verified or privacy notice published. Final
checks passed 298 JVM / 36 artifact checks, contracts/lint/builds, plus the final
10 affected Settings tests at each of normal and compact/200% system text.
[Evidence and initial test-only encoding correction](../spikes/android-integrated/evidence/support-setup-2026-09-08/README.md).
Current debug APK SHA-256:
`8c04d759ac6696684d1cefbb41460f4eb727ebc761de0015af5a0199ae45e4eb`.
Current unsigned development AAB SHA-256:
`4513dbf6275e67229fff141d6a6c66fcffe31a65efbd495b2d7ca1e9173a5abd`.
These supersede older hashes below. Package remains com.justnoise.androiddev;
the distinct Android Dev system/service label is retained until production
packaging. No phone update, iOS change, Console recheck, signing or upload occurred.

**Latest September 8 local presentation build:** Android timer/recovery contrast
and mode-aware onboarding are implemented and verified. Final checks passed
296 JVM tests, 36 artifact checks, contracts/lint/builds; 12 affected emulator UI
cases passed at standard size and again at compact/200% system text. Evidence:
[experience polish](../spikes/android-integrated/evidence/experience-polish-2026-09-08/README.md).
Current debug APK SHA-256:
`cb828a3708c2eee0e91001dd0e1e5b43b34c68106441350f7147cb60859841e8`.
Current unsigned development AAB SHA-256:
`d54cbf4c425deac4e1349b7c93f4b11b3376181ca38ec5f0d17703c40f36409a`.
These supersede the older local-output hashes below. Both still use
`com.justnoise.androiddev`; the owner phone retains its previously accepted modes
build. No iOS change, production signing, identity recheck or Play upload occurred.

**Later September 8 owner resubmission:** after the owner reported uploading the
requested documents, a fresh read-only Console inspection confirmed **Google is
verifying your identity** and explicitly stated that documents were uploaded.
The account owner will receive the result by email; Console says this may take a
few days. This supersedes the failed-verification checkpoint below, but is not
approval. **Create app remains disabled**, with phone-number verification still
dependent on organization/identity approval. The agent did not handle or upload
identity documents or change any account field.

**Live Console update — September 8:** read-only inspection now shows **Create app
disabled**, identity verification **couldn't be verified**, and **Organization /
Action required**. Google instructs the owner to check the errors in its email and
try again. This supersedes September 7's documents-under-review status; do not tell
the owner simply to wait. Phone-number verification remains dependent on the other
checks. The verification detail was opened but no document, identity field, account
setting, agreement or application record was submitted or changed. The rejection's
specific reason is not shown on this screen and the email was not accessed.

**September 8 local correction:** the stale schedule-success banner is now cleared
on confirmed consent withdrawal during list/editor reload. Failed reads preserve
save uncertainty/errors, and accepted-consent recovery warnings remain visible.
Three new JVM regressions passed; the required complete local verifier passed
292 JVM tests, 36 artifact-checker tests, lint and debug/release builds. Product
contracts passed (18 colors, 27 copy entries, 89 cases). No phone install or iOS
change occurred. First verifier attempt stopped because the UI project lacked an
SDK path; rerunning with the installed SDK explicitly supplied passed.
Log: `/private/tmp/justnoise-schedule-withdrawal-2026-09-08.log`.

The local output paths have changed again; the older modes artifact hashes below
identify previous builds. Current debug APK SHA-256:
`b7b4f77fa96ed4752b15229bffc4b38a35e7a621fcd0f0668eadafcc9c1dbee0`.
Current unsigned development AAB SHA-256:
`3c2a484c3f1299a7aaec93fd7dcb03c9cde3139a5e897ef94de3a3e9c1c91f4e`.
Both still use `com.justnoise.androiddev`; neither is the signed production
deliverable. The owner phone retains the previously accepted modes build.

**Latest isolated-test checkpoint — September 8:** the owner-authorized Cleanup
Check copy was installed separately on September 7 and its installed hash verified.
The owner manually enabled the disposable service, paired the Zap and completed a
real test session. Actual withdrawal cleared pairing, consent, mode, schedule and
History; the test service disabled itself. An old Schedule saved banner remained
on the empty list and needs correction. After charging/reconnection, disposable
uninstall was confirmed and the identical APK reinstalled successfully. Initial
onboarding appeared; the owner confirmed they then completed onboarding and paired
again, explaining the new pairing record. Fresh consent was visibly unchecked.
The disposable app was finally removed through Android's uninstall UI. Regular
pairing/consent and **power / 1 app** remain intact, idle revision 68. The owner
manually restored its service; a 16:07 check confirmed it connected, the test copy
absent, and no enablement/recovery prompt. The isolated exercise is finished with
its documented observation limits and stale-banner correction. No release GO is
implied. See [the current isolated exercise](../spikes/android-integrated/evidence/a16-cleanup-2026-09-08/README.md).
Older not-installed statements below describe their earlier preparation checkpoints.

**Later September 7 mode extension:** the current local output paths now contain
the named-mode build documented in [ANDROID_NAMED_MODES.md](ANDROID_NAMED_MODES.md),
with its own tests and checksums. The pre-mode artifact table below is historical;
it does not identify the files now at those paths. The modes debug APK was later
installed on the A16 in place, with installed SHA-256
`9d5efacf4eb4a06aca96374253c374078ef045b26ef5f70fc4cc29f889c0a36c`.
The owner created **power** and subsequently confirmed scheduled and manual Zap
sessions work. Reuse this scoped feature acceptance; do not rerun those ordinary
flows. The unsigned modes AAB remains a development artifact. Play/signing/cleanup
gates are unchanged; the older installed-build paragraphs below are historical.

### Owner request to prepare the Play build — 2026-09-07

The owner requested **“ok lets go for plAY STORE BUILD.”** The release preflight
confirmed the existing integrated release is non-debuggable but still uses
`com.justnoise.androiddev`, version `1 / 0.1-integrated-dev`, development labeling,
and no release signing configuration. No production AAB, upload key or release
approval is inferred from the previously passing local build.

The existing plan requires an evidence-supported internal-beta GO before
production promotion. The separately isolated cleanup/reinstall/fresh-consent
exercise still needs owner coordination, including temporary manual service
switching while preserving the regular development app. The physical foreign-tag
and scoped repeat observation gaps remain distinct; do not present cleanup alone
as completing every release gate. No phone commands, data removal, settings
changes, production promotion or Play upload were performed in this preflight.
The dated Console checkpoint below was not freshly rechecked in this turn.

The isolated cleanup APK was rebuilt from the current modes code, without any
source change or phone installation. `lintCleanupCheck` and `assembleCleanupCheck`
passed. Current SHA-256:
`a15342da4aa3ff177a0daa5b32304607f5b6e5100b6d54a76bf45b6efea10d54`.
Packaged inspection confirms `com.justnoise.androiddev.cleanupcheck`, **Justnoise
Cleanup Check**, `testOnly=true`, debug signing (v2 signature verifies), and disabled
direct NFC activity. The five existing platform permissions are unchanged. This
supersedes the older cleanup APK hash below at the same output path, not its
not-installed status. Log:
`/private/tmp/justnoise-mode-release-cleanup-preflight-2026-09-07.log`.
It is explicitly not a production-signed or Play-uploadable artifact. No ordinary
feature tests or instrumentation were rerun; iOS remains unchanged.

### Live Console checkpoint — rechecked 2026-09-07

Read-only inspection of the existing **JustNoise** organization account confirms
the same account-setup blocker on September 7:

- The account exists and the website-ownership notification says verification is
  complete.
- Google is still verifying the submitted identity documents. Console says the
  owner will receive an email when review finishes; it may take a few days.
- Phone-number verification depends on the other verification tasks completing.
- **Create app is disabled** until account verification completes; no app is listed.

This is observed Console state, not a prediction that the application will pass
review. No account settings, app record, package registration, agreement, upload or
rollout was changed. `com.justnoise.app` registration remains unverified. Android
device acceptance can continue independently of this account review.

### Current local candidate — read-only preflight, 2026-09-07

The integrated development project builds an unsigned release AAB under
`spikes/android-integrated/app/build/outputs/bundle/release/`. Its identity is still
`com.justnoise.androiddev` / **Justnoise Android Dev**. Do not upload that artifact.

The most recently installed and hash-verified development APK is the later
notification-comparison build
`5ef848daa5c891cdd383d942d8eb6088b0754f57f44057dd5808aa9e9ce16b76`;
see its [exact evidence](../spikes/android-integrated/evidence/session-notification-2026-09-06/README.md).
Its source retains the earlier observed modal contrast correction and unchanged
session/alarm behavior. Reuse the clean scheduled start→Zap stop→History and
continuous-held-Zap samples in
[the build-scoped evidence](../spikes/android-integrated/evidence/schedule-modal-contrast-2026-09-06/README.md)
without relabeling them as new tests on this artifact.

The unsigned development AAB inspected before the new verification run had checksum
`6fd2e40151374e9cb8a776c797b93cc5c9093c427820f0c045f7974b92cd9f61`.
Local `jarsigner` inspection reports **jar is unsigned**. Gradle has no release
signing configuration and still uses version `1 / 0.1-integrated-dev`. Neither this
bundle nor its debug APK is the signed production deliverable. No external signing
credential was supplied or provisioned by this preflight.

After the publisher/contact update, service-description correction and isolated
cleanup variant, the September 7 local verifier passed: **237 JVM tests** with zero
failures/errors/skips across the three projects, debug/release lint and development
builds, static policy/package boundaries, and **36 Python artifact-checker tests**.
Product contracts separately passed 18 colors, 27 copy entries and 89 behavior
cases. Gradle reused unchanged tasks where applicable; no physical instrumentation
or phone installation was run. Log:
`/private/tmp/justnoise-release-preparation-final-2026-09-07.log`.
New JVM cases check consistent publisher/contact copy, the retained draft warning,
truthful while-enabled observation wording and the cleanup build boundary. The
existing emulator-only Settings test was updated and compiled, not executed on the
owner's phone. The publisher-only run remains
`/private/tmp/justnoise-publisher-readiness-2026-09-07.log` (233 JVM cases), and the
earlier preflight log remains
`/private/tmp/justnoise-internal-upload-preflight-2026-09-07.log` (232 JVM cases).

The newly generated local artifacts are **not installed or upload-approved**:

| Artifact | SHA-256 |
| --- | --- |
| Unsigned development AAB | `324edc12893a2384bcc76f76707ed2112c2b35e7ecefab978c002d6f6f31c85f` |
| Local development debug APK | `74dbe26e0cb09e6a1566cb90d3a700f5a83d81f67db09fd1d5204f9e027ea983` |
| Unsigned development release APK | `cd821a46aa212db44b2d3bb06686b1146ae2c361756e682425bda6ac413630b9` |

Post-build `jarsigner -verify` again explicitly reports **jar is unsigned**. The
Gradle `signReleaseBundle` task name is not signing evidence. The phone retains its
previous installation; these local build hashes are not substituted for its hash.

The owner paused Live Update/Now Bar work before this upload request. Nothing was
removed; the experimental promotion remains debug-only and is not a release claim.
The connected A16 was inspected passively at active revision **55**, with its service
connected and no recovery flag. No session, phone setting or development data was
changed. The last deliberate developer-override change was its recorded restoration
to OFF on September 6; this preflight did not retoggle it or assert a fresh UI check.

### Smallest remaining acceptance work

The internal-beta decision remains **HOLD**, not because of the deferred full
endurance matrix or another phone requirement. Remaining distinct checks are:

1. Real unpaired/invalid NFC input and rapid remove/re-present within the existing
   guard; the completed continuous-held-tag sample is not the latter test.
2. **Finished September 8:** isolated withdrawal, uninstall/reinstall and fresh
   unchecked consent, with documented observation limits. Test copy removed and
   regular service restored. The resulting stale-message bug is fixed/tested
   locally; carry that correction into the final candidate, not another destructive
   exercise on the regular owner app.
3. Final scoped package/privacy/disclosure and visual review, then record the
   evidence-supported internal-beta decision before production promotion.

Do not reopen the completed Chrome-unblock, normal navigation/emergency, recovery,
Force Stop, clean manual/scheduled History or continuous-held-Zap samples. The owner
was asked about an available second NFC tag and the publisher/contact. Both answers
are now recorded below; do not ask for the same details again. Physical scans are
not presumed.

The owner subsequently confirmed that only the already-paired Zap is available.
Do not ask again for the same inventory or rewrite that Zap. The physical foreign-tag
sample remains unverified; parser/unit coverage is not substituted for it. The
rapid remove/re-present check can still use the existing paired Zap. Its passive
preflight remains active revision 55, connected and without recovery; no physical
repeat outcome has yet been reported or generated by automation.

### Subsequent owner-reported rapid-repeat result — 2026-09-07

The owner was instructed to stop the active session from unlocked Home, move the
paired Zap fully away, then immediately re-present it within three seconds. The
owner replied that the double tap was ignored and the Zap was put away. Two passive
post-checks showed **idle revision 58**, connected service, no active-session ID and
no recovery flag. No scan, stop, settings change or install was injected.

Record this as **bounded owner-reported success with a confirmed idle outcome**.
The earlier active baseline was revision 55, so the full interval contains three
revision increments, not the single increment expected from an isolated stop plus
ignored repeat. Their timing and cause are not independently established. Do not
invent intervening user taps, classify a defect from the counter alone, or claim a
fully timed/one-transition rapid-repeat PASS. Resolve that observation gap during
the next otherwise-needed NFC workflow, not through a blind repetition request.

A proposed full private History database copy was rejected by the safety reviewer
and was not performed. The narrower on-device SQLite read was unavailable; no
database copy, alternative export or state rewrite was used. Only the already
scoped session-status reads support the stored-state observation above. The
foreign-tag, separate-install cleanup/fresh-consent, signing/account/privacy and
final review requirements remain open; no internal-beta GO is inferred.

The approved release identity is `com.justnoise.app` / **Justnoise**, subject to
Console registration. After the smaller A16 internal-beta checkpoint records GO,
promote the reviewed implementation to one canonical `android-app/app` module;
replace the predevice verifier's phase-specific production-directory absence guard
with release verification. Do not copy the prototype's fake state/reducers into it.

The existing paired development installation remains intact. The owner approved
fresh setup and pairing in the separately installed release. Before release-device
checks, prevent the development service and NFC entry from competing, with explicit
owner coordination; do not uninstall it, clear its data, or silently alter settings.

### Disposable cleanup preparation — built locally, not installed

The existing integrated module now has one additional `cleanupCheck` build type,
not another runtime/engine or a production module. Its actual packaged identity is:

- Package: `com.justnoise.androiddev.cleanupcheck`.
- Label: **Justnoise Cleanup Check**; Accessibility label explicitly says disposable.
- Version: `1 / 0.1-integrated-dev-cleanup-check`; min SDK 31, target SDK 36.
- APK: `spikes/android-integrated/app/build/outputs/apk/cleanupCheck/app-cleanupCheck.apk`.
- SHA-256: `1593983c288cea2e848c234fa7f83d6219757f5554448bb0d027100ad3bc21b5`.

The actual APK is `testOnly=true`, debuggable and signed with the existing Android
Debug certificate; APK Signature Scheme v2 verifies. It is **not** the final upload
key or Play bundle. The release verifier rejects its non-release output metadata.
DirectNfcActivity is disabled only in this variant; foreground NFC still uses the
real shared implementation. Its three declared activities contain no preview/probe
activity, and its permissions remain the five scoped platform permissions plus
its package-local dynamic-receiver permission. There is no Internet, broad app
visibility, overlay-settings, promoted-notification or foreground-service permission.
The packaged Accessibility XML retains package-window events only, no content
retrieval and `isAccessibilityTool=false`. App data/Keystore/provider authority
remain isolated by the separate Android package sandbox; no shared user ID is used.

Local build, cleanup-variant lint and the shared JVM suite (including three new
source-boundary cases) passed. The updated integrated instrumentation APK also
compiled; no instrumentation was executed. Log:
`/private/tmp/justnoise-cleanup-variant-final-2026-09-07.log`.
The initial task invocation referenced a nonexistent `testCleanupCheckUnitTest`;
the pinned plugin exposes the shared debug JVM suite instead. The corrected
command and bounded owner-coordinated protocol are in
[the integrated README](../spikes/android-integrated/README.md#coordinated-device-exercise--not-executed).

No APK was installed, no device permission or session was changed, no data was
deleted and no cleanup/reinstall result is claimed. The next physical exercise
needs owner coordination for the separate install and one-service-at-a-time setup.
It does not resolve the physical foreign-tag or direct rapid-repeat observation
gap, or replace later signed-release checks. The internal-beta decision stays HOLD.

## Current Google requirements — checked 2026-09-06

September 8 read-only official-doc refresh reconfirmed API 36 for new phone apps,
the internal-track incomplete-listing allowance (up to 100 testers), and the
Accessibility disclosure/consent/declaration/video requirements below. Deterministic
user-defined automation is distinguished from prohibited autonomous planning in
Google's Accessibility guidance; this supports reviewing the current rules-based
design, not a promise of Google approval. No public-launch tester requirement is
added to internal testing. Account eligibility is inspected separately below.

- New phone apps must target API 36 or higher from 31 August 2026. The existing
  project already targets 36. [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- Use a signed AAB and configure Play App Signing for the new app. Keep the upload
  key separate from the debug key and do not confuse it with Google's app-signing
  key. [Signing guidance](https://developer.android.com/studio/publish/app-signing)
- Internal testing supports up to 100 testers with Google accounts. A valid bundle
  can be tested before full app setup/listing completion; configure a feedback URL
  or email and supply the opt-in link. The first uploaded package name is permanent.
  Standard policy/security review **might** not run, which is not an approval
  guarantee. [Internal testing](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en)
- Internal-only apps are exempt from the Data safety form. Reassess before any
  closed/open/production track; keep the truthful inventory now. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- Do not invent an internal-test exemption from privacy or Accessibility obligations.
  Provide an in-app policy and public policy URL with real publisher/contact and
  actual access, use, sharing, security and retention/deletion details. The URL must
  be accessible, non-geofenced, non-editable and not a PDF. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
- This is not an accessibility tool for disabilities. Keep that declaration false,
  with separate disclosure and affirmative consent before access, and prepare the
  required declaration/video. [Accessibility requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

The production-access rule for newer personal accounts (12 testers opted in for
14 continuous days) is **not** an internal-test prerequisite and is not a blanket
organization-account requirement. Do not add it to this milestone.
[Personal-account testing guidance](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB)

## Owner-supplied publisher and contact — 2026-09-07

- Public app name: **Justnoise** (unchanged).
- Publisher: **Justnoise Media**.
- Privacy and internal-tester feedback email: **hello@justnoise.shop**.
- Public Android privacy-policy URL: **not published/supplied yet**.

The owner supplied these details explicitly. They now replace the publisher/contact
placeholders in the Android privacy draft and its in-app rendering. This is not
independent business-registry verification or an email-delivery test, and does not
change the existing Play account name or settle Google's identity review. Do not
reuse the shop/iOS policy URL as proof that this Android notice is published.
The in-app policy remains clearly marked as a draft until publication and final
release review. No external page, account field or email was changed/sent here.

### Prepared reader-facing materials — 2026-09-07

- [Plain-language Android privacy candidate](play/ANDROID_PRIVACY_POLICY_DRAFT.md),
  distinguishing local app processing from voluntary email/external website use.
- [Internal tester guide](play/INTERNAL_TESTER_GUIDE.md), with real four-feature
  paths, safe escape controls and the owner-supplied feedback email.
- [Accessibility reviewer/recording package](play/ACCESSIBILITY_REVIEW.md), with
  actual consent/refusal flow and a public, nonsecret NDEF review-tag setup.

These are local drafts, not a published policy, video, provisioned tag or invitation.
The currently connected Shopify store is not Justnoise; nothing was written there.
Correct website access and final operator/support details remain publication
dependencies. The new readable policy is not yet the bundled policy; reconcile
both with the final release before publication, rather than presenting divergent
drafts as final. The existing shop/iOS policy remains untouched.

The integrated Android system service description was corrected to state that
foreground identifiers are used while the service is enabled, with the latest kept
temporarily for scheduled starts. Blocking remains eligible-session-only. This
matches the existing v4 disclosure and actual code; it introduces no new data
access or behavior and does not silently change consent.

## Prepared listing copy — reconcile with the final tested release

App name: **Justnoise**

Short description: **Create focus modes. Tap your Zap. Make time for what matters.**

Full description draft:

> Make a little more room for what matters. Create your own focus modes and choose
> which apps each mode blocks. Select a mode, then tap your paired Zap to start a
> focus session. Tap it again to finish.
>
> Give each schedule its own saved mode for automatic starts. Manual Zap starts
> use your last selected mode. Look back at completed sessions in History, with a
> calendar view of your session time.
>
> Justnoise uses Android's Accessibility service to recognize the foreground app
> and display a blocking screen for apps in your active selection. It does not read
> screen content, typed text or passwords. Session time is not a guarantee of
> uninterrupted protection; interruptions and uncertain timing are indicated.
>
> Requires an NFC-capable Android 12 or newer phone and a compatible physical Zap.
> Accessibility is enabled manually after disclosure and consent. Scheduling uses
> separate alarms-and-reminders access; Android may delay or miss a start. After
> Force Stop, open Justnoise to recover future scheduling—missed starts do not catch
> up automatically.
>
> This Android version keeps modes, configuration, pairing, schedules and session History
> on your device. It has no app account or cloud sync. Android's normal disable,
> Force Stop and uninstall controls remain available.

Release notes draft:

> First Justnoise Android internal beta: create custom modes, choose blocked apps,
> start and stop with your paired Zap, schedule automatic starts with saved modes,
> and view session History. Please report
> device-specific blocking, NFC, scheduling and layout issues through the tester
> feedback channel. This is a controlled beta, not a public reliability claim.

The four features including named modes exist in the development app; this copy
does not establish production packaging, signed-release acceptance or approval.
Reconcile the final disclosure and affected release checks before publication.
No feature counts as complete merely because it appears in this copy.

## Listing assets and tester material

Prepare a 512×512 PNG app icon (at most 1,024 KB), a 1024×500 JPEG or 24-bit PNG
feature graphic, and at least two real screenshots for the completed listing.
Screenshots must be JPEG or 24-bit PNG without alpha, 320–3840 px, with the long edge
no more than twice the short edge. Use honest framing for tall device screenshots;
do not cut off essential controls or substitute iOS/prototype screens. Full listing
assets are planned deliverables, not a blanket gate to the first internal upload.
[Google asset specifications](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)

Capture real release states: selected apps, Zap idle/active, saved schedule, History
with a real completion/day detail, and permission/recovery where relevant. Keep
notifications, personal names and unrelated app content out of public materials.

The tester handoff must identify version/build/checksum, installation opt-in link,
supported requirements, physical tag pairing instructions, four feature paths,
known limitations and a **working** feedback contact. Use the owner-supplied
**hello@justnoise.shop**; confirm it can receive tester messages before distribution.
No placeholder address may be presented as that channel.

## Accessibility video and reviewer access

Use the final release UI, recording: launch; the entire separate disclosure;
refusal and return; affirmative consent; manual Android enablement; selected-app
blocking; paired-Zap stop; visible navigation/escape controls. Add a schedule and
History sample to demonstrate the declared data use. Do not grant access off-camera
and imply onboarding granted it. Avoid private tag payloads and unrelated content.

Provide English instructions stating no app login is required, NFC-capable Android
12+ is required, and how to obtain/provision a compatible single UTF-8 NDEF Text tag
and pair it through the public flow. Record actual release labels and results.
A video is not assumed to replace access to the functionality; provide any required
non-text resources at a stable URL. Google guidance does not establish a universal
requirement to ship reviewers a phone or hardware. Workable access still needs
confirmation in the live submission. [Review access requirements](https://support.google.com/googleplay/android-developer/answer/15748846)

The detailed existing [declaration and privacy draft](../spikes/android-gate0/evidence/PLAY_DECLARATION_DRAFT.md)
and [data inventory](../spikes/android-gate0/evidence/DATA_INVENTORY.md) remain the
source material. Replace development labels and the remaining public-policy URL
placeholder against the final release; do not publish the drafts unchanged.

## Final upload-ready checklist — all currently unclaimed

### Read-only artifact preflight prepared

[`verify-release-aab.py`](../spikes/android-integrated/scripts/verify-release-aab.py)
adds a fail-closed check for the eventual production bundle. It uses installed JDK
signature tools, an existing external certificate truststore and an independently
approved upload-certificate SHA-256 pin, plus local bundletool. It downloads,
builds and signs nothing. The current unsigned development AAB is rejected, not
converted or approved. Use `--help` for the explicit local tool/trust arguments;
passwords are supplied only through an existing environment variable, never as a
literal command argument. No signing key or truststore was created here.

The helper checks production manifest identity/component boundaries and scoped
native ELF/AAB alignment evidence. It does not replace the companion packaged-APK
Accessibility XML check, generated-APK alignment/runtime checks, physical release
acceptance, final dependency/tooling review, or Play account/policy approval.
No real signed-production positive result exists yet. Its pure regression tests
are discovered by the existing predevice verifier.

Local verification on September 6 passed the complete Android verifier, including
217 JVM tests and **32 Python artifact-checker tests** (15 new AAB cases plus the
17 existing APK cases). The current actual unsigned AAB is rejected. Its actual
bundletool manifest shapes and four native ABI ELF layouts were inspected only as
development-artifact evidence; no signed release was produced or approved. Final
local log: `/private/tmp/justnoise-aab-preflight-readiness.log`.

### Required final results

- [ ] Four real features and agreed iOS-style rendered acceptance complete.
- [ ] Remaining A16 safety evidence supports the separate internal-beta GO.
- [ ] One canonical production implementation; release name/ID and version checked.
- [ ] Live owner account verified and eligible; chosen package registration checked.
- [ ] Upload key kept outside Git, backed up safely; passwords neither logged nor
      committed. Final build fails rather than falling back to a debug/unsigned key.
- [ ] AAB structure and signature/certificate verified; non-debuggable packaged
      manifest, exact components/permissions and no preview/development tooling.
- [ ] Native libraries checked for applicable 16-KB compatibility, not inferred
      from an APK ZIP-alignment check alone.
- [ ] Separate signed release installed and affected A16 checks passed without
      erasing development data or competing services/NFC routing.
- [ ] Final privacy identity/contact/public URL/in-app entry verified.
- [x] Owner supplied publisher **Justnoise Media** and privacy/feedback email
      **hello@justnoise.shop** on 2026-09-07; Android drafts updated. This does not
      close policy publication, mailbox availability or final release verification.
- [ ] Final declaration, video and workable physical-Zap reviewer instructions.
- [ ] Real screenshot/icon materials, release notes and working feedback channel.
- [ ] Deliver final AAB path, SHA-256, version, application ID, signing certificate
      fingerprint and scoped device evidence together.
- [x] Owner authorized work toward the internal-track upload on 2026-09-07. This
      does not waive account verification, safe release acceptance or signing.
- [ ] Execute the authorized upload only after the above gates pass and live Console
      requirements/warnings are checked. No public rollout or tester invitation is
      implied; no artifact has been uploaded.

Generate the final checksum only from the immutable signed candidate, for example
`shasum -a 256 /absolute/path/to/final-release.aab`. A checksum of today's unsigned
development bundle must never be substituted for that handoff.

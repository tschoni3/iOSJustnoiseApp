# Justnoise release closeout — 2026-10-11

## Exact candidate and decision

Retain **1.0.16 (18)**, package `shop.justnoise.app`, from
`android-app/build/candidates/1.0.16-18-6b2d94af/`.
No runtime change or new build is justified by this closeout. This document
supersedes older candidate status only for the evidence explicitly listed here;
historical reports and immutable build-time reports remain unchanged.

**Internal distribution: active and available. Public production: blocked, not
published. Complete live account/first-run acceptance: still bounded below.**

## Evidence verified or retained

- Live Play releases overview reports `1.0.16 (18) - Internal`, version code 18,
  `Available to internal testers`, `Full rollout`; bundle 18 is `Active`.
- All six frozen candidate files match `SHA256SUMS` in this closeout. This is
  artifact-integrity verification, not a repeated application test.
- Retained build evidence: 555 JVM tests / 70 suites, zero failures/errors/skips;
  lint zero errors, 109 warnings; signed package, alignment, contracts and 44
  release-boundary checks passed. None of these suites was rerun.
- A16 Play update to build 18 and normal account-screen capture were observed in
  the scoped account/first-run closure. No new install or phone action now.
- New Email-provider signup, confirmation link opened on the originating A16,
  and signed-in app are owner-reported PASS. Supabase independently confirms the
  new account, email confirmation and last sign-in at 18:44 Europe/Berlin.
- Previously accepted modes, app selection, manual/scheduled sessions,
  blocking/unblocking, NFC repeat handling, shield navigation, session shortcuts,
  sheets, Year grid and Now Bar remain accepted without repetition.
- Existing reviewed phone-only model exclusions remain recorded in
  `docs/play/PHONE_ONLY_RELEASE_PREPARATION.md`; no device targeting change now.
- No iOS, production source, permissions, owner data or backend account changed
  during this closeout. Existing dirty files are preserved.

AAB SHA-256:
`6b2d94af85855798b0bc0c0c3a420334d783d26a651311aa0300c25518b9ebfd`.

## Remaining acceptance boundaries

1. Actual permission return/progression and the separate first-session video,
   dismissal and Home in a genuinely fresh journey. Visual onboarding acceptance
   and replay are retained, not repeated or mislabeled as the entire journey.
   Asked the owner only to report the already experienced post-signup path.
2. Live password-reset completion, app self-service account deletion and
   cross-account isolation remain unverified. Administrative removal of the old
   account/profile does not prove in-app deletion. Further destructive tests require
   an explicitly disposable account; the new owner account is not disposable.
3. Secure offline signing-key backup remains an owner dependency. Credentials are
   not included in the source archive or this report.

No new core defect is established by this closeout. The historical interruption
cause remains unknown; there is no justification for repeating working sessions
or weakening recovery checks. Investigate with passive diagnostics if it recurs.

## Current public-release blocker observed in Play

Live Dashboard: `Draft app`, `Production Inactive`, and **0 of 11 completed** initial
setup tasks. Production explicitly says `Complete the initial setup tasks first`.
The visible tasks are:

- Set privacy policy.
- Sign in details.
- Ads.
- Content rating.
- Target audience.
- Data safety.
- Government apps.
- Financial features.
- Health.
- App category and contact details.
- Store listing.

This is a concrete Console gate, not an Android build failure. Privacy/store-policy
work remains excluded by the owner and was not modified. No declarations were
invented, legal terms accepted, production submission or rollout performed. A
20:00 public availability promise is not supported by this state or a completed
Google review. Proceeding publicly requires completion of the Console gates and
explicit authorization for the exact production submission/rollout.

## Handoff

Use the existing frozen signed AAB; do not increment a version or re-upload it just
to close evidence. Preserve accepted tests, the current installation and new
account. Complete only the missing acceptance paths with appropriate owner/tester
participation and the separate public Console prerequisites.

## Owner-requested production preparation

The owner next requested reaching the Play Store release button, without an
instruction to execute the final public rollout. Created production draft release
1 on track `4697751973251895784`, selected the existing library bundle **18
(1.0.16)**, named the draft `1.0.16 (18) - Production`, and added concise en-US
release notes describing Zap, custom modes, schedules and session history.

Reached `Preview and confirm`. Play displays **8 errors, 2 warnings** and disables
`Save` with `To save, fix errors`. Visible blocking messages cover incomplete
Dashboard setup, missing full store description, no production countries/regions,
undeclared sensitive permissions (shown twice), missing sign-in details before
the permission declaration, financial features and health declarations.

No country was guessed, no declaration submitted, no privacy setting changed and
no production rollout performed. The existing internal release is untouched.
Proof: `/private/tmp/justnoise-production-review-blockers.png`. Proceeding beyond
this preview requires missing distribution choices and clarification of the
owner's previous exclusion of privacy/store-declaration work.

## Worldwide scope authorized — subsequent preparation

The owner explicitly requested completing all public-release work and worldwide
publication. This supersedes the prior privacy/store exclusion for this task.
Production targeting was saved for all 178 available countries/regions, pending
Publishing overview/review. Ads = none, Advertising ID = no, Government = no and
Financial features = none were saved in the live Console. The Dashboard now
reports 4/11 initial tasks completed (Advertising ID is a separate declaration).

The actual connected app exposed a stale offline-only in-app policy. A new signed
candidate **1.0.17 (19)** corrects this via production-only resources. Its runtime
DEX bytes compare identical to 1.0.16 (18); existing accepted device behavior is
retained, not re-tested. Build/lint, matching signer, APK/AAB/production boundaries,
product contracts and the single new policy-resource regression pass. See the
frozen candidate `1.0.17-19-0dcc554c/HANDOFF.md` for exact checksums.

Published dedicated Android pages in the verified Justnoise Shopify shop:
https://justnoise.shop/pages/android-privacy-policy and
https://justnoise.shop/pages/android-account-deletion. Existing shop/iOS notices
were not replaced. The policy's public rendering was checked and its URL saved in
Play. Scope includes Supabase accounts, Resend email, optional PostHog with observed
IP/GeoIP, local account workspaces and Accessibility data that stays on-device.

Health classification was prepared as no health features for the focus/productivity
functionality. Saving was stopped by the automatic risk review pending explicit
owner classification confirmation; no workaround was attempted. Target audience
also awaits owner choice. Required reviewer account and actual Accessibility
disclosure/consent/core-function video remain missing; the old stability recording
is not a substitute. No public submission or rollout has occurred.

## Production draft 19 and Console declarations — 19:25–19:29 Berlin

Uploaded the frozen signed **1.0.17 (19)** AAB to the existing production draft,
removed bundle 18 from this draft only, and saved the draft as
`1.0.17 (19) - Production`. Internal release 18 and the owner's phone were not
changed. All six frozen artifact checksums pass `SHA256SUMS` verification.

Saved the English-US listing as ready for review: Justnoise, Productivity,
`hello@justnoise.shop`, HTTPS website, supplied app icon, native SVG-based feature
graphic, and two real A16 screenshots (Home and whole-year History). The dedicated
Android privacy and account-deletion pages are both publicly rendered and verified;
existing shop/iOS notices remain unchanged.

The owner explicitly approved no health features, IARC terms, and a target audience
of 16–17 and 18+. Health and the completed IARC questionnaire are saved. IARC shows
PEGI 3 / USK all ages / ESRB Everyone; content rating is distinct from target
audience. Play blocks the target-audience questionnaire until sign-in details are
completed, so the approved target audience is **not yet saved**.

Dashboard now shows **8/11 initial tasks completed**. Remaining initial tasks are
sign-in details, target audience and Data safety. Data safety is saved as a draft:
email and account IDs for account functionality/management/security, and optional
analytics interactions, random process IDs and IP-derived approximate location.
No final Data safety declaration was submitted. Actual Google profile field
collection still needs a scoped read-only check; the automatic risk review blocked
unverified Name/Other info selections and private Supabase inspection. Explicit
permission was requested; no alternate access route was attempted.

Release preview now shows **3 errors**, all concerning Accessibility declaration
and sign-in details (permission error repeated), and **2 warnings**: no
deobfuscation mapping and no native debug symbols. Minification is explicitly
disabled, so a mapping is not generated. Native-symbol upload remains a
non-blocking diagnostic recommendation, not a passed action.

Required durable reviewer credentials and the real disclosure/refusal/consent/
enablement/blocking video remain missing. Permission to prepare a separate
`play-review@justnoise.shop` account, manual password entry and A16 video
participation was requested. No real account was changed or deleted. No already
accepted device tests were repeated. **No production submission or rollout.**

Proof files: `/private/tmp/justnoise-iarc-complete.png`,
`/private/tmp/justnoise-account-deletion-live.png` and
`/private/tmp/justnoise-production19-review.png`.

## Scoped account verification and reviewer-password handoff

The owner explicitly approved a separate reviewer account and read-only Supabase
verification, preserving the personal account. An aggregate-only SELECT returned
Google metadata field names and counts, not field values or individual accounts:
`name` / `full_name` on 30 Google accounts, `avatar_url` / `picture` URL fields on
29, plus email, provider IDs and verification/issuer metadata. Name collection is
therefore evidenced and must be added to the final Data safety declaration.
No photo binary collection is established by an avatar URL alone. No user records,
permissions or authentication settings were changed by this query.

Exact search for `play-review@justnoise.shop` returned no existing user. Prepared
Supabase Authentication → Users → Add user → Create new user with only that email
entered. Password remains blank; account creation has **not** been submitted.
The owner must enter the new password and complete creation personally. The
form's default Auto confirm user option is visible; it applies only to this new
reviewer account, not a project-wide confirmation setting. No password was read,
generated, entered, stored locally or sent to Play by the agent. Proof:
`/private/tmp/justnoise-reviewer-password-handoff.png`.

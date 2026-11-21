# Development closure after 1.0.12 (14)

Scope: Android development and technical packaging. No privacy drafting, public
store launch, owner-data reset or repetition of already accepted phone tests.

## Accepted evidence retained

1.0.12 (14) is published on the existing internal Play track and installed in place
on the A16 through com.android.vending. Owner now confirms its shield Zap artwork,
long-press session start and Emergency Unzap all work. Recorded as owner-reported
PASS, not a new automated device trace. Prior mode creation/selection, normal and
scheduled sessions, blocking/unblocking, held/repeated NFC, Home-navigation,
Year overview and Now Bar evidence remain retained. None was repeated this turn.

## Current presentation change

1.0.13 (15) reorganizes Settings only:

- Account: existing account entry.
- Your Zap: Emergency Unzap with remaining count and unchanged confirmation;
  existing Now Bar/Live Update management.
- Help: replay introduction and contact support. Copyable address/failure text
  appears only if the email-app handoff fails.
- More settings: device setup/recovery, notification management, Android app info,
  existing privacy and local-data review. Privacy content is unchanged/out of scope.
- Version is a small footer instead of a two-row About card.

Each settings level has independent saved scroll state; Back returns through the
correct parent and the host retains its subpage during recreation. All actions
remain explicit existing callbacks. No session engine, storage migration,
permission grant, telemetry or account mutation is introduced. iOS source, assets
and project remain unchanged.

## Still required before unconditional development closure

1. New Settings visual/navigation acceptance after an authorized update. This is
   a changed layout check, not a repeat of Emergency/session behavior.
2. Complete current first-run acceptance: permission guide, actual Settings
   return/automatic progression, video presentation/dismissal and Home. Existing
   owner data must not be cleared or working permissions revoked to manufacture
   a new first run. Use a genuinely fresh tester installation when available;
   record already observed owner steps and test only the missing portions.
3. Remaining live account paths: Google entry is owner-reported working. Repaired
   SMTP and received/confirmed mail do not by themselves prove Android email-link
   return, password reset, account deletion and cross-account isolation in live
   use. Preserve the owner's account; use owner-approved disposable test accounts
   for unresolved destructive/account cases. Local regression evidence is separate.
4. Final immutable release package after those findings: signed bundle, source
   snapshot/checksums, internal upload and data-preserving Play install each have
   their own evidence. Offline signing-key backup remains an owner dependency.

## Historical interruption disposition

No positive old crash/Force Stop cause was established. A package-update record is
not proof of the earlier interruption's cause. UI automation can suppress the
Accessibility service, so it is prohibited on the owner's paired A16; do not use
it to create another warning. No new interruption was reported with this accepted
candidate. Retain conservative readiness/recovery rules and inspect passive
diagnostics only if a real interruption recurs. This historical unanswered cause
does not justify arbitrary repeats of passed core tests or a new UI debug panel.

These are development/acceptance boundaries, not a public Play approval claim.

## Local Settings verification

The four new organization/navigation source regressions and the existing shortcut
wiring regression pass. The production suite passes 551 tests / 70 suites with
zero failures, errors or skips. The final signed Release build succeeds; lint has
zero errors, 106 warnings and three hints (unused resources increase after removing
the large main-screen cards). Product contracts and production layout pass.
Changed emulator-only UI tests compile; no instrumentation is run on the A16.
These checks do not establish rendered Settings acceptance or close live account
and first-run evidence. The previous iOS Shield source and diff hashes are unchanged.
The final full pre-device verifier passes, including 44 release-verifier regressions.
Signed candidate: android-app/build/candidates/1.0.13-15-5b2c7a23/. Not distributed.

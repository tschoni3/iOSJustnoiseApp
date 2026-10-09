# Connected account UI and PostHog — September 9, 2026

## Implemented source

- Production Application performs local account reconciliation on an IO worker.
  The offline Gate 0/development Applications still have no auth/network runtime.
- Grouped Settings opens a native Account Activity: email sign-in, registration,
  recovery request, Google browser handoff, validated PKCE return, password update,
  logout and explicitly confirmed/retryable account deletion. Password fields are
  memory-only and cleared on submission; the Activity uses FLAG_SECURE.
- Existing local data stays separate by explicit UI choice. Account operations
  use the real coordinator/journal/product namespaces, not preview state. Account
  transitions gate product rendering and replace retained History/mode/schedule
  ViewModels and drafts before exposing the new workspace.
- Account callbacks are exported only at the exact scheme/host/path and are
  independently checked against a live pending flow and generation. Incoming
  callbacks wait for startup reconciliation instead of being dropped while busy.
- Separate optional analytics opt-in, initially off. Owner asked to reuse existing
  events; read-only iOS source contains `focus_session_started` and
  `focus_session_ended`. Android uses those names, not invented mode/schedule events.
- The native PostHog capture adapter sends only the event, random unidentified ID,
  public project token and `$process_person_profile: false`. No account/email,
  selected apps, mode name, NFC contents, session ID/duration or Accessibility
  observations. No SDK autocapture, persistent queue, retry, person/alias call or
  redirect following. Network IP disclosure is included in the opt-in dialog.
- Successful manual/scheduled starts and successful Zap stops emit typed events;
  consent-generation tickets reject an outcome from a previous account/consent.
  Capture cannot throw into the session engine. Consent/account boundaries stop
  capture, persist revocation and cancel/join in-flight sends before success.
  Previously delivered requests cannot be recalled.

## Verified locally

- **392 release JVM tests, zero failures/errors/skips; release lint PASS.**
- Five real-runtime tests combine the production account runtime, real journal/
  coordinator/product storage and real SDK with in-memory disk and MockEngine:
  sign-in/logout workspace preservation, active-session rejection with stop intact,
  Google callback/commit, sanitized network rejection and analytics revocation.
- Seven analytics tests cover consent gating, stale generation rejection, in-flight
  cancellation/join, durable-write failure, ID rotation, exact payload/host and
  no redirect following. They send no real PostHog requests.
- Full Android verifier PASS: contracts, capability/UI/integrated tests/builds/lint,
  assembled (not executed) instrumentation, **44 artifact regressions**, integrated
  APK boundary. Production callback checks reject alternate/broad URI filters,
  extra activities and missing account reconciliation Application.
- iOS tracked app/widget/project diff is empty; `git diff --check` passes.
- No real login/email/deletion, PostHog upload, phone install, phone permission
  change, signing-key replacement or Play operation occurred.

Logs: `/private/tmp/justnoise-account-ui-checks-2026-09-09.log` and
`/private/tmp/justnoise-account-ui-readiness-2026-09-09.log`.

## Signed integration snapshot

Local package checks passed for both production artifacts. Preserved snapshot:
`android-app/build/candidates/1.0.0-1-0bd6b56c/`, with verification JSON/log.

- APK SHA-256: `b7fce830c34e518988981e44919acff6d50b7eaac9a725383673d7f54aaeadf2`
- AAB SHA-256: `0bd6b56cf1b2c7bd6424d28d3fdc2b3529b381973178612f456e1adcae810b03`
- Existing upload certificate, v2 APK signature, ZIP alignment, strict AAB signature,
  bundle validation, exact production manifest and native library alignment passed.
- The AAB check initially rejected bundletool's numeric-string representation of
  `singleTop`; direct inspection confirmed value `1`. A focused regression now
  accepts only the equivalent reviewed representations and rejects other modes.
- Build/verification logs: `/private/tmp/justnoise-account-ui-package-2026-09-09.log`
  and `/private/tmp/justnoise-account-ui-verification-2026-09-09.log`.
- **Integration build only, distribution HOLD. Not installed or uploaded.**

## Remaining, not credited as complete

- Live Supabase redirect allowlist/Google configuration and actual sign-in,
  registration, recovery/password update and server deletion acceptance.
- Expired/revoked credential UX and deletion reauthentication need further work;
  a pending deletion must not become an unfinishable UI state.
- Process-death/recreation handling for an already claimed/consumed callback,
  local handoff failures and account UI retry paths need broader end-to-end checks.
- Recovery credentials are temporarily held in memory pending the password form;
  verify cleanup on abandoning that form or accepting another account.
- Rendered compact/large-text/TalkBack account UI and A16 visual/layout checks.
- Signed package validation passed separately; it does not prove backend,
  default-user Now Bar rendering, Chrome keyboard blocking or safe distribution.
- The installed A16 candidate predates this work. No installation approval is
  inferred from source implementation or a successful local build.

Primary reference: [PostHog capture/anonymous-event API](https://posthog.com/docs/api/capture).
The iOS event-name reference is `Features/NFC/NFCViewModel.swift`; iOS was not edited.

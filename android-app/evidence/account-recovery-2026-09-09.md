# Account recovery hardening — September 9, 2026

## Implemented and locally verified

- Expiring deletion credentials refresh before the authenticated delete request.
  Rejected credentials lead to explicit same-account reauthentication without
  discarding the pending deletion or switching to another account.
- Google and password-recovery reauthentication flows are bound to the current
  account generation and purpose. A different returned account is rejected before
  any password update. Local deletion cleanup cannot start a new auth flow.
- Leaving the password-update form clears its temporary credentials and cancels
  only the matching flow. A later login also clears abandoned recovery state.
- Activity recreation does not deliver the same Intent again. An encrypted,
  already-claimed link can be retried explicitly after process restoration; local
  restoration itself makes no provider request.
- **401 release JVM tests passed, zero failures/errors/skips. Release lint passed.**
  Nine additional tests cover the runtime and pending-flow cases above using mock
  HTTP, not live accounts. These checks do not establish physical-device UX.
- Log: `/private/tmp/justnoise-account-recovery-checks-2026-09-09.log`.
- Full Android pre-device verifier passed, including 44 artifact regressions and
  the integrated APK boundary. Instrumentation was built, not run on the phone.
  Log: `/private/tmp/justnoise-account-recovery-readiness-2026-09-09.log`.
  `git diff --check` passed; tracked iOS app/widget/project diff is empty.

## Read-only live configuration check

The existing project's public Auth settings endpoint reports Google and email
enabled, signup enabled, and email auto-confirm disabled. This does not verify
Google credentials, the redirect allowlist, email delivery or an end-to-end login.
No real sign-in, password reset email, account deletion or analytics event was sent.

The owner authorized appending only the Android return address to the existing
Supabase project. Dashboard access currently stops at sign-in. **No backend
configuration was changed.** Existing Site URL and iOS entries must be preserved.
The Android callback has the exact scheme/host/path
`com.justnoise.app://auth/callback` and a per-flow `state` query value. Verify the
allowlist match against that dynamic query before saving; do not allow unrelated
hosts or paths. Reference: https://supabase.com/docs/guides/auth/redirect-urls.

## Remaining limitations

- Live registration, Google sign-in, recovery/password update and server deletion
  still need acceptance. A server deletion with a lost response cannot be inferred
  successful from a subsequent authorization failure; support/backend evidence
  may be needed if the account no longer exists.
- Rendered lifecycle/large-text/accessibility and affected A16 checks remain open.
- The signed `1.0.0-1-0bd6b56c` snapshot predates these recovery changes. No new
  phone installation, permission change, package upload or distribution occurred.
- iOS remains read-only. This is not a release GO.

## Later checkpoint — successful-path coverage and signed snapshot

Three further real-runtime/MockEngine tests passed: successful password update
before accepting the account workspace (with duplicate submission rejected),
Google reauthentication preserving modes and remote-deletion intent, and
unconfirmed registration preserving the local workspace without a stored session.

- **404 release JVM tests**, zero failures/errors/skips; release lint PASS.
- Full Android pre-device verifier PASS, including 44 artifact regressions.
- New signed snapshot: `android-app/build/candidates/1.0.0-1-2cb5a5e7/`.
- APK SHA-256: `38f582eb4f6c75b4289351f1778dd98ea260308ddefd2ce1559f03b94bea5330`.
- AAB SHA-256: `2cb5a5e76ccf6d945ef379f3eb7cc6fe9f706ec01955c8cc91a7ba42c3605993`.
- Existing upload certificate, release manifest/DEX boundaries, APK signature and
  alignment, strict AAB signature/bundle validation/native alignment passed.
  Verification JSON/log are preserved beside both artifacts. Not installed/uploaded;
  distribution HOLD. This does not replace pending live account/device evidence.
- Logs: `/private/tmp/justnoise-account-final-package-2026-09-09.log`,
  `/private/tmp/justnoise-account-final-verification-2026-09-09.log`, and
  `/private/tmp/justnoise-account-final-readiness-2026-09-09.log`.

The Dashboard became accessible during this follow-up. Its existing redirect list
contains exactly `justnoise://open` and `justnoise://reset-password`; the Site URL
is `https://example.com`. The Android-only pattern
`com.justnoise.app://auth/callback\?state=*` is prepared but **not yet saved**,
pending the browser's action-time confirmation for the auth destination addition.
The escaped question mark is literal under Supabase's documented glob syntax;
only the flow-state value is variable. Existing entries have not been edited.

Two newly observed release risks remain open:

1. Registration currently supplies no Android redirect override and would use the
   existing placeholder Site URL after confirmation. Android needs a deliberate
   confirmation-return flow before live registration acceptance. Do not alter the
   shared Site URL or iOS entries to hide this issue.
2. Supabase displays "Grace period is over" and warns that exhausted quota stops
   requests. The project currently reports Healthy. No billing/plan change was
   attempted; this banner is not evidence that requests already fail.

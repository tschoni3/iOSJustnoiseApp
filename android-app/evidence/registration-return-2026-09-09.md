# Android registration return — September 9, 2026

## Cause and fix

Read-only Supabase Dashboard inspection showed the shared Site URL was
`https://example.com`. Android signup previously supplied no redirect override,
so the confirmation return would inherit that placeholder. The shared Site URL,
both iOS redirects and all iOS source remain unchanged.

Android now creates an encrypted, generation-bound REGISTRATION pending flow,
sends its own `com.justnoise.app://auth/callback?state=<flow-id>` redirect and S256
PKCE challenge, and waits for verified durable verifier storage before reporting
the confirmation request successful. A returned user without a session does not
sign in or assign local data. The confirmation callback uses the existing strict
URI/nonce/expiry/one-time claim checks and real account coordinator. Google URL
creation rejects registration-purpose handles. Expired or wrong-phone callbacks
cannot restore a session; the user can confirm email and sign in manually.

This is Android-only configuration at the request level. It does not change
Supabase's shared email templates or Site URL and does not itself prove that the
live template honors the request's redirect.

## Verification

- **405 release JVM tests, zero failures/errors/skips; release lint PASS.**
- Real pinned SDK + MockEngine asserts signup endpoint, Android redirect,
  S256 challenge and retained verifier. Runtime tests cover unconfirmed signup
  preserving local data and confirmation callback accepting the account once.
- Full Android pre-device verifier PASS, including 44 artifact regressions.
- Existing upload certificate, APK v2 signature, release manifest/DEX boundary,
  APK ZIP alignment, strict AAB signature, bundle validation and native library
  alignment PASS. These are local artifact checks, not device/backend acceptance.
- `git diff --check` passed; tracked iOS app/widget/project diff remains empty.
- No live signup/email/deletion, PostHog event, phone install or Play upload.

## Preserved signed snapshot

`android-app/build/candidates/1.0.0-1-6b14c24a/` contains APK, AAB and verification
JSON/log. Application ID `com.justnoise.app`, version 1.0.0 (1).

- APK SHA-256: `f10d5c4a3e7f00bdaa8cee65e58cc62cc1af98e3bb2af9a7a334ad454d9f947a`.
- AAB SHA-256: `6b14c24a6945822299628f4722acdaf02ab607fb0f19f673bd96a63d0832f53b`.

Logs under `/private/tmp/`:
`justnoise-registration-return-checks-2026-09-09.log`,
`justnoise-registration-return-package-2026-09-09.log`,
`justnoise-registration-return-readiness-2026-09-09.log`,
`justnoise-registration-return-verification-2026-09-09.log`.

**Distribution HOLD.** The owner-authorized Android redirect is prepared in the
Dashboard but awaits action-time save confirmation. Its pattern is
`com.justnoise.app://auth/callback\?state=*`; the existing two iOS redirects remain
`justnoise://open` and `justnoise://reset-password`. Live Google, registration,
recovery and deletion acceptance, rendered account UI, the affected A16 tests,
default-user Now Bar evidence and secure offline signing-key backup remain open.
The observed Supabase quota/grace-period banner is recorded separately in
`account-recovery-2026-09-09.md`; no billing or plan change was attempted.

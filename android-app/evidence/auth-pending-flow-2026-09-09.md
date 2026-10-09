# Pending account links and SDK integration — September 9, 2026

## Implemented

- Production-only AES-GCM pending-flow storage in Android's no-backup directory,
  separate authenticated context from the existing session journal. Existing
  journal ciphertext remains compatible. No password is stored.
- Random flow IDs, account-generation binding, recovery/Google purpose, 30-minute
  expiry, exact URI/query validation, duplicate-claim rejection and explicit
  restart retry that invalidates an older worker claim.
- Durable encrypted tombstones for consumption/cancellation; stale callbacks
  cannot clear a newer flow. Clock rollback, stale account generation, unreadable
  storage and failed writes never become successful login.
- Native Google OAuth URL generation through the existing pinned Supabase SDK,
  with a state-bound callback and PKCE challenge. No browser was opened and no
  Google account or backend configuration was changed.

## Integration defect found and corrected

The real-SDK/encrypted-store test initially failed. Supabase 3.6 generates a
padded base64url verifier (64 random bytes) and launches cache writes asynchronously.
The original strict unpadded grammar rejected that verifier, and short-lived
client disposal could race its write. The adapter now retains the exact SDK bytes,
awaits successful save/readback before returning a usable URL/request-success
result, and captures asynchronous storage failure without leaking its contents.
Recovery HTTP can already have been submitted when a local write fails; the app
must report retry, not claim the resulting link is usable.

SDK lifecycle observers are disabled, avoiding incidental lifecycle restoration
outside the authoritative account coordinator. No developer override/private
Samsung API is involved.

## Verification

Final suite: **380 JVM tests, zero failures/errors/skips; release lint PASS**.
This includes 12 pending-flow cases and 15 real-SDK transport/configuration cases.
Additional regressions cover delayed verifier persistence and failed Google/recovery
writes. The complete Android pre-device verifier also passed (contracts, offline
capability/UI/integrated builds/tests/lint, 42 artifact-checker regressions and
integrated APK boundary verification). Native instrumentation was assembled only,
not run. Tracked iOS tree diff is empty and `git diff --check` passed.

Logs:
- `/private/tmp/justnoise-auth-flow-checks-2026-09-09.log`
- `/private/tmp/justnoise-auth-flow-readiness-2026-09-09.log`

All authentication HTTP uses Ktor MockEngine and synthetic test accounts. No
email, real login, provider mutation, phone install, instrumentation, iOS edit,
signing-key change or Play upload occurred.

## Not complete

No exported callback Activity, account UI, verified live redirect allowlist,
Google end-to-end login, recovery password UI or actual PostHog integration is
claimed. Runtime startup/account transitions must call pending-flow purge and
must validate the claim before accepting a provider session. Recovery must retain
its password-update purpose. A server-consumed code after a lost response may need
a new link. The owner's installed candidate predates these account changes.

Primary reviewed sources:
- [Pinned PKCE generation](https://github.com/supabase-community/supabase-kt/blob/3.6.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/PKCE.kt)
- [Pinned asynchronous verifier save](https://github.com/supabase-community/supabase-kt/blob/3.6.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthImpl.kt)
- [Pinned Android lifecycle setup](https://github.com/supabase-community/supabase-kt/blob/3.6.0/Auth/src/androidMain/kotlin/io/github/jan/supabase/auth/setupPlatform.kt)

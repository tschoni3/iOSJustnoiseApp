# Native Supabase provider — 2026-09-09

## Scope

Production-only `SupabaseAccountProvider` uses the existing iOS Supabase project
and public anon key, with native Kotlin Auth/Functions SDK 3.6.0 and Ktor Android
3.4.3. This pin matches the project's Kotlin 2.3.21 toolchain; latest upstream
3.8.0 uses Kotlin 2.4. No service-role/admin key or backend change was introduced.

Implemented SDK operations: email/password login, registration with explicit
confirmation-required outcome, same-account refresh, PKCE recovery request/code
exchange, password update and the existing delete-account function. Password
update/deletion first retrieve and compare the server's authenticated account ID.
Deletion accepts the existing iOS client's HTTP-200 contract, not timeout/202/4xx.
No server response text/headers/SDK exception cause is exposed in UI errors.

Each request uses a disposable SDK client: in-memory session manager, automatic
load/save/refresh disabled, logging NONE, HTTPS project URL, 15-second request
timeout, redirect following disabled. Provider results do not persist credentials
or publish login. The existing journal/coordinator remains authoritative.

The external PKCE cache retains ownership of verifier expiry/consumption. A test
caught that SDK `clearSession()` deleted it on request disposal; the cache adapter
now delegates read/save but prevents SDK disposal from deleting a still-pending
flow. The actual encrypted, generation-bound flow store is not implemented yet.

## Verification

- **365 release JVM tests, zero failures/errors/skips**; 12 new tests execute the
  real SDK with Ktor MockEngine. No external authentication requests or emails.
- Verified request host, method, path, auth headers, preserved password whitespace,
  confirmation versus session, same-owner refresh, error redaction, no network on
  invalid input/construction, PKCE challenge/verifier, verified-identity checks and
  exact deletion success status.
- Release lint PASS. 42 APK/AAB boundary regressions PASS.
- Complete pre-device verifier PASS: contracts, capability/UI/integrated tests,
  lint/builds, artifact regressions and integrated APK boundary inspection.
- Logs: `/private/tmp/justnoise-supabase-checks-2026-09-09.log` and
  `/private/tmp/justnoise-supabase-readiness-2026-09-09.log`.
- Merged production manifest adds INTERNET and the SDK's metadata entry under the
  existing private AndroidX Startup provider. Its reviewed `SupabaseInitializer`
  bytecode only retains application context; it creates no client/request and has
  no initializer dependencies. No extra permission, visibility query or service.
- The package-specific APK/AAB verifier allows INTERNET only for `com.justnoise.app`;
  development/capability package restrictions remain unchanged.
- No iOS edits, native instrumentation, device installation, real account mutation,
  signing-key change or Play operation occurred.

## Still required

Actual Account Activity/UI and coordinator wiring; retained UI invalidation;
encrypted pending PKCE flow with callback origin/generation/expiry validation;
Google sign-in; Supabase redirect configuration verification; real opt-in PostHog
boundary; native/device end-to-end tests; combined release build and acceptance.
These HTTP-mock tests do not establish a working production recovery link or real
backend account deletion, and the installed A16 candidate still predates this code.

Primary references:
- [Supabase SDK installation](https://supabase.com/docs/reference/kotlin/installing)
- [Pinned upstream dependency versions](https://github.com/supabase-community/supabase-kt/blob/3.6.0/gradle/libs.versions.toml)
- [Pinned Auth configuration](https://github.com/supabase-community/supabase-kt/blob/3.6.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthConfig.kt)
- [Supabase recovery](https://supabase.com/docs/reference/kotlin/auth-resetpasswordforemail)
- [Supabase Functions](https://supabase.com/docs/reference/kotlin/functions-invoke)

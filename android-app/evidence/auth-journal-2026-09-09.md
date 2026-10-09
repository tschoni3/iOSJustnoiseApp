# Android account-session journal — 2026-09-09

Scope: first production-only storage slice of the owner-approved connected release.
Not a working sign-in screen, product-account isolation or backend acceptance.
No owner account, provider configuration, telemetry or iOS source was modified.

## Implemented

- Native AES-256-GCM encrypted versioned session record; package/version-bound
  associated data, random nonce per write, separate Android Keystore key, atomic
  replacement in the application's no-backup directory and bounded reads.
- Only account ID/email, access/refresh credentials and expiry are stored. No
  password field, plaintext fallback, logging of record contents or new permission.
- Durable operation generations reject late sign-in/refresh results. A refresh
  cannot change the account identity. Sign-out removes stored credentials; failed
  writes are errors, not successful UI state changes.
- Durable deletion states distinguish remote deletion still pending from remote
  success with local cleanup still pending. Cleanup ownership survives a restart;
  ordinary sign-out and another sign-in cannot bypass deletion. Missing keys,
  tampered/truncated/unknown records remain unavailable, never silently overwritten.
- Unique production adapters live under `android-app/app/src/main/java/com/justnoise/app/auth/`.
  The one-module layout verifier permits only that reviewed namespace and its tests;
  canonical NFC/restriction/schedule/History code remains reused, not copied.

## Verification and limits

317 release JVM tests passed, zero failures/errors/skips, including **21 new auth
tests**. These execute the real journal, binary codec and JCA AES-GCM implementation
with synthetic identities and replace only the Android key/file adapters. Release
lint and the complete Android pre-device verifier passed; the latter also ran the
42 existing package-verifier regressions. iOS tracked-tree diff is empty and
`git diff --check` passed.

The first test compile exposed one public-test/internal-type visibility error,
which was fixed. A readiness invocation without JAVA_HOME stopped before Gradle;
the completed rerun used the bundled Android Studio JDK. Successful logs:

- `/private/tmp/justnoise-auth-journal-checks-2026-09-09.log`
- `/private/tmp/justnoise-auth-journal-readiness-2026-09-09.log`

The production source compiles, but **the journal is not yet constructed by the
application**. No new authentication file or key has been created on the owner's
phone. Android Keystore/filesystem behavior still requires dedicated acceptance;
JVM tests do not prove it. The existing signed db0c2798 snapshot does not contain
this later auth work and is retained unchanged.

## Next required integration

1. Account-owned product namespaces and explicit legacy-local-data migration;
   quiesce stale UI, alarm and analytics work before publishing account transitions.
2. Supabase native provider, confirmation/recovery flow, credential refresh,
   remote deletion and actual account screens. Disable SDK automatic plaintext
   session storage; late callbacks must pass the journal generation barrier.
3. Consent-gated PostHog and durable account-boundary queue cleanup.
4. Actual A/B, offline, crash/relaunch and deletion acceptance, then rebuild/sign
   and verify the combined release. No completion credit is claimed for these yet.

Primary technical references reviewed:
[Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[AtomicFile](https://developer.android.com/reference/android/util/AtomicFile),
[Supabase Auth configuration](https://supabase-community.github.io/supabase-kt/auth-kt/io.github.jan.supabase.auth/-auth-config-defaults/index.html).

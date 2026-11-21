# Android RC1 — technical release closure, October 11

## Reopened: fresh-install signup blocker

The owner subsequently uninstalled the app and started the real fresh Play flow.
Agent did not delete data. Play installation of 1.0.8 (10), the two tutorial pages
and real pairing scanner were observed. The full first-run check is **not passed**:
owner-reported email registration failed, and live Supabase Auth logs confirm
Resend SMTP rejected confirmation mail because the key's domain is not verified.
See `signup-diagnosis-2026-10-11.md`. This is a customer-registration blocker,
not an artifact/signature failure. Do not call this customer-ready until repaired.

Working source now contains the separate owner-requested auth-button cleanup and
sanitized, operation-specific signup error handling. These are not in the immutable
1.0.8 (10) APK/AAB/source snapshot below. Bundle a new version only after the shared
mail-delivery fix and one real signup/confirmation acceptance. Existing binaries,
checksums and previously accepted physical checks remain unchanged.

Exact candidate: `android-app/build/candidates/1.0.8-10-f2f046d2/`.
Public package: `shop.justnoise.app`, version 1.0.8 (10).
Owner requested pushing development to final release. This does not authorize
public rollout, owner-data deletion, account mutation or provider changes.
Privacy drafting and store publication remain outside the development scope.

## Verified and frozen

| Area | Evidence / boundary |
| --- | --- |
| Packaging | Signed APK/AAB checks PASS; non-debuggable; no preview/debug probe classes; pinned external upload key; 16 KB alignment checks pass. |
| Automated tests | 512 production JVM tests / 61 suites, zero failures/errors. Reused October 11 build results; unchanged suites were not repeated. |
| Lint | Zero errors, 93 warnings, two hints. Triage below; not silently suppressed. |
| Distribution | Active internal Play release, one version code; A16 installed 1.0.8 (10) through `com.android.vending`. Not public release. |
| Year | All 365 days visible on standard A16 without scrolling; real October 10 dot/details verified. Leap-year geometry covered locally; enlarged-text/rendered emulator checks not executed. |
| Be Right Back | Owner answered `j a passt passed` to the exact Home-navigation / active-session / Zap-stop check on October 11. Owner-reported PASS, not an independently automated phone trace. |
| Existing core flows | Prior accepted physical manual/scheduled sessions, blocking/unblocking, duplicate Zap and Now Bar evidence retained. No affected engine change in 1.0.8; no repeat performed. |
| Data and iOS | Mode/app and History visibly retained after Play update; no reset, uninstall or permissions changed. Pre-existing Shield checksum unchanged. |

## Warning disposition

57 unused resources primarily include presentation copy removed from visible UI.
They are not proof that those texts remain visible. Do not delete contract/reference
resources speculatively before release. Dependency upgrade recommendations (six)
are not a reason for last-minute untested library upgrades. No vulnerability claim
is made by this review.

Portrait warnings (six) follow the owner's deliberate phone-only portrait UX;
Android's large-screen behavior is platform-dependent, not bypassed. NFC-required
warning is intentional for the physical-Zap product. Persistent `commit()` is
intentional for durability checks; replacing it with `apply()` would weaken that
boundary. Inflating the overlay without a parent is deliberate before WindowManager
attachment. Remaining KTX, state-boxing, typography, plural and background-overdraw
recommendations are non-blocking polish; no speculative runtime refactor.

Target SDK 36 is unchanged and was accepted by Play for this internal release.
Lint's newer-target recommendation is not proof of current public-store eligibility.
Play's two warnings concern R8 mapping/native symbols; R8 is disabled, and the
native libraries are dependencies. Neither blocked the internal upload.

## Remaining honest limits before calling this fully final

1. **Fresh production onboarding on Samsung:** existing data must not be cleared.
   Current route requires tutorial/pairing/account and consent/manual system access,
   with no mandatory mode creation. Four targeted disposable-emulator route checks
   pass; complete fresh Play-installed Samsung flow has not been recorded on this
   candidate. An update opening Home does not prove first-run acceptance.
2. **Skipped account lifecycle:** Google product entry works; live registration,
   recovery/deletion and full A→B→A were explicitly skipped by owner because the
   password was unavailable. Synthetic account isolation tests are not live proof.
   Do not sign out, reset or delete the owner's account to close this item.
3. **Historical interruption:** current inspection and subsequent owner use did
   not establish its old cause. Retained process history showed PACKAGE UPDATED,
   not a positive old crash/Force-Stop cause. Keep conservative service checks;
   correlate local diagnostics if it recurs, not another arbitrary blocking test.
4. **Public-distribution dependency (not implemented here):** bundled privacy draft
   still claims no accounts/analytics/server account. It contradicts the connected
   app and remains reachable through Settings. This is a public-release blocker;
   owner excluded privacy work, so do not rewrite/publish it without scope approval.
   Public Play app-content/Accessibility review and upload-key backup are unverified.

Decision: **RC1 delivered for final owner testing; technical package ready;
not an unconditional public-production GO.** No new binary needed for this audit.

## Source preservation

A curated Android source/resource snapshot and per-file hashes are attached beside
the immutable binaries. It excludes keys, local properties, caches and owner data;
includes canonical sources, Gradle wrapper, shared contracts, artwork, docs and
the read-only NFC Swift reference needed by Android label tests. It is a source
handoff, not a claim of a clean Git commit or bit-identical independent rebuild.
External toolchain/dependency downloads and the existing external signing key
remain prerequisites. Archive contents are byte-verified against the manifest.

The final 299-input archive (SHA256
`b923d94bb6b68c33762304b5dcaccf6217d71ba5d32c568d96c1eac75d4c7371`)
was extracted into an empty `/private/tmp` tree. Offline assembleRelease and
bundleRelease succeeded in 31 seconds using the existing local Gradle/dependency
cache (42 tasks executed, 16 from cache). The rebuilt APK passes the release
boundary check and apksigner with the exact pinned upload certificate. No unit or
phone tests were rerun. The first standalone artifact-check invocation omitted
JAVA_HOME/ANDROID_HOME; the corrected invocation passed. This environment error
was not an application failure. The original tested/published binaries are not
replaced by the rebuilt ones. Log: candidate `frozen-source-rebuild.log`.

Official release-preparation and rollout guidance checked October 11:
https://developer.android.com/studio/publish/preparing
https://support.google.com/googleplay/android-developer/answer/9859348

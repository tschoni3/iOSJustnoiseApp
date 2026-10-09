# Play release identity — September 20, 2026

The verified JustNoise organization created Google Play app `4972093831482851244`.
Public name **Justnoise**, type **App**, pricing **Free** and default language
English (US) were selected. The permanent package is `shop.justnoise.app`.

Google Play reported `com.justnoise.app` as already in use. No app was created with
that identifier. The domain-derived `shop.justnoise.app` was then checked in the
same form and Google Play reported **Package name available**.

The production Android application ID and exact PKCE callback were changed to
`shop.justnoise.app`. Existing Kotlin source namespaces were intentionally retained;
the installed identity is independent. Supabase's redirect allow list now contains
`shop.justnoise.app://auth/callback\?state=*` in addition to the unchanged iOS and
earlier Android entries.

Candidate `android-app/build/candidates/1.0.0-1-94741f2a/` was built and signed:

- AAB SHA-256: `94741f2afee9319b25e5cdca11ac126baf768d1dc755ec74ccb27e1cd86e697d`
- APK SHA-256: `fba6969b635554d0c2984fe5d45cd53230660a7e5b626330c91f24a61e31225f`
- upload certificate SHA-256:
  `2b51da4e2d3fc6345480db87fdc15a185b12a1480358faf8e00e6d2991b26ebd`

All 470 production JVM tests pass. Release lint reports 0 errors, 74 warnings and
2 hints. Product contracts, full Android pre-device verification, all 44 artifact
regressions and the signed APK/AAB verification pass.

The account holder explicitly accepted the Developer Program Policies, Play App
Signing Terms and US export-law declarations. Google Play accepted the checked AAB
as version code 1 / version 1.0.0 and activated internal-track release
`1.0.0 (1) – Internal` on September 20, 2026. Track ID:
`4701314183475386469`.

The release has no Play validation errors. Play shows two non-blocking diagnostic
warnings: no deobfuscation mapping and no native debug-symbol archive. The track is
**Active**, the release is **Available to internal testers**, and the selected list
contains `tschoni.nguyen@gmail.com`. Tester opt-in URL:
`https://play.google.com/apps/internaltest/4701314183475386469`.

Google currently labels the internal app `shop.justnoise.app (unreviewed)` until
the wider app setup and review are completed. This is an internal-test publication,
not public production approval.

After Play propagation completed, the owner joined with the invited account,
installed the internal-track build on the Galaxy A16 and reported the requested
fresh smoke test successful. This is owner-reported hardware acceptance, not an
automated observation. No defect was reported and the already accepted full behavior
suite must not be repeated unless a later build changes affected behavior.

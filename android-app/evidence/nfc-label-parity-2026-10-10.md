# NFC compatibility-label gate — source follow-up

Owner requested Android to recognize NFC Text labels like unchanged iOS, rather
than accepting arbitrary valid Text at pairing. Read-only source reference:
`justnoiseradio/Features/NFC/NFCViewModel.swift` and `Shared/PortableZapRules.swift`.
No iOS file was edited. Existing Shield source checksum remains
`ba11a54393985636cc314fdcb5e55363dd6b2439a1901af711a38b224c5b3173`.

Android now verifies decoded strict UTF-8 text against fingerprints of the three
shipped iOS compatibility labels before initial or replacement pairing. New NFC
session starts additionally require this recognition; the existing exact-record
local HMAC comparison remains. Do not claim interchangeable-tag parity or
cryptographic genuine-Zap proof. Unknown previously paired tags retain stop-only
escape during an existing requested session; saved schedules are not silently
revoked. No tag bytes are written or added to analytics/logs.

Verification:

- Production `testReleaseUnitTest lintRelease`: PASS. 503 tests / 58 suites,
  zero failures/errors/skips. Lint: zero errors, 94 warnings, two hints.
- Six new regressions cover read-only iOS-list parity, exact matching in valid
  languages, whitespace/altered/unknown rejection, malformed/wrong-type/UTF-16/
  oversize rejection, caller-buffer preservation, all eight identifier-free
  migration fixtures and controller guard integration.
- Product contracts: PASS (18 colors, 27 copy entries, 89 original shared cases).
- Full pre-device verifier: PASS across the three development roots, including
  44 verifier regressions. Builds/lint pass; no owner-device instrumentation run.
- Scoped whitespace check: PASS.

Distribution boundary: the A16 and active Play track still have immutable
1.0.5 (6), without this source change. No new production candidate, upload or
physical foreign-tag result is claimed. The real first-run test is in progress
after the separately authorized owner-app reset; no further reset was performed.

Later packaging: signed local 1.0.6 (7), AAB hash
`888be688ff1bed287972d7451afd1897095a027be9a915e0a2e5d7fb657cd91e`, now
includes this label guard and the blocker Help cleanup. Production 504 tests / 58
suites, release lint (zero errors, 94 warnings, two hints), full pre-device checks
and signed artifact verification pass. This supersedes only the no-candidate
boundary above; no upload, install or real foreign-tag acceptance is claimed.

# Settings, real completion and replacement pairing — 2026-09-06

Android development implementation evidence, not Play upload or A16 acceptance.
Source starts from `11d4d9816809ff99b1d2720c8eff5fe4fa3d9d82` plus the preserved
dirty worktree and this Android-only slice. No commit, owner-phone operation,
production promotion, signing-key creation or upload occurred. iOS and product
contracts remain unchanged. The read-only emulator was closed without saving.

## What changed

- History gear opens grouped Settings with actual setup truth/version, read-only
  tutorial replay, explicit native Setup/App-info actions and bundled draft privacy.
  Review local-data removal navigates to Setup; it does not delete data.
- Foreground stop returns the exact newly committed outbox record. The completion
  sheet uses the existing iOS source threshold of at least 20 minutes, real target
  count/duration, and independent interruption/timing uncertainty. No latest-row
  lookup, fabricated mode/Signal/reflection, verified-protection claim or background
  popup. Duplicate/stale/failed/legacy-ineligible stops return no receipt.
- Replacement pairing now needs explicit confirmation and a frozen, one-shot
  process/consent/session/digest-generation token. Atomic guards reject active,
  recovery, pending-deletion, failed-write or stale state. Rollback preserves the
  earlier pairing on failed commit. No-token calls are initial-only; initial pairing
  still grants no consent. Native cancellation/pause invalidates late reads.

See [source baseline](../../../../docs/reference/ios-release-2026-09-06/settings-completion-source-baseline.md)
and [implementation tracker](../../../../docs/ANDROID_PLAY_IMPLEMENTATION.md).

## Verification actually run

| Check | Result | Scope |
| --- | --- | --- |
| Full integrated instrumentation | 58 passed | Existing 39 cases plus 5 real completion Activity, 6 completion surface, 4 real Settings Activity and 4 Settings surface cases |
| Compact / actual system text 200% | 19 passed | All four new integrated test classes; same real adapters and synthetic fixtures as standard |
| Capability transaction/UI regressions | 36 passed | 12 replacement-pairing safety, 13 History/receipt and 11 first-run persistence cases |
| Full repository verifier | PASS | 217 JVM cases: 147 capability + 15 prototype contracts + 55 integrated; zero reported failures/errors/skips |
| Contracts / lint / builds / release boundary | PASS | 18 colors, 27 copy keys, 89 fixtures; lint/builds and 17 Python package-checker tests |
| iOS and portable-contract Git comparison | Empty | App/tests/project/extensions/product against starting HEAD |

Instrumented runs reported `OK` with exactly the listed counts; no failure or skip
was reported. Original command logs are alongside this README. Gradle verifies
build/package boundaries, not physical NFC, actual interception or Google approval.

Standard emulator: read-only `medium_phone`, Android API 36, `emulator-5554`,
1080×2400, density 420, system `font_scale=1.0`. Compact: override 945×1680
(360×640 dp), same density, **Android system `font_scale=2.0`**, not merely a
Compose density override. Animator/window/transition scales were zero in both runs.
Physical A16 was never targeted. All test cleanup and seeded records were disposable.

The initial completion surface test used a parent Compose font override, which
cannot prove a ModalBottomSheet window's text scaling. It was corrected before the
final 58/19 runs. The retained final bounded-viewport capture has a neutral filename;
no earlier falsely labeled font-scale screenshot is included in this evidence folder.

## Visual evidence

PNGs are unmodified full-display screenshots. Standard captures show the actual
foreground completion with a synthetic interrupted 25-minute session and a labeled
Settings presentation fixture. Compact captures include real Settings navigation,
the real completion receipt and a synthetic warning-sheet scrolled to Done.
They were visually inspected for text wrapping, group hierarchy, scrolling and
reachable controls. At 200% text, content intentionally scrolls; cropped text at the
viewport edge is not claimed to fit simultaneously. Real Activity tests scroll and
activate Done; surface tests also exercise native Back.

The completion Activity tests use a synthetic paired byte value and a synthetic
prior-process timestamp to exercise real controller/outbox/SQLite behavior without
waiting 20 minutes or claiming service readiness. The background-stop regression
calls the shared controller and resumes MainActivity: it proves no popup on return,
**not** real DirectNfcActivity tag routing. Surface fixtures never feed app data.

Remaining visual acceptance includes comparison with the missing actual iOS
Release renders and the A16, including system-bar contrast/navigation on the final
release. These screenshots do not establish full pixel parity or physical safety.

## Final artifact identity

| Artifact | SHA-256 |
| --- | --- |
| Integrated debug APK | `3d9ff301d41895509387c4f6758c7b53004e2ec538e13e3d19e6d0681c8fc85b` |
| Integrated test APK | `e0598160762ed7c3c08838cc789b71141d44b295e0d5a26ec9bc6a493e602dac` |
| Integrated unsigned release APK | `9bfb41fe6d648f72a409b28353952f289abc9d16a42917373fdb1ac17dad3483` |
| Integrated unsigned AAB | `93547197ffa6eea6335510766dd2a9f018152c0a4126a0275077badd763b92bf` |
| Gate 0 debug APK | `a7f3512adf465d6912ca3a902b2cca8a1f755f4094fe0fab2890a8051c9382dd` |
| Gate 0 test APK | `cf70d3a193f625486299b657864628734c0ec1adc25e1959d3a73354d3d77bd9` |

`jarsigner -verify` reports the AAB is **unsigned**. It uses the development ID
`com.justnoise.androiddev`, not the agreed release ID. The smaller A16 beta GO,
canonical release promotion, signed release checks, real publisher/contact/policy
URL, account eligibility and Play/reviewer materials remain open. Actual upload
requires separate authorization.

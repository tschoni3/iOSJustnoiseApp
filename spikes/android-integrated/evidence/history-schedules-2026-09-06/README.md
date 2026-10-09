# Real History and schedule presentation — 2026-09-06

This checkpoint implements the approved Android-only source-first UI plan. It is
**not** an internal-beta GO, iOS rendered-parity sign-off or Play-upload-ready build.
The read-only API36 `medium_phone` AVD was explicitly targeted as `emulator-5554`.
The paired owner phone, its data and all iOS files were untouched. The AVD was closed
without saving its disposable test state.

## Final verification

- `ui-final-standard.txt`: **39 passed**, no failures/skips. Breakdown: 5 real
  first-run Activity, 4 real selection Activity, 7 selection ViewModel, 3 real
  History Activity, 7 History ViewModel/SQLite, 7 Zap render and 6 schedule render
  cases. Render fixtures do not earn real NFC/alarm/enforcement evidence.
- `ui-final-compact-font200.txt`: all **9 affected History/schedule cases passed**
  at 945×1680 px / 420 dpi (360×640dp), actual Android system font scale2.0.
  Day/History native Back, modal Save/Cancel, weekday access, error refresh and
  persisted History navigation were exercised. The enlarged day dialog scrolls;
  its lower Close action is not visible in the top-of-dialog screenshot.
- Full repository verifier passed: **208 JVM cases** (147 capability, 15 existing
  permission-free prototype contracts, 46 integrated); debug/release lint, builds
  and17 release-checker unit tests. Unchanged tasks may be Gradle-up-to-date/cached.
  After the final History contrast correction, the integrated46 JVM cases,
  debug/release lint, debug/test/release APKs and AAB were checked again in
  `final-integrated-build.txt`; the packaged release boundary and17 checker tests
  passed again. No signing/device/Play claim follows from the unsigned build.
- Product contracts:18 semantic colors,27 copy entries,89 portable behavior cases
  passed. `git diff --check` passed and iOS path diffs remained empty.

Final artifact SHA-256:

```text
a4f1f5f0a94b1612b740a316c0c76febb9e5f7dce995db71b32e398343b49350  app-debug.apk
fbf2ac8cf0dfea1e2ac2a16c94b4526ec3faed79ad45f582d9417071f4272b28  app-debug-androidTest.apk
a0ecb0933be790ed3c162111182e97254c74fccea49bec37440cfbab821df190  app-release-unsigned.apk
2a47c26ac527bb7a463530e6a5e12372e7d35d0d2fe275e061876575f9571ae4  app-release.aab
```

All artifacts still use `com.justnoise.androiddev`; `jarsigner -verify` reports the
AAB **unsigned**. This is not the final `com.justnoise.app` Play candidate.

## What is real

History reads all relevant existing completed records on a worker, independently
of list pagination. Tests use real SQLite with synthetic emulator-only rows and
verify every stored field remains unchanged. The126-row test covers pagination,
local-midnight attribution and exact totals. Range switching, date/time-zone
refresh, stale-result invalidation, background clearing, recreation, incomplete
reads, retry and withdrawal are covered. Warnings remain visible in the summary
and day details, never recast as verified protection. The90-day range is today−60
through today+29; Year is the current local calendar year.

Schedule presentation uses the existing real retained ViewModel and adapter. Its
new six render tests prove callback/refusal/layout behavior, not new storage or
alarm delivery. The prior verified schedule editor/management/runtime remain
unchanged. The only additional UI guard offers Delete only for a matching loaded
ID/revision, followed by explicit confirmation. No new consent, permission,
persisted field, alarm rule or session engine was introduced.

## Screenshots to inspect

Use **`final-standard/`** and **`compact-font200/`** for this final checkpoint.
Standard is1080×2400 px /420dpi, font1.0. Final runs set Android animator, window
and transition animation scales to0. This verifies static reduced-motion layouts,
not animation performance or all accessibility behavior.

- `real-history-ninety-day-persisted-fixtures.png`,
  `real-history-year-persisted-fixtures.png`, and
  `real-history-persisted-day-dialog.png`: actual Activity and real saved synthetic
  History, not owner data. At compact font2.0 the range captures show the tall
  scrollable all-time summary; the calendar is reached by scrolling and was tested.
- `fixture-schedules-compact-list.png` and
  `fixture-schedule-editor-weekday-controls.png`: explicitly pure render fixtures.
  The editor screenshot is mid-scroll after selecting Sunday; it is not a full
  form composite. Android's actual system font was2.0 in the compact run.
- Zap/selection screenshots in `final-standard/` repeat the prior checkpoint's
  roles: Zap states are fixtures; selection uses actual emulator launcher choices.
- **Ignore `fixture-schedule-editor-font200-weekday-controls.png`** wherever it
  appears: it is a retained earlier filename from a parent-LocalDensity test. A
  modal sheet has its own Android window density, so that file does not establish
  200% sheet text. The corrected test uses system font settings and the neutral
  filename above. Earlier `zap-ui-evidence/` and `settled-standard/` copies also
  precede the final History button-contrast correction; they are not final renders.

The compact run initially failed because the test tried to locate a not-yet-
composed lazy Refresh item. `compact-before-lazy-test-fix.txt` preserves that
failure. The test now scrolls the parent lazy list to the target before asserting
and clicking it; the final9-case run passes. No production behavior was weakened.

## Reproduce safely

Build with the repository's pinned Gradle wrapper, JDK17 and SDK36. Install debug
and test APKs only on an explicitly selected disposable emulator. Tests enforce
`ranchu`/`goldfish` hardware and `com.justnoise.androiddev` before fixture cleanup.

```sh
adb -s emulator-5554 shell am instrument -w -e captureZapEvidence true com.justnoise.androiddev.test/androidx.test.runner.AndroidJUnitRunner
```

For compact checks set emulator `wm size 945x1680`, system `font_scale 2.0`, then
run with `-e class com.justnoise.integrated.IntegratedHistoryActivityInstrumentationTest,com.justnoise.integrated.IntegratedScheduleUiInstrumentationTest`.
Pull unmodified PNGs from the app's external-files `zap-ui-evidence` directory.
Never run this instrumentation on the paired owner phone. Do not save AVD test state.

Remaining: authenticated iOS Release render comparisons, final A16 visual/functional
acceptance, actual completion sheet/grouped Settings, legacy replacement-pairing
hardening, signed production packaging, and real publisher/account/declaration
materials. See the [implementation tracker](../../../../docs/ANDROID_PLAY_IMPLEMENTATION.md).

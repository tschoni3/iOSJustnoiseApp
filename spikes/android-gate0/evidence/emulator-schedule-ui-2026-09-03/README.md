# Connected schedule editor — local verification

Verified 2026-09-03 Europe/Berlin against `2292763` plus this working-tree change.
This connects the native development UI to the existing schedule/session engine;
it does not promote the A16 gate, release identity, signing or Google Play status.

## Source boundary

- Real Schedules routing, persisted list/latest outcomes and native create/edit/
  enable/delete use the existing versioned preferences and session/History engine.
  One-time date/time and repeating weekdays use shared timing rules. Each scheduled
  start uses the current saved app selection; an active session keeps its snapshot.
- The management bridge checks the frozen consent epoch and schedule revision
  under the existing lock, then releases it before rearm. Saving can succeed while
  setup remains required; a rearm exception is not reported as a failed save.
  Only an explicit requested-enabled alarm-access button opens system Settings.
- A retained ViewModel owns in-flight changes across rotation. Android-managed
  instance state restores raw draft/route fields, original revision/instant/zone
  and an uncertain-mutation warning. An uncertain change is not automatically
  retried; reload/discard is explicit. Observed consent revocation clears the draft,
  and old queued consent epochs cannot survive withdrawal followed by re-consent.
- No new app storage schema, network destination, production module or permission
  was added for the editor. v4 disclosure, NFC authorization, active-session
  ownership and the completed History fields remain unchanged. iOS UI/runtime is
  untouched; this Android-only slice does not claim a new iOS test run.

## Verified results

| Check | Result |
| --- | --- |
| Product contracts | 18 semantic colors, 27 English copy entries and 89 portable behavior cases passed |
| JVM reports | **157 tests**, zero failures/errors/skips: 124 Gate 0, 15 UI prototype, 18 integrated |
| Editor JVM suite | **14 tests** of input/defaults, past dates, DST, travel, revisions and fixed instants; [saved XML](editor-jvm-results.xml) |
| Full emulator instrumentation | **83 tests**, zero failures/errors/skips; [saved XML](instrumentation-results.xml), 54.844 seconds in XML, 1 minute 4 seconds for Gradle |
| New management instrumentation | **14 tests** within those 83; CRUD/CAS, consent/withdrawal/re-consent, rollback, explicit refresh, lock release, saved-but-rearm-failed and real cancellation-token checks |
| Local build boundary | All verifier lint/build tasks, integrated debug APK, unsigned release APK/AAB, **13 Python release-checker tests** and packaged-release checks passed |

The full verifier reused up-to-date suites where Gradle determined appropriate;
the totals above are the actual XML report totals, not an assertion that every JVM
method reran. Lint completed with nonfatal warnings, not a zero-warning claim.
The saved XML differs only by LF line-ending normalization and a final newline.

Instrumentation ran exclusively on `emulator-5554`, API-36 Android 16
`medium_phone`, a disposable read-only AVD without snapshot saving. Tests use local
synthetic preferences and do not enable a real Accessibility service or use NFC
hardware. The existing runtime suite shell-grants exact-alarm access only to
`com.justnoise.gate0`; its real alarm callback still records
`CAPABILITY_UNAVAILABLE` without a bound service. This is not evidence of the
integrated permission flow or live blocking. Management cancellation tests check
actual PendingIntent removal, not successful scheduled enforcement.

## Exact commands

Run from the repository root; these are the recorded invocations, not new runs:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/tj/Library/Android/sdk \
bash scripts/verify-android-predevice-readiness.sh \
  > /private/tmp/justnoise-schedule-ui-readiness-final2.log 2>&1
```

After its successful exit:

```sh
ANDROID_SERIAL=emulator-5554 \
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/tj/Library/Android/sdk \
spikes/android-gate0/gradlew --no-daemon -p spikes/android-gate0 \
  connectedDebugAndroidTest \
  > /private/tmp/justnoise-schedule-ui-instrumentation.log 2>&1
```

The verifier runs contract/static-policy checks, each spike's JVM/lint/debug tasks,
the Gate 0 instrumentation-APK build, integrated release lint/APK/AAB builds and
the Python/packaged-release boundary. `connectedDebugAndroidTest` exercises the
Gate 0 test package, not the integrated Compose schedule screen.

## Artifact SHA-256

| Artifact | Digest |
| --- | --- |
| Integrated debug APK | `2cdd7df5ae1a8ce9e9028dbfb25329de1d90d1cdae6bd3784b69e0326c2e8afc` |
| Integrated unsigned release APK | `a06b83d9b52d14d790d67015e5981d6d771c8ff1539d0dfacac3ae91f598cbc0` |
| Integrated unsigned AAB | `ba24404f45dfdb594d21551c5e2584554db667798fa9eb9ca5b3892db9b7f0d3` |
| Gate 0 debug APK tested | `d351ad6c076bcc5c3913eaed2b6f5cd5626c9f6cf16ec4f660df724c939db21e` |
| Instrumentation APK | `a3e9292487027b3fba7f6e43f4b3beee4f6d68942a8ce170b0052de33399ca06` |
| Saved instrumentation XML | `7c875f01bf5475182c7336fff021ed8cc40db0228dfec92540631eb455eeac36` |
| Saved editor JVM XML | `21b6a3b98e9ce2252f05eb1597b3da6677f2f39f4509828ffe97ab740fc07215` |
| Saved-state screenshot | `4920b53eed65b1874985fbd77ab31dec2814d10e6b915edff1a74194dff1b0b8` |
| Scrolled-card screenshot | `ded800cd4f21261c7a9f4e6ba678e7595dfad8d20e1f49ef9b3aedfd4366dc2d` |

Integrated artifacts retain development ID `com.justnoise.androiddev`; the release
build and packaged boundary do not establish a signed `com.justnoise.app` release.

## Integrated UI smoke — limited pass

Installed the integrated debug APK identified above only on `emulator-5554` and
opened `com.justnoise.integrated.MainActivity`. No personal phone was addressed.
The emulator had no enabled Accessibility service. Its current v4 disclosure was
accepted through the visible setup checkbox/button, then Accessibility Settings
was left without enabling the service. No Zap was paired and no apps were selected.

Observed through visible controls, screenshots and scoped read-only sample-state checks:

- Zap → Schedules showed the real empty list and consent/setup-required state.
  Opening the list did not open Android Settings automatically.
- Created draft `UI-Smoke`, with the default one-time future date/time. Its name
  and schedule route survived portrait → landscape → portrait recreation.
- The explicit alarm-access button opened this app's Alarms & reminders page.
  Back without granting access returned to the same draft and scroll position.
- Saving produced exactly one stored record, revision 1, with its original
  one-time instant/creation zone. The list said **setup required**, not ready,
  and displayed the next planned time. [Saved state](saved-schedule.png) and
  [scrolled schedule card](saved-schedule-card.png) were visually inspected.
- Edit reopened the stored fields. An unsaved text change followed by Cancel
  left the original name and revision unchanged.
- The list switch disabled the same record at revision 2; the alarm-access card
  disappeared because no enabled schedule remained. It did not create a duplicate.
- Cancel in the delete dialog retained that disabled record. Confirming Delete
  removed it and returned the empty list. Emulator auto-rotation was restored.

The emulator was closed cleanly after the checks. The integrated package still had no exact-alarm operation/grant and no enabled
Accessibility service after this flow; no active session was started. This bounded
smoke does not cover successful permission grant, repeating/date/time picker
interaction, process-death restoration, an in-flight-save rotation race, large-font
acceptance or physical alarm/NFC/blocking behavior. Those are not inferred from the
screenshots or model/bridge tests.

## Physical and release limits

No A16 installation, pairing, selected apps, Accessibility settings or personal
content was changed for these checks. Real scheduled start while a selected app is
already open, screen-off delivery, Zap stop and one History completion, plus essential
permission/Force Stop recovery still need the existing A16 candidate flow. OEM
endurance and Play policy/account/signing remain separate gates. No A17 purchase is
required and no physical matrix cell or Play decision is promoted by this evidence.

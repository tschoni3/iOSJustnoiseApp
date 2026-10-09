# Explicit local-data import — September 19, 2026

## Scope and authority

Owner approved implementation of an explicit one-time import after mandatory
Google sign-in exposed an empty account mode picker. Before the previously
approved update, local Focus with one selected app was visible. Empty account
UI alone is not evidence that the separate local archive was erased.

This checkpoint authorizes source implementation, tests and local packaging.
It does not authorize a new owner-phone installation, automatic data adoption,
cloud sync, backend changes, upload or distribution. iOS is read-only for this
task. An unrelated existing/concurrent ShieldConfigurationExtension Swift diff
was observed and preserved; the whole working tree is therefore not iOS-clean.

## Implemented contract

`product/behavior/android-local-import.v1.json` records the Android-only extension.

- After verified sign-in, offer counts of unowned local modes, schedules and
  History. Confirmation explicitly identifies the destination account.
- Import modes with their app selections, schedules disabled, and all History
  records independent of UI pagination. Keep existing account records/selection.
- Deduplicate equivalent modes/schedules and exact History records. Remap mode
  and schedule ID collisions deterministically; retain History UUIDs.
- Reject differing records with the same History UUID in read-only preflight.
  No claim, source deletion or account overwrite is made for this conflict.
- A durable account claim and stored merged plan protect interrupted transfers.
  History copy is a destination SQLite transaction. Clear source History only
  after destination commit; publish workspace state and remove source profile
  only after the copy/cleanup sequence. Retries preserve exact records.
- During unfinished transfer, existing workspace guards prevent session starts
  or switching the transfer to another account. Legitimate storage/recovery
  warnings remain visible. Active sessions cannot be imported over.
- Keep the single-account claim after completion/account deletion so another
  account cannot reclaim the original local archive.
- Not now preserves the source and leaves the option in Account settings.
- Pairing/permissions are installation-owned and unchanged. Import emits no
  analytics or network requests. Secure account-screen protection is retained.

## Verification

- Production `testReleaseUnitTest`, `lintRelease`, `assembleRelease`,
  `bundleRelease`, `writeVerifierClasspath`: PASS, final exit 0.
- **452 release JVM tests: zero failures/errors/skips.** Includes coordinator,
  runtime, token/account identity, deferral, active-session rejection, collision,
  capacity, History conflict, injected storage failure and cold-resume coverage.
- `scripts/verify-android-predevice-readiness.sh`: final unchanged-source run
  PASS. Product contracts, Gate 0, UI prototype, integrated lint/tests/builds and
  44 package-boundary verifier tests passed. An earlier run overlapped source
  edits and failed compilation; it is not counted as final evidence.
- Disposable read-only `medium_phone`, API 36 arm64, `emulator-5556`: targeted
  real-SQLite instrumentation **OK (2 tests)**. Only synthetic Gate 0 app/test
  APKs were installed; no instrumentation ran on the owner's A16.
  - `localHistoryMergeStreamsAllRowsAndRetriesWithoutDuplicates`: 125 rows,
    repeat import without duplicates, preserved source and exact stored totals.
  - `localHistoryMergeConflictRollsBackEarlierRowsAndPreservesBothSources`:
    conflict preflight rejects; forced transactional merge rolls back earlier
    rows and preserves both source records and existing target record.
- `android-app/verify-candidate.py`: PASS. Production package boundaries,
  non-debuggable APK, certificate/signature, ZIP/native alignment and AAB
  structure checked. Not a Play-generated APK or physical 16-KB runtime check.
- Scoped Android/product/docs whitespace check passes. Unrelated Swift trailing
  whitespace is outside this task and was not modified.

## Preserved candidate

Directory: `android-app/build/candidates/1.0.0-1-7ee1df3c/` (outside Git).
Application ID `com.justnoise.app`, version 1.0.0 (1), target SDK 36.

| Artifact | SHA-256 |
| --- | --- |
| `justnoise-1.0.0-1.apk` | `61fad8658d4ecf9f954570f1629346396abb0c5bca058da8a904f2feb3da7030` |
| `justnoise-1.0.0-1.aab` | `7ee1df3ced5d2e4178c97b9444dff73a8624d589d207b0bb9e9c9a4a17c269ae` |

`verification.json` accompanies the snapshot. Both copied artifact hashes were
independently rechecked. Signing credentials remain outside Git, unchanged.

## Remaining acceptance / handoff

The owner A16 remains on `1.0.0-1-1e0b85fd`; this new candidate is not installed.
No owner data has been imported. An approved in-place update followed by the
owner's explicit import confirmation is needed to verify real mode visibility,
disabled schedule review and History continuity in the actual account. Do not
repeat accepted unrelated NFC checks solely for this data-import change.

Full release remains HOLD: affected device/auth/account-isolation checks,
normal-settings Samsung Now Bar, Chrome keyboard-window acceptance and the
remaining connected-release checklist are not closed by these local results.
Secure offline signing-key backup remains an owner action. No distribution GO.

## Subsequent owner-authorized A16 hardware test

Owner requested real hardware testing instead of more emulator tests. USB A16
`RFGYC3DYKBR` / SM-A165F was connected. The visible mode picker was empty; after
closing it, the main screen showed idle "Zap to Block" and no selected mode.
An in-place update of the exact `7ee1df3c` candidate returned Success at
2026-09-19 22:22:47. The installed public base APK SHA-256 is
`61fad8658d4ecf9f954570f1629346396abb0c5bca058da8a904f2feb3da7030`, matching
the preserved candidate. No uninstall, data clear, permission grant or owner
data import was performed. AccountActivity was opened and confirmed foreground;
its protected contents were not captured. The owner must confirm import in the
app before real mode/History continuity can be accepted. This supersedes the
earlier not-installed status only; no new functional PASS or release GO.

## Owner-confirmed import: real A16 visibility check

After the owner replied "Ok done" to the in-app import instruction, the phone
was on Home. Opening MainActivity at approximately 22:25 showed idle Focus with
"1 app selected", no longer the empty account selection. History at 22:26 showed
4 completed sessions and 200h 22m elapsed session time, with 2 interrupted-session
warnings and 1 uncertain-timing warning still visible. The elapsed total must
not be reported as verified protection time.

Schedules showed the existing "Release Check" one-time entry, September 8,
20:13 Europe/Berlin, referring to Focus and 1 app. It is Disabled, with its prior
execution result/date visible. No toggle or schedule edit was made. The app was
returned to the idle Zap screen. No session was started, account content captured,
private database read, new build installed or emulator test run in this check.

PASS for actual post-import visibility of the mode, selection count, History and
disabled schedule/reference on the owner's A16. This is not row-by-row database
equivalence, package-identity verification of the selected app, cross-account
isolation acceptance, or the remaining broader physical release checks.

Follow-up at approximately 22:43 on candidate 25098600: opened the actual Focus
mode editor and observed Chrome checked (other visible apps unchecked, total
1 app). Cancelled without saving and closed the mode sheet. This strengthens
the earlier count-only evidence to visible Chrome-selection preservation.
Production Accessibility remains enabled. No active session or temporary
schedule was created; the exact scheduled-start-under-keyboard test still needs
an owner-attended run with the Zap available to stop afterwards.

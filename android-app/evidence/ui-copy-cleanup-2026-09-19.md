# Android UI copy cleanup — September 19, 2026

Owner requested removal of debugging text during the authorized A16 hardware
test workflow. Changes are Android presentation-only; no storage, auth, NFC,
restriction or scheduling engine mutation, and no iOS source edits.

- Removed successful schedule "Last result" receipt/timestamp and redundant
  healthy/disabled-state commentary; switch state remains accessible.
- Kept next planned start, actionable missing setup and failed-attempt messages.
- Removed the technical elapsed/protection disclaimer from the History header;
  truthful Session time label and detailed explanation remain. Real interruption
  and uncertain-duration counts remain visible in plain language.
- Simplified schedule guidance and Live Update settings without promising
  Samsung lock-screen placement or hiding required permissions.
- Kept user-created schedule name "Release Check" unchanged. It is stored user
  data, not an app debug label. Refresh remains a functional reload action.
- Privacy/disclosure text and protected account screens were not changed.

## Build and physical evidence

Release assemble/bundle and signing completed successfully (39 seconds).
Build-integrated vital lint and signed candidate verifier passed. No JVM,
emulator or full pre-device suite rerun, following the owner's real-hardware
testing direction. Earlier 452-test results belong to candidate 7ee1df3c, not a
new test run of this copy-only candidate.

Before update, A16 RFGYC3DYKBR showed idle Focus with 1 app selected. In-place
installation returned Success at 22:31:27. Installed APK hash independently
matched the preserved build. No uninstall, data clear, permission toggle,
schedule toggle, import, or session start was performed.

Actual A16 screenshots after update confirm:

- Schedule card retains Release Check, timing, Focus, 1 app and off switch;
  successful execution diagnostics are gone.
- History retains 4 sessions / 200h 22m, with 2 interruption warnings and
  1 potentially inaccurate-duration warning. The technical header line is gone.
  This elapsed total is not asserted as protected time.
- Ordinary Settings displays simplified Live Update copy and current allowed
  status. This is not a real active-session Now Bar acceptance test.

The phone was returned to the idle Zap screen. Remaining full-release gates are
unchanged; no upload or distribution.

## Snapshot

`android-app/build/candidates/1.0.0-1-25098600/` contains APK, AAB and
`verification.json`. The verifier's generic pre-install status is superseded by
the explicit installation evidence above, not by rewriting its original report.

| Artifact | SHA-256 |
| --- | --- |
| APK | `bc05492082f149801734d60e9d5d4736d2cb6c926c0e6c1f92090e164942f534` |
| AAB | `25098600f8558e55464b11bebc4c5f81e0d921f96396bd35bdcefca9975cfa47` |

Signing credentials remain outside Git. Existing unrelated iOS changes remain
untouched. No commit or external publication was performed.

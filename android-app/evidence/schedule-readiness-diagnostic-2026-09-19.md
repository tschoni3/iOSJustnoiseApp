# A16 schedule readiness diagnosis — September 19, 2026

The failed 22:54 attempt on `25098600` did not identify which service readiness
check rejected the start. A new bounded diagnostic distinguishes preparation,
service rejection, missing foreground, main-thread timeout, endpoint replacement,
generation changes and lease expiry without changing any readiness criterion.
One process-local enum is exposed by Android's read-only service dump; no event
log, persistence, UI debug text or telemetry is added. The data inventory records
this boundary. A late timed-out callback cannot publish over the terminal result.

Signed candidate `1.0.0-1-cfa5e637` built in 44 seconds. Build-integrated vital
lint, static policy/product-contract checks and signed APK/AAB verification pass.
The first artifact-verifier run used macOS's older Python and failed on the host's
missing hashlib.file_digest; rerunning with installed Homebrew Python 3.14 passed.
No runtime source workaround was made. No JVM suite or emulator was run, per owner
direction. Existing instrumentation assertions were updated but not executed.

| Artifact | SHA-256 |
| --- | --- |
| APK | `3959c56c62498bec8e4f433f805c4d52ad643684fd45b645d28ed5ee8aee56da` |
| AAB | `cfa5e6375549bb640fb8a7f770ef05433bf0bab22e9eb218fd3b67114e33d366` |

Owner explicitly approved update plus the targeted real test. A16 was visually
idle; `adb install -r` succeeded. After installation, real Zap UI retained Focus
and 1 app. The bound production service dump returned `NOT_ATTEMPTED`, proving the
diagnostic entry point works on the actual phone. No private app data was extracted.
No data clear, uninstall, permission grant, account operation or iOS edit occurred.

Before a second test schedule was armed, the owner requested removal of helper
copy throughout the app. That presentation work took priority, then the approved
diagnostic hardware test resumed on the installed `cfa5e637` build. Its installed
APK SHA-256 was independently reread and matched the artifact above.

## Second physical attempt: 23:24 Europe/Berlin

Only the agent-created `A16 Keyboard Check` schedule was edited to September 19,
23:24, once, Focus / Chrome. The owner's `Release Check` stayed disabled and was
not edited. Chrome's blank address field and Samsung keyboard were visible before
and after the due time. This was not continuous recording at the deadline.

The live service dump returned **`LEASE_EXPIRED`** (pid 12642). A later read in the
following work slice confirmed the same value. This isolates the rejection to an
expired preparation lease, not disabled Accessibility or an unknown foreground.
It does not measure the duration of each intervening operation.

Normal app UI at 23:31 showed the 23:24 failed-attempt receipt and the one-time
test switch off. Returning to Zap showed idle, Focus / 1 app. No session began;
no owner Zap stop was required. No private app storage was extracted.

Evidence captures in `/private/tmp/`: `justnoise-diag-keyboard-before.png`,
`justnoise-diag-after-2324.png`, `justnoise-schedule-lease-idle.png` (schedule
receipt) and `justnoise-schedule-lease-zap.png` (idle). These local screenshots
contain ordinary product screens, not secure account screens.

Follow-up source/build: `schedule-discovery-order-2026-09-19.md`. This failed test
does not accept keyboard interception or close the automatic-start defect.

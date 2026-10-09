# Local release review while physical testing is deferred

Owner is away from the A16 and explicitly requested continuing without the
physical Zap check for now. Candidate: **1.0.0-1-75b4c6dd**. No phone commands,
installation, account/provider/Play changes or iOS edits in this follow-up.

## Completed

- Full `:app:lintRelease` (not only vital lint) completed successfully in 41s:
  **0 errors, 74 warnings, 2 hints**. This analyzes test sources but does not run
  the JVM suite, emulator or owner-app instrumentation.
- Recomputed preserved APK and AAB hashes; both match the independently verified
  candidate report. No rebuild or artifact alteration was needed.
- Reviewed the focused overlay fix again: event package must equal our own
  package before Activity lookup; declared Activity identity is checked; the
  transparent DirectNfcActivity is excluded; unknown/self events retain the
  existing flicker guard. Real own-UI transitions update foreground generation.
  No event content/window-tree collection or new permission/subscription.
- Reviewed merged release manifest and service metadata: release is configured
  non-debuggable; backup and cleartext traffic disabled; no new broad app-visibility
  or overlay permission. Schedule and notification-dismiss receivers remain
  non-exported; Accessibility binding remains system-permission protected.
- Scoped production/account and restriction source search found no logging calls
  or TODO/FIXME markers. This is not proof of zero defects or a dependency audit.

## Lint triage (not blindly suppressed or autofixed)

| Findings | Count | Disposition |
| --- | ---: | --- |
| Unused resources | 43 | Mostly removed helper-copy resources and shared-source leftovers. Not evidence those texts are rendered. No bulk deletion across shared variants in this stability slice. |
| Kotlin convenience API suggestions | 15 | Style suggestions; no behavior change needed to package the candidate. |
| SDK/dependency version notices | 7 | No automatic toolchain/library upgrade. Existing pins preserved; target/upload eligibility remains a separate live release check. |
| Layout inflation / overdraw | 3 | Overlay supplies explicit WindowManager bounds; no parent ViewGroup. Keep opaque touch-blocking background. Actual affected rendering still needs device acceptance. |
| Plural candidates | 2 | Character-limit messages; no demonstrated wrong value in this pass. |
| Typography | 2 | Existing out-of-scope privacy-draft text left unchanged. |
| Required NFC feature | 1 | Intentional physical-Zap product requirement, not removed to silence lint. |
| Synchronous preference commit | 1 | Existing optional notification-dismiss path; potential main-thread latency remains a performance note, not measured ANR evidence. No unreviewed durability change to apply(). |
| Compose primitive boxing hints | 2 | Optional allocation optimization; no mass UI refactor while the corrective candidate awaits acceptance. |

Warnings remain visible in the archived report. Lint success is not a claim that
all warnings are harmless or that the app is release-accepted.

## Technical handoff

The preserved candidate directory contains APK, AAB, verification snapshot,
checksums, the full lint report and a concise handoff. Device acceptance remains
**DEFERRED**; distribution remains **HOLD**. The older generic verification
snapshot's installation-authorization text is not an authority decision.

When the owner is physically available, first complete the existing session's
Zap stop, Chrome release, notification removal and History check; then update in
place and repeat the affected direct-own-app switch/blocker stability checks.
Do not wipe data or repeatedly re-test already accepted unrelated flows.
Now Bar presentation and broader connected-account/consent acceptance stay open.

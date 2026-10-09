# Scheduled-start discovery ordering — September 19, 2026

## Measured failure and bounded correction

The second owner-approved A16 attempt on `cfa5e637` returned `LEASE_EXPIRED`.
Source inspection found full launcher discovery, label loading and sorting inside
the schedule transaction after acquisition of the one-second readiness lease.
That is unnecessary work in a time-sensitive path; its exact share of the elapsed
time has not been independently profiled.

Android-only changes:

- LauncherAppRepository exposes a package-identity-only query using the existing
  scoped launcher intent and visibility. No labels/icons/sorting or new permissions.
- Gate0Controller finishes that per-attempt query before requesting service
  readiness, outside the preference lock. Discovery exceptions fail closed.
- The atomic dispatch still resolves the current schedule and mode, validates
  its actual target set against the inventory and freshly resolves protected
  system roles. The inventory is not persisted or reused across attempts.
- Consent, account/workspace, recovery, pairing, exact alarm, enabled/bound
  service, foreground generation and the original one-second lease remain gates.
  No lease extension, forced readiness, retries of consumed occurrences or
  Force Stop catch-up were introduced.
- Manual/selection eligibility also uses package identities instead of labels;
  protected-package checks remain. Picker presentation is unchanged.
- Includes the owner's helper-copy cleanup and the two remaining schedule-editor
  explanatory paragraphs removed after `766a1b5b` was built.

The existing emulator-only regression source now covers slow discovery finishing
before readiness and explicit expired-lease rejection. Those tests were **not
run**, in accordance with the owner's hardware-first instruction. No emulator,
JVM suite or owner-app instrumentation was run in this slice.

## Local candidate

`build/candidates/1.0.0-1-e553541c/` contains signed APK/AAB and verification.json.
Release build passed in 41 seconds, including vital lint. Static capability
policy, product contracts and independent signed APK/AAB verification passed.
One pre-existing harmless Compose unnecessary-safe-call warning remains.

| Artifact | SHA-256 |
| --- | --- |
| APK | `3015b70a63065e9de234290e1678e025ca699afdb6c179cc78088d02723ae5a9` |
| AAB | `e553541ca463231eda44122964c9bb8cc97e53a43bbd650ce707d47d9d81faf8` |

Not installed. Scoped owner approval for an in-place update and the affected
Chrome/keyboard schedule retry was requested. Existing phone data, pairing,
permissions and the owner's schedule are preserved. iOS was not edited by this
work; its pre-existing Shield change remains untouched.

**Release HOLD:** build checks prove packaging, not a successful real scheduled
start. Required next evidence is the updated A16 automatic start with Chrome's
keyboard open, stable blocking with safe navigation, then owner physical-Zap stop.
The other connected-release acceptance items remain open.

# iOS release verification

This checklist protects the currently published behavior while the app is prepared for Android. Automated checks are necessary but do not replace physical-device checks for NFC, Family Controls, schedules, or Live Activities.

## One-command local check

Requirements: Xcode with an iOS simulator runtime and network access for the first Swift package resolution.

From the repository root, run:

```sh
bash scripts/verify-ios.sh
```

The command resolves the pinned `Package.resolved`, builds the `justnoiseradio` scheme in Release for the simulator, and runs its XCTest suite on the first compatible iPhone simulator. Override its defaults when needed:

```sh
IOS_SIMULATOR_ID=<simulator-uuid> \
DERIVED_DATA_PATH=<derived-data-directory> \
SPM_CACHE_PATH=<package-cache-directory> \
bash scripts/verify-ios.sh
```

The same script runs in GitHub Actions. CI caches only Swift package checkouts; Derived Data is deliberately rebuilt to expose integration problems.

## Before creating the release candidate

- [ ] Worktree is clean and the candidate commit is pushed.
- [ ] Pull-request CI passes on the exact candidate commit.
- [ ] `Package.resolved` changes, if any, were reviewed intentionally.
- [ ] Marketing version and build number are higher than the latest uploaded build.
- [ ] Release notes identify every intended user-visible change.
- [ ] No unreviewed UI, entitlement, privacy, URL-scheme, or capability change is present.
- [ ] `bash scripts/verify-ios.sh` passes locally.

## Archive and TestFlight

- [ ] Select the `justnoiseradio` scheme and a generic iOS device, then create a Release archive.
- [ ] Confirm the archive contains the app, widget, shield configuration, and focus-monitor extensions.
- [ ] Run Organizer validation and resolve every error before upload.
- [ ] Upload to App Store Connect and wait for processing to finish.
- [ ] Confirm version, build, export-compliance answers, privacy details, and release notes.
- [ ] Install the processed build from TestFlight; do not validate only an Xcode-installed build.
- [ ] Also upgrade one device from the current App Store version to exercise migration behavior.

## Physical iPhone smoke test

Record the device model and iOS version. Use a dedicated test account where deletion or purchases may be exercised.

### Launch, account, and navigation

- [ ] Clean install reaches the expected onboarding/sign-in route without a crash.
- [ ] Email and Google sign-in return to the correct signed-in screen.
- [ ] Relaunch, background/foreground, and force-quit preserve the expected session.
- [ ] All main tabs, settings, mode creation/editing, and sign-out work.
- [ ] Password recovery deep link opens the intended flow.

### NFC, restrictions, and schedules

- [ ] Family Controls authorization and app/category selection complete successfully.
- [ ] A known NDEF tag starts a session and shields the selected apps.
- [ ] The shield UI appears correctly and scanning again stops the session and clears shields.
- [ ] Create, edit, enable, disable, and delete a schedule.
- [ ] With the app force-quit, a schedule starts and later releases at the expected times.
- [ ] Relaunching during an active NFC or scheduled session restores the correct state.

### Capture, Signal, and history

- [ ] Record a capture, stop it, play it back, and confirm its duration/waveform are plausible.
- [ ] Successful backend analysis appears in Signal and remains after relaunch.
- [ ] Offline or failed analysis presents the expected retry/error behavior.
- [ ] Session history, day dots, Signal timeline, and weekly insight show existing data correctly.

### Extensions and system integration

- [ ] Widget renders and refreshes after the app has supplied data.
- [ ] Live Activity starts, updates, and ends with a focus session.
- [ ] Notifications appear only after permission and open the expected destination.
- [ ] Denying and later enabling relevant permissions does not leave the app stuck.

### Visual and performance regression pass

- [ ] Compare onboarding/sign-in, home, modes, capture, Signal, history, and settings with the approved reference screenshots.
- [ ] Check light/dark appearance where supported, Dynamic Type, long labels, loading, empty, and error states.
- [ ] Cold launch, tab switching, scrolling, recording, and waveform rendering remain responsive.
- [ ] No new crash, hang, repeated permission prompt, or unexpected network error appears in device logs.

## Release evidence

Attach this evidence to the release PR or release record:

- Candidate commit, marketing version, and build number
- Successful GitHub Actions run
- Organizer validation and TestFlight processing result
- Device/iOS test matrix and tester name
- Known issues accepted for this release
- Final go/no-go decision

A release is ready only when automated verification passes and the physical-device checklist has no unexplained failure.

# App Store handoff: Justnoise 1.1.0

Status: **Source prepared for build and archive; no build, archive, upload, or release validation has been performed.**

## Candidate identity

- Marketing version: `1.1.0`
- Build number: `6`
- Scheme: `justnoiseradio`
- Archive configuration: `Release`
- Capture availability: enabled in Debug, disabled in Release

Build 6 is intentional. Local Xcode Organizer history records successful App Store uploads of 1.1.0 builds 3, 4, and 5; build 5 was uploaded on July 27, 2026. Before uploading, confirm in App Store Connect that no build newer than 5 was uploaded from another Mac or automation.

## Release behavior

- Release opens directly on Zap without a bottom tab bar.
- Capture, the post-session Capture route, its onboarding page, and its Settings preference are unavailable.
- Debug builds retain the complete Capture development surface.
- Existing Capture files and account-deletion cleanup remain intact.
- The privacy manifest declares file-timestamp access used only to manage files inside the app container.

## Suggested What's New

Justnoise 1.1.0 keeps the focus on your Zap experience.

- A cleaner, single-screen experience for starting and ending focus sessions.
- Blocked-app messages now name the app that is currently Zapped.
- Improved reliability for session recovery, sign-in, and account deletion.
- Capture is temporarily unavailable while we refine the experience.
- General stability improvements.

## Suggested App Store description

Turn distraction into action with Justnoise and your Zap.

Choose what you want to protect, select a mode, and scan your physical Zap to start a focused session. Scan again when you're ready to finish.

With Justnoise, you can:

- Create custom modes for work, study, rest, or anything that matters.
- Block selected apps, categories, and websites during a session.
- Schedule one-time or repeating focus sessions.
- Follow active sessions from the Lock Screen with Live Activities.
- Review your session history and weekly focus patterns.
- Use Emergency Unzap if your Zap is unavailable.

Justnoise uses Apple Screen Time controls. A compatible Justnoise Zap NFC tag and iOS 18 or later are required.

## App Store Connect actions before submission

- Replace the current description, which advertises voice recording and Focus Companion.
- Remove or replace every screenshot or app preview showing Capture, Reflect, voice recording, Focus Companion, Signal, or the old two-tab bar.
- Recommended screenshots: Zap idle/mode selection, mode setup, active session/Live Activity, personalized blocked-app shield, schedules, and history/weekly Rewind.
- Review the privacy questionnaire against actual PostHog, Supabase, authentication, analytics, and backend behavior. Do not claim precise location unless a verified dependency or backend uses it.
- Decide how existing Monthly and Annual subscriptions are handled. The current Release UI has no purchase or restore surface; do not submit IAPs with this version unless their visible benefit and purchase flow are functional and explained.
- Update the linked Zap product page and privacy policy so they do not promise Focus Mentor, AI journaling, renewal in the app, or local-only processing that this Release does not provide.
- Add a non-expiring demo account in App Store Connect, never in this repository.
- Explain the physical Zap activation requirement and provide Apple with the necessary hardware/testing path in Review Notes.

Suggested final Review Notes sentence:

> Capture/Focus Companion is intentionally not part of this Release build and is not advertised in the submitted metadata. Development-only Capture code is not accessible to customers.

## Build and archive handoff

1. Confirm the candidate commit is merged and the worktree is clean.
2. Confirm App Store Connect has no 1.1.0 build newer than 5.
3. Open `justnoise.xcodeproj` in Xcode 26.6 or a currently supported newer Xcode.
4. Select the `justnoiseradio` scheme and a generic iOS device destination.
5. Confirm the app, widget, Shield Configuration, and Focus Monitor all show version 1.1.0 and build 6.
6. Choose **Product → Archive**.
7. In Organizer, validate the archive before uploading it.
8. After upload processing, install the exact build from TestFlight and complete `docs/RELEASE_VERIFICATION.md` before submission.

Do not upload or submit a dirty-worktree archive. Do not enable `CAPTURE_ENABLED` in Release for this candidate.

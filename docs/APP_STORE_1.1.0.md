# App Store release record: Justnoise 1.1.0

Status: **Released / live on the App Store, as confirmed by the owner in this task.**

Repository evidence proves the source candidate and automated checks. Archive validation, App Store Connect processing, review, and public availability happened outside Git and are recorded here from the owner's confirmation; no App Store receipt or exported review record is vendored in this repository.

## Released source identity

- Marketing version: `1.1.0`
- Build number: `6`
- Scheme: `justnoiseradio`
- Archive configuration: `Release`
- Capture availability: enabled in Debug, disabled in Release

Build 6 was intentional. At handoff, local Xcode Organizer history recorded successful App Store uploads of 1.1.0 builds 3, 4, and 5; build 5 had been uploaded on July 27, 2026. The handoff required checking App Store Connect for any build uploaded from another Mac or automation before archiving build 6.

The release line is merged at `main` commit `4bb4631148a1c5fdab0455c4e09b247c3ca74428`. GitHub **iOS verification** and **Product contracts** both passed on that exact commit on 2026-08-04. The owner subsequently completed the archive/submission flow and reported the version live.

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

## Historical App Store Connect submission checklist

The items below were the handoff checklist. They are retained as release context, not as independently verifiable repository evidence.

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

## Historical build and archive handoff

1. Confirm the candidate commit is merged and the worktree is clean.
2. Confirm App Store Connect has no 1.1.0 build newer than 5.
3. Open `justnoise.xcodeproj` in Xcode 26.6 or a currently supported newer Xcode.
4. Select the `justnoiseradio` scheme and a generic iOS device destination.
5. Confirm the app, widget, Shield Configuration, and Focus Monitor all show version 1.1.0 and build 6.
6. Choose **Product → Archive**.
7. In Organizer, validate the archive before uploading it.
8. After upload processing, install the exact build from TestFlight and complete `docs/RELEASE_VERIFICATION.md` before submission.

The archived candidate was required to come from a clean worktree with `CAPTURE_ENABLED` absent from Release. The current project configuration still preserves that boundary.

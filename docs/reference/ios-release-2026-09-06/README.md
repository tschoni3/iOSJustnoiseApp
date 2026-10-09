# Unchanged iOS Release reference — 2026-09-06

Source revision: `11d4d98` with no iOS source/project/asset changes. The working
tree's Android/evidence changes do not enter this build. This is a source-built
Release reference, not a claim of screenshots from the installed App Store binary.

- Scheme: `justnoiseradio`; configuration: `Release` (Capture disabled).
- Simulator: isolated `Justnoise Release Reference`, iPhone 17 Pro, iOS 26.5.
- Build: version 1.1.0 (6), normal launch and onboarding; no seeded application
  data, permission grants, authentication bypass or NFC bypass.
- Tracking, Screen Time and notification prompts were declined on this disposable
  simulator. They are system surfaces, not Android visual templates.
- PNGs are native 1206×2622 simulator captures. Onboarding embeds moving video, so a frame
  in that video is not a pixel-perfect animation golden.

## Captured through the normal flow

| File | State |
| --- | --- |
| `01-onboarding-select-mode.png` | Release onboarding page 1 |
| `02-onboarding-stay-focused.png` | Release onboarding page 2 |
| `03-zap-activation.png` | Activation before physical NFC pairing |

## Still required

The physical-NFC/authenticated flow cannot be completed normally in this simulator.
Current iOS screenshots or a recording are requested for Zap idle/active, app
selection, schedules, History (including a selected day/year), Settings, completion
and the current white-button blocker. Source measurements are documented in the
implementation audit, but are not represented as captured visual acceptance.

The old `IMG_6096.png` has obsolete shield copy and a green button; it is not used
as the current reference. `IMG_6787.JPG` depicts Android, not iOS.

Android intentionally adapts the first page to selecting apps **to block** and
keeps its own separate disclosure and manual permission setup.

## Capture SHA-256

- Page 1: `3c1dc08207d78af9992e5338177cb5c594f1268f4d5da2185a94669daae45966`
- Page 2: `a91e1308adea17f6d42e0503f8cbf46a66e267ba6d26936a957157f3e7082b18`
- Activation: `b8b969dfa8c653d5578fb1f1193561ca107aa570b0358118bc6e2168e96a144e`

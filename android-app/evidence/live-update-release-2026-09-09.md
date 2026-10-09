# Live Update-only release candidate — 2026-09-09

Scope: owner-approved source/build work for Now Bar, with follow-ups requiring no
ordinary fallback and an iOS Live Activity-like design. No installation, account
operation, telemetry, phone setting, upload or iOS modification performed.

Subsequent owner-approved A16 installation and manual notification enablement are
recorded separately in [the device follow-up](live-update-a16-2026-09-09.md).
The original build-only results below remain historical, not overwritten approval.

## Implemented

- Production-only `POST_PROMOTED_NOTIFICATIONS`; exact permission verification
  distinguishes production from the smaller development/capability manifests.
- Same canonical notification/session engine, count-up chronometer and single
  notification ID/channel. Removing/dismissing the display cannot stop a session.
- `LOCKED IN FOR`, existing active-mode name, lime `#D7FA00` accent, and original
  unchanged Zap artwork as a native large icon. Android owns layout, fonts,
  background, icon placement and the compact symbol; no custom RemoteViews.
- No ordinary notification fallback or recovery notification. Denial,
  unsupported platform, missing consent, idle and unhealthy sessions post nothing.
  An eligible request must contain the promotion flag and pass the native
  characteristic check. Rejected/demoted posted items are canceled after the
  asynchronous promotion decision, without heartbeat reposts. An OEM may briefly
  display its ordinary interpretation before this check; zero transient appearance
  cannot be promised.
- Settings distinguish unsupported, notification access off, promotion access
  off, rejected, allowed and actually promoted. Permission is never described as
  proof of Samsung Now Bar rendering. System settings open only on explicit tap.
- Local mode name is now part of the optional lock-screen content, never selected
  packages, NFC contents, account credentials or analytics. No Internet permission
  or new periodic work/foreground service added.

## Verification

Final artwork-inclusive build: **PASS** for 296 release JVM tests (zero failures,
errors or skips), release lint, APK/AAB assembly/signing, full Android pre-device
verifier and 42 artifact regressions. Signed artifact verification, certificate
pin, packaged permissions/components and 16-KB packaging checks all **PASS**.
No device instrumentation or visual acceptance was run. iOS tracked-tree diff is
empty; `git diff --check` passes.

Preserved snapshot: `android-app/build/candidates/1.0.0-1-db0c2798/` from repository
root. Version 1.0.0 (1), application ID `com.justnoise.app`, public name Justnoise.

| File | SHA-256 |
| --- | --- |
| `justnoise-1.0.0-1.apk` | `52606ff0dd27d67deec4a09f593847a21492053b70793983d71e2725744cd7e5` |
| `justnoise-1.0.0-1.aab` | `db0c2798f065385b4b7c94a48e01c6aed9bc6e56edbcb48b4da379730802816d` |

The snapshot includes `verification.json` and `verification.log`. The two previous
immutable snapshots remain unchanged. Logs:
`/private/tmp/justnoise-live-update-final-checks-2026-09-09.log`,
`/private/tmp/justnoise-live-update-artwork-build-2026-09-09.log`, and
`/private/tmp/justnoise-live-update-artwork-package-2026-09-09.log`.

The first incremental production compilation reported duplicate cached function
declarations. A full non-incremental Kotlin compilation succeeded without a source
workaround. The final build command uses `-Pkotlin.incremental=false` too.

Pure package regressions now include the exact production promotion permission:
missing, duplicated, SDK-qualified or development-package promotion permissions
are rejected. Foreground service/Internet/broad visibility restrictions stay intact.
New unit cases cover version/permission support, truthful promotion status, no
ordinary posting and bounded active-mode-name projection. Existing timer,
withdrawal, dismissal and recovery tests remain; instrumentation sources have been
updated to the new no-fallback behavior, but were not run on a phone.

## Still unverified / not delivered

- Signed-candidate Now Bar appearance under normal Samsung settings. The earlier
  A16 sample needed a developer override; a framework promoted flag alone did not
  ensure the Now Bar. No new physical PASS is claimed.
- Artwork/template acceptance on the A16, timer legibility, start/stop/cancel,
  lock/unlock, permissions off and user dismissal/demotion on this exact build.
- Pending Chrome address-entry/keyboard interception correction from the previous
  candidate remains included but physically unverified.
- Sign-in, per-account storage/cleanup and PostHog are subsequent connected-release
  slices, not implemented runtime features in this bundle.
- No internal-beta/full-reliability/distribution GO; offline signing-key backup
  and the existing release dependencies remain open.

The desired connected scope and read-only backend audit are in
[`ANDROID_CONNECTED_RELEASE.md`](../../docs/ANDROID_CONNECTED_RELEASE.md).

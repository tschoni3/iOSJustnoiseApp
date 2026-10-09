# Account Activity lifecycle follow-up — September 9, 2026

## Defect corrected

Finishing the recovery Activity during code exchange could occur before Compose
displayed a recovery flow ID. The old destruction handler therefore queued no
cleanup, allowing the later response to retain temporary recovery credentials.

The Activity now resolves its strictly parsed callback ID even before rendering
and queues exact-owner cleanup behind the outstanding runtime operation. A newly
delivered callback takes priority over a previously rendered form. A malformed
callback cannot supply an ID; rotation does not run finishing cleanup. The runtime
still requires the matching recovery owner, so older activities cannot cancel a
newer unrelated flow. No password or token is added to saved state.

Two focused tests cover delayed HTTP plus immediate Activity-style abandonment,
and newest-callback versus previous-frame ID selection. After the delayed response,
the encrypted flow and temporary credentials are gone; password submission makes
no request and the local workspace stays selected. These tests exercise the real
runtime with mock HTTP and the exact parser, not Android Activity lifecycle on a
device.

Account controls now provide explicit TalkBack labels for keeping local data
separate and optional analytics. Account feedback is a polite live region. This
is an implementation improvement; actual TalkBack/large-text rendering still needs
device acceptance.

## Local verification and preserved artifact

- **407 release JVM tests passed**, zero failures/errors/skips; release lint PASS.
- Full Android pre-device verifier PASS, including 44 artifact regressions.
- APK/AAB signature, production component/permission/DEX boundary, APK ZIP alignment,
  AAB validation and native library alignment PASS using the existing upload key.
- Snapshot: `android-app/build/candidates/1.0.0-1-e42c39ad/`, with APK, AAB,
  verification JSON and log. Version 1.0.0 (1), `com.justnoise.app`.
- APK SHA-256: `2463ef3f2bff26f5bdf948ba751a903424eaab81af8eee4d7cbbe00b4ce501f9`.
- AAB SHA-256: `e42c39ad47dd7b38aa49df7829de3fb05427d545a96476e576659758752f5226`.
- Logs: `/private/tmp/justnoise-account-lifecycle-final-2026-09-09.log`,
  `/private/tmp/justnoise-account-lifecycle-readiness-2026-09-09.log`,
  `/private/tmp/justnoise-account-lifecycle-verification-2026-09-09.log`.
- No phone installation, owner data change, backend mutation or upload occurred.
  iOS tracked app/widget/project diff remains empty; `git diff --check` passes.

## Current acceptance constraints — not a release GO

- `adb devices -l` and `adb mdns services` both returned empty lists. No connected
  A16, emulator or advertised wireless-debugging connection was available.
- The host had approximately 1.6 GiB free before this build. No emulator was
  started and no destructive cleanup was attempted.
- Supabase's Android-only redirect remains prepared, not saved, pending the
  browser's action-time confirmation. Existing iOS entries and Site URL are intact.
- Real registration/Google/recovery/deletion and account A/B rendering are still
  required. Mock HTTP does not establish email delivery or server behavior.
- The installed A16 build predates accounts. Default-user visible Now Bar and
  the affected Chrome/keyboard interception check remain outstanding. Historical
  NFC/mode/schedule results are retained, not repeated or credited to this build.
- Distribution remains HOLD; offline signing-key backup remains an owner action.

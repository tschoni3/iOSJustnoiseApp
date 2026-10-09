# Customer setup copy and support — September 8, 2026

Implemented locally, not installed on the owner's A16. No production promotion,
signing, Play mutation, email sending or physical-phone operation occurred.

## Implemented

- Normal setup title/actions/statuses now use customer-facing Justnoise wording
  instead of feasibility, test Zap and Gate 0 terminology. Technical NFC error
  diagnostics remain unchanged. The standalone spike app label and integrated
  Android Dev system/service identities remain distinct until production packaging.
- The disclosure's opening sentence and consent-checkbox label are reworded.
  The disclosure body after the first sentence is unchanged. No capability/data
  scope, disclosure version, accepted-consent storage, permission code, pairing,
  session, scheduling or restriction behavior was changed. Existing consent is
  neither granted nor reset by a wording update.
- Grouped Settings offers Contact support, the selectable owner-supplied address
  hello@justnoise.shop, and an explicit Email support button. Its ACTION_SENDTO
  mailto intent contains the recipient only: no body, diagnostics or attachments.
  Failure leaves a readable copy-address fallback and permits retry.
- The email callback only opens a composer; the user decides whether to send.
  Tests intercept the handoff rather than launching/sending real email. Inbox
  receipt/delivery has not been verified. No network permission or email SDK added.
- The privacy notice remains clearly marked as an unpublished draft. This support
  route does not publish the notice or finalize release/privacy review.

## Verification

- Final full local verifier PASS: 298 JVM tests (zero failures/errors/skips), 36
  artifact checks, contracts, debug/release lint and builds. Two new JVM copy
  regressions guard meaningful disclosure boundaries and normal setup wording.
- Settings surface and real-Activity classes: 10 tests passed at normal size
  (20.439 seconds), and the same 10 at compact/system font 200% (18.172 seconds).
  These are 20 final executions of 10 cases, not 20 distinct new tests.
- The first UI run had one incorrect test expectation: Uri.fromParts encodes @
  as %40. The assertion now checks mailto plus the decoded recipient, while retaining
  empty-extras/clip-data and unavailable-app/security-failure checks. The complete
  two-class suite was rerun successfully. No production workaround was introduced.
- Real-Activity checks retain History/authority across Settings navigation and
  verify that opening native setup leaves consent unchecked, Continue disabled,
  and the scanner inactive. They use guarded synthetic emulator data only.
- UI target: explicitly emulator-5558 / medium_phone, verified ranchu and booted,
  read-only/no-snapshot-save. Normal 1080x2400/420 dpi, compact 945x1680/420 dpi,
  actual Android font_scale 2.0. Animation scales zero. The emulator was closed
  without saving after evidence retrieval. No owner phone was targeted.
- iOS app/project/test tracked comparison empty; git diff --check passed.

## Evidence limits

Unmodified screenshots are in standard/ and compact-font200/. Fixture support
captures deliberately simulate a failed composer launch, and some are scrolled;
their visible fallback is not a real email delivery failure. The standard folder's
font200 fixture uses a bounded Compose content harness; the compact folder also
uses the actual Android system text scale. Real native-setup images contain an
explicit synthetic unavailable app entry, not the owner's selection.

Support fallback and native setup screenshots were inspected for copy/wrapping.
These are not complete final A16 visual acceptance, TalkBack certification,
physical NFC evidence or an internal-beta GO. Existing completed phone evidence
remains reusable; do not request routine scan/blocking/cleanup tests again.

## Local artifacts

Development ID remains com.justnoise.androiddev, not com.justnoise.app.

- Debug APK SHA-256: `8c04d759ac6696684d1cefbb41460f4eb727ebc761de0015af5a0199ae45e4eb`
- Unsigned development AAB SHA-256: `4513dbf6275e67229fff141d6a6c66fcffe31a65efbd495b2d7ca1e9173a5abd`

The owner still has the earlier accepted modes build. Final policy publication,
production identity/signing, reviewer materials and the scoped internal-beta
decision remain open. Logs alongside: local-verifier.log, ui-build.log,
ui-initial-encoding-assertion.log, ui-standard.log, ui-compact.log.

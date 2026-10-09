# A16 scheduled start with Chrome keyboard — September 19, 2026

## Result: FAIL to start; keyboard interception remains unverified

Owner message `test starten` approved one real one-time schedule. No synthetic
session toggle, emulator, instrumentation, data extraction or permission grant
was used. Installed signed candidate: `1.0.0-1-25098600`.

- Existing `Release Check` schedule remained disabled and unchanged.
- Focus editor showed Chrome selected; inspection was cancelled without saving.
- Created `A16 Keyboard Check`, once September 19 at 22:54 Europe/Berlin,
  enabled, using Focus / 1 app. Product UI confirmed its next start.
- Opened a blank Chrome tab and focused the empty address field. Screenshot and
  input-method metadata confirmed Samsung keyboard visible at 22:52:54 and
  22:53:50 (`mInputShown=true`, `mIsInputViewShown=true`).
- At 22:54:20 Chrome and its keyboard remained visible without a blocker.
- Subsequent product inspection showed the test schedule disabled with
  `Last attempt: App blocking needs setup`, timestamp September 19, 22:54.
- Zap screen was idle. History still displayed 4 sessions / 200h 22m with the
  existing 2 interruption warnings and 1 uncertain-duration warning. This is
  visible continuity only, not a full database comparison.
- Android service metadata after the attempt showed production Accessibility
  enabled and bound, with no binding or crashed services. Product Settings said
  `Ready for your next session. App blocking is currently off.`

The alarm reached dispatch, but no session started. Therefore this is NOT proof
of a running-session keyboard bypass and NOT a keyboard-fix acceptance pass.
No physical Zap stop is needed. The one-time test schedule remains disabled as
an outcome record; no future test start is armed by it.

## Diagnosis boundary

Source maps this message to `CAPABILITY_UNAVAILABLE`: either Accessibility is
not enabled at the dispatch check, or the prepared service acknowledgment is
missing/stale. Post-attempt bound/enabled state does not prove its exact state at
dispatch. Preparation also depends on a known foreground, main-thread response
within 750 ms, unchanged foreground generation and a 1-second readiness lease.
Target-app discovery currently happens after preparation and before the final
lease check. Any timing explanation remains a hypothesis, not measured cause.

Do not weaken readiness checks, silently retry an already-consumed occurrence,
or count this failure as a successful schedule/Now Bar test. Next development
step is bounded readiness diagnosis followed by the affected real-device retry.
Now Bar acceptance is still pending; this run produced no active test session.

Local screenshots (temporary, not release assets):
`/private/tmp/justnoise-hw-schedule-saved.png`,
`/private/tmp/justnoise-hw-chrome-ime-before.png`,
`/private/tmp/justnoise-hw-scheduled-ime-after.png`,
`/private/tmp/justnoise-hw-schedule-outcome.png`,
`/private/tmp/justnoise-hw-after-schedule-zap.png`,
`/private/tmp/justnoise-hw-setup-after-failed-start.png`.

No runtime source or iOS edits, new build, installation, upload, account mutation,
or owner permission changes were performed in this test.

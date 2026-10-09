# A16 account-switch acceptance — September 20, 2026

Status: **PREPARED, NOT PASSED**. Candidate 8dc7ba54. No runtime or iOS changes.

Owner decision: the live A -> B -> A switch was skipped because the owner does
not know the required account password. No sign-out or authentication attempt was
performed after the baseline. This is an explicit unverified release limitation,
not a failed product result and not permission to reset either existing account.

## Approved identities and account safety

The owner supplied a shop address and a personal Gmail address for account testing.
After the owner restored the correct Supabase organization access, read-only exact
email searches in the existing Justnoise project returned one account per address.
The two accounts have different user IDs and each displays Google OAuth as its
provider. This is existing-account evidence, not a new registration test. User IDs
and email addresses are not duplicated in this repository evidence file.

Both identities must be treated as real existing accounts. No password recovery,
password change, remote sign-out, deletion, new account, organization, or provider
configuration was performed in the dashboard. Neither is approved for deletion.
A separately approved disposable account is still required for destructive
account-lifecycle acceptance.

## New test boundary, not another Zap/Chrome/analytics repetition

The September 20 consented start/end and bounded opt-out start/end receipt checks
are complete and must be reused; see `posthog-audit-2026-09-20.md`. This new test
checks A -> B -> A local workspace separation and restoration, not NFC blocking.

Before any sign-out, screenshot-only navigation over the existing trusted wireless
connection recorded the current visible workspace at approximately 20:00:

- Normal idle screen, **Focus**, **1 app selected**, no recovery warning.
- History **8 Zaps / 215h 53m**.
- No account switch, data import, mode edit, schedule edit or new session occurred.

Screenshots: `a16-account-idle-baseline-2026-09-20.png` and
`a16-account-history-baseline-2026-09-20.png`.

Your account was then opened. AccountActivity is intentionally protected by
FLAG_SECURE; no tree dump, private-preference access or screenshot bypass was
used. The owner identified the currently signed-in account as the supplied
personal Gmail identity. Therefore the visible Focus / 1 app selected / 8 Zaps /
215h 53m baseline is assigned to account A. No sign-out has yet occurred.

## Remaining assertions

1. Owner confirms current identity and analytics remains off.
2. Sign out through the app, then sign in to the other approved Google identity.
3. The new workspace does not inherit A's private modes, schedules or History.
   Existing B data is not presumed empty. Do not accept any local-data import offer
   or create placeholder data to make the result look correct.
4. Return to A and verify retained baseline data and unchanged Zap pairing.
5. Verify analytics remains off at the account boundary without generating another
   unnecessary session. Existing local regressions are not live UI proof.

These assertions remain **NOT RUN** after the owner chose to skip the live switch.

Registration, password recovery and deletion remain separate, unverified flows;
Google OAuth account existence cannot satisfy them.

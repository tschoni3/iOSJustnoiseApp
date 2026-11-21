# Fresh Play signup failure — 2026-10-11

## Live evidence

Owner attempted email registration on the fresh Play-installed 1.0.8 (10).
Read-only Supabase Auth log inspection found `/signup` failures at 01:33:21 and
01:35:45 Europe/Berlin. The latest returned HTTP 500 / `unexpected_failure` while
requesting user confirmation. SMTP rejected email with code 550 because the
associated sending-key domain was not verified.

The dashboard shows custom SMTP enabled, host `smtp.resend.com`, port 465, sender
`hello@justnoise.app`, sender name Justnoise. Password remained hidden and was not
read. At the initial diagnosis, no account, password, permission, DNS or provider
configuration had been changed.
No raw request, recipient, IP address, nonce or credential is copied here.

## Android correction

- Separate sanitized HTTP 5xx and 429 failures from generic rejected requests.
- Signup failure copy no longer blames connectivity for a known server rejection,
  requests a nonexistent link, or mentions offline Zap state.
- Create account is the retry action; the preceding Close/generic Try again
  cleanup is retained. Passwords still clear on submission and are never stored.
- Confirmation, generation-bound PKCE, account isolation and product-access gates
  remain intact. iOS source stays unchanged.

## Local verification

Final corrected source compiles. All 148 account regression tests across 13 suites
pass with zero failures/errors/skips, using synthetic SDK HTTP only. Release lint
passes with zero errors, 93 existing warnings and two hints. Product contracts
pass (18 colors, 27 copy entries, 89 behavior cases). No owner-phone tests were
repeated. This verifies the Android error-handling/UI change, not SMTP delivery.

## Remaining live work

Owner explicitly approved retaining Resend and adding only three DNS records for
`auth.justnoise.shop`. In the owner's authenticated IONOS account in Arc, added:

- TXT `resend._domainkey.auth`: Resend's supplied public DKIM key.
- TXT `send.auth`: `v=spf1 include:amazonses.com ~all`.
- MX `send.auth`: `feedback-smtp.eu-west-1.amazonses.com`, priority 10.

All three are publicly resolvable and Resend reports the domain and all three
records **Verified**. Receiving remains disabled. Existing IONOS MX/SPF/DKIM,
Shopify website/email records and domain security settings were not modified.

Prepared an unsubmitted key form with **Sending access** restricted to
`auth.justnoise.shop`; no key was created or read. Prepared sender
`noreply@auth.justnoise.shop` in the Supabase SMTP form but did not save it. The
owner must create/copy the secret and complete SMTP credential entry/submission
directly, without posting it in chat. Host/port remain `smtp.resend.com` / 465;
Resend documents username `resend` and API key as SMTP password.

Owner subsequently created the key and saved the SMTP settings directly. A
separately opened, freshly loaded Supabase SMTP page confirms the persisted
sender `noreply@auth.justnoise.shop`, custom SMTP enabled, host
`smtp.resend.com`, and a disabled Save changes button. The old editing tab still
has a stale dirty form; it is not evidence of the persisted server state. No
secret was read or copied by the agent. The subsequent real signup check below
verifies SMTP handoff, but not receipt/confirmation.

Shared mail settings affect Android and iOS Auth emails. Do not disable email
confirmation or use a full-access key unnecessarily.

After repair, accept one real signup -> received email -> confirmation -> signed-in
Home check. Do not repeat previously accepted NFC/blocking/Now Bar tests. Source
and mock checks alone do not close this live release blocker. No new Play upload
or owner-phone update has been performed for this patch.

## Real signup after SMTP repair

The owner's next Android signup at 02:13:37 Europe/Berlin returns HTTP **200** in
Supabase Auth with `user_confirmation_requested`, rather than the previous 500.
After refreshing Resend's email list, the corresponding **Confirm Your Signup**
message is present with **Bounced** status. Resend's bounce details report
**Transient / Mailbox Full**, and the recipient server's response begins
`554 5.2.2 mailbox full` with `QuotaExceededException`.

This proves the repaired SMTP connection accepted and sent the real confirmation
message; the recipient mailbox rejected final delivery because its storage quota
was exceeded. No duplicate signup/resend was triggered by the agent. Full
confirmation and signed-in Home remain unverified. Next: owner frees mailbox
storage and requests one new confirmation, or uses another working email address.
Do not weaken confirmation or delete the pending account to work around this.

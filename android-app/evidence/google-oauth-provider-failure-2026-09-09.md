# Google OAuth failure — September 9, 2026

## Confirmed service-side blocker

The owner reports that Continue with Google opens a Supabase URL and then fails.
No Google account/password was entered by the agent and no real account was changed.

Two bounded live diagnostics were performed against the configured production
project `zusnyjctxdnnxghpbhki`:

1. Public `/auth/v1/settings`, using the public anon key already shipped in the
   app, returns Google enabled (`external.google: true`). This alone does not
   prove the browser OAuth provider is configured correctly.
2. `/auth/v1/authorize` with provider Google, the Android callback containing an
   explicitly synthetic state UUID, and a synthetic S256 challenge returns
   **HTTP 400**, `error_code: validation_failed`, and:
   **`Unsupported provider: missing OAuth secret`**.
   Redirect following was disabled. No Google authorization or token exchange
   was attempted; no user callback, verifier, token or cookie was inspected.

This establishes a current backend configuration blocker before the Google page.
It is not evidence of an incorrect owner password, an Android network outage, or
a completed callback test. The host can reach the project. Other untested Android
account issues are not ruled out by identifying this first failure.

## Required remediation and boundary

Supabase's documented browser Google OAuth setup requires a Web OAuth client ID
and matching client secret, with the Supabase callback authorized in Google:
`https://zusnyjctxdnnxghpbhki.supabase.co/auth/v1/callback`.

Source: <https://supabase.com/docs/guides/auth/social-login/auth-google>

The prior approval covers only adding the Android return URL, not modifying shared
Google-provider credentials. Obtain approval before adding the missing provider
configuration. Preserve existing client IDs, iOS redirects, Site URL and all
unrelated settings; never print or commit a client secret. The secret belongs in
Supabase, not in the Android APK or iOS source.

After configuration is approved and corrected, repeat the bounded authorization
entry check, then let the owner complete Google sign-in on the A16. A Google
redirect response alone still does not prove successful app callback/account
isolation. No new application build is justified solely by this backend error.

No backend settings, phone data or iOS files were changed during diagnosis.

## Subsequent configuration approval and access check

The owner explicitly approved supplementing the shared Google provider, preserving
existing iOS client IDs and redirects. Read-only provider UI confirms Google on,
one existing client ID (`60378924357-at4eeqslt180ca999e97i9tt785qt4rb.apps.googleusercontent.com`),
and an empty OAuth secret field. Skip nonce checks is already on; it was not changed.
No provider form value was edited or saved.

The Google Cloud tab initially showed first-use terms for `hello@justnoise.shop`.
The agent did not accept those terms. An attempt to open Add another account was
denied by the browser permission reviewer because the additional account had not
been specifically authorized. No alternate login surface, CLI, token extraction
or other workaround was used; the owner was asked to perform the login directly.

On the subsequent read-only checkpoint, Google Cloud had externally progressed to
its welcome page with the same account. The project selector's All tab showed
only No organization, with no existing project visible. No new project, billing
trial, credential or access grant was created. Access to the Google project that
owns the existing client remains required; absence from this view is not proof
that the project no longer exists.

The user-facing handoff is to open the existing project with its managing Google
account. Do not substitute a new project or replace the existing client IDs merely
to bypass this access dependency. The browser's credential-change handoff rules
still apply to entering/creating a new secret even after general configuration
approval. No secret is requested in chat.

## Existing Google project found; configuration drafts prepared

After the owner explicitly requested another Google login and completed it,
Google Cloud now shows project `justnoise zap` (`justnoise-zap-1738771970450`).
The client list contains the exact existing iOS ID above and two Web clients:

- `Web client 2`, ID `60378924357-542043j5luousje1n0qlpgivf4c7rih5.apps.googleusercontent.com`,
  has only the Google OAuth Playground redirect. It was left unchanged.
- `Web client (Auto-created for Google Sign-in)`, ID
  `60378924357-5sempd2r0ikv2qkth4832qsfkcbr3bbf.apps.googleusercontent.com`,
  had no origins or redirects. Its information panel shows an enabled existing
  secret, last use February 5, 2025 and an unused-client deletion warning.
  Google explicitly says old secrets can no longer be viewed/downloaded.

The latter existing Web client is the selected browser-login client. A draft adds
only the exact Supabase callback under Authorized redirect URIs. In the Supabase
Google provider draft, Client IDs contains that Web ID first, then the original
iOS ID unchanged. No other switches or fields were changed.

**Neither draft has been saved by the agent.** The user is handed off to save the
Google callback, use Information and summary -> Add secret, then paste the new
secret directly into Supabase's Client Secret (for OAuth) field and save there.
No secret has been created, revealed, copied, logged or transmitted by the agent.
Do not delete the old secret or iOS client. Do not read a newly displayed secret
during the handoff. After the owner confirms completion, verify the public OAuth
entry returns the expected Google redirect; only actual use can establish the
previously unused client's functioning status. Account and device acceptance
remain open.

## Owner "saved" follow-up

Fresh navigation to the existing auto-created Web client's detail page now shows
the exact Supabase callback persisted. This closes the Google redirect draft
checkpoint; the agent did not click Save. The iOS client was not edited.

The public synthetic OAuth entry still returns HTTP 400 `validation_failed` /
`Unsupported provider: missing OAuth secret` after that observation. Thus Google
callback persistence must not be reported as complete Supabase provider setup.
The next required owner action remains entering the matching new Web-client
secret directly into the prepared Supabase provider form and saving it. No secret
view or phone operation was performed in this follow-up.

## Supabase save confirmed by working OAuth entry

After the owner explicitly reported saving the secret in Supabase, the same
synthetic authorization-entry check now returns **HTTP 302** to
`https://accounts.google.com/o/oauth2/v2/auth`, using Web client
`60378924357-5sempd2r0ikv2qkth4832qsfkcbr3bbf.apps.googleusercontent.com`
and Google return URI `https://zusnyjctxdnnxghpbhki.supabase.co/auth/v1/callback`.
The requested scopes are `email profile`. Redirect following remained disabled;
no real account, secret, cookie, Google state value or token was inspected.

The formerly reproducible missing-secret failure is no longer present at the
provider entry. This verifies the Web client selection and Google handoff only,
not the validity of the secret at token exchange or successful Android login.
Next: owner retries Continue with Google on the existing A16 build, completes the
Google account choice and reports the app outcome. No APK rebuild/reinstall is
needed solely for this backend configuration correction.

## Real Google sign-in reported successful on A16

After the retry request, the owner reports **"ok bin angelmeldes"**. Record this as
owner-confirmed successful Google sign-in on the installed `ffb1ec06` A16 build,
not merely a synthetic redirect pass. No agent accessed credentials or the
protected Account screen contents.

Reopening the saved Supabase Google provider confirms the Web ID first and the
original iOS ID still present, with Save disabled. The secret field was not
printed or revealed. This verifies the prepared client-ID addition was saved
without dropping the iOS ID.

This one successful login does not close email registration/recovery, logout,
account deletion, A/B product-data isolation, offline restoration or PostHog
consent acceptance. The immediate next check is account/local product-data
separation: before sign-in, the local workspace had mode `hunk`, one selected app,
and History with 3 Zaps / 3h32m (including visible uncertainty warnings). Those
records should remain in the separate local workspace, not be implicitly assigned
to the newly selected account. No owner account deletion is authorized for testing.

## Owner-confirmed logout and local-mode restoration

The owner clarified that the signed-in account had **no modes**, not a blank app
screen. After the requested normal Account -> Sign out action, the owner confirms:
**"ja jetzt sehe ich hunk"**.

This closes the bounded real-device sequence of Google sign-in, an account view
without the previous local mode, normal sign-out, and restoration of visible local
mode `hunk`. It supports account/local mode separation and retention across this
round trip. It does not prove all private stores, selected packages, schedule
receipts or History totals are preserved, and does not substitute for a distinct
account A -> account B isolation check. No account was deleted and no data was
cleared. Phone is owner-reported signed out with its local mode visible.

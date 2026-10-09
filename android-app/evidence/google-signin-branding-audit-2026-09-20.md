# Android Google sign-in branding audit

Date: September 20, 2026. Read-only Google Cloud and source inspection. No OAuth
client, branding field, Supabase setting, account, credential, Android runtime or
iOS source was changed.

## Current behavior and cause

Android production currently asks Supabase for a PKCE Google OAuth URL and opens
that URL in the browser. Supabase then brokers Google authentication and redirects
the verified result back to Justnoise. The visible `<project>.supabase.co` identity
on Google's screen is therefore expected from this browser flow; it is not a second
Justnoise user account or an unknown login recipient.

The Google Auth Platform project was inspected with the already authenticated
project owner account:

- OAuth app name is **Justnoise**.
- An app logo is present.
- Branding status says it must be verified before it can be shown to users.
- Home-page, privacy-policy and terms links are empty.
- The only authorized domain is the current Supabase project domain.
- Existing OAuth clients are two Web applications and one iOS client.
- **No Android OAuth client exists.**

## Release decision

Keep the currently working browser OAuth flow in final-test candidate `8dc7ba54`.
Do not swap it for native Credential Manager immediately: native Google ID-token
sign-in first requires an Android OAuth client bound to `com.justnoise.app` and the
correct signing certificate, corresponding Supabase provider acceptance and a new
reviewed/tested binary. Adding only client code would create a broken login path.

Two future production-polish routes are valid:

1. complete Google branding verification and use a Justnoise-controlled Supabase
   custom auth domain; or
2. configure a proper Android OAuth client and implement native Google Credential
   Manager -> Supabase ID-token exchange.

The first route also needs the currently excluded public policy/domain work. The
second is a post-final-test authentication migration, not an invisible label edit.
Until one route is completed, final testers may see the Supabase project domain
while the Google consent screen still belongs to the configured Justnoise project.


# Justnoise Android — Accessibility review package

Updated 2026-09-08. **Draft, not submitted.** The recording and access verification
must use the actual signed release. A script or development screenshot does not
count as a completed video. Publisher: **Justnoise Media**; contact:
**hello@justnoise.shop**. Intended release ID: `shop.justnoise.app`.
Named modes are implemented and the owner confirmed manual/scheduled use on the
A16. Later timer/onboarding/setup/support changes passed local checks but are not
installed on the owner phone. Reconcile these with the signed candidate before
using this script; earlier feature acceptance is not release acceptance.

## Declaration answers to reconcile with the live form

**Accessibility tool:** No. Justnoise is a focus app, not an app whose primary purpose
is helping people with disabilities access their device.

**Purpose:** App functionality. The user saves named modes containing apps to
interrupt and explicitly selects one for manual Zap starts. Each schedule holds its
own mode reference; a scheduled start does not change the user's recent selection.
Missing/empty modes do not start, and active sessions retain their target snapshot.
While the service is enabled, Justnoise
uses foreground-app identifiers, keeping only the latest temporarily in memory.
During an eligible session, matching selected apps receive an app-owned blocking
screen. It does not retrieve screen content or operate controls inside other apps.
These are user-defined rules, not autonomous planning or AI-driven UI actions.

**Data handling:** Window-change event type and foreground package identifier are
used locally; the latest identifier is cleared on screen-off/service uncertainty.
No view trees, typed text, passwords or notification content are read. No
Accessibility-derived data is sent off-device or retained as an app-usage history.
Local mode names/IDs/app sets, recent selection, session/History/schedule/pairing retention is described in the
privacy notice. Read the live form's definitions before answering its collection/
sharing question; local access must still be disclosed even where collection means
off-device transmission. Do not equate “not uploaded” with “not accessed.”

This is a supportable submission explanation, not Google approval. The declaration,
separate consent flow and recording must match the release.
[Google Accessibility guidance](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

## Recording sequence

Use a separately coordinated clean installation. Do not clear the owner's paired
development app or imply that an already-enabled service was newly granted.
Record actual taps and readable text; keep unrelated notifications and private
content out of the recording. Do not impose a duration cap that makes disclosure
unreadable.

| Scene | Show on the signed release | Short explanatory caption |
| --- | --- | --- |
| Launch | App name, introduction and initial Zap pairing | Pairing does not grant Accessibility access. |
| Refusal | Full disclosure, unchecked consent, leaving and returning | Blocking stays unavailable without affirmative consent. |
| Consent | Slowly show all disclosure text, check consent and press the settings action | The user chooses to continue. |
| Manual permission | Correct Justnoise service in Android Settings and the user's enable action | Android permission is enabled manually. |
| Core function | Save eligible apps in a named mode, explicitly select it, start with the paired Zap, open a selected target | Only that mode's saved targets are interrupted during an eligible session. |
| Escape and stop | Native Home/Back, paired-Zap stop, target usable again | The user can leave or disable the service. |
| MVP context | Choose a mode for a schedule, observe automatic start, paired stop and its History entry | Schedules resolve their own mode; History records Session time without mode names. |

Scenes through core functionality cover the flow identified in
[Google's video requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).
The extra safe-escape/MVP scenes explain Justnoise's actual behavior. Only record
affected release evidence; do not ask the owner to repeat earlier development tests
just to fill this script. A published privacy page is not the separate disclosure.
[Disclosure guidance](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en).

## Reviewer access — proposed public flow, not yet verified on release

No app username, password, subscription or server account is required. Review needs
an NFC-capable Android 12+ phone and a physical compatible NDEF Text tag. Provide a
working way for the reviewer to obtain that tag or a prepared review Zap; do not
substitute a video, copied intent extras, hidden code or permission bypass.

Provide a prewritten review tag, or use an external NFC writer to put exactly one
well-known **Text** record on a dedicated NDEF-capable review tag: **UTF-8**,
language **en**, nonempty nonsecret text such as **Justnoise review**. Text is
limited to 512 UTF-8 bytes; the language field is nonempty and at most 35 bytes.
Justnoise reads and pairs tags; it does not write them. No factory secret, app login
or payment is required by the current local pairing flow.

The parser rejects malformed or ambiguous multiple-Text records. Pairing is local
through **Activate Zap → Scan your Zap** or the explicit setup pairing action.
Keep the complete record unchanged afterward: authorization includes language,
status and text, not only the displayed words. The tag's hardware ID is not read,
so this is not physical-tag uniqueness or anti-cloning verification.
Do not instruct a reviewer to use the owner's private paired text, rewrite the
owner's tag or send an NFC payload by email. Final tag-provisioning instructions
must be tried through the public release flow before they are submitted.

Provide the actual reusable video/resource URLs in Console. Verify anonymous or
otherwise reviewer-authorized access and that any resource remains available for
review. Do not invent a requirement for the owner to buy a second phone. Google
requires workable access to restricted functionality; the exact access fields
must be checked in the live submission.
[Reviewer access guidance](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en).

## Known labels to reconcile before recording

The latest local build uses **Blocking & Zap setup**, **Pair or replace Zap**, and
**Withdraw consent and clear local data**. The disclosure now begins “Justnoise uses
Android Accessibility”; its data-handling body and consent rules are unchanged.
The integrated app still deliberately uses **Justnoise Android Dev** and
**Justnoise Android Dev app blocking** as its system/service identities. The
standalone capability spike remains separately labeled **Justnoise Gate 0**.
These development identities must be reconciled with the production package.
The schedule sheet uses **Save**, not “Save schedule.” Freeze the final wording
and version before recording; do not omit the disclosure or re-enable permissions
silently. Use the actual release, not the older installed development UI.

## Required attachments, still pending

- Signed release version, package and certificate/checksum identity.
- Final disclosure text, packaged Accessibility metadata and local data inventory.
- Actual video URL and tested physical-Zap reviewer access.
- Public Android privacy URL, matching in-app entry and working support contact.
- Evidence-supported internal-beta GO and affected signed-release checks.

No attachment, signature, device result, policy approval or upload is claimed by
this preparation. Track the authoritative state in
[the internal-upload handoff](../ANDROID_PLAY_INTERNAL_UPLOAD.md).

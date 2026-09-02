# Android NFC activation architecture

Status: **GATE 0 CANDIDATE IMPLEMENTED — PHYSICAL EVIDENCE AND PRODUCTION HOLD**

This document records the Android activation direction established after reviewing Android's NFC dispatch model and comparable focus products. It does not authorize `android-app/`, choose a permanent package name, or claim that direct activation works on the required devices.

As of 2026-09-01, the disposable Gate 0 sources and integrated development APK
implement the narrow `ACTION_NDEF_DISCOVERED` Activity described below. It requires a
live `EXTRA_TAG`, re-reads NDEF from the physical tag, ignores message-only intent
forgeries, feeds the shared parser/controller, and shares a short process mutation gate
with the foreground reader. Those source and test properties are not routing evidence.
The [September 2 A16 follow-up](../spikes/android-gate0/evidence/A16_PARTIAL_RUN_2026-09-02.md#warm-home--navigation-follow-up)
records the initial warm unlocked-Home start that revealed the existing app screen,
then a successful post-fix physical start without Justnoise or a chooser appearing;
Samsung Home remained foreground. The task-isolation update also passed no-live-tag
rejection samples while idle and active. Confirmed direct stops, repetitions,
cold-process, lock, competing-handler, and Force Stop cases remain unverified;
G0-19 is still blocked.

## Product outcome

The intended unlocked-phone interaction is:

> Tap the physical Zap → Android routes the tag to Justnoise → Justnoise validates it → the current session stops, or the most recently selected valid mode starts.

The user should not have to find Justnoise, open it, press **Scan**, and then tap the Zap. A missing mode, empty target selection, unavailable restriction capability, unauthorized/malformed tag, duplicate callback, or lifecycle cancellation must leave restriction state unchanged and expose a truthful recovery result.

Android normally dispatches a recognized NDEF tag to a matching Activity while the device is unlocked. `ACTION_NDEF_DISCOVERED` is the preferred route; an Android Application Record (AAR) can make the intended package the preferred target. Reader mode remains foreground-only and is therefore the right tool for pairing, diagnostics, and recovery—not the final low-friction session-toggle entry. See the official [NFC guide](https://developer.android.com/develop/connectivity/nfc/nfc) and [`NfcAdapter`](https://developer.android.com/reference/android/nfc/NfcAdapter).

## Separate responsibilities

| Responsibility | Android mechanism | Security meaning |
| --- | --- | --- |
| Route a scan to Justnoise | Narrow NDEF intent filter; future tag may also contain an AAR | Routing only; it does not authorize the Zap |
| Parse the tag | Bounded NDEF adapter | Reject malformed, ambiguous, or unsupported envelopes |
| Authorize the Zap | Versioned provisioning/verification policy | Decides whether state may mutate |
| Select what starts | Last explicitly selected mode, only if it still exists and has targets | Portable product rule; no silent fallback to another mode |
| Enforce restrictions | Disclosed, user-enabled Accessibility candidate | Still requires Gate 0 and Play review |
| Show blocked UI | Justnoise-owned accessibility overlay candidate | App-owned presentation and escape-path responsibility |

An AAR, MIME type, external record type, package name, intent action, `EXTRA_NDEF_MESSAGES`, or the fact that Android launched Justnoise is never authorization. The entry Activity must run the same authorization and session reducer as an in-app scan.

An exported Activity is an untrusted boundary on older target/platform combinations. Starting with Android 17/API 37, an NFC Activity targeting newer than Android 16 must use `android.permission.DISPATCH_NFC_MESSAGE`, which restricts dispatch to the NFC system service. The production manifest must adopt that protection when the target/API combination supports it, while the A16/A17 matrix must prove backward-compatible behavior on their installed Android versions. See the official [NFC manifest and dispatch requirements](https://developer.android.com/develop/connectivity/nfc/nfc#manifest).

## Two NFC entry paths

### 1. Foreground reader mode

Keep the current one-shot reader path for:

- initial pairing or replacement;
- diagnostics when Android dispatch cannot route a legacy tag;
- explicit recovery inside Justnoise; and
- Gate 0 comparison with the published iOS foreground Core NFC flow.

The reader must perform tag I/O before disabling reader mode, then disable it before applying state. Activity pause, cancellation, or replacement invalidates a callback already reading. One message may contain non-Text auxiliary records, but the Gate 0 parser requires exactly one bounded well-known UTF-8 Text candidate and rejects multiple Text candidates as ambiguous.

### 2. Direct system tag dispatch

The Gate 0 Android session-toggle candidate is a small exported NFC entry Activity that:

1. accepts only the approved NDEF action/dispatch shape and uses `DISPATCH_NFC_MESSAGE` where supported;
2. requires `EXTRA_TAG` and performs a live `Ndef` re-read while the physical tag is present;
3. treats `EXTRA_NDEF_MESSAGES` only as non-authoritative system-parsed data and never mutates from that extra alone;
4. applies the same bounded parser and authorization policy as reader mode to the live read;
5. reconciles consent, selected mode, target validity, restriction capability, active-session truth, and the stop guard;
6. performs at most one eligible transition;
7. shows a minimal truthful result only when user action is needed; and
8. finishes without exposing the full application flow.

The entry Activity must not treat an NFC scan as Accessibility consent, open Accessibility settings automatically, start when the device is locked unless device evidence proves and product review accepts that behavior, or resurrect enforcement after Force Stop. A forged or replayed explicit intent—even one containing copied authorized NDEF message bytes—must leave state unchanged without a usable live `Tag`. Requiring a live read reduces intent spoofing; it does not make a static NDEF tag unclonable.

The entry declares an explicit empty `android:taskAffinity` in both disposable APKs,
while keeping `singleTop`, `noHistory`, and `excludeFromRecents`. This prevents a
new-task NFC launch from reusing the normal Justnoise task and revealing its existing
screen when the entry finishes. Android documents this isolation pattern for
[transient activities](https://developer.android.com/develop/ui/views/notifications/navigation).
A node-scoped manifest regression checks both declarations. A synthetic launch without
a live tag can verify navigation and rejection only; returning to Home after an actual
physical scan still requires device evidence.

## Tag compatibility strategy

### Existing Zap profile

Existing hardware is treated as a legacy well-known NDEF Text profile. Gate 0 may test a narrow `text/plain` dispatch filter without publishing a real authorized value in source or evidence. This profile can conflict with another installed app that also handles generic Text tags, producing a chooser. That is a device result, not something architecture can assume away.

### Future Zap profile

Future tags should use a reviewed, Justnoise-specific external NDEF type as the first record and may add an AAR for the final production package. The permanent external type, package identifier, migration plan, and cross-platform fallback must be chosen only after ownership and signing identities are final. Do not put a web URL in the tag merely to open the app: Android 16 routes NFC web links through `ACTION_VIEW`, and Android 17 adds an explicit open-link notification, increasing friction.

The future profile should preserve iOS readability. An Android-only AAR may be an auxiliary record, while the cross-platform authorization material remains in an explicitly versioned record. Test the complete multi-record envelope on both platforms before manufacturing or rewriting customer hardware.

## Authorization decision still required

The shipped iOS application currently recognizes decoded Text values from a compatibility allow-list. The Android Gate 0 spike pairs one syntactically valid Text record locally and stores a Keystore HMAC digest over its canonical bytes. These are materially different provisioning and migration models.

Before production parity is claimed, choose and document one product model covering:

- factory-recognized versus user-paired hardware;
- installation, account, or household ownership scope;
- replacement, removal, migration, rotation, restore, and account deletion;
- compatibility with already shipped tags; and
- cloning expectations for static NDEF hardware.

A keyed local digest prevents raw payload persistence but does not make a copyable static tag unclonable. Cryptographic anti-cloning would require an authenticated tag protocol and compatible hardware; it cannot be created by an intent filter or AAR.

Until that decision is frozen, the disposable spike may use local pairing for safe physical feasibility evidence, but it is not proof of iOS authorization parity.

## Required evidence

Direct dispatch becomes part of the A16/A17 launch promise only after both required launch families, for every named variant being listed, demonstrate:

- legacy Text routing from an unlocked home screen with Justnoise backgrounded and cold-started normally;
- no app-open or Scan-button prerequisite;
- ten consecutive authorized starts and stops with at most one mutation per tap;
- unauthorized, malformed, empty, ambiguous, repeated, and canceled inputs never mutate state;
- truthful behavior when Accessibility is disabled/revoked, mode data is invalid, or the app was Force Stopped;
- no chooser or second confirmation for the direct-flow claim; if product accepts such friction, G0-19 remains failed and the advertised behavior must be explicitly rescoped to foreground scanning;
- a forged explicit NFC intent containing copied NDEF messages but no usable live `EXTRA_TAG` never mutates state;
- `DISPATCH_NFC_MESSAGE` protection on API/target combinations that support it, plus truthful fallback behavior on the tested A16/A17 Android versions;
- correct behavior with competing NFC handlers, screen lock, reboot, process death, default battery settings, and OEM NFC preferences;
- the future external-type/AAR envelope on both Android and iOS before adopting it for hardware; and
- no payload, tag identifier, foreground-package history, or accessibility-derived data in logs, analytics, or network traffic.

`docs/ANDROID_GATE_0.md` and `spikes/android-gate0/evidence/DEVICE_MATRIX.md` remain authoritative for the pass/fail decision. Unit tests and an emulator can prove parsing and state boundaries, but not RF reliability, routing ownership, OEM behavior, restriction durability, or Play acceptance.

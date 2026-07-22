# Gate 0 local data and Accessibility inventory

Status: draft for technical, privacy, and Google Play policy review

Gate 0 has no Internet permission, backend client, account, analytics SDK, telemetry, crash reporter, or network destination. Production source contains no logging calls. The inventory below is the complete intended data boundary; device evidence must confirm it with reviewed logs and network observation.

| Source | Field or derived value | Purpose | Processing | Persistence and retention | Network destination |
| --- | --- | --- | --- | --- | --- |
| Accessibility `TYPE_WINDOW_STATE_CHANGED` | `packageName` only | Exact comparison with the one selected target | Transient, in the service callback | Not persisted, queued, logged, or collected as history | None |
| Accessibility event | Node tree, source, text, content description, password state, notification content, window list | Not used | APIs are not called; `canRetrieveWindowContent=false`; no retrieval flags | None | None |
| Scoped launcher query | App label and package identifier for launchable activities | Display the local picker and selected app name on the blocked surface | In memory while the picker or blocked surface is built | Only the chosen package identifier persists | None |
| User configuration | Selected package identifier | Exact restriction target | Local SharedPreferences | Until changed, withdrawal, uninstall, or app-data clear | None |
| Consent | Disclosure version and acceptance epoch milliseconds | Prove which prominent disclosure was affirmatively accepted | Local SharedPreferences | Until withdrawal, uninstall, app-data clear, or a new version requires fresh consent | None |
| Session state | Desired-active Boolean, start epoch milliseconds, monotonic session revision, fail-closed recovery marker | Start/stop guard and cross-component reconciliation; suppress enforcement after a failed durable write | Local SharedPreferences | Active-session lifetime; revision/recovery marker remain until a successful transition or withdrawal/uninstall/data clear | None |
| Service liveness | Connected marker and `elapsedRealtime` heartbeat | Reject stale active claims | Local SharedPreferences; refreshed about every two seconds | Removed on known disconnect; invalidated at process start; cleared on withdrawal/uninstall/data clear | None |
| NFC reader | One NDEF message containing exactly one well-known Text record | Pair or authorize one Zap interaction | Record is validated as UTF-8; language max 35 bytes; text max 512 bytes; canonical record bytes are zeroed after HMAC use | Raw/canonical record is not persisted or logged | None |
| NFC tag object | Hardware tag identifier | Not used | `Tag.id`/`getId()` is never read | None | None |
| Android Keystore | Non-exportable HMAC-SHA256 key | Compute a keyed digest of the canonical NDEF Text record | Android Keystore | Until withdrawal, uninstall, or app-data/key clear | None |
| Zap configuration | Base64 HMAC-SHA256 digest | Constant-time local authorization comparison | Local SharedPreferences | Until replacement, withdrawal, uninstall, or app-data clear | None |
| Overlay | Static Justnoise copy and buttons | Interrupt exactly the selected package and expose escape routes | In memory while visible | No overlay content is retained | None |

## Withdrawal and deletion behavior

Withdrawal first clears desired enforcement and publishes a state revision so any visible overlay is removed. It deletes the Keystore alias and clears the dedicated Gate 0 SharedPreferences contents, sends an app-scoped non-exported withdrawal signal to the bound service, and asks the service to disable itself. Re-enabling instructions require a newly accepted current disclosure. Android uninstall or Clear storage also starts with consent unchecked because cloud backup and device transfer are explicitly excluded.

## Evidence still required

- Confirm through redacted logs that no foreground package history or NDEF data appears.
- Observe network traffic on both required devices; expected Gate 0 traffic from the app is zero.
- Confirm OEM backups do not restore consent or pairing; `allowBackup=false` is a source claim until verified.
- Review every dependency and merged manifest from the CI report before policy sign-off.

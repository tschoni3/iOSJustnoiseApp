# Registering NTAG215 Zaps for iOS

Each registration authorizes one chip on any compatible iPhone. The iOS app checks it offline. A build of Android that uses legacy text labels will reject the new format. Plan stock for both platforms separately until Android supports registered Zaps.

## Private iPhone packing workflow

Open `scripts/Zap-Station.command` on the Mac and keep its window open. Connect the paired iPhone to the same Wi-Fi. Open **Zap Station** on the iPhone, tap **Zap vorbereiten**, and allow local network access if prompted. Hold one empty, writable NTAG215 at the top of the phone until **Fertig ✓** appears, then repeat. The phone reads the chip UID, gets a signed credential from the Mac, writes it, and checks it by reading the same chip again. **Bereits fertig ✓** confirms a previously prepared tag. A tag with other data, including an old Zap, is left intact. For a customer scan, switch to **JustNoise**. The station's issuance log records generated credentials. Only the iPhone can confirm the physical write.

If the Mac restarts or changes Wi-Fi, reopen the station. If macOS requests incoming-network access for Node, allow it on the packaging network. Close the Terminal window when done. The temporary pairing file is transferred separately into the iPhone app's `Library/Caches` using Xcode's paired device service. On first use, the app imports it into the device-only Keychain and removes the temporary file. Keep both the issuer directory and the pairing file private. To revoke the phone's issuing access, stop the station, rotate its token and restart; only re-pair trusted devices.

For first-time installation, open `ZapStation/ZapStation.xcodeproj` in Xcode, select the paired iPhone and run the **ZapStation** scheme with the team's development signing. Start the Mac station. From the mobile repository, replace `<device>` below with the iPhone identifier from `xcrun devicectl list devices` and transfer its pairing file:

```sh
xcrun devicectl device copy to --device <device> --domain-type appDataContainer \
  --domain-identifier stilltschoni.zapstation \
  --source ../.justnoise-zap-issuer-v1/workshop/ZapWorkshopPairing.json \
  --destination Library/Caches/ZapWorkshopPairing.json
```

The independent JustNoise project builds the customer reader. For internal testing of tags issued by an earlier test authority, use `python3 scripts/stage-zap-test-reader.py NEW_DIRECTORY TEST_READER_TRUST_JSON` and build JustNoise from the staged directory. This adds the specified reader trust exclusively to that internal copy. Both apps use the same production verifier and trust resource.

## Manual preparation

1. Read the seven-byte serial number from the NTAG215 with an NFC reader. Use the displayed byte order. The tag must still be writable.
2. From the mobile repository, run `node scripts/zap-issuer.mjs register ../.justnoise-zap-issuer-v1 <UID>`, substituting the serial number. The tool accepts fourteen hex digits or seven colon-separated bytes. It registers the UID locally and prints its tag text. Repeating the command returns the same text.
3. Write that text to the same chip as a single standard NFC Forum UTF-8 Text record. Exclude the terminal newline. Read it back to confirm. A generic reader should see a `JNZ1.` prefix and Base64 data.
4. Before shipping a batch, verify fresh activation on two iPhones with the same tag and rejection on a different ordinary NTAG215 holding the same record. Check start/stop on an activated installation and the upgrade path for existing customers.

Previously sold text Zaps still activate a fresh iOS installation, including after reinstall or a phone change. They also continue to control sessions. This legacy compatibility means copies of a recognized old text can still work. UID binding applies to newly registered Zaps.

## Issuer and recovery

The initial local issuer directory is `../.justnoise-zap-issuer-v1` next to the mobile repository. Keep an encrypted offline backup of that directory before provisioning inventory. It contains the private Ed25519 signing key and issued registrations. Restrict access to manufacturing operators. The app bundles only `ZapTrust.json`, containing the verification key and the AES reader key. Future batches must use the same issuer. Removing a record from the local manufacturing log does not revoke a tag already signed.

For an independent development issuer, use `node scripts/zap-issuer.mjs init /secure/new-issuer /path/to/new-test-trust.json`. Both destinations must be new. The corresponding app must use the matching trust configuration. Never put the private issuer in a mobile repository or bundle. Losing the private issuer requires a planned trust migration for future stock.

## Credential format

Zap Station writes UTF-8 with language `en`. Earlier station versions could write the same signed credential as UTF-16. Both iPhone apps read those credentials in place and verify them against the scanned chip UID. Legacy label matching keeps its original UTF-8 rules.

A Text record contains `JNZ1.` followed by standard Base64 of a 64-byte Ed25519 signature followed by an AES-256-GCM box (12-byte random nonce, ciphertext, 16-byte authentication tag). The issuer signs the UTF-8 domain `JustNoise/Zap/1`, the seven raw UID bytes and the entire box in that order. The domain and UID are also GCM associated data. The app reads the UID from the connected chip, verifies the signature, decrypts the box, and checks the exact product value `justnoise.zap.activation.v1`.

The reader key is distributed with the app, so encryption provides opacity to ordinary NFC readers. The separate issuer signature authorizes the UID. Protection covers ordinary chips with different factory UIDs. Static credentials cannot distinguish hardware that reproduces both the data and the UID.

import Foundation

enum ZapScanAuthorization {
    static func accepts(records: [ZapNDEFRecordInput], uid: Data, issuer: RegisteredZap?,
                        purpose: ScanningPurpose, isActivated: Bool,
                        legacyLabels: Set<String>) -> Bool {
        if purpose == .sessionToggle && !isActivated { return false }
        let texts = records.compactMap { ZapReadPolicy.decodedText($0, allowsUTF16: true) }
        if let credential = texts.first(where: { $0.hasPrefix("JNZ1.") }) {
            guard records.count == 1 else { return false }
            if issuer?.accepts(text: credential, physicalUID: uid) == true { return true }
            #if ZAP_READER_TEST_TRUST
            return RegisteredZap.previousOperatorTestIssuer?.accepts(text: credential, physicalUID: uid) == true
            #else
            return false
            #endif
        }
        return ZapReadPolicy.firstRelevantDecision(
            records: records, authorizedPayloads: legacyLabels, acceptedReadAlreadyHandled: false
        ).mutationEligible
    }
}

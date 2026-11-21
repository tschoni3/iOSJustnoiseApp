import CoreNFC
import SwiftUI

enum WorkshopError: LocalizedError {
    case pairing, station, busyTag(String), unsuitable, verification

    var errorDescription: String? {
        switch self {
        case .pairing: return "iPhone erneut mit der Mac-Station koppeln."
        case .station: return "Zap-Station am Mac öffnen. Beide Geräte brauchen dasselbe WLAN."
        case .busyTag(let detail): return detail
        case .unsuitable: return "Einen einzelnen, beschreibbaren NTAG215 ans iPhone halten."
        case .verification: return "Code nicht bestätigt. Bitte denselben Zap erneut prüfen."
        }
    }
}

@MainActor
private final class ZapWorkshopWriter: NSObject, ObservableObject, @preconcurrency NFCTagReaderSessionDelegate {
    @Published var message: String?
    @Published var busy = false
    private var reader: NFCTagReaderSession?
    private var mac: ZapWorkshopConnection?
    private var processing = false

    func begin() async {
        guard !busy else { return }
        busy = true
        message = "Verbinde mit dem Mac …"
        do {
            let station = try ZapWorkshopConnection()
            mac = station
            guard try await station.request() == "ready" else { throw WorkshopError.station }
            guard NFCTagReaderSession.readingAvailable, let reader = NFCTagReaderSession(
                pollingOption: .iso14443, delegate: self, queue: .main
            ) else { throw WorkshopError.unsuitable }
            self.reader = reader
            processing = false
            message = nil
            reader.alertMessage = "Halte einen leeren Zap oben ans iPhone, bis „Fertig“ erscheint."
            reader.begin()
        } catch {
            reset((error as? WorkshopError ?? .station).localizedDescription)
        }
    }

    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        guard reader === session else { return }
        let canceled = (error as? NFCReaderError)?.code == .readerSessionInvalidationErrorUserCanceled
        reset(canceled ? nil : "Scan beendet. Bitte denselben Zap erneut prüfen.")
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard reader === session, !processing, let mac else { return }
        guard tags.count == 1, let tag = tags.first, case .miFare(let chip) = tag,
              chip.identifier.count == 7 else { fail(session, .unsuitable); return }
        processing = true
        Task {
            do {
                try await session.connect(to: tag)
                guard reader === session else { return }
                let (status, capacity) = try await chip.queryNDEFStatus()
                guard status != .notSupported, let issuer = RegisteredZap.bundled else { throw WorkshopError.unsuitable }
                let existing: [NFCNDEFPayload]
                do { existing = try await chip.readNDEF().records }
                catch let error as NFCReaderError where error.code == .ndefReaderSessionErrorZeroLengthMessage { existing = [] }
                guard reader === session else { return }
                if let old = Self.text(in: existing), issuer.accepts(text: old, physicalUID: chip.identifier) {
                    finish(session, "Bereits fertig ✓")
                    return
                }
                for record in existing {
                    switch StationTagContent(format: record.typeNameFormat.rawValue, type: record.type,
                                             id: record.identifier, payload: record.payload) {
                    case .blank: continue
                    case .text:
                        let zapCode = Self.text(in: [record])?.hasPrefix("JNZ1.") == true
                        throw WorkshopError.busyTag(zapCode ? "Auf diesem Tag ist bereits ein Zap-Code."
                                                           : "Auf diesem Tag ist ein Texteintrag gespeichert.")
                    case .link:
                        throw WorkshopError.busyTag("Auf diesem Tag ist ein Link gespeichert.")
                    case .other:
                        throw WorkshopError.busyTag("Vorhandener NFC-Eintrag (Typ \(record.typeNameFormat.rawValue), \(record.payload.count) Byte).")
                    }
                }
                guard status == .readWrite else { throw WorkshopError.unsuitable }
                let text: String
                do { text = try await mac.request(uid: chip.identifier) }
                catch { throw error as? WorkshopError ?? WorkshopError.station }
                guard reader === session else { return }
                guard issuer.accepts(text: text, physicalUID: chip.identifier) else { throw WorkshopError.verification }
                let record = NFCNDEFPayload(format: .nfcWellKnown, type: ZapTextPayload.type,
                                            identifier: Data(), payload: ZapTextPayload.encode(text))
                let ndef = NFCNDEFMessage(records: [record])
                guard ndef.length <= capacity else { throw WorkshopError.unsuitable }
                session.alertMessage = "Zap ruhig halten – wird geschrieben und geprüft."
                try await chip.writeNDEF(ndef)
                guard reader === session else { return }
                let readback = try await chip.readNDEF()
                guard reader === session else { return }
                guard let value = Self.text(in: readback.records), value == text,
                      issuer.accepts(text: value, physicalUID: chip.identifier)
                else { throw WorkshopError.verification }
                finish(session, "Fertig ✓")
            } catch {
                guard reader === session else { return }
                fail(session, error as? WorkshopError ?? .verification)
            }
        }
    }

    private static func text(in records: [NFCNDEFPayload]) -> String? {
        guard records.count == 1, let record = records.first,
              record.typeNameFormat == .nfcWellKnown, record.type == ZapTextPayload.type else { return nil }
        return ZapTextPayload.decode(record.payload, allowsUTF16: true)
    }

    private func finish(_ session: NFCTagReaderSession, _ text: String) {
        session.alertMessage = text
        session.invalidate()
        reset(text)
    }

    private func fail(_ session: NFCTagReaderSession, _ error: WorkshopError) {
        session.invalidate(errorMessage: error.localizedDescription)
        reset(error.localizedDescription)
    }

    private func reset(_ text: String?) {
        reader = nil
        mac?.close()
        mac = nil
        processing = false
        busy = false
        message = text
    }
}

struct ZapStationView: View {
    @StateObject private var writer = ZapWorkshopWriter()
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Label("Mac und iPhone im selben WLAN", systemImage: "wifi")
                    .font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                if writer.busy { ProgressView().accessibilityHidden(true) }
                Text(writer.message ?? "Leeren Zap bereitlegen.")
                    .font(.title2.bold())
                    .accessibilityIdentifier("workshopStatus")
                Button {
                    Task { await writer.begin() }
                } label: {
                    Label("Zap vorbereiten", systemImage: "wave.3.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(writer.busy)
            }
            .padding(24)
            .navigationTitle("Zap Station")
        }
    }
}

#Preview { ZapStationView() }

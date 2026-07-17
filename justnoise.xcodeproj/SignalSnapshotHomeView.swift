import SwiftUI

struct SignalSnapshotHomeView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var showInfo = false

    var body: some View {
        HStack(spacing: 8) {

            Text(nfcViewModel.signalLabel)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)

            Button {
                showInfo.toggle()
            } label: {
                Image(systemName: "info.circle")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white.opacity(0.75))
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
        .sheet(isPresented: $showInfo) {
            SignalStateInfoModal(
                currentLabel: nfcViewModel.signalLabel
            )
        }
    }
}

struct SignalStateInfoModal: View {
    let currentLabel: String
    @Environment(\.dismiss) private var dismiss

    var explanation: String {

        switch currentLabel {

        case "Fragmented Attention":
            return "Your focus was interrupted by many short sessions today."

        case "Noise Rising":
            return "Your attention has been more fragmented today."

        case "Signal Starting":
            return "You are beginning to build intentional focus."

        case "Signal Building":
            return "Your focus rhythm is becoming more stable."

        case "Strong Signal":
            return "You reached stable and intentional focus today."

        case "Deep Clarity":
            return "Exceptional sustained focus and clarity detected."

        default:
            return "Your current attention state is based on focus depth and rhythm."
        }
    }

    var body: some View {

        NavigationStack {

            VStack(spacing: 24) {

                Text(currentLabel)
                    .font(.title.bold())

                Text(explanation)
                    .font(.body)
                    .multilineTextAlignment(.center)

                Spacer()
            }
            .padding(24)
            .navigationTitle("Signal State")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    SignalSnapshotHomeView()
        .environmentObject(NFCViewModel())
        .preferredColorScheme(.dark)
}

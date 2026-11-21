// NFCActivationView.swift

import SwiftUI

struct NFCActivationView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @AppStorage("hasSeenZapScanGuide") private var hasSeenZapScanGuide = false
    @State private var showIntroduction = false
    @State private var scanAfterIntroduction = false

    var body: some View {
        NavigationView {
            ZStack(alignment: .center) {
                // Background color based on activation status
                backgroundColor
                    .ignoresSafeArea()

                VStack(spacing: 16) {
                    Spacer()

                    Image("zap_button")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 220, height: 220)
                        .opacity(1.0)
                        .onTapGesture(perform: startActivation)
                        .frame(width: 240, height: 240)
                        .padding(.vertical, 40)
                        .accessibilityLabel("Zap button")
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAction { startActivation() }

                    if !nfcViewModel.isActivated {
                        VStack(spacing: 8) {
                            Text("Tap the Zap on your screen to start.")
                                .font(.title)
                                .fontWeight(.semibold)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(foregroundColor)

                            Text("Hold the top of your iPhone against your Zap until you hear the confirmation tone.")
                                .font(.body)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(foregroundColor.opacity(0.8))
                        }
                        .padding(.horizontal)
                    }

                    Spacer()

                    if !nfcViewModel.isActivated {
                        Button("Watch how it works") {
                            showIntroduction = true
                        }

                        Button(action: {
                            if let url = URL(string: "https://justnoise.shop/pages/the-zap") {
                                UIApplication.shared.open(url)
                            }
                        }) {
                            Text("Don't have a Zap? Purchase one here.")
                                .font(.subheadline)
                                .foregroundStyle(foregroundColor)
                                .underline()
                        }
                        .padding(.top, 10)
                        .tint(foregroundColor)
                    }

                    Spacer()
                }
                .padding()
            }
            .navigationBarTitle("Connect your Zap", displayMode: .inline)
            .environment(\.colorScheme, nfcViewModel.isActivated ? .dark : .light)
            .toolbarBackground(backgroundColor, for: .navigationBar)
            .toolbarColorScheme(nfcViewModel.isActivated ? .dark : .light, for: .navigationBar)
            .tint(foregroundColor)
            .alert(item: $nfcViewModel.activeAlert) { unifiedAlert in
                switch unifiedAlert {
                case .error(let alertItem):
                    return Alert(
                        title: alertItem.title,
                        message: alertItem.message,
                        dismissButton: .default(Text("OK"))
                    )
                }
            }
        }
        .onAppear {
            if !hasSeenZapScanGuide { showIntroduction = true }
        }
        .fullScreenCover(isPresented: $showIntroduction, onDismiss: {
            hasSeenZapScanGuide = true
            guard scanAfterIntroduction else { return }
            scanAfterIntroduction = false
            guard !nfcViewModel.isActivated else { return }
            nfcViewModel.startScanning(purpose: .activation)
        }) {
            OnboardingVideoOverlay(continueTitle: "Got it – start scan") {
                scanAfterIntroduction = true
            }
        }
    }

    private func startActivation() {
        if !hasSeenZapScanGuide {
            showIntroduction = true
        } else {
            nfcViewModel.startScanning(purpose: .activation)
        }
    }

    // MARK: - Computed Properties
    var backgroundColor: Color {
        nfcViewModel.isActivated ? .black : .white
    }

    var foregroundColor: Color {
        nfcViewModel.isActivated ? .white : .black
    }
}

#Preview {
    NFCActivationView()
        .environmentObject(NFCViewModel())
}

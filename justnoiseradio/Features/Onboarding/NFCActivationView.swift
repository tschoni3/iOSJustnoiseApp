// NFCActivationView.swift

import SwiftUI

struct NFCActivationView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel

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
                        .onTapGesture {
                            nfcViewModel.startScanning(purpose: .activation)
                        }
                        .frame(width: 240, height: 240)
                        .padding(.vertical, 40)
                        .accessibilityLabel("Zap button")

                    if !nfcViewModel.isActivated {
                        VStack(spacing: 8) {
                            Text("Tap the button on your screen to start.")
                                .font(.title)
                                .fontWeight(.semibold)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(foregroundColor)

                            Text("Then hold your Zap near your phone to connect.")
                                .font(.body)
                                .multilineTextAlignment(.center)
                                .foregroundStyle(foregroundColor.opacity(0.8))
                        }
                        .padding(.horizontal)
                    }

                    Spacer()

                    if !nfcViewModel.isActivated {
                        Button(action: {
                            if let url = URL(string: "https://store.justnoise.shop/products/thezap") {
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
            .navigationBarTitle("Activate Zap", displayMode: .inline)
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

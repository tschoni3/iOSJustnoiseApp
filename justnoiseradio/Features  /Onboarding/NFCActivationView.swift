// NFCActivationView.swift

import SwiftUI

struct NFCActivationView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var isScanning = false // Loading indicator control

    var body: some View {
        NavigationView {
            ZStack(alignment: .center) {
                // Background color based on activation status
                backgroundColor
                    .ignoresSafeArea()

                VStack(spacing: 16) {
                    Spacer()

                    // Central Circular Button (Consistent with ContentView)
                    NFCScanButton(
                        action: {
                            isScanning = true
                            nfcViewModel.startScanning(purpose: .activation)
                        },
                        isBlocked: nfcViewModel.isActivated
                    )
                    .frame(width: 200, height: 200)
                    .padding(.vertical, 40)
                    // Ensure any internal icon/label inside the button inherits readable tint
                    .tint(foregroundColor)

                    if !nfcViewModel.isActivated {
                        Text("Tap your Zap to get started with JustNoise.")
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                            // Safety net in case parent env gets overridden
                            .foregroundStyle(foregroundColor)
                    }

                    Spacer()

                    if !nfcViewModel.isActivated {
                        // Purchase NFC Tag Button
                        Button(action: {
                            if let url = URL(string: "https://store.justnoise.shop/products/thezap") {
                                UIApplication.shared.open(url)
                            }
                        }) {
                            Text("Don't have a Zap? Purchase one here.")
                                .font(.subheadline)
                                // Use tint so it adjusts with color scheme, but also force contrast
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
            // Make the *environment color scheme* match our background so .primary text is always readable.
            // Light scheme => black text on white bg. Dark scheme => white text on black bg.
            .environment(\.colorScheme, nfcViewModel.isActivated ? .dark : .light)

            // Keep toolbar readable and consistent
            .toolbarBackground(backgroundColor, for: .navigationBar)
            .toolbarColorScheme(nfcViewModel.isActivated ? .dark : .light, for: .navigationBar)
            .tint(foregroundColor)

            // Unified alert using activeAlert from the view model.
            .alert(item: $nfcViewModel.activeAlert) { unifiedAlert in
                switch unifiedAlert {
                case .error(let alertItem):
                    return Alert(
                        title: alertItem.title,
                        message: alertItem.message,
                        dismissButton: .default(Text("OK"), action: {
                            isScanning = false
                        })
                    )
                case .reflectionPrompt:
                    return Alert(
                        title: Text(""),
                        message: nil,
                        dismissButton: .default(Text("OK"), action: {
                            isScanning = false
                        })
                    )
                }
            }
            .onChange(of: nfcViewModel.isActivated) { _, newValue in
                if newValue { isScanning = false }
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

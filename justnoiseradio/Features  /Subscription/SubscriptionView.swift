//
//  SubscriptionView.swift
//

import SwiftUI
import StoreKit
import AVKit
import AVFoundation
import UIKit

// ─────────────────────────────────────────────
// MARK: Autoplay header
// ─────────────────────────────────────────────
struct AutoplayLoopingPlayer: UIViewControllerRepresentable {
    let url: URL
    class Coordinator { var looper: AVPlayerLooper? }
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let item   = AVPlayerItem(url: url)
        let player = AVQueuePlayer()
        context.coordinator.looper = AVPlayerLooper(player: player, templateItem: item)

        let vc = AVPlayerViewController()
        vc.player = player
        vc.showsPlaybackControls = false
        vc.videoGravity          = .resizeAspectFill
        player.play()
        return vc
    }
    func updateUIViewController(_: AVPlayerViewController, context: Context) {}
}

// ─────────────────────────────────────────────
// MARK: Paywall
// ─────────────────────────────────────────────
struct SubscriptionView: View {

    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @EnvironmentObject var nfcViewModel:        NFCViewModel
    @Environment(\.presentationMode) private var presentationMode

    private let videoURL = Bundle.main.url(forResource: "Journaling",
                                           withExtension: "mp4")!

    @State private var isLoading          = false
    @State private var selectedProduct: Product?
    @State private var showRedeemAlert    = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VStack(spacing: 8) {

                // Header video + close
                ZStack(alignment: .topTrailing) {
                    AutoplayLoopingPlayer(url: videoURL)
                        .frame(height: 250)
                        .clipped()

                    Button { nfcViewModel.showSubscriptionOffer = false } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(.white)
                            .padding(.trailing, 10)
                    }
                }

                Text("Try Justnoise Journaling for free")
                    .font(.largeTitle.bold())
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal)

                featureList

                // Plan picker
                Group {
                    if isLoading {
                        ProgressView("Loading Plans…")
                            .foregroundColor(.white)
                    } else if subscriptionManager.products.isEmpty {
                        Text("No plans available.")
                            .foregroundColor(.gray)
                    } else {
                        VStack(spacing: 12) {
                            ForEach(subscriptionManager.products, id: \.id) { product in
                                SubscriptionPlanView(
                                    product: product,
                                    isSelected: product == selectedProduct
                                )
                                .onTapGesture {
                                    selectedProduct = product
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)

                Text("Recurring billing. Cancel anytime.")
                    .font(.footnote)
                    .foregroundColor(.gray)

                // Subscribe
                Button {
                    guard let product = selectedProduct else { return }
                    Task {
                        isLoading = true
                        await subscriptionManager.purchase(product)
                        isLoading = false
                    }
                } label: {
                    Text("Try free and subscribe")
                        .font(.headline)
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(selectedProduct != nil ? Color.white : Color.gray)
                        .cornerRadius(12)
                }
                .disabled(selectedProduct == nil)
                .padding(.horizontal)

                // One-time offer code redemption
                Button("Have a one-time offer code?") {
                    showRedeemAlert = true
                }
                .foregroundColor(.white)
                .padding(.top, 6)

                // Restore purchases
                Button("Restore purchases") {
                    Task { await subscriptionManager.restore() }
                }
                .font(.footnote)
                .foregroundColor(.white)
                .padding(.top, 4)
            }
            .padding(.vertical)
        }
        .onAppear {
            Task {
                isLoading = true
                await subscriptionManager.fetchProducts()
                selectedProduct = selectedProduct ?? subscriptionManager.products.first
                await subscriptionManager.updateSubscriptionStatus()
                isLoading = false
            }
        }
        // Alert to guide them into the system redemption sheet
        .alert("Redeem your one-time code", isPresented: $showRedeemAlert) {
            Button("Enter Code") {
                let scene = UIApplication.shared.connectedScenes
                    .first { $0.activationState == .foregroundActive }
                    as? UIWindowScene
                Task {
                    await subscriptionManager.redeemOfferCode(in: scene)
                    // if redemption succeeded, dismiss
                    if subscriptionManager.isProActive {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Enter the 12-character offer code you received in your email to unlock one year of JustNoise Pro.")
        }
        // Also auto-dismiss if entitlement changes
        .onReceive(subscriptionManager.$isProActive) { active in
            if active {
                presentationMode.wrappedValue.dismiss()
            }
        }
    }

    // — Helper: feature list —
    private var featureList: some View {
        VStack(alignment: .leading, spacing: 12) {
            featureRow("Unlock the full JustNoise library")
            featureRow("AI-powered journaling insights")
            featureRow("Personalized productivity coaching")
            featureRow("Exclusive focus & reflection tools")
        }
        .padding(.horizontal)
        .padding(.bottom, 20)
    }

    @ViewBuilder
    private func featureRow(_ text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
            Text(text)
                .foregroundColor(.white)
                .font(.subheadline)
        }
    }
}

// ─────────────────────────────────────────────
// MARK: Plan row
// ─────────────────────────────────────────────
struct SubscriptionPlanView: View {
    let product: Product
    let isSelected: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 6) {
                Text(product.displayName)
                    .font(.headline)
                    .foregroundColor(isSelected ? .black : .white)
                Text(product.displayPrice)
                    .font(.subheadline)
                    .foregroundColor(isSelected ? .black : .white)
            }
            Spacer()
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.black)
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(isSelected ? Color.yellow : Color.gray.opacity(0.2))
        .cornerRadius(12)
    }
}

// MARK: – Preview
struct SubscriptionView_Previews: PreviewProvider {
    static var previews: some View {
        SubscriptionView()
            .environmentObject(SubscriptionManager())
            .environmentObject(NFCViewModel())
    }
}

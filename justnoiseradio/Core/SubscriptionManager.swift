//  SubscriptionManager.swift
//

import Foundation
import StoreKit
import SwiftUI

@MainActor
final class SubscriptionManager: ObservableObject {

    // MARK: – State
    @Published var products: [Product] = []
    @Published var isProActive        = false

    // MARK: – Your product IDs (exact match from App Store Connect)
    private let productIDs = [
        "com.justnoise.app.pro",          // 1-month (Zap Start)
        "com.justnoise.app.pro.annually"  // 12-month (Zap Flow)
    ]

    // MARK: – Load from the App Store
    func fetchProducts() async {
        do {
            products = try await Product.products(for: productIDs)
            print("✅ StoreKit loaded:", products)
        } catch {
            print("❌ StoreKit error:", error.localizedDescription)
        }
    }

    // MARK: – Purchase
    func purchase(_ product: Product) async {
        guard case .success(let result) = try? await product.purchase(),
              case .verified(let tx) = result else { return }
        await tx.finish()
        await updateSubscriptionStatus()
    }

    // MARK: – Entitlement check (StoreKit only)
    func updateSubscriptionStatus() async {
        var active = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let tx) = result,
               productIDs.contains(tx.productID) {
                active = true
                break
            }
        }
        isProActive = active
        print("🔔 Premium status:", active)
    }

    // MARK: – Restore
    func restore() async {
        try? await AppStore.sync()
        await updateSubscriptionStatus()
    }

    // MARK: – Offer Code Redemption
    /// Presents the system redemption sheet, then refreshes entitlements.
    func redeemOfferCode(in scene: UIWindowScene?) async {
        if #available(iOS 18.0, *) {
            guard let scene = scene else { return }
            try? await AppStore.presentOfferCodeRedeemSheet(in: scene)
        }
        // Give StoreKit a moment, then refresh status
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        await updateSubscriptionStatus()
    }
}

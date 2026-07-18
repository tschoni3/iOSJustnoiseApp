//  SubscriptionManager.swift
//

import Foundation
import StoreKit
import SwiftUI

@MainActor
final class SubscriptionManager: ObservableObject {

    @Published var isProActive = false

    // MARK: – Your product IDs (exact match from App Store Connect)
    private let productIDs = [
        "com.justnoise.app.pro",          // 1-month (Zap Start)
        "com.justnoise.app.pro.annually"  // 12-month (Zap Flow)
    ]

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
}

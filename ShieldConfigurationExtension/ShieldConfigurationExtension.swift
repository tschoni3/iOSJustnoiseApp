//
//  ShieldConfigurationExtension.swift
//  ShieldConfigurationExtension
//
//  Created by TJ on 23.01.25.
//

import ManagedSettings
import ManagedSettingsUI
import UIKit
import OSLog

// MARK: - Logger
private let SHIELDLOG = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "shield.config")

// MARK: - UIColor (Hex)
private extension UIColor {
    /// Create a UIColor from a 6- or 8-digit hex string. Accepts "#18181A", "18181A", or "18181AFF".
    convenience init(hex: String) {
        let s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
            .uppercased()

        var rgb: UInt64 = 0
        guard Scanner(string: s).scanHexInt64(&rgb) else { self.init(white: 0, alpha: 1); return }

        switch s.count {
        case 6:
            let r = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
            let g = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
            let b = CGFloat(rgb & 0x0000FF) / 255.0
            self.init(red: r, green: g, blue: b, alpha: 1.0)
        case 8:
            let r = CGFloat((rgb & 0xFF000000) >> 24) / 255.0
            let g = CGFloat((rgb & 0x00FF0000) >> 16) / 255.0
            let b = CGFloat((rgb & 0x0000FF00) >> 8) / 255.0
            let a = CGFloat(rgb & 0x000000FF) / 255.0
            self.init(red: r, green: g, blue: b, alpha: a)
        default:
            self.init(white: 0, alpha: 1)
        }
    }
}

// MARK: - ShieldConfiguration Data Source
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    // MARK: Branding & Copy
    /// Asset name (make sure **target membership** includes the extension).
    private let iconAssetName = "zap_button"

    // MARK: Colors (explicit dark surface regardless of system appearance)
    private let bgColor              = UIColor(hex: "#18181A")        // deep charcoal
    private let titleColor           = UIColor(white: 1.0, alpha: 1.0)
    private let subtitleColor        = UIColor(white: 0.78, alpha: 1.0)
    private let primaryButtonBGColor = UIColor.white
    private let primaryButtonText    = UIColor(white: 0.05, alpha: 1.0)

    // MARK: Helpers
    private func makeIcon() -> UIImage? {
        if let img = UIImage(named: iconAssetName) { return img }
        // Fallback so we never render stock by accident if asset isn’t in the extension target:
        return UIImage(systemName: "bolt.circle.fill")?.withTintColor(.white, renderingMode: .alwaysOriginal)
    }

    private func makeTitle() -> ShieldConfiguration.Label {
        ShieldConfiguration.Label(text: "THIS IS JUSTNOISE", color: titleColor)
    }

    private func makeSubtitle(itemName: String) -> ShieldConfiguration.Label {
        let text = "\(itemName) is currently Zapped.\nTap your Zap to access it."
        return ShieldConfiguration.Label(text: text, color: subtitleColor)
    }

    private func makePrimaryButton() -> ShieldConfiguration.Label {
        ShieldConfiguration.Label(text: "Keep going", color: primaryButtonText)
    }

    /// Central builder so all shield contexts look identical.
    /// **Key change:** We set `backgroundBlurStyle: .dark` so the background actually renders dark.
    private func buildConfig(itemName: String?, fallbackName: String) -> ShieldConfiguration {
        SHIELDLOG.info("Building ShieldConfiguration (dark blur + explicit colors)")
        let trimmedName = itemName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName: String
        if let trimmedName, trimmedName.isEmpty == false {
            resolvedName = trimmedName
        } else {
            resolvedName = fallbackName
        }

        return ShieldConfiguration(
            backgroundBlurStyle: .dark,                 // <- THIS makes the background dark
            backgroundColor: bgColor,                   // tints the blur with your brand charcoal
            icon: makeIcon(),
            title: makeTitle(),
            subtitle: makeSubtitle(itemName: resolvedName),
            primaryButtonLabel: makePrimaryButton(),
            primaryButtonBackgroundColor: primaryButtonBGColor,
            secondaryButtonLabel: nil
        )
    }

    // MARK: - ShieldConfigurationDataSource
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        buildConfig(itemName: application.localizedDisplayName, fallbackName: "This app")
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        buildConfig(itemName: application.localizedDisplayName, fallbackName: "This app")
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        buildConfig(itemName: webDomain.domain, fallbackName: "This website")
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        buildConfig(itemName: webDomain.domain, fallbackName: "This website")
    }
}

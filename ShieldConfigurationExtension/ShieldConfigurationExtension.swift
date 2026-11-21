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

private let SHIELDLOG = Logger(
    subsystem: "com.stilltschoni.justnoiseradioapp",
    category: "shield.config"
)


// MARK: - UIColor Extension

private extension UIColor {

    /// Create UIColor from a 6- or 8-digit hex string.
    /// Examples:
    /// "#18181A"
    /// "18181A"
    /// "18181AFF"
    convenience init(hex: String) {

        let s = hex
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
            .uppercased()

        var rgb: UInt64 = 0

        guard Scanner(string: s).scanHexInt64(&rgb) else {
            self.init(white: 0, alpha: 1)
            return
        }

        let red: CGFloat
        let green: CGFloat
        let blue: CGFloat
        let alpha: CGFloat

        switch s.count {

        case 8:
            red = CGFloat((rgb & 0xFF000000) >> 24) / 255.0
            green = CGFloat((rgb & 0x00FF0000) >> 16) / 255.0
            blue = CGFloat((rgb & 0x0000FF00) >> 8) / 255.0
            alpha = CGFloat(rgb & 0x000000FF) / 255.0

        default:
            red = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
            green = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
            blue = CGFloat(rgb & 0x0000FF) / 255.0
            alpha = 1.0
        }

        self.init(
            red: red,
            green: green,
            blue: blue,
            alpha: alpha
        )
    }
}


// MARK: - Shield Configuration

class ShieldConfigurationExtension: ShieldConfigurationDataSource {

    private let iconAssetName = "zap_button"

    private let backgroundColor = UIColor(hex: "#18181A")
    private let textColor = UIColor.white
    private let buttonTextColor = UIColor.black
    private let buttonBackgroundColor = UIColor.white


    // MARK: - Build Shield

    private func makeConfiguration(
        itemName: String
    ) -> ShieldConfiguration {

        let icon = UIImage(named: iconAssetName)

        if icon == nil {
            SHIELDLOG.error(
                "Could not load image asset: \(self.iconAssetName, privacy: .public)"
            )
        }

        return ShieldConfiguration(
            backgroundBlurStyle: nil,

            backgroundColor: backgroundColor,

            icon: icon,

            title: ShieldConfiguration.Label(
                text: "THIS IS\nJUSTNOISE",
                color: textColor,
                
            ),

            subtitle: ShieldConfiguration.Label(
                text: "\(itemName) is currently Zapped.\nUnzap your phone to use it again.",
                color: textColor
            ),

            primaryButtonLabel: ShieldConfiguration.Label(
                text: "Be right back",
                color: buttonTextColor
            ),

            primaryButtonBackgroundColor: buttonBackgroundColor
        )
    }


    // MARK: - Application

    override func configuration(
        shielding application: Application
    ) -> ShieldConfiguration {

        let itemName =
            application.localizedDisplayName
            ?? "This app"

        return makeConfiguration(
            itemName: itemName
        )
    }


    // MARK: - Application inside Category

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {

        let itemName =
            application.localizedDisplayName
            ?? "This app"

        return makeConfiguration(
            itemName: itemName
        )
    }


    // MARK: - Website

    override func configuration(
        shielding webDomain: WebDomain
    ) -> ShieldConfiguration {

        let itemName =
            webDomain.domain
            ?? "This website"

        return makeConfiguration(
            itemName: itemName
        )
    }


    // MARK: - Website inside Category

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {

        let itemName =
            webDomain.domain
            ?? "This website"

        return makeConfiguration(
            itemName: itemName
        )
    }
}

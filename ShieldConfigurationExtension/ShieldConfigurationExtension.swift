//
//  ShieldConfigurationExtension.swift
//  ShieldConfigurationExtension
//
//  Created by TJ on 23.01.25.
//

import ManagedSettings
import ManagedSettingsUI
import UIKit

// MARK: - UIColor Extension for Hex Initialization
extension UIColor {
    /// Initializes UIColor with a hex string.
    /// - Parameter hex: Hex string (e.g., "#FFFFFF" or "FFFFFF").
    convenience init(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")
        
        var rgb: UInt64 = 0
        Scanner(string: hexSanitized).scanHexInt64(&rgb)
        
        let r = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
        let g = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
        let b = CGFloat(rgb & 0x0000FF) / 255.0
        
        self.init(red: r, green: g, blue: b, alpha: 1.0)
    }
}

// MARK: - ShieldConfigurationExtension Class
class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    
    // MARK: - Properties
    
    /// List of dynamic quotes for the subtitle.
    private let quotes = [
        "Flow like water, steady and sure. Distractions fade, clarity endures.",
        "Stay focused and never give up.",
        "Your concentration is your superpower.",
        "Mindfulness brings clarity and peace.",
        "Stay the course, your goals await."
    ]
    
    /// Name of the icon image in Assets.
    private let iconName = "IconShield"
    
    // MARK: - Helper Methods
    
    /// Retrieves a random quote from the list.
    /// - Returns: A randomly selected quote string.
    private func getRandomQuote() -> String {
        return quotes.randomElement() ?? "Stay focused and never give up."
    }
    
    /// Creates a common shield configuration for all shield types.
    /// - Returns: A configured `ShieldConfiguration` object.
    private func commonShieldConfiguration() -> ShieldConfiguration {
        // Define custom colors using hex codes
        let backgroundColor = UIColor(hex: "#18181A")
        let primaryButtonBackgroundColor = UIColor(hex: "#D7FA00")
        
        // Load the icon image
        let iconImage = UIImage(named: iconName)
        
        // Define the title without a custom font
        let titleLabel = ShieldConfiguration.Label(
            text: "THIS IS JUSTNOISE",
            color: .white // White color for visibility
        )
        
        // Define the subtitle with dynamic quote
        let subtitleText = """
        Your phone is currently Zaped. To access App tap your Zap.
        
        "\(getRandomQuote())"
        """
        let subtitleLabel = ShieldConfiguration.Label(
            text: subtitleText,
            color: .lightGray // Light gray for subtitle text
        )
        
        // Define the primary button label
        let primaryButtonLabel = ShieldConfiguration.Label(
            text: "Keep going",
            color: .black // Black text on yellow button
        )
        
        // Create and return the ShieldConfiguration with the icon
        return ShieldConfiguration(
            backgroundColor: backgroundColor,
            icon: iconImage,
            title: titleLabel,
            subtitle: subtitleLabel,
            primaryButtonLabel: primaryButtonLabel,
            primaryButtonBackgroundColor: primaryButtonBackgroundColor
        )
    }
    
    // MARK: - ShieldConfigurationDataSource Overrides
    
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        return commonShieldConfiguration()
    }
    
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        return commonShieldConfiguration()
    }
    
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        return commonShieldConfiguration()
    }
    
    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        return commonShieldConfiguration()
    }
}

//
//  Analytics.swift
//  JustNoise
//
//  Centralized analytics events for PostHog.
//

import Foundation

@MainActor
enum Analytics {

    /// Track generic event
    static func capture(_ name: String, props: [String: Any]? = nil) {
        JustNoiseAnalyticsRuntime.shared.capture(name, properties: props)
    }

}

//
//  Analytics.swift
//  JustNoise
//
//  Centralized analytics events for PostHog.
//

import Foundation
import PostHog

enum Analytics {

    /// Track generic event
    static func capture(_ name: String, props: [String: Any]? = nil) {
        PostHogSDK.shared.capture(name, properties: props)
    }

}

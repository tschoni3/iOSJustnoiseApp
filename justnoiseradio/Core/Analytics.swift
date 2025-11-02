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

    /// Identify logged-in user
    static func identify(_ id: String, email: String? = nil, plan: String? = nil) {
        var props: [String: Any] = [:]
        if let email = email { props["email"] = email }
        if let plan = plan { props["plan"] = plan }
        PostHogSDK.shared.identify(id, userProperties: props)
    }

    /// Track errors
    static func error(_ type: String, details: String? = nil) {
        var props = ["error_type": type]
        if let details { props["details"] = details }
        capture("error_occurred", props: props)
    }
}

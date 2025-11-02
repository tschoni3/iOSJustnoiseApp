    // SessionAttributes.swift

    import ActivityKit
    import Foundation

    struct SessionAttributes: ActivityAttributes {
        public struct ContentState: Codable, Hashable {
            var startDate: Date
        }

        var modeName: String
    }

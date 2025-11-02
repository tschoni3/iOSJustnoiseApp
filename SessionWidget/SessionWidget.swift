// SessionWidget.swift

import WidgetKit
import SwiftUI
import ActivityKit

// Define SessionAttributes struct
struct SessionAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var isBlocked: Bool
        var elapsedTime: TimeInterval
    }
    var sessionName: String
}

@main
struct SessionWidgetBundle: WidgetBundle {
    var body: some Widget {
        SessionWidget()
    }
}

struct SessionWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SessionAttributes.self) { context in
            // Lock screen/banner UI
            LockScreenView(context: context)
        } dynamicIsland: { context in
            // Dynamic Island UI (for iPhone 14 Pro models)
            DynamicIsland {
                // Expanded view
                DynamicIslandExpandedRegion(.center) {
                    LockScreenView(context: context)
                }
            } compactLeading: {
                // Leading content
                Text("Blocked")
                    .font(.caption)
            } compactTrailing: {
                // Trailing content
                Text(context.state.isBlocked ? "ON" : "OFF")
                    .font(.caption)
            } minimal: {
                // Minimal view
                Text("⏳")
            }
        }
    }
}

// Views used in the widget

struct LockScreenView: View {
    let context: ActivityViewContext<SessionAttributes>
    
    var body: some View {
        VStack {
            Text("Session Duration")
                .font(.headline)
            Text(formattedElapsedTime())
                .font(.largeTitle)
                .bold()
        }
        .padding()
    }
    
    func formattedElapsedTime() -> String {
        let totalSeconds = Int(context.state.elapsedTime)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }
}

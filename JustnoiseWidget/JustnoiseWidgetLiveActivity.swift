// JustNoiseWidgetLiveActivity.swift

import ActivityKit
import WidgetKit
import SwiftUI

@available(iOSApplicationExtension 16.1, *)


struct JustNoiseWidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SessionAttributes.self) { context in
            // Lock Screen and StandBy UI
            VStack(spacing: 6) {
                Text("LOCKED IN FOR")
                    .font(.caption)
                    .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000)) // Changed to D7FA00
                    .multilineTextAlignment(.center) // Align text to the center
                    .textCase(.uppercase) // Converts text to uppercase
                    .padding(.bottom, 2) // Adjust bottom padding to balance spacing
                
                Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                    .font(.custom("Technology-Bold", size: 52))
                    .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000)) // Changed to D7FA00
                    .multilineTextAlignment(.center)
                
                Text(context.attributes.modeName)
                    .font(.caption)
                    .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000)) // Changed to D7FA00
                    .multilineTextAlignment(.center)
                    .textCase(.uppercase) // Converts text to uppercase
            }
            .padding(50)
            .frame(maxWidth: .infinity, alignment: .center)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(red: 0.094, green: 0.094, blue: 0.102))
                
            )
            .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland(
                expanded: {
                    // Expanded Region
                    DynamicIslandExpandedRegion(.center) {
                        VStack(spacing: 12) {
                            Text("LOCKED IN FOR")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.green)
                                .tracking(2)
                                .multilineTextAlignment(.center)
                            
                            
                            Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                                .font(.custom("TechnologyBold", size: 36)) // Using Technology-bold font
                                .foregroundColor(.green)
                                .multilineTextAlignment(.center)
                            
                            Text(context.attributes.modeName)
                                .font(.headline)
                                .foregroundColor(.green)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .center) // Centers content horizontally
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.black)
                        )
                    }
                },
                compactLeading: {
                    Text("🔒")
                        .font(.title2)
                        .foregroundColor(.green)
                        .multilineTextAlignment(.center)
                },
                compactTrailing: {
                    Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                        .font(.custom("TechnologyBold", size: 12)) // Use Technology-bold for compact timer
                        .foregroundColor(.green)
                        .monospacedDigit()
                        .multilineTextAlignment(.center)
                },
                minimal: {
                    Text("🔒")
                        .foregroundColor(.green)
                        .multilineTextAlignment(.center)
                }
            )
            .widgetURL(URL(string: "justnoise://session"))
            .keylineTint(.green)
        }
    }
}

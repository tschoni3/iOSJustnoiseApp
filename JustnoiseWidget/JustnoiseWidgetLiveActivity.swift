// JustNoiseWidgetLiveActivity.swift

import ActivityKit
import WidgetKit
import SwiftUI

@available(iOSApplicationExtension 16.1, *)


struct JustNoiseWidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SessionAttributes.self) { context in
            // Lock Screen and StandBy UI
            HStack(spacing: 20) {
                // Left: Image
                Image("zap_button")
                    .renderingMode(.original)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 72, height: 72)

                // Right: Text
                VStack(alignment: .leading, spacing: 6) {
                    Text("LOCKED IN FOR")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000))
                        .textCase(.uppercase)
                        .padding(.bottom, 2)

                    Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                        .font(.custom("Technology-Bold", size: 52))
                        .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000))
                        .monospacedDigit()

                    Text(context.attributes.modeName)
                        .font(.caption)
                        .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000))
                        .textCase(.uppercase)
                }
            }
            .padding(32)
            .frame(maxWidth: .infinity, alignment: .center)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(red: 0.094, green: 0.094, blue: 0.102))
            )
            .activitySystemActionForegroundColor(.white)
        }
        dynamicIsland: { context in
            DynamicIsland(
                expanded: {
                    DynamicIslandExpandedRegion(.center) {
                        VStack(spacing: 12) {
                            Text("LOCKED IN FOR")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.green)
                                .tracking(2)
                                .multilineTextAlignment(.center)

                            Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                                .font(.custom("TechnologyBold", size: 36))
                                .foregroundColor(.green)
                                .multilineTextAlignment(.center)

                            Text(context.attributes.modeName)
                                .font(.headline)
                                .foregroundColor(.green)
                                .multilineTextAlignment(.center)
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .center)
                        .background(
                            RoundedRectangle(cornerRadius: 20)
                                .fill(Color.black)
                        )
                    }
                },
                compactLeading: {
                    Image("zap_button")
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                },
                compactTrailing: {
                    Text(timerInterval: context.state.startDate...Date.distantFuture, countsDown: false)
                        .font(.custom("TechnologyBold", size: 12))
                        .foregroundColor(.green)
                        .monospacedDigit()
                        .multilineTextAlignment(.center)
                },
                minimal: {
                    Image("zap_button")
                        .renderingMode(.original)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 16, height: 16)
                }
            )
            .widgetURL(URL(string: "justnoise://session"))
            .keylineTint(.green)
        }
    }
}

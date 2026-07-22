// SessionHistoryView.swift

import SwiftUI
import UIKit

// MARK: - View Mode

enum HistoryViewMode: String, CaseIterable, Identifiable {
    case ninetyDays = "90 Days"
    case year = "Year"

    var id: String { rawValue }
}

// MARK: - Main View

struct SessionHistoryView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @Environment(\.dismiss) private var dismiss

    @State private var viewMode: HistoryViewMode = .ninetyDays
    @State private var showSettings = false

    private let customDarkColor = Color(red: 14/255, green: 14/255, blue: 13/255)
    private let summaryLabelAccent = Color.orange.opacity(0.92)

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {

                // Top summary
                let headerHeight: CGFloat = 162
                let cardsGap: CGFloat = 12
                let headerPaddingH: CGFloat = 16
                let headerPaddingTop: CGFloat = 0
                let headerPaddingBottom: CGFloat = 14

                ZStack(alignment: .bottom) {
                    customDarkColor
                        .clipShape(RoundedCorners(radius: 30, corners: [.bottomLeft, .bottomRight]))
                        .frame(height: headerHeight)

                    HStack(spacing: cardsGap) {
                        SummaryCard(
                            title: "Zaps",
                            value: "\(nfcViewModel.sessionHistory.count)",
                            labelColor: summaryLabelAccent
                        )

                        SummaryCard(
                            title: "Protected Attention",
                            value: formattedTotalTime,
                            labelColor: summaryLabelAccent
                        )
                    }
                    .padding(.horizontal, headerPaddingH)
                    .padding(.top, headerPaddingTop)
                    .padding(.bottom, headerPaddingBottom)
                }

                // Dot grid overview
                DayDotGridView(viewMode: viewMode)
                    .environmentObject(nfcViewModel)
            }
            .background(Color.white)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(customDarkColor, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .sheet(isPresented: $showSettings) {
                SettingsView()
                    .environmentObject(nfcViewModel)
                    .environmentObject(subscriptionManager)
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        ForEach(HistoryViewMode.allCases) { mode in
                            Button(mode.rawValue) {
                                viewMode = mode
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text("Timeline")
                                .font(.system(size: 20, weight: .semibold))
                                .foregroundColor(.white)

                            Image(systemName: "chevron.down")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 24) {
                        Button { dismiss() } label: {
                            Image("Justnoise_logo")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 26, height: 26)
                        }

                        Button { showSettings = true } label: {
                            Image(systemName: "gearshape")
                                .imageScale(.large)
                                .foregroundColor(.white)
                        }
                    }
                    .padding(.leading, 8)
                    .padding(.trailing, 10)
                }
            }
        }
    }

    private var formattedTotalTime: String {
        let totalSeconds = Int(
            nfcViewModel.sessionHistory.reduce(0) { $0 + $1.duration }
        )
        let totalMinutes = totalSeconds / 60
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h"
        }

        return "\(minutes)m"
    }
}

// MARK: - Supporting Views

struct RoundedCorners: Shape {
    var radius: CGFloat
    var corners: UIRectCorner

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

struct SummaryCard: View {
    let title: String
    let value: String
    var labelColor: Color = .gray

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(labelColor)
                .lineLimit(2)
                .frame(maxWidth: .infinity, minHeight: 32, maxHeight: 32, alignment: .topLeading)

            Spacer(minLength: 0)

            Text(value)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .minimumScaleFactor(0.82)
                .frame(maxWidth: .infinity, maxHeight: 64, alignment: .bottomLeading)
        }
        .padding()
        .frame(maxWidth: .infinity, minHeight: 128, maxHeight: 128, alignment: .topLeading)
        .background(Color(red: 19/255, green: 19/255, blue: 18/255))
        .cornerRadius(10)
        .shadow(color: .black.opacity(0.5), radius: 5, x: 0, y: 4)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.gray.opacity(0.1), lineWidth: 1)
        )
    }
}

#Preview("History Scroll Test — 40% Year with Mixed Density") {
    let vm = NFCViewModel()
    let calendar = Calendar.current

    let year = calendar.component(.year, from: Date())
    let startOfYear = calendar.date(from: DateComponents(year: year, month: 1, day: 1))!

    // 40% of the year ≈ 146 days
    let dayCount = Int(Double(365) * 0.60)

    // Generate Jan 1 → Day 146 sequence
    var days: [Date] = []
    for offset in 0..<dayCount {
        if let d = calendar.date(byAdding: .day, value: offset, to: startOfYear) {
            days.append(d)
        }
    }

    for date in days {
        let roll = Int.random(in: 1...100)

        let sessionsPerDay: Int
        switch roll {
        case 1...40:
            // 50% empty days
            continue

        case 51...70:
            // 20% light days (1 short session)
            sessionsPerDay = 1

        case 71...90:
            // 20% medium days (1–2 sessions)
            sessionsPerDay = Int.random(in: 1...2)

        default:
            // 10% dense days (2–4 sessions)
            sessionsPerDay = Int.random(in: 2...4)
        }

        for _ in 0..<sessionsPerDay {
            vm.sessionHistory.append(
                Session(
                    startDate: date,
                    duration: TimeInterval(Int.random(in: 10...90) * 60),
                    modeName: ["Focus", "Deep Work", "Light Focus"].randomElement()!,
                    transcription: nil
                )
            )
        }
    }

    return SessionHistoryView()
        .environmentObject(vm)
}

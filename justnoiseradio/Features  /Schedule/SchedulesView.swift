// SchedulesView.swift

import SwiftUI
import FamilyControls

// Monday → Sunday visual order
private let weekdayOrder: [Int] = [2, 3, 4, 5, 6, 7, 1]   // 1 = Sun, 2 = Mon, …, 7 = Sat
private let weekdayShortByWD = ["", "S", "M", "T", "W", "T", "F", "S"] // index 1..7

struct SchedulesView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var showingNewSchedule = false
    @State private var editingSchedule: Schedule?

    var body: some View {
        NavigationStack {
            List {
                // Keep visible so toggling can prompt auth inline
                if AuthorizationCenter.shared.authorizationStatus != .approved {
                    Text("Enable Screen Time access to run schedules automatically.")
                        .foregroundColor(.red)
                        .font(.footnote)
                }

                if nfcViewModel.schedules.isEmpty {
                    Text("No schedules yet. Tap + to create one.")
                        .foregroundColor(.gray)
                        .italic()
                } else {
                    ForEach(Array(nfcViewModel.schedules.enumerated()), id: \.element.id) { index, schedule in
                        let scheduleId = schedule.id

                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(schedule.name)
                                    .font(.headline)

                                scheduleSubtitle(schedule)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)

                                WeekdayDots(selected: Set(schedule.repeatWeekdays))
                                    .opacity(schedule.repeatWeekdays.isEmpty ? 0 : 1)
                            }

                            Spacer()

                            Toggle("", isOn: Binding(
                                get: { nfcViewModel.schedules[index].isEnabled },
                                set: { newValue in
                                    // Optimistic UI
                                    nfcViewModel.schedules[index].isEnabled = newValue
                                    nfcViewModel.saveSchedules()

                                    if newValue {
                                        Task {
                                            do {
                                                try await DeviceActivityBridge.ensureAuthorization()

                                                // Persist the active selection for the monitor (mode, apps, weekdays)
                                                await MainActor.run {
                                                    if let idx = nfcViewModel.schedules.firstIndex(where: { $0.id == scheduleId }) {
                                                        let s = nfcViewModel.schedules[idx]
                                                        SharedSelectionBridge.writeForSchedule(s, allModes: nfcViewModel.modes)
                                                    }
                                                }

                                                // Arm/refresh monitor with latest schedule definition
                                                await MainActor.run {
                                                    if let idx = nfcViewModel.schedules.firstIndex(where: { $0.id == scheduleId }) {
                                                        DeviceActivityBridge.sync(
                                                            schedule: nfcViewModel.schedules[idx],
                                                            allModes: nfcViewModel.modes
                                                        )
                                                    }
                                                }
                                            } catch {
                                                // Revert UI on failure
                                                await MainActor.run {
                                                    if let idx = nfcViewModel.schedules.firstIndex(where: { $0.id == scheduleId }) {
                                                        nfcViewModel.schedules[idx].isEnabled = false
                                                        nfcViewModel.saveSchedules()
                                                    }
                                                }
                                                print("❌ Screen Time auth/arm failed: \(error)")
                                            }
                                        }
                                    } else {
                                        // Disarm the monitor for this schedule
                                        DeviceActivityBridge.stop(scheduleId: scheduleId)
                                    }

                                    // Keep nudges in sync
                                    resyncNudges()
                                }
                            ))
                            .labelsHidden()
                        }
                        .contentShape(Rectangle())
                        .onTapGesture { editingSchedule = schedule }
                    }
                    .onDelete { indexSet in
                        nfcViewModel.deleteSchedule(at: indexSet)
                        resyncNudges()
                    }
                }
            }
            .navigationTitle("Schedules")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showingNewSchedule = true } label: { Image(systemName: "plus") }
                }
            }
            // New schedule
            .sheet(isPresented: $showingNewSchedule, onDismiss: resyncNudges) {
                NewScheduleView()
                    .environmentObject(nfcViewModel)
            }
            // Edit schedule
            .sheet(item: $editingSchedule, onDismiss: {
                // If the edited schedule remains enabled, refresh the persisted selection so the Monitor applies changes.
                if let edited = editingSchedule,
                   let idx = nfcViewModel.schedules.firstIndex(where: { $0.id == edited.id }) {
                    let s = nfcViewModel.schedules[idx]
                    if s.isEnabled {
                        SharedSelectionBridge.writeForSchedule(s, allModes: nfcViewModel.modes)
                        DeviceActivityBridge.sync(schedule: s, allModes: nfcViewModel.modes)
                    }
                }
                resyncNudges()
            }) { schedule in
                NewScheduleView(editingSchedule: schedule)
                    .environmentObject(nfcViewModel)
            }
        }
        .onAppear(perform: resyncNudges)
    }

    // MARK: - Helpers

    private func resyncNudges() {
        NotificationManager.shared.syncPreferredTimeFromSchedules()
        NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
    }

    @ViewBuilder
    private func scheduleSubtitle(_ schedule: Schedule) -> some View {
        let time = schedule.date.formatted(
            date: schedule.repeatWeekdays.isEmpty ? .abbreviated : .omitted,
            time: .shortened
        )
        let modeName = nfcViewModel.modes.first(where: { $0.id == schedule.modeId })?.name ?? "Unknown Mode"
        Text("\(modeName) • \(time)")
            .foregroundColor(modeName == "Unknown Mode" ? .red : .secondary)
    }
}

private struct WeekdayDots: View {
    let selected: Set<Int> // 1..7, where 1 = Sunday

    var body: some View {
        HStack(spacing: 6) {
            ForEach(weekdayOrder, id: \.self) { wd in
                Circle()
                    .fill(selected.contains(wd) ? Color.accentColor : Color.gray.opacity(0.3))
                    .frame(width: 8, height: 8)
                    .overlay(
                        Text(weekdayShortByWD[wd])
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.clear)
                    )
            }
        }
    }
}

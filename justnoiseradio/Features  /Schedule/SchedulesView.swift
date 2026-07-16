//
//  SchedulesView.swift
//
import SwiftUI
import FamilyControls

// Monday → Sunday visual order
private let weekdayOrder: [Int] = [2, 3, 4, 5, 6, 7, 1]   // 1 = Sun, 2 = Mon, …, 7 = Sat
private let weekdayShortByWD = ["", "S", "M", "T", "W", "T", "F", "S"] // index 1..7

struct SchedulesView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @State private var showingNewSchedule = false
    @State private var editingSchedule: Schedule?
    @State private var lastEditedScheduleId: UUID?

    var body: some View {
        NavigationStack {
            List {
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
                    ForEach(nfcViewModel.schedules, id: \.id) { schedule in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(schedule.name).font(.headline)

                                scheduleSubtitle(schedule)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)

                                WeekdayDots(selected: Set(schedule.repeatWeekdays))
                                    .opacity(schedule.repeatWeekdays.isEmpty ? 0 : 1)

                                if let fired = schedule.lastFireDate {
                                    Text("Last used: \(fired.formatted(date: .abbreviated, time: .shortened))")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                            }

                            Spacer()

                            Toggle("",
                                   isOn: Binding(
                                       get: {
                                           nfcViewModel.schedules.first(where: { $0.id == schedule.id })?.isEnabled ?? false
                                       },
                                       set: { newValue in
                                           handleToggle(scheduleId: schedule.id, newValue: newValue)
                                       }
                                   )
                            )
                            .labelsHidden()
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            lastEditedScheduleId = schedule.id
                            editingSchedule = schedule
                        }
                    }
                    .onDelete { indexSet in
                        nfcViewModel.deleteSchedule(at: indexSet)   // saveSchedules() → rebalanceArming() runs inside
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
            // Create
            .sheet(isPresented: $showingNewSchedule, onDismiss: {
                nfcViewModel.consumeScheduleFireMarkers()
                resyncNudges()
            }) {
                NewScheduleView()
                    .environmentObject(nfcViewModel)
            }
            // Edit
            .sheet(item: $editingSchedule, onDismiss: {
                // ❗️No direct sync of the edited schedule here.
                // NewScheduleView(update) already called saveSchedules() → rebalanceArming()
                nfcViewModel.consumeScheduleFireMarkers()
                resyncNudges()
                lastEditedScheduleId = nil
            }) { schedule in
                NewScheduleView(editingSchedule: schedule)
                    .environmentObject(nfcViewModel)
            }
        }
        .onAppear {
            nfcViewModel.consumeScheduleFireMarkers()
            resyncNudges()
        }
    }

    // MARK: - Toggle handling
    private func handleToggle(scheduleId: UUID, newValue: Bool) {
        guard let idx = nfcViewModel.schedules.firstIndex(where: { $0.id == scheduleId }) else { return }
        let schedule = nfcViewModel.schedules[idx]

        // If turning OFF the currently armed schedule while a session is running, defer disable until unblock.
        let activeIdStr = JNShared.suite.string(forKey: SharedKeys.activeScheduleIdKey)
        let isThisScheduleActive = (activeIdStr == schedule.id.uuidString)
        let isSessionRunning = nfcViewModel.isAppsBlocked
        if !newValue && isThisScheduleActive && isSessionRunning {
            nfcViewModel.pendingDisableScheduleId = schedule.id
            return
        }

        if newValue {
            // Enable → clear old fire stamp (so UI shows upcoming), persist, and LET REBALANCER PICK THE WINNER.
            nfcViewModel.schedules[idx].isEnabled = true
            nfcViewModel.clearLastFireIfRearming(schedule.id)
            nfcViewModel.saveSchedules()   // saveSchedules() → DeviceActivityBridge.rebalanceArming(...)
        } else {
            // Disable → persist; rebalancer will stop current and choose next (or clear if none).
            nfcViewModel.schedules[idx].isEnabled = false
            nfcViewModel.saveSchedules()
        }

        // No direct DeviceActivityBridge.sync(...) here. That is the whole fix.
        resyncNudges()
    }

    // MARK: - Helpers
    private func resyncNudges() {
        NotificationManager.shared.syncPreferredTimeFromSchedules()
        NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
        NotificationManager.shared.scheduleDailyStreakSave()

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

// MARK: - Weekday Dots
private struct WeekdayDots: View {
    let selected: Set<Int> // 1..7
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

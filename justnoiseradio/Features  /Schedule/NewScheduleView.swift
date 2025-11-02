import SwiftUI

struct NewScheduleView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.presentationMode) var presentationMode

    @State private var name: String = ""
    @State private var date: Date = Date()                 // full date for one-off; time used for repeats
    @State private var selectedModeId: UUID?               // ⬅️ make selection primitive
    @State private var selectedWeekdays: Set<Weekday> = [] // 1=Sun .. 7=Sat

    var editingSchedule: Schedule?

    // Resolve the selected Mode from the id (or fall back to the VM’s default)
    private var selectedModeResolved: Mode? {
        if let id = selectedModeId {
            return nfcViewModel.modes.first { $0.id == id }
        }
        return nfcViewModel.selectedMode
    }

    // Precompute a lightweight Binding the compiler can handle
    private var modeSelectionBinding: Binding<UUID?> {
        Binding<UUID?>(
            get: { selectedModeId ?? nfcViewModel.selectedMode?.id },
            set: { selectedModeId = $0 }
        )
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("DETAILS")) {
                    TextField("Schedule Name", text: $name)

                    if selectedWeekdays.isEmpty {
                        DatePicker(
                            "Date & Time",
                            selection: $date,
                            in: Date()..., // block past
                            displayedComponents: [.date, .hourAndMinute]
                        )
                    } else {
                        DatePicker("Time", selection: $date, displayedComponents: [.hourAndMinute])
                    }

                    Picker("Mode", selection: modeSelectionBinding) {
                        ForEach(nfcViewModel.modes, id: \.id) { mode in
                            // tag as UUID? to match the Binding<UUID?>
                            Text(mode.name).tag(Optional(mode.id))
                        }
                    }
                }

                Section(header: Text("REPEAT")) {
                    RepeatWeekdayPicker(selected: $selectedWeekdays)
                }

                Text(selectedWeekdays.isEmpty
                     ? "One-time schedule at \(date.formatted(date: .abbreviated, time: .shortened))."
                     : "Repeats on selected weekdays at \(date.formatted(date: .omitted, time: .shortened))."
                )
                .font(.footnote)
                .foregroundColor(.secondary)
            }
            .navigationTitle(editingSchedule == nil ? "New Schedule" : "Edit Schedule")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        guard let mode = selectedModeResolved else { return }

                        var saveDate = dateTrimSeconds(date)

                        if selectedWeekdays.isEmpty {
                            let minAllowed = roundedUpToNextMinute(Date())
                            if saveDate < minAllowed { saveDate = minAllowed }
                        }

                        let schedule = Schedule(
                            id: editingSchedule?.id ?? UUID(),
                            name: name.isEmpty ? "Schedule" : name,
                            modeId: mode.id,
                            date: saveDate,
                            repeatWeekdays: Array(selectedWeekdays.sorted()),
                            isEnabled: true,
                            lastFireDate: editingSchedule?.lastFireDate
                        )

                        if editingSchedule == nil {
                            nfcViewModel.addSchedule(schedule)
                        } else {
                            nfcViewModel.updateSchedule(schedule)
                        }

                        NotificationManager.shared.syncPreferredTimeFromSchedules()
                        NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .onAppear {
                if let s = editingSchedule {
                    name = s.name
                    date = s.date
                    selectedWeekdays = Set(s.repeatWeekdays)
                    selectedModeId = s.modeId
                } else {
                    selectedModeId = nfcViewModel.selectedMode?.id
                }

                if selectedWeekdays.isEmpty {
                    let minAllowed = roundedUpToNextMinute(Date())
                    if date < minAllowed { date = minAllowed }
                }
            }
            // Use single-parameter overloads to avoid extra generic inference
            .onChange(of: selectedWeekdays) { newValue in
                if newValue.isEmpty {
                    let minAllowed = roundedUpToNextMinute(Date())
                    if date < minAllowed { date = minAllowed }
                }
            }
            .onChange(of: date) { newDate in
                if selectedWeekdays.isEmpty {
                    let minAllowed = roundedUpToNextMinute(Date())
                    if newDate < minAllowed { date = minAllowed }
                }
            }
        }
    }

    // MARK: - Helpers

    private func roundedUpToNextMinute(_ d: Date) -> Date {
        let cal = Calendar.current
        let comps = cal.dateComponents([.year,.month,.day,.hour,.minute], from: d.addingTimeInterval(1))
        return cal.date(from: comps) ?? Date()
    }

    private func dateTrimSeconds(_ d: Date) -> Date {
        let cal = Calendar.current
        let comps = cal.dateComponents([.year,.month,.day,.hour,.minute], from: d)
        return cal.date(from: comps) ?? d
    }
}

private struct RepeatWeekdayPicker: View {
    @Binding var selected: Set<Weekday> // 1..7

    private let displayDays: [(label: String, weekday: Weekday)] = [
        ("M", 2), ("T", 3), ("W", 4), ("T", 5), ("F", 6), ("S", 7), ("S", 1)
    ]

    var body: some View {
        HStack(spacing: 10) {
            ForEach(displayDays, id: \.weekday) { item in
                let isOn = selected.contains(item.weekday)
                Circle()
                    .fill(isOn ? Color.accentColor : Color.gray.opacity(0.2))
                    .frame(width: 34, height: 34)
                    .overlay(
                        Text(item.label)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(isOn ? .white : .primary)
                    )
                    .onTapGesture { toggle(item.weekday) }
                    .accessibilityLabel(Text(weekdayFullName(item.weekday)))
                    .accessibilityAddTraits(isOn ? .isSelected : [])
            }
        }
    }

    private func toggle(_ wd: Weekday) {
        if selected.contains(wd) { selected.remove(wd) } else { selected.insert(wd) }
    }

    private func weekdayFullName(_ wd: Weekday) -> String {
        switch wd {
        case 1: return "Sunday"
        case 2: return "Monday"
        case 3: return "Tuesday"
        case 4: return "Wednesday"
        case 5: return "Thursday"
        case 6: return "Friday"
        case 7: return "Saturday"
        default: return "Day"
        }
    }
}

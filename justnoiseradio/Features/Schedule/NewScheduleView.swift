//
//  NewScheduleView.swift
//
import SwiftUI

// Uncomment if not globally defined
// typealias Weekday = Int

struct NewScheduleView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var date: Date = Date()                 // full date for one-off; time used for repeats
    @State private var selectedModeId: UUID?               // primitive selection
    @State private var selectedWeekdays: Set<Weekday> = [] // 1=Sun .. 7=Sat

    private let minuteInterval = 5
    var editingSchedule: Schedule?

    // Resolve the selected Mode from the id (or fall back to the VM’s default)
    private var selectedModeResolved: Mode? {
        if let id = selectedModeId { return nfcViewModel.modes.first { $0.id == id } }
        return nfcViewModel.selectedMode
    }

    // Lightweight binding
    private var modeSelectionBinding: Binding<UUID?> {
        .init(
            get: { selectedModeId ?? nfcViewModel.selectedMode?.id },
            set: { selectedModeId = $0 }
        )
    }

    // MARK: - Body
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("DETAILS")) {
                    TextField("Schedule Name", text: $name)

                    datePickers

                    Picker("Mode", selection: modeSelectionBinding) {
                        ForEach(nfcViewModel.modes, id: \.id) { mode in
                            Text(mode.name).tag(UUID?.some(mode.id))
                        }
                    }
                }

                Section(header: Text("REPEAT")) {
                    RepeatWeekdayPicker(selected: $selectedWeekdays)
                }

                Group {
                    if selectedWeekdays.isEmpty {
                        Text("One-time schedule at \(date.formatted(date: .abbreviated, time: .shortened)).")
                    } else {
                        Text("Repeats on selected weekdays at \(date.formatted(date: .omitted, time: .shortened)).")
                    }
                }
                .font(.footnote)
                .foregroundColor(.secondary)
            }
            .navigationTitle(editingSchedule == nil ? "New Schedule" : "Edit Schedule")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
            }
            .onAppear(perform: hydrate)
        }
    }

    // MARK: - Subviews
    @ViewBuilder private var datePickers: some View {
        if selectedWeekdays.isEmpty {
            // One-time: choose a calendar date, then time on 5-min grid; hide past slots when date is today
            DatePicker(
                "Date",
                selection: Binding<Date>(
                    get: { date },
                    set: { date = $0 }
                ),
                in: Date()...,
                displayedComponents: [.date]
            )
            .datePickerStyle(.compact)

            CustomTimePicker(
                title: "Time",
                date: $date,
                minuteInterval: minuteInterval,
                hidePastToday: true
            )
        } else {
            // Repeating: time only (no "past" concept)
            CustomTimePicker(
                title: "Time",
                date: $date,
                minuteInterval: minuteInterval,
                hidePastToday: false
            )
        }
    }

    // MARK: - Actions
    private func save() {
        guard let mode = selectedModeResolved else { return }

        var saveDate = trimSeconds(date)

        if selectedWeekdays.isEmpty,
           Calendar.current.isDate(saveDate, inSameDayAs: Date()),
           let floor = nextValidSlotCeil(from: Date(), interval: minuteInterval),
           saveDate < floor {
            saveDate = floor
        }

        let isEnabled = editingSchedule?.isEnabled ?? true

        let schedule = Schedule(
            id: editingSchedule?.id ?? UUID(),
            name: name.isEmpty ? "Schedule" : name,
            modeId: mode.id,
            date: saveDate,
            repeatWeekdays: Array(selectedWeekdays.sorted()),
            isEnabled: isEnabled,
            lastFireDate: editingSchedule?.lastFireDate
        )

        if editingSchedule == nil { nfcViewModel.addSchedule(schedule) }
        else { nfcViewModel.updateSchedule(schedule) }


        NotificationManager.shared.syncPreferredTimeFromSchedules()
        NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
        NotificationManager.shared.scheduleDailyStreakSave()

        dismiss()
    }


    private func hydrate() {
        if let s = editingSchedule {
            name = s.name
            date = trimSeconds(s.date)
            selectedWeekdays = Set(s.repeatWeekdays)
            selectedModeId = s.modeId
        } else {
            selectedModeId = nfcViewModel.selectedMode?.id
            date = trimSeconds(date)
        }
    }

    // MARK: - Helpers
    private func trimSeconds(_ d: Date) -> Date {
        let cal = Calendar.current
        var c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: d)
        c.second = 0
        return cal.date(from: c) ?? d
    }

    private func nextValidSlotCeil(from now: Date, interval: Int) -> Date? {
        let cal = Calendar.current
        var c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        if let m = c.minute {
            let remainder = m % interval
            if remainder != 0 { c.minute = m + (interval - remainder) }
        }
        c.second = 0
        return cal.date(from: c)
    }
}


// MARK: - Custom 5-Min Picker (hide past times for today)
private struct CustomTimePicker: View {
    let title: String
    @Binding var date: Date
    let minuteInterval: Int
    let hidePastToday: Bool   // true for one-time (today), false for repeating

    @State private var selectedHour: Int = 0
    @State private var selectedMinute: Int = 0

    private let allHours = Array(0...23)

    private func allMinutes() -> [Int] {
        Array(stride(from: 0, through: 55, by: minuteInterval))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline)
                .foregroundColor(.secondary)

            HStack(spacing: 0) {
                // HOURS
                Picker(selection: $selectedHour, label: Text("Hour")) {
                    ForEach(hourOptions(), id: \.self) { h in
                        Text(String(format: "%02d", h)).tag(h)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity, minHeight: 120)
                .clipped()
                .accessibilityLabel("Hour")

                Text(":")
                    .font(.title3)
                    .monospacedDigit()
                    .frame(width: 16)

                // MINUTES
                Picker(selection: $selectedMinute, label: Text("Minute")) {
                    ForEach(minuteOptions(for: selectedHour), id: \.self) { m in
                        Text(String(format: "%02d", m)).tag(m)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity, minHeight: 120)
                .clipped()
                .accessibilityLabel("Minute")
            }
            .onAppear { syncFromDate(clamp: true) }
            .onChange(of: date) { _, _ in syncFromDate(clamp: true) }
            .onChange(of: selectedHour) { _, _ in applySelectionFromWheels() }
            .onChange(of: selectedMinute) { _, _ in applySelectionFromWheels() }

            if hidePastToday, isToday(date) {
                Text("")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
    }

    // MARK: - Data sources
    private func hourOptions() -> [Int] {
        guard hidePastToday, isToday(date), let floor = nextValidSlotCeil(from: Date()) else {
            return allHours
        }
        let minHour = Calendar.current.component(.hour, from: floor)
        return Array(minHour...23)
    }

    private func minuteOptions(for hour: Int) -> [Int] {
        let base = allMinutes()
        guard hidePastToday, isToday(date), let floor = nextValidSlotCeil(from: Date()) else {
            return base
        }

        let cal = Calendar.current
        let minHour = cal.component(.hour, from: floor)
        let minMinute = cal.component(.minute, from: floor)

        if hour > minHour { return base }
        if hour == minHour { return base.filter { $0 >= minMinute } }
        // hour < minHour should never appear
        return []
    }

    // MARK: - Selection logic
    private func applySelectionFromWheels() {
        let cal = Calendar.current
        var comps = cal.dateComponents([.year, .month, .day], from: date)
        comps.hour = selectedHour
        comps.minute = selectedMinute
        comps.second = 0
        guard var proposed = cal.date(from: comps) else { return }

        // Clamp if user scrolled into a filtered-out combo
        if hidePastToday, isToday(proposed), let floor = nextValidSlotCeil(from: Date()), proposed < floor {
            // Force wheels to first valid slot
            let h = cal.component(.hour, from: floor)
            let m = cal.component(.minute, from: floor)
            if selectedHour != h { selectedHour = h }
            if selectedMinute != m { selectedMinute = m }
            proposed = floor
        }

        date = trimSeconds(proposed)
    }

    private func syncFromDate(clamp: Bool) {
        var d = trimSeconds(date)

        // If today and before the floor, bump to floor
        if clamp, hidePastToday, isToday(d), let floor = nextValidSlotCeil(from: Date()), d < floor {
            d = floor
            date = d
        }

        // Align wheels to current date within allowed options
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)

        // Fix hour if not in options
        let hours = hourOptions()
        if let h = c.hour, hours.contains(h) {
            selectedHour = h
        } else if let first = hours.first {
            selectedHour = first
        }

        // Fix minute if not in options
        let minutes = minuteOptions(for: selectedHour)
        if let m = c.minute, minutes.contains(m) {
            selectedMinute = m
        } else if let first = minutes.first {
            selectedMinute = first
        }
    }

    // MARK: - Utilities
    private func isToday(_ d: Date) -> Bool {
        Calendar.current.isDateInToday(d)
    }

    private func trimSeconds(_ d: Date) -> Date {
        let cal = Calendar.current
        var c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: d)
        c.second = 0
        return cal.date(from: c) ?? d
    }

    private func nextValidSlotCeil(from now: Date) -> Date? {
        let cal = Calendar.current
        var c = cal.dateComponents([.year, .month, .day, .hour, .minute], from: now)
        if let m = c.minute {
            let remainder = m % minuteInterval
            if remainder != 0 { c.minute = m + (minuteInterval - remainder) }
        }
        c.second = 0
        return cal.date(from: c)
    }
}

// MARK: - RepeatWeekdayPicker
private struct RepeatWeekdayPicker: View {
    @Binding var selected: Set<Weekday>

    private let displayDays: [(label: String, weekday: Weekday)] = [
        ("M", 2), ("T", 3), ("W", 4), ("T", 5),
        ("F", 6), ("S", 7), ("S", 1)
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

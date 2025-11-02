// SharedSelectionBridge+App.swift  (Target Membership: App ✅, Extension ❌)
import Foundation
import FamilyControls

extension SharedSelectionBridge {
    /// Convenience wrapper that uses your app models.
    static func writeForSchedule(_ schedule: Schedule, allModes: [Mode]) {
        guard let mode = allModes.first(where: { $0.id == schedule.modeId }) else { return }
        writeActiveSelection(
            modeId: mode.id,
            selection: mode.selectedApps,
            scheduleId: schedule.id
        )
    }
}

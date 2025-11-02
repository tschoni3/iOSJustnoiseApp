// SharedKeys.swift  (Target Membership: App ✅, Extension ✅)
import Foundation
import FamilyControls
import DeviceActivity

enum SharedKeys {
    static let appGroupID           = "group.stilltschoni.Noise"

    // Payload consumed by the monitor at fire time
    static let selectionDataKey     = "jn_active_selection_data"   // Data(FamilyActivitySelection)
    static let activeModeIdKey      = "activeModeId"               // String(UUID)
    static let activeScheduleIdKey  = "activeScheduleId"           // String(UUID)

    // Optional mirrored session state
    static let isAppsBlockedKey     = "isAppsBlocked"
    static let sessionStartKey      = "sessionStartDate"
}

// Shared activity name (used by App + Extension)
enum JNActivityName {
    static let interval = DeviceActivityName("jn.interval")
}

// App group UserDefaults handle
struct JNShared {
    static var suite: UserDefaults { UserDefaults(suiteName: SharedKeys.appGroupID)! }
}

struct SharedSelectionBridge {
    private static var suite: UserDefaults { JNShared.suite }

    /// Write the active selection with only primitives so the extension can read it.
    static func writeActiveSelection(
        modeId: UUID,
        selection: FamilyActivitySelection,
        scheduleId: UUID? = nil
    ) {
        if let data = try? JSONEncoder().encode(selection) {
            suite.set(data, forKey: SharedKeys.selectionDataKey)
        }
        suite.set(modeId.uuidString, forKey: SharedKeys.activeModeIdKey)
        if let scheduleId { suite.set(scheduleId.uuidString, forKey: SharedKeys.activeScheduleIdKey) }
        suite.synchronize()
    }

    static func readSelection() -> FamilyActivitySelection? {
        guard let data = suite.data(forKey: SharedKeys.selectionDataKey) else { return nil }
        return try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
    }

    static func clearActiveSelection() {
        suite.removeObject(forKey: SharedKeys.selectionDataKey)
        suite.removeObject(forKey: SharedKeys.activeModeIdKey)
        suite.removeObject(forKey: SharedKeys.activeScheduleIdKey)
        suite.synchronize()
    }
}

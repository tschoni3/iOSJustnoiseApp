// SharedKeys.swift  (App ✅, Extension ✅)
import Foundation
import FamilyControls
import DeviceActivity

enum SharedKeys {
    static let appGroupID           = "group.stilltschoni.Noise"

    // 🔗 Cross-process (App ↔ Extension)
    static let selectionDataKey     = "jn_active_selection_data"
    static let activeModeIdKey      = "activeModeId"
    static let activeScheduleIdKey  = "activeScheduleId"
    static let isAppsBlockedKey     = "isAppsBlocked"
    static let sessionStartKey      = "sessionStartDate"
    static let plannedStartKey      = "jn_planned_start_epoch"
    static let lastApplyEpochKey    = "jn_last_apply_epoch"
    static let allowedWeekdaysKey   = "jn_allowed_wd"
    static let preferredModeIdKey   = "jn_preferred_mode_id" // ✅ NEW


    // ✅ NEW: fired markers (extension writes, app consumes)
    static let lastFiredScheduleIdKey = "jn_last_fired_schedule_id"
    static let lastFiredEpochKey      = "jn_last_fired_epoch"
    
    static let lastEndedScheduleIdKey = "jn_last_ended_schedule_id"
    static let lastEndedEpochKey      = "jn_last_ended_epoch"
    static let shieldOwnerKey = "jn_shield_owner"  // "app" | "ext" | nil


    // 💡 App-only (local persistence)
    static let activationKey        = "isActivated"
    static let emergencyUnzapKey    = "emergencyUnzapCount"
    static let longestStreakKey     = "jn_longest_streak"
    static let lastStreakCalcKey    = "jn_last_streak_calc_yyyyMMdd"
    static let allSchedulesKey      = "jn_all_schedules_data"
}

enum JNActivityName {
    static let interval = DeviceActivityName("jn.interval")
}

struct JNShared {
    static var suite: UserDefaults { UserDefaults(suiteName: SharedKeys.appGroupID)! }
}


struct SharedSelectionBridge {
    private static var suite: UserDefaults { JNShared.suite }

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

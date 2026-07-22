// SharedKeys.swift  (App ✅, Extension ✅)
import Foundation
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
    /// Cross-process fail-closed latch for the account-deletion boundary. The app sets
    /// this before stopping monitoring and keeps it set until a later authenticated
    /// account explicitly resumes scheduling.
    static let accountDeletionQuiescenceKey = "jn_account_deletion_quiesced_v1"


    // ✅ NEW: fired markers (extension writes, app consumes)
    static let lastFiredScheduleIdKey = "jn_last_fired_schedule_id"
    static let lastFiredEpochKey      = "jn_last_fired_epoch"
    
    static let lastEndedScheduleIdKey = "jn_last_ended_schedule_id"
    static let lastEndedEpochKey      = "jn_last_ended_epoch"
    static let shieldOwnerKey = "jn_shield_owner"  // "app" | "ext" | nil
    static let activeSessionIdKey = "jn_active_session_id_v1"


    // 💡 App-only (local persistence)
    static let activationKey        = "isActivated"
    static let emergencyUnzapKey    = "emergencyUnzapCount"
    static let allSchedulesKey      = "jn_all_schedules_data"
    static let legacySchedulesKey   = "schedules"

    /// Account-owned app-group state that may be removed while the deletion sentinel
    /// itself remains installed. Shared by the app cleaner and monitor extension so a
    /// late extension callback can self-clear without restoring deleted-account state.
    static let accountOwnedTransientKeys: Set<String> = [
        selectionDataKey,
        activeModeIdKey,
        activeScheduleIdKey,
        isAppsBlockedKey,
        sessionStartKey,
        plannedStartKey,
        lastApplyEpochKey,
        allowedWeekdaysKey,
        preferredModeIdKey,
        lastFiredScheduleIdKey,
        lastFiredEpochKey,
        lastEndedScheduleIdKey,
        lastEndedEpochKey,
        shieldOwnerKey,
        allSchedulesKey,
        activeSessionIdKey,
    ]
}

enum JNActivityName {
    static let interval = DeviceActivityName("jn.interval")
}

struct JNShared {
    static var suite: UserDefaults { UserDefaults(suiteName: SharedKeys.appGroupID)! }
}

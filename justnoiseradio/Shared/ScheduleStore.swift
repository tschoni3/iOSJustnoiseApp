import Foundation

/// The single persistence boundary for schedules created by the main app.
///
/// Schedules live in the app group because the Device Activity extension also
/// needs them. The legacy standard-defaults store is read once and migrated so
/// existing App Store users keep their schedules after upgrading.
struct ScheduleStore {
    private let sharedDefaults: UserDefaults
    private let legacyDefaults: UserDefaults

    init(
        sharedDefaults: UserDefaults = JNShared.suite,
        legacyDefaults: UserDefaults = .standard
    ) {
        self.sharedDefaults = sharedDefaults
        self.legacyDefaults = legacyDefaults
    }

    func load() -> [Schedule] {
        if let data = sharedDefaults.data(forKey: SharedKeys.allSchedulesKey),
           let schedules = try? JSONDecoder().decode([Schedule].self, from: data) {
            return schedules
        }

        guard let legacyData = legacyDefaults.data(forKey: SharedKeys.legacySchedulesKey),
              let schedules = try? JSONDecoder().decode([Schedule].self, from: legacyData) else {
            return []
        }

        save(schedules)
        legacyDefaults.removeObject(forKey: SharedKeys.legacySchedulesKey)
        return schedules
    }

    func save(_ schedules: [Schedule]) {
        guard let data = try? JSONEncoder().encode(schedules) else { return }
        sharedDefaults.set(data, forKey: SharedKeys.allSchedulesKey)
        sharedDefaults.synchronize()
    }
}

// DeviceActivityMonitorExtension.swift
import DeviceActivity
import ManagedSettings
import Foundation
import FamilyControls
import OSLog

private let LOG = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "schedule.monitor")

final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let store = ManagedSettingsStore()

    override func intervalDidStart(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }
        guard let ud = UserDefaults(suiteName: SharedKeys.appGroupID) else { return }

        let now = Date()

        // Planned start must exist and must not be in the future (allow tiny skew)
        let plannedEpoch = ud.integer(forKey: SharedKeys.plannedStartKey)
        guard plannedEpoch > 0 else {
            LOG.error("plannedStart missing → skip")
            return
        }
        let planned = Date(timeIntervalSince1970: TimeInterval(plannedEpoch))
        let skewAllowance: TimeInterval = 2
        guard now.addingTimeInterval(skewAllowance) >= planned else {
            LOG.info("Woke early (now=\(now.timeIntervalSince1970, privacy: .public), planned=\(planned.timeIntervalSince1970, privacy: .public)) → skip")
            return
        }

        // Only run on allowed weekdays (if provided)
        if let allowed = ud.array(forKey: SharedKeys.allowedWeekdaysKey) as? [Int], !allowed.isEmpty {
            let wd = Calendar.current.component(.weekday, from: now) // 1=Sun ... 7=Sat
            if allowed.contains(wd) == false {
                LOG.info("Weekday \(wd) not allowed → skip shielding")
                return
            }
        }

        // Need selection payload to apply shields
        guard let data = ud.data(forKey: SharedKeys.selectionDataKey),
              let sel  = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) else {
            LOG.error("No selection payload → skip shielding")
            return
        }

        // Apply shields on main thread
        DispatchQueue.main.async {
            self.store.shield.applications = sel.applicationTokens
            self.store.shield.applicationCategories = .specific(sel.categoryTokens, except: [])
            self.store.shield.webDomains = sel.webDomainTokens
            self.store.shield.webDomainCategories = .specific(sel.categoryTokens, except: [])
        }

        // Mirror ACTIVE state for the app
        ud.set(true, forKey: SharedKeys.isAppsBlockedKey)
        ud.set("ext", forKey: SharedKeys.shieldOwnerKey)

        ud.set(Int(now.timeIntervalSince1970), forKey: SharedKeys.lastApplyEpochKey)

        // Stamp "start" markers (for UI 'Last fired' only; no auto-disable at start)
        if let activeId = ud.string(forKey: SharedKeys.activeScheduleIdKey) {
            ud.set(activeId, forKey: SharedKeys.lastFiredScheduleIdKey)
            ud.set(Int(now.timeIntervalSince1970), forKey: SharedKeys.lastFiredEpochKey)
        }

        ud.synchronize()
        LOG.info("Applied shields at now=\(now.timeIntervalSince1970, privacy: .public)")
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }
        guard let ud = UserDefaults(suiteName: SharedKeys.appGroupID) else { return }

        // ⛔ If app has a running manual session, do NOT clear
        let appHasManualSession = (ud.object(forKey: SharedKeys.sessionStartKey) as? Date) != nil

        // Who owns the shields?
        let owner = ud.string(forKey: SharedKeys.shieldOwnerKey) // "app" | "ext" | nil

        if owner == "ext", appHasManualSession == false {
            // ✅ Extension owns shields and no manual session → safe to clear
            store.shield.applications = nil
            store.shield.applicationCategories = nil
            store.shield.webDomains = nil
            store.shield.webDomainCategories = nil

            ud.set(false, forKey: SharedKeys.isAppsBlockedKey)
            ud.removeObject(forKey: SharedKeys.shieldOwnerKey)
        } else {
            // ❎ App owns shields or manual session active → leave shields alone
            LOG.info("intervalDidEnd: skip clearing (owner=\(owner ?? "nil", privacy: .public), manual=\(appHasManualSession, privacy: .public))")
        }

        // end markers (keep)
        if let activeId = ud.string(forKey: SharedKeys.activeScheduleIdKey) {
            let now = Int(Date().timeIntervalSince1970)
            ud.set(activeId, forKey: SharedKeys.lastEndedScheduleIdKey)
            ud.set(now,     forKey: SharedKeys.lastEndedEpochKey)
        }
        ud.removeObject(forKey: SharedKeys.activeScheduleIdKey)

        // Optional hygiene: clear planned keys for one-offs
        if let allowed = ud.array(forKey: SharedKeys.allowedWeekdaysKey) as? [Int], allowed.isEmpty {
            ud.removeObject(forKey: SharedKeys.plannedStartKey)
            ud.removeObject(forKey: SharedKeys.allowedWeekdaysKey)
            ud.removeObject(forKey: SharedKeys.lastApplyEpochKey)
        }

        ud.synchronize()
    }

}

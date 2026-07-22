// DeviceActivityMonitorExtension.swift
import DeviceActivity
import ManagedSettings
import Foundation
import FamilyControls
import OSLog

private let LOG = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "schedule.monitor")

final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let store = ManagedSettingsStore()

    private func clearAllShields() {
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
        store.shield.webDomainCategories = nil
    }

    private func deletionGate(for defaults: UserDefaults) -> AccountDeletionDeviceActivityGate {
        AccountDeletionDeviceActivityGate(defaults: defaults) {
            self.clearAllShields()
        }
    }

    override func intervalDidStart(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }
        guard let ud = UserDefaults(suiteName: SharedKeys.appGroupID) else { return }
        let gate = deletionGate(for: ud)
        guard gate.continueIfActive() else {
            LOG.info("intervalDidStart: account deletion boundary is quiesced")
            return
        }

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
            let mainGate = self.deletionGate(for: ud)
            guard mainGate.performMutationIfActive({
                self.store.shield.applications = sel.applicationTokens
                self.store.shield.applicationCategories = .specific(sel.categoryTokens, except: [])
                self.store.shield.webDomains = sel.webDomainTokens
                self.store.shield.webDomainCategories = .specific(sel.categoryTokens, except: [])
            }) else {
                LOG.info("intervalDidStart: discarded late shield mutation")
                return
            }
        }

        // Mirror ACTIVE state for the app
        guard gate.setIfActive(true, forKey: SharedKeys.isAppsBlockedKey),
              gate.setIfActive("ext", forKey: SharedKeys.shieldOwnerKey) else { return }

        // ✅ Write a real session start so the app can adopt immediately
        guard gate.setIfActive(now, forKey: SharedKeys.sessionStartKey) else { return }

        guard gate.setIfActive(
            Int(now.timeIntervalSince1970),
            forKey: SharedKeys.lastApplyEpochKey
        ) else { return }

        // Stamp "start" markers (for UI 'Last fired')
        if let activeId = ud.string(forKey: SharedKeys.activeScheduleIdKey) {
            guard gate.setIfActive(activeId, forKey: SharedKeys.lastFiredScheduleIdKey),
                  gate.setIfActive(
                    Int(now.timeIntervalSince1970),
                    forKey: SharedKeys.lastFiredEpochKey
                  ) else { return }
        }

        guard gate.continueIfActive() else { return }
        ud.synchronize()
        LOG.info("Applied shields at now=\(now.timeIntervalSince1970, privacy: .public)")
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }
        guard let ud = UserDefaults(suiteName: SharedKeys.appGroupID) else { return }
        let gate = deletionGate(for: ud)
        guard gate.continueIfActive() else {
            LOG.info("intervalDidEnd: account deletion boundary is quiesced")
            return
        }

        // Who owns the shields?
        let owner = ud.string(forKey: SharedKeys.shieldOwnerKey) // "app" | "ext" | nil
        // If the app has a running/manual session (timestamp present), don't clear from the extension.
        let appHasManualSession = (ud.object(forKey: SharedKeys.sessionStartKey) as? Date) != nil

        if owner == "ext", appHasManualSession == false {
            // ✅ Extension owns shields and no manual session → safe to clear
            guard gate.performMutationIfActive({
                self.clearAllShields()
            }) else { return }

            guard gate.setIfActive(false, forKey: SharedKeys.isAppsBlockedKey),
                  gate.removeIfActive(SharedKeys.shieldOwnerKey) else { return }

            // ✅ Since the extension started it, also clear the adoption start
            guard gate.removeIfActive(SharedKeys.sessionStartKey) else { return }
        } else {
            // ❎ App owns shields or manual session active → leave shields alone
            LOG.info("intervalDidEnd: skip clearing (owner=\(owner ?? "nil", privacy: .public), manual=\(appHasManualSession, privacy: .public))")
        }

        // End markers (keep)
        if let activeId = ud.string(forKey: SharedKeys.activeScheduleIdKey) {
            let now = Int(Date().timeIntervalSince1970)
            guard gate.setIfActive(activeId, forKey: SharedKeys.lastEndedScheduleIdKey),
                  gate.setIfActive(now, forKey: SharedKeys.lastEndedEpochKey) else { return }
        }
        guard gate.removeIfActive(SharedKeys.activeScheduleIdKey) else { return }

        // Optional hygiene: clear planned keys for one-offs
        if let allowed = ud.array(forKey: SharedKeys.allowedWeekdaysKey) as? [Int], allowed.isEmpty {
            guard gate.removeIfActive(SharedKeys.plannedStartKey),
                  gate.removeIfActive(SharedKeys.allowedWeekdaysKey),
                  gate.removeIfActive(SharedKeys.lastApplyEpochKey) else { return }
        }

        guard gate.continueIfActive() else { return }
        ud.synchronize()
    }
}

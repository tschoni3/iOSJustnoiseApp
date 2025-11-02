// DeviceActivityMonitorExtension.swift
import DeviceActivity
import ManagedSettings
import Foundation
import FamilyControls

final class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let store = ManagedSettingsStore()

    override func intervalDidStart(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }

        let ud = UserDefaults(suiteName: SharedKeys.appGroupID)

        var selection: FamilyActivitySelection?
        if let data = ud?.data(forKey: SharedKeys.selectionDataKey) {
            selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        }

        guard let sel = selection else {
            store.shield.applications = nil
            store.shield.applicationCategories = nil
            store.shield.webDomains = nil
            store.shield.webDomainCategories = nil
            return
        }

        store.shield.applications = sel.applicationTokens
        store.shield.applicationCategories = .specific(sel.categoryTokens, except: [])
        store.shield.webDomains = sel.webDomainTokens
        store.shield.webDomainCategories = .specific(sel.categoryTokens, except: [])
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        guard activity == JNActivityName.interval else { return }
        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
        store.shield.webDomainCategories = nil
    }
}

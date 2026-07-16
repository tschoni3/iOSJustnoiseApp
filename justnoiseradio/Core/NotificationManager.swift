//
//  NotificationManager.swift
//

import Foundation
import UserNotifications

// MARK: - Deep links
enum DeepLinkType: String {
    case startSession
    case streakSave
}

extension Notification.Name {
    static let didReceiveDeepLink = Notification.Name("didReceiveDeepLink")
}

// MARK: - Notification payload keys
private enum NKeys {
    static let deeplink = "deeplink"
    static let modeId   = "modeId"
}

private enum NIDs {
    static let preSessionNudge = "pre-session-nudge"
    static let streakSave = "streak-save"
}

// MARK: - User preference keys
private enum NPrefs {
    static let preSessionLeadMinutesKey = "preSessionLeadMinutes"
    static let preferredSessionTimeKey  = "preferredSessionTime"
}

// MARK: - Daily quota (still 1/day)
private enum NQuota {
    static let lastSentKey = "lastNotificationSentYYYYMMDD"

    static func todayKey(for date: Date = Date()) -> String {
        let d = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", d.year ?? 0, d.month ?? 0, d.day ?? 0)
    }
    static func markSentToday() {
        UserDefaults.standard.set(todayKey(), forKey: lastSentKey)
    }
    static func hasSentToday() -> Bool {
        UserDefaults.standard.string(forKey: lastSentKey) == todayKey()
    }
}

// MARK: - Lightweight stores
private struct ScheduleStore {
    static func load() -> [Schedule] {
        guard let data = UserDefaults.standard.data(forKey: "schedules"),
              let saved = try? JSONDecoder().decode([Schedule].self, from: data) else {
            return []
        }
        return saved.filter { $0.isEnabled }
    }
}

private struct ModeNameStore {
    struct Item: Codable { let id: UUID; let name: String }
    static func name(for id: UUID) -> String? {
        if let d = UserDefaults.standard.data(forKey: "modes"),
           let items = try? JSONDecoder().decode([Item].self, from: d),
           let m = items.first(where: { $0.id == id }) {
            return m.name
        }
        if let d = UserDefaults.standard.data(forKey: "modeNames"),
           let items = try? JSONDecoder().decode([Item].self, from: d),
           let m = items.first(where: { $0.id == id }) {
            return m.name
        }
        return nil
    }
}

// MARK: - Preferred context picker
private struct PreferredContextPicker {
    static func scheduleDefiningPreferredNow() -> Schedule? {
        let cal = Calendar.current
        let schedules = ScheduleStore.load()
        
        let repeating = schedules.filter { !$0.repeatWeekdays.isEmpty }
        if let s = repeating.sorted(by: {
            let a = cal.dateComponents([.hour, .minute], from: $0.date)
            let b = cal.dateComponents([.hour, .minute], from: $1.date)
            return (a.hour ?? 0, a.minute ?? 0) < (b.hour ?? 0, b.minute ?? 0)
        }).first {
            return s
        }
        
        let now = Date()
        return schedules
            .filter { $0.repeatWeekdays.isEmpty && $0.date > now }
            .sorted(by: { $0.date < $1.date })
            .first
    }
}

// MARK: - NotificationManager
final class NotificationManager: NSObject, UNUserNotificationCenterDelegate {

    static let shared = NotificationManager()
    private override init() { super.init() }

    // MARK: - Public

    func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            center.getNotificationSettings { settings in
                print("📣 Notification Settings:",
                      "auth=\(settings.authorizationStatus.rawValue)",
                      "alert=\(settings.alertSetting.rawValue)",
                      "sound=\(settings.soundSetting.rawValue)")
                if let error { print("❌ Authorization error: \(error)") }
            }
        }
    }

    func cancelAllScheduledSmartNotifications() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: [
                NIDs.preSessionNudge,
                NIDs.streakSave
            ]
        )
    }

    func cancelNoiseRewindNotifications() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: ["noise-rewind-ready"]
        )
    }

    // MARK: 1) Pre-Session Nudge
    func scheduleDailyPreSessionNudgeIfNeeded() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [NIDs.preSessionNudge])

        let now = Date()
        guard let preferred = Self.nextPreferredSessionDate(after: now) else {
            print("ℹ️ No preferred session time set → skip pre-session nudge.")
            return
        }

        let candidate = preferred.addingTimeInterval(TimeInterval(-preSessionLeadMinutes * 60))
        let fire = candidate > now
            ? candidate
            : preferred.addingTimeInterval(86400).addingTimeInterval(TimeInterval(-preSessionLeadMinutes * 60))

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)

        let contextSchedule = PreferredContextPicker.scheduleDefiningPreferredNow()
        let scheduleName = contextSchedule?.name
        let modeId = contextSchedule?.modeId
        let modeName = modeId.flatMap { ModeNameStore.name(for: $0) }

        let content = UNMutableNotificationContent()
        content.title = scheduleName != nil
            ? "⚡ \(scheduleName!) starts in \(preSessionLeadMinutes) min"
            : "⚡ Focus starts in \(preSessionLeadMinutes) min"
        content.body = modeName ?? ""
        content.sound = .default

        var info: [String: Any] = [NKeys.deeplink: DeepLinkType.startSession.rawValue]
        if let modeId { info[NKeys.modeId] = modeId.uuidString }
        content.userInfo = info

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: NIDs.preSessionNudge, content: content, trigger: trigger)
        )
    }

    // MARK: 2) Streak Save (now tied to preferred time)
    func scheduleDailyStreakSave() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [NIDs.streakSave])

        guard let preferred = Self.nextPreferredSessionDate(after: Date()) else {
            print("ℹ️ No preferred time → skipping streak-save.")
            return
        }

        let fireDate = preferred.addingTimeInterval(-30 * 60)
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate)

        let streak = Self.currentStreakFromStorage()

        let content = UNMutableNotificationContent()
        content.title = streak > 0
            ? "⏳ Don’t lose your 🔥 \(streak)-day streak"
            : "⏳ Save your streak"
        content.body = "Even 5 minutes counts — tap to focus."
        content.sound = .default
        content.userInfo = [NKeys.deeplink: DeepLinkType.streakSave.rawValue]

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        UNUserNotificationCenter.current().add(
            UNNotificationRequest(identifier: NIDs.streakSave, content: content, trigger: trigger)
        )
    }

    func refreshStreakSaveForToday(hasSessionToday: Bool) { /* noop */ }

    // MARK: - Delegate
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {

        let id = notification.request.identifier

        if NQuota.hasSentToday() {
            completion([]); return
        }

        if id == NIDs.streakSave, Self.didLogSessionToday() {
            completion([]); return
        }

        NQuota.markSentToday()
        completion([.banner, .sound, .list])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completion: @escaping () -> Void) {

        let info = response.notification.request.content.userInfo
        guard let raw = info[NKeys.deeplink] as? String,
              let type = DeepLinkType(rawValue: raw) else {
            completion(); return
        }

        var payload: [String: Any] = ["type": type]
        if let modeId = (info[NKeys.modeId] as? String).flatMap(UUID.init(uuidString:)) {
            payload[NKeys.modeId] = modeId
        }

        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .didReceiveDeepLink, object: nil, userInfo: payload)
        }

        completion()
    }

    // MARK: - Preferred session time
    func syncPreferredTimeFromSchedules() {
        let cal = Calendar.current
        let schedules = ScheduleStore.load()

        let repeating = schedules.filter { !$0.repeatWeekdays.isEmpty }
        if let date = repeating
            .compactMap({ cal.dateComponents([.hour, .minute], from: $0.date) })
            .compactMap({ cal.date(bySettingHour: $0.hour!, minute: $0.minute!, second: 0, of: Date()) })
            .sorted().first {
            UserDefaults.standard.set(date, forKey: NPrefs.preferredSessionTimeKey)
            return
        }

        let now = Date()
        if let oneOff = schedules.filter({ $0.repeatWeekdays.isEmpty && $0.date > now }).sorted(by: { $0.date < $1.date }).first {
            let hm = cal.dateComponents([.hour, .minute], from: oneOff.date)
            if let date = cal.date(bySettingHour: hm.hour!, minute: hm.minute!, second: 0, of: now) {
                UserDefaults.standard.set(date, forKey: NPrefs.preferredSessionTimeKey)
                return
            }
        }

        UserDefaults.standard.removeObject(forKey: NPrefs.preferredSessionTimeKey)
    }

    private static func nextPreferredSessionDate(after now: Date) -> Date? {
        guard let stored = UserDefaults.standard.object(forKey: NPrefs.preferredSessionTimeKey) as? Date else { return nil }
        let cal = Calendar.current
        let hm = cal.dateComponents([.hour, .minute], from: stored)
        var todayAt = cal.date(bySettingHour: hm.hour!, minute: hm.minute!, second: 0, of: now)!
        if todayAt <= now { todayAt = cal.date(byAdding: .day, value: 1, to: todayAt)! }
        return todayAt
    }

    static func didLogSessionToday() -> Bool {
        guard let data = UserDefaults.standard.data(forKey: "sessionHistory"),
              let sessions = try? JSONDecoder().decode([Session].self, from: data) else { return false }
        let cal = Calendar.current
        return sessions.contains { cal.isDateInToday($0.startDate) }
    }

    private static func currentStreakFromStorage(now: Date = Date()) -> Int {
        guard let data = UserDefaults.standard.data(forKey: "sessionHistory"),
              let sessions = try? JSONDecoder().decode([Session].self, from: data) else { return 0 }
        let cal = Calendar.current
        let today = cal.startOfDay(for: now)

        func hasSession(on day: Date) -> Bool {
            sessions.contains { cal.isDate(cal.startOfDay(for: $0.startDate), inSameDayAs: day) }
        }

        var count = 0
        var cursor = today
        while hasSession(on: cursor) {
            count += 1
            cursor = cal.date(byAdding: .day, value: -1, to: cursor)!
        }
        return count
    }

    // MARK: - Config
    private var preSessionLeadMinutes: Int {
        let v = UserDefaults.standard.integer(forKey: NPrefs.preSessionLeadMinutesKey)
        return v > 0 ? v : 15
    }
}

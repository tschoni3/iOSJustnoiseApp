

//
//  NotificationManager.swift
//

import Foundation
import UserNotifications

// MARK: - Deep links you’ll use from notifications
enum DeepLinkType: String {
    case startSession            // open app with Start Session highlighted (optionally for a modeId)
    case streakSave              // open app with focus mode preselected
}

extension Notification.Name {
    static let didReceiveDeepLink = Notification.Name("didReceiveDeepLink")
}

// MARK: - Notification payload keys
private enum NKeys {
    static let deeplink = "deeplink"
    static let modeId   = "modeId"
}

// MARK: - User preference keys
private enum NPrefs {
    static let preSessionLeadMinutesKey = "preSessionLeadMinutes"      // Int, default 15
    static let preferredSessionTimeKey  = "preferredSessionTime"       // Date (hour/minute used)
}

// MARK: - Daily notification quota
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

/// Reads schedules saved by your NFCViewModel
private struct ScheduleStore {
    static func load() -> [Schedule] {
        guard let data = UserDefaults.standard.data(forKey: "schedules"),
              let saved = try? JSONDecoder().decode([Schedule].self, from: data) else {
            return []
        }
        return saved.filter { $0.isEnabled }
    }
}

/// Best-effort mode-name lookup (non-breaking if not present).
/// It tries two common keys and expects items with { id: UUID, name: String }.
private struct ModeNameStore {
    struct Item: Codable { let id: UUID; let name: String }
    static func name(for id: UUID) -> String? {
        // Try "modes" (full objects) first
        if let d = UserDefaults.standard.data(forKey: "modes"),
           let items = try? JSONDecoder().decode([Item].self, from: d),
           let m = items.first(where: { $0.id == id }) {
            return m.name
        }
        // Try a lighter cache "modeNames"
        if let d = UserDefaults.standard.data(forKey: "modeNames"),
           let items = try? JSONDecoder().decode([Item].self, from: d),
           let m = items.first(where: { $0.id == id }) {
            return m.name
        }
        return nil
    }
}

// MARK: - Pick the schedule that defines the preferred time (for title/body context)
private struct PreferredContextPicker {
    /// Rule:
    /// 1) Earliest enabled repeating schedule's **time-of-day**
    /// 2) Else earliest future one-off (time-of-day)
    /// Returns the chosen schedule (to access its name + modeId).
    static func scheduleDefiningPreferredNow() -> Schedule? {
        let cal = Calendar.current
        let schedules = ScheduleStore.load()
        // repeating first
        let repeating = schedules.filter { !$0.repeatWeekdays.isEmpty }
        if let s = repeating.sorted(by: { lhs, rhs in
            let a = cal.dateComponents([.hour, .minute], from: lhs.date)
            let b = cal.dateComponents([.hour, .minute], from: rhs.date)
            return (a.hour ?? 0, a.minute ?? 0) < (b.hour ?? 0, b.minute ?? 0)
        }).first {
            return s
        }
        // else earliest future one-off
        let now = Date()
        return schedules
            .filter { $0.repeatWeekdays.isEmpty && $0.date > now }
            .sorted(by: { $0.date < $1.date })
            .first
    }
}

/// Singleton notification manager (also set as UNUserNotificationCenter delegate)
final class NotificationManager: NSObject, UNUserNotificationCenterDelegate {

    static let shared = NotificationManager()

    private override init() {
        super.init()
    }

    // MARK: - Public API

    /// Ask once and log what iOS actually granted (helps support).
    func requestAuthorization() {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, error in
            center.getNotificationSettings { settings in
                print("📣 Notification Settings:",
                      "auth=\(settings.authorizationStatus.rawValue) (0=notDetermined,1=denied,2=provisional,3=authorized,4=ephemeral)",
                      "alert=\(settings.alertSetting.rawValue)",
                      "sound=\(settings.soundSetting.rawValue)")
                if let error = error { print("❌ requestAuthorization error: \(error)") }
                print("✅ requestAuthorization granted=\(granted)")
            }
        }
    }

    /// Clear our scheduled smart notifications (does not clear other app notifications).
    func cancelAllScheduledSmartNotifications() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [
            "pre-session-nudge",
            "streak-save"
        ])
    }

    // MARK: 1) Pre-Session Nudge — lead minutes before preferred time (with schedule/mode names)

    /// Schedule ONE nudge for the next preferred session time minus lead.
    /// Call on app launch and whenever the preferred time or lead changes.
    func scheduleDailyPreSessionNudgeIfNeeded() {
        // Clear existing one-shot nudge
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["pre-session-nudge"])

        let now = Date()
        guard let preferred = Self.nextPreferredSessionDate(after: now) else {
            print("ℹ️ No preferred session time set → skipping pre-session nudge.")
            return
        }

        // Compute fire date (today/tomorrow) minus lead
        let candidate = preferred.addingTimeInterval(TimeInterval(-preSessionLeadMinutes * 60))
        let fire = candidate > now
            ? candidate
            : preferred.addingTimeInterval(24 * 3600).addingTimeInterval(TimeInterval(-preSessionLeadMinutes * 60))

        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire)

        // Context: schedule & mode names
        let contextSchedule = PreferredContextPicker.scheduleDefiningPreferredNow()
        let scheduleName = contextSchedule?.name
        let modeId = contextSchedule?.modeId
        let modeName = modeId.flatMap { ModeNameStore.name(for: $0) }

        // Notification content
        let content = UNMutableNotificationContent()
        if let scheduleName {
            content.title = "⚡ \(scheduleName) starts in \(preSessionLeadMinutes) min"
        } else {
            content.title = "⚡ Focus starts in \(preSessionLeadMinutes) min"
        }
        if let modeName {
            content.body = "Mode: \(modeName)"
        } else {
            content.body = ""
        }
        content.sound = .default

        // Deep link + pass modeId so UI can preselect mode
        var info: [String: Any] = [ NKeys.deeplink: DeepLinkType.startSession.rawValue ]
        if let modeId { info[NKeys.modeId] = modeId.uuidString }
        content.userInfo = info

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: "pre-session-nudge", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req)
    }

    // MARK: 2) Streak Save — one-shot daily at 18:00 with dynamic title

    /// Schedules a single Streak Save for the next 18:00 with the *current* streak in the title.
    /// Call this on app launch and when the app returns to foreground (so it refreshes daily).
    func scheduleDailyStreakSave() {
        // Remove any previously scheduled one-shot
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["streak-save"])

        let fireDate = nextSixPM()
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: fireDate)

        // Compute streak number now (reads from persisted sessionHistory)
        let streak = Self.currentStreakFromStorage()
        
        let content = UNMutableNotificationContent()
        if streak > 0 {
            content.title = "⏳ Don’t lose your 🔥 \(streak)-day streak"
            content.body  = "Even 5 minutes counts — tap to focus now."
        } else {
            content.title = "⏳ Don’t lose your streak"
            content.body  = "Even 5 minutes counts — tap to focus now."
        }
        content.sound = .default
        content.userInfo = [ NKeys.deeplink: DeepLinkType.streakSave.rawValue ]

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(identifier: "streak-save", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req)
    }

    /// Optional: kept for symmetry; suppression happens in willPresent().
    func refreshStreakSaveForToday(hasSessionToday: Bool) { /* noop for now */ }

    // MARK: - Delegate: present + tap

    /// Foreground presentation rules: daily cap + session-aware suppression.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {

        let id = notification.request.identifier

        // Suppress if we've already allowed one notification today
        if NQuota.hasSentToday() {
            completion([]); return
        }

        // Suppress streak if a session is already logged today
        if id == "streak-save", Self.didLogSessionToday() {
            completion([]); return
        }

        // Present + burn today's quota on delivery
        NQuota.markSentToday()
        completion([.banner, .sound, .list])
    }

    /// Deep-link on tap.
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

    // MARK: - Preferred time derivation from Schedules

    /// Compute and persist preferred time from the saved schedules.
    /// Rule: use the earliest **enabled repeating** schedule's time-of-day; if none, use earliest enabled one-off in the future.
    func syncPreferredTimeFromSchedules() {
        let cal = Calendar.current
        let schedules = ScheduleStore.load()

        // 1) enabled repeating schedules -> pick earliest time-of-day
        let repeating = schedules.filter { !$0.repeatWeekdays.isEmpty }
        if let date = repeating
            .compactMap({ cal.dateComponents([.hour, .minute], from: $0.date) })
            .compactMap({ cal.date(bySettingHour: $0.hour ?? 9, minute: $0.minute ?? 0, second: 0, of: Date()) })
            .sorted().first {
            UserDefaults.standard.set(date, forKey: NPrefs.preferredSessionTimeKey)
            return
        }

        // 2) fallback: earliest enabled one-off in future (take its time-of-day)
        let now = Date()
        if let oneOff = schedules.filter({ $0.repeatWeekdays.isEmpty && $0.date > now }).sorted(by: { $0.date < $1.date }).first {
            let hm = cal.dateComponents([.hour, .minute], from: oneOff.date)
            if let date = cal.date(bySettingHour: hm.hour ?? 9, minute: hm.minute ?? 0, second: 0, of: now) {
                UserDefaults.standard.set(date, forKey: NPrefs.preferredSessionTimeKey)
                return
            }
        }

        // 3) none found -> clear preferred
        UserDefaults.standard.removeObject(forKey: NPrefs.preferredSessionTimeKey)
    }

    /// Return the next occurrence of the preferred daily time (today or tomorrow).
    private static func nextPreferredSessionDate(after now: Date) -> Date? {
        guard let stored = UserDefaults.standard.object(forKey: NPrefs.preferredSessionTimeKey) as? Date else { return nil }
        let cal = Calendar.current
        let hm  = cal.dateComponents([.hour, .minute], from: stored)
        guard var todayAt = cal.date(bySettingHour: hm.hour ?? 9, minute: hm.minute ?? 0, second: 0, of: now) else { return nil }
        if todayAt <= now { todayAt = cal.date(byAdding: .day, value: 1, to: todayAt)! }
        return todayAt
    }

    /// True if at least one session exists with startDate = today.
    static func didLogSessionToday() -> Bool {
        guard let data = UserDefaults.standard.data(forKey: "sessionHistory"),
              let sessions = try? JSONDecoder().decode([Session].self, from: data) else { return false }
        let cal = Calendar.current
        return sessions.contains { cal.isDateInToday($0.startDate) }
    }

    // MARK: - Streak helpers (reads from persisted sessionHistory)

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
            guard let prev = cal.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = prev
        }
        return count
    }

    // Next 18:00 local (today if in future, else tomorrow)
    private func nextSixPM(after now: Date = Date()) -> Date {
        let cal = Calendar.current
        var components = cal.dateComponents([.year, .month, .day], from: now)
        components.hour = 18; components.minute = 0; components.second = 0
        let todaySix = cal.date(from: components)!
        return (todaySix > now) ? todaySix : cal.date(byAdding: .day, value: 1, to: todaySix)!
    }

    // MARK: - Config

    private var preSessionLeadMinutes: Int {
        let v = UserDefaults.standard.integer(forKey: NPrefs.preSessionLeadMinutesKey)
        return v > 0 ? v : 15 // default 15
    }
}

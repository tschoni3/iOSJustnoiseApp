//
//  DeviceActivityBridge.swift
//
import Foundation
import DeviceActivity
import FamilyControls
import OSLog

// MARK: - Loggers
private let BRIDGE_LOG = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "bridge")
private let SCHED      = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "schedule.arm")

enum BridgeError: Error { case notApproved }

enum DeviceActivityBridge {

    // MARK: - Coalescing / State
    private static let queue = DispatchQueue(label: "devactivity.rearm", qos: .userInitiated)
    private static var pendingWork: DispatchWorkItem?
    private static var lastArmedScheduleId: UUID?
    private static var lastArmedEpoch: Int = 0

    /// Minimum seconds the start time must be in the future to avoid framework flakiness
    private static let minLeadSeconds: TimeInterval = 90

    // MARK: Authorization
    static func ensureAuthorization() async throws {
        let center = AuthorizationCenter.shared
        if center.authorizationStatus != .approved {
            try await center.requestAuthorization(for: .individual)
        }
        guard center.authorizationStatus == .approved else { throw BridgeError.notApproved }
    }

    // MARK: Next fire calculation (with lead-time guard)
    private static func nextFireDate(for schedule: Schedule, now: Date = Date()) -> Date {
        let cal = Calendar.current
        let comps = cal.dateComponents([.year, .month, .day, .hour, .minute], from: schedule.date)
        let h = comps.hour ?? 9, m = comps.minute ?? 0

        let strictlyFuture: (Date) -> Date = { candidate in
            // Ensure at least minLeadSeconds in the future
            if candidate.timeIntervalSince(now) < minLeadSeconds {
                // bump to the next minute boundary with minLeadSeconds buffer
                let bump = now.addingTimeInterval(Self.minLeadSeconds)
                return cal.date(bySettingHour: cal.component(.hour, from: bump),
                                minute: cal.component(.minute, from: bump),
                                second: 0, of: bump)!
            }
            return candidate
        }

        if !schedule.repeatWeekdays.isEmpty {
            let allowed = Set(schedule.repeatWeekdays) // 1..7 (Sun..Sat)
            for i in 0..<7 {
                guard let day = cal.date(byAdding: .day, value: i, to: now) else { continue }
                let wd = cal.component(.weekday, from: day)
                let candidate = cal.date(bySettingHour: h, minute: m, second: 0, of: day)!
                if allowed.contains(wd) && candidate > now {
                    return strictlyFuture(candidate)
                }
            }
            // Fallback: 1 week ahead at (h:m).
            let next = cal.nextDate(after: now,
                                    matching: DateComponents(hour: h, minute: m),
                                    matchingPolicy: .nextTime,
                                    direction: .forward)!
            return strictlyFuture(cal.date(byAdding: .day, value: 7, to: next)!)
        } else {
            // ONE-OFF: honor the chosen date/time, but not in the past.
            let chosen = cal.date(from: comps)! // yyyy-MM-dd HH:mm (local)
            if chosen > now { return strictlyFuture(chosen) }
            // Roll to tomorrow same time, then guard lead time.
            let nextDay = cal.date(byAdding: .day, value: 1, to: cal.startOfDay(for: now))!
            let tmrw = cal.date(bySettingHour: h, minute: m, second: 0, of: nextDay)!
            return strictlyFuture(tmrw)
        }
    }

    // MARK: Low-level arming (no stop here; caller decides)
    // DeviceActivityBridge.swift — replace the entire `sync(...)` body with this version

    private static func sync(schedule: Schedule, allModes: [Mode], overrideFire: Date? = nil) {
        let center = DeviceActivityCenter()

        let cal  = Calendar.current
        let fire = overrideFire ?? nextFireDate(for: schedule)
        let fireEpoch = Int(fire.timeIntervalSince1970)

        // Persist selection/context for the extension
        let suite = JNShared.suite
        suite.set(fireEpoch,                        forKey: SharedKeys.plannedStartKey)
        suite.set(schedule.repeatWeekdays,          forKey: SharedKeys.allowedWeekdaysKey)
        suite.set(schedule.id.uuidString,           forKey: SharedKeys.activeScheduleIdKey)

        // ✅ NEW: write active mode + selection so the extension knows WHAT to shield
        if let mode = allModes.first(where: { $0.id == schedule.modeId }) {
            if let data = try? JSONEncoder().encode(mode.selectedApps) {
                suite.set(data, forKey: SharedKeys.selectionDataKey)
            }
            suite.set(mode.id.uuidString, forKey: SharedKeys.activeModeIdKey)
        } else {
            BRIDGE_LOG.error("ARM ⚠️ No mode found for schedule \(schedule.id.uuidString, privacy: .public)")
            // We still arm time-wise, but the extension will skip shielding if selection is missing.
        }

        suite.synchronize()

        // Narrow window: fire .. fire+20m (clamped to same day)
        let endCandidate = cal.date(byAdding: .minute, value: 20, to: fire)!
        let startHour = cal.component(.hour,   from: fire)
        let startMin  = cal.component(.minute, from: fire)

        var endHour = cal.component(.hour,   from: endCandidate)
        var endMin  = cal.component(.minute, from: endCandidate)

        if cal.isDate(endCandidate, inSameDayAs: fire) == false {
            endHour = 23; endMin = 59
        }

        let intervalStart = DateComponents(hour: startHour, minute: startMin, second: 0)
        let intervalEnd   = DateComponents(hour: endHour,   minute: endMin,   second: 0)

        do {
            try center.startMonitoring(
                JNActivityName.interval,
                during: DeviceActivitySchedule(
                    intervalStart: intervalStart,
                    intervalEnd: intervalEnd,
                    repeats: !schedule.repeatWeekdays.isEmpty
                )
            )
            lastArmedScheduleId = schedule.id
            lastArmedEpoch      = fireEpoch
            SCHED.info("ARM ✅ fire=\(fire.timeIntervalSince1970, privacy: .public) start=\(startHour):\(startMin) end=\(endHour):\(endMin) repeats=\(!schedule.repeatWeekdays.isEmpty)")
        } catch {
            BRIDGE_LOG.error("ARM ❌ \(String(describing: error), privacy: .public)")
        }
    }


    private static func clearArmMarkersAndStop() {
        let center = DeviceActivityCenter()
        center.stopMonitoring([JNActivityName.interval])

        let suite = JNShared.suite
        suite.removeObject(forKey: SharedKeys.plannedStartKey)
        suite.removeObject(forKey: SharedKeys.activeScheduleIdKey)
        suite.removeObject(forKey: SharedKeys.allowedWeekdaysKey)
        suite.removeObject(forKey: SharedKeys.lastApplyEpochKey)
        suite.synchronize()

        lastArmedScheduleId = nil
        lastArmedEpoch      = 0
    }

    // MARK: - Coordinator: pick & arm the earliest upcoming enabled schedule
    private static func pickEarliestEnabled(_ schedules: [Schedule], now: Date = Date()) -> (schedule: Schedule, fire: Date)? {
        let enabled = schedules.filter { $0.isEnabled }
        guard !enabled.isEmpty else { return nil }
        var best: (Schedule, Date)? = nil
        for s in enabled {
            let f = nextFireDate(for: s, now: now)
            guard f > now else { continue }
            if let cur = best {
                if f < cur.1 { best = (s, f) }
            } else {
                best = (s, f)
            }
        }
        return best
    }

    /// Coalesced, idempotent rebalance. Safe to call many times; it will arm at most once.
    static func rebalanceArming(schedules: [Schedule], allModes: [Mode], isSessionRunning: Bool? = nil) {
        // Double-guard: param OR shared flag
        let running = isSessionRunning ?? JNShared.suite.bool(forKey: SharedKeys.isAppsBlockedKey)
        if running {
            BRIDGE_LOG.info("REBALANCE ⏸️ session active → skip arming")
            return
        }

        // Debounce rapid callers (unchanged)
        queue.async {
            pendingWork?.cancel()
            let work = DispatchWorkItem { _rebalanceArmingCoalesced(schedules: schedules, allModes: allModes) }
            pendingWork = work
            queue.asyncAfter(deadline: .now() + 0.30, execute: work)
        }
    }

    private static func _rebalanceArmingCoalesced(schedules: [Schedule], allModes: [Mode]) {
        let now = Date()
        let suite = JNShared.suite

        // Pick candidate
        guard let pick = pickEarliestEnabled(schedules, now: now) else {
            // Nothing to arm → ensure we’re stopped and cleared.
            clearArmMarkersAndStop()
            return
        }

        let desiredEpoch = Int(pick.fire.timeIntervalSince1970)
        let plannedEpoch = suite.integer(forKey: SharedKeys.plannedStartKey)
        let activeIdStr  = suite.string(forKey: SharedKeys.activeScheduleIdKey)
        let alreadySame  = (activeIdStr == pick.schedule.id.uuidString) && (plannedEpoch == desiredEpoch)

        // Idempotency: if planned matches what we’d arm, do nothing.
        if alreadySame {
            // 🔄 Refresh selection even when schedule/time didn't change
            if let mode = allModes.first(where: { $0.id == pick.schedule.modeId }) {
                let suite = JNShared.suite
                let currentModeId = suite.string(forKey: SharedKeys.activeModeIdKey)
                var needsUpdate = (currentModeId != mode.id.uuidString)

                if let newData = try? JSONEncoder().encode(mode.selectedApps) {
                    let oldData = suite.data(forKey: SharedKeys.selectionDataKey)
                    if oldData != newData { needsUpdate = true }
                    if needsUpdate {
                        suite.set(newData, forKey: SharedKeys.selectionDataKey)
                        suite.set(mode.id.uuidString, forKey: SharedKeys.activeModeIdKey)
                        suite.synchronize()
                        SCHED.info("REBALANCE 🔄 refreshed selection for unchanged schedule/time")
                    }
                }
            }

            lastArmedScheduleId = pick.schedule.id
            lastArmedEpoch      = desiredEpoch
            return
        }

        // Only stop when we actually need to switch/update.
        let center = DeviceActivityCenter()
        center.stopMonitoring([JNActivityName.interval])

        // Arm with the computed fire (respecting minLeadSeconds).
        sync(schedule: pick.schedule, allModes: allModes, overrideFire: pick.fire)
    }

    // MARK: Manual stop for a specific schedule (used when explicitly disabling)
    static func stop(scheduleId: UUID) {
        clearArmMarkersAndStop()
        BRIDGE_LOG.info("DISARM schedule=\(scheduleId.uuidString, privacy: .public)")
    }
}

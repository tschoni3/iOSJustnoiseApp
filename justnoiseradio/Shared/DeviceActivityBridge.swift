// DeviceActivityBridge.swift
import Foundation
import DeviceActivity
import FamilyControls

enum BridgeError: Error { case notApproved }

enum DeviceActivityBridge {

    static func ensureAuthorization() async throws {
        let center = AuthorizationCenter.shared
        if center.authorizationStatus != .approved {
            try await center.requestAuthorization(for: .individual)
        }
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            throw BridgeError.notApproved
        }
    }

    static func sync(schedule: Schedule, allModes: [Mode]) {
        // Build schedule window from the schedule’s time-of-day.
        let comps = Calendar.current.dateComponents([.hour, .minute], from: schedule.date)
        let start = DateComponents(hour: comps.hour ?? 9, minute: comps.minute ?? 0)
        // Keep window simple and long; you clear via manual stop.
        let end   = DateComponents(hour: ((comps.hour ?? 9) + 23) % 24,
                                   minute: ((comps.minute ?? 0) + 59) % 60)

        let dSchedule = DeviceActivitySchedule(
            intervalStart: start,
            intervalEnd: end,
            repeats: !schedule.repeatWeekdays.isEmpty
        )

        let center = DeviceActivityCenter()
        do {
            // Your toolchain wants a single name here (even on iOS 17+).
            try center.startMonitoring(JNActivityName.interval, during: dSchedule)
        } catch {
            print("❌ startMonitoring failed: \(error)")
        }
    }

    static func stop(scheduleId: UUID) {
        let center = DeviceActivityCenter()
        // iOS 17+ signature expects an array of names.
        center.stopMonitoring([JNActivityName.interval])
    }

    }


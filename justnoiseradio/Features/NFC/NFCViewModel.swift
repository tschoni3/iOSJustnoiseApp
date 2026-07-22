//
//  NFCViewModel.swift
//
import UIKit
import Foundation
import CoreNFC
import SwiftUI
import Combine
import FamilyControls
import ManagedSettings
import ActivityKit
import OSLog

// Rename our alert enum to UnifiedAlert to avoid conflicts.
enum UnifiedAlert: Identifiable {
    case error(AlertItem)

    var id: String {
        switch self {
        case .error(let alertItem):
            return "error_\(alertItem.id.uuidString)"
        }
    }
}

enum ScanningPurpose {
    case activation
    case sessionToggle
}

enum DotLevel: Int, Codable {
    case empty
    case light
    case medium
    case dense
}

private enum DotDensityThresholds {
    static let lightMinutes = 15.0
    static let goodMinutes = 120.0
    static let greatMinutes = 360.0
}

struct DaySummary: Identifiable, Hashable {
    var id: Date { Calendar.current.startOfDay(for: date) }
    let date: Date
    let totalFocus: TimeInterval
    let dotLevel: DotLevel
}

class NFCViewModel: NSObject, ObservableObject, NFCNDEFReaderSessionDelegate {
    @Published var journalHistory: [JournalEntry] = []
    @Published var sessionHistory: [Session] = []
    @Published var isAppsBlocked: Bool = false
    @Published var elapsedTime: TimeInterval = 0
    @Published var isActivated: Bool = false
    @Published var activeAlert: UnifiedAlert?  // Unified alert state
    @Published var isHydrated: Bool = false
    @Published var lastLocalModeChangeAt: Date?
    @Published var pendingDisableScheduleId: UUID? = nil
    @Published var showNoiseRewindCard: Bool = false
    private let lastNoiseRewindSeenWeekStartKey = "jn_last_noise_rewind_seen_week_start"
    

    // Guard to avoid saving while restoring from disk
    private var isRestoring: Bool = false
    private var isMutatingSchedules: Bool = false

    @Published var modes: [Mode] = [] {
        didSet { if !isRestoring { saveModes() } }
    }
    @Published var selectedMode: Mode? {
        didSet { if !isRestoring { saveSelectedMode() } }
    }
    // Emergency Unzap tokens
    @Published var emergencyUnzapCount: Int {
        didSet { UserDefaults.standard.set(emergencyUnzapCount, forKey: SharedKeys.emergencyUnzapKey) }
    }

    // Compatibility allow-list for Zaps already in use. Provisioning, rotation,
    // migration, and secret storage remain a separate product/security decision.
    private let authorizedTagPayloads: Set<String> = [
        "tschoni",
        "Tschoni",
        "ZAP-123456",
    ]

    var timer: Timer?
    var session: NFCNDEFReaderSession?
    var store = ManagedSettingsStore()
    private var sessionStartDate: Date?
    private var unauthorizedTagDetected = false
    private var acceptedNFCReadHandled = false
    private let selectedModeKey = "selectedModeID"
    private let scheduleStore = ScheduleStore()

    var scanningPurpose: ScanningPurpose?
    private let logger = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "NFCViewModel")
    @Published var liveActivity: Activity<SessionAttributes>?

    private let emergencyUnzapKey = "emergencyUnzapCount"

    private var sharedDefaults: UserDefaults { JNShared.suite }

    private func writeShared(_ key: String, _ value: Any?) {
        sharedDefaults.set(value, forKey: key)
        sharedDefaults.synchronize()
    }

    // Stable PostHog correlation ID for focus start/end, persisted across relaunch.
    private var currentSessionId: String?
    private let activeSessionIdKey = "jn_active_session_id_v1"
    private let journalHistoryKey = "jn_journal_history_v1"

    // MARK: - Init
    override init() {
        // Load emergency tokens from storage, default = 5
        let savedEmergency = UserDefaults.standard.object(forKey: emergencyUnzapKey) as? Int
        self.emergencyUnzapCount = savedEmergency ?? 5
        super.init()
        // ⛔️ Do NOT load here — wait for protected data (see hydrateOnLaunch()).
    }

    /// Entry point called by the App on first scene .task
    func hydrateOnLaunch() {
        if !(UIApplication.shared.isProtectedDataAvailable) {
            logger.warning("Protected data not available yet. Waiting for unlock…")
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(_protectedDataReady),
                name: UIApplication.protectedDataDidBecomeAvailableNotification,
                object: nil
            )
            return
        }
        _performHydration()
    }

    @objc private func _protectedDataReady() {
        NotificationCenter.default.removeObserver(self, name: UIApplication.protectedDataDidBecomeAvailableNotification, object: nil)
        _performHydration()
    }

    private func _performHydration() {
        isRestoring = true
        // 💧 Single, deterministic load path
        loadModes()              // load modes (don’t auto-pick here)
        loadSessions()
        loadJournalHistory()
        loadActivationStatus()
        loadSchedules()
        loadSelectedMode()       // restore last user choice if possible

        loadActiveSessionIdIfAny()

        isRestoring = false      // from now on, saves are allowed

        // Mirror last known state
        Task { @MainActor in
            self.restoreBlockingState()
        }

        // Sticky activation — never regress after real activation
        if !isActivated {
            if let shared = sharedDefaults.object(forKey: SharedKeys.activationKey) as? Bool, shared {
                isActivated = true
            } else if UserDefaults.standard.bool(forKey: SharedKeys.activationKey) {
                isActivated = true
            }
            if isActivated { saveActivationStatus() }
        }

        isHydrated = true
        logger.info("Hydration completed. UI may proceed.")

        reconcileLiveActivityWithState()
    }

    private func reconcileLiveActivityWithState() {
        Task { @MainActor in
            let isRunning = JNShared.suite.bool(forKey: SharedKeys.isAppsBlockedKey)

            if isRunning {
                if let modeIdString = JNShared.suite.string(forKey: SharedKeys.activeModeIdKey),
                   let modeId = UUID(uuidString: modeIdString),
                   let restoredMode = self.modes.first(where: { $0.id == modeId }),
                   self.selectedMode?.id != restoredMode.id {
                    self.selectedMode = restoredMode
                }

                if Activity<SessionAttributes>.activities.isEmpty {
                    startLiveActivity()
                } else if let existing = Activity<SessionAttributes>.activities.first {
                    self.liveActivity = existing
                }
            } else {
                endLiveActivity()
            }
        }
    }

    // MARK: - Activation
    func loadActivationStatus() {
        if let shared = sharedDefaults.object(forKey: SharedKeys.activationKey) as? Bool, shared {
            isActivated = true
        } else if UserDefaults.standard.bool(forKey: SharedKeys.activationKey) {
            isActivated = true
        }
    }

    func saveActivationStatus() {
        UserDefaults.standard.set(isActivated, forKey: SharedKeys.activationKey)
        sharedDefaults.set(isActivated, forKey: SharedKeys.activationKey)
        sharedDefaults.synchronize()
    }

    // MARK: - Emergency Unzap
    @MainActor func useEmergencyUnzap() {
        guard isAppsBlocked else {
            setError(.unknown(description: "No active focus session to unlock."))
            return
        }
        guard emergencyUnzapCount > 0 else {
            setError(.unknown(description: "No Emergency Unzap left. Please use your Zap to unblock."))
            return
        }
        emergencyUnzapCount -= 1
        unblockApplications()
    }

    // MARK: - Restore state
    @MainActor func restoreBlockingState() {
        guard sharedDefaults.bool(forKey: SharedKeys.isAppsBlockedKey) else { return }

        if selectedMode == nil {
            if let modeIdString = sharedDefaults.string(forKey: SharedKeys.activeModeIdKey),
               let modeId = UUID(uuidString: modeIdString),
               let restoredMode = modes.first(where: { $0.id == modeId }) {
                selectedMode = restoredMode
            }
        }

        // Restore analytics session correlation if the app died mid-session.
        if currentSessionId == nil {
            loadActiveSessionIdIfAny()
        }

        isAppsBlocked = true

        if let savedStart = sharedDefaults.object(forKey: SharedKeys.sessionStartKey) as? Date,
           savedStart <= Date() {
            sessionStartDate = savedStart
            resumeTimer()
            logger.info("Restored blocking state at \(savedStart), mode: \(self.selectedMode?.name ?? "nil")")
        } else {
            logger.warning("Active session has no valid start date.")
        }

        if !isActivated {
            isActivated = true
            saveActivationStatus()
        }
    }

    @MainActor private func resumeTimer() {
        timer?.invalidate()
        updateElapsedTime()
        let newTimer = Timer(timeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.updateElapsedTime()
        }
        RunLoop.main.add(newTimer, forMode: .common)
        timer = newTimer
        self.logger.info("Timer resumed from saved session start date.")
    }

    // MARK: - NFC
    func startScanning(purpose: ScanningPurpose) {
        if purpose == .sessionToggle, isAppsBlocked, let start = sessionStartDate {
            let elapsed = Date().timeIntervalSince(start)
            if !AccidentalStopGuard.allowsStop(elapsedSeconds: elapsed) {
                let gen = UINotificationFeedbackGenerator()
                gen.notificationOccurred(.warning)

                let alertItem = AlertItem(
                    title: Text("Too Early"),
                    message: Text("Please wait a moment — you don’t even lock in less than 3 seconds."),
                    dismissAction: { self.activeAlert = nil }
                )
                DispatchQueue.main.async { self.activeAlert = UnifiedAlert.error(alertItem) }
                logger.warning("Blocked early exit in startScanning: elapsed=\(elapsed)s")
                return
            }
        }

        if session != nil {
            logger.warning("NFC session already active, ignoring new request.")
            return
        }

        switch purpose {
        case .activation:
            guard !isActivated else { setError(.alreadyActivated); return }
        case .sessionToggle:
            if !isAppsBlocked {
                guard let mode = selectedMode else { setError(.invalidModeSelection); return }
                guard mode.selectedApps.hasBlockingTargets else { setError(.invalidModeSelection); return }
            }
        }

        guard NFCNDEFReaderSession.readingAvailable else { setError(.nfcNotAvailable); return }

        scanningPurpose = purpose
        acceptedNFCReadHandled = false
        unauthorizedTagDetected = false
        session = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: true)
        switch purpose {
        case .activation:
            session?.alertMessage = "Hold your iPhone close to the Zap to activate the app."
        case .sessionToggle:
            session?.alertMessage = "Hold your iPhone close to the Zap to toggle the focus session."
        }
        session?.begin()
    }

    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        DispatchQueue.main.async {
            defer { self.session = nil }
            if self.unauthorizedTagDetected {
                self.logger.info("Unauthorized tag error already handled.")
                self.unauthorizedTagDetected = false
                return
            }
            if let readerError = error as? NFCReaderError {
                switch readerError.code {
                case .readerSessionInvalidationErrorUserCanceled:
                    self.logger.info("NFC session canceled by user."); return
                case .readerSessionInvalidationErrorSystemIsBusy:
                    self.setError(.nfcSessionFailed(description: "System busy. Try again in a few seconds.")); return
                case .readerSessionInvalidationErrorFirstNDEFTagRead:
                    self.logger.info("NFC session ended after first tag read (normal)."); return
                default:
                    self.setError(.nfcSessionFailed(description: error.localizedDescription))
                }
            } else {
                self.setError(.unknown(description: error.localizedDescription))
            }
        }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            guard self.session != nil else {
                self.logger.warning("Duplicate NFC event ignored.")
                return
            }

            let records = messages.flatMap(\.records).map { record in
                ZapNDEFRecordInput(
                    typeNameFormat: record.typeNameFormat == .nfcWellKnown ? .wellKnown : .unsupported,
                    type: record.type,
                    payload: record.payload
                )
            }
            let decision = self.firstRelevantZapReadDecision(for: records)

            switch decision.outcome {
            case .accepted:
                self.handleAcceptedZapRead()
            case .duplicateIgnored:
                self.logger.info("Duplicate NFC read ignored.")
            case .unauthorized:
                self.unauthorizedTagDetected = true
                self.setError(.unauthorizedNFCTag)
            case .invalid:
                self.setError(.invalidNFCTag)
            case .canceled:
                self.logger.info("Canceled NFC read ignored.")
            }

            session.invalidate()
            self.session = nil
            self.logger.info("NFC session closed after handling one read outcome.")
        }
    }

    func readerSessionDidBecomeActive(_ session: NFCNDEFReaderSession) {
        logger.info("NFC session did become active.")
    }

    private func firstRelevantZapReadDecision(for records: [ZapNDEFRecordInput]) -> ZapReadDecision {
        let decision = ZapReadPolicy.firstRelevantDecision(
            records: records,
            authorizedPayloads: authorizedTagPayloads,
            acceptedReadAlreadyHandled: acceptedNFCReadHandled
        )
        if decision.mutationEligible {
            acceptedNFCReadHandled = true
        }
        return decision
    }

    @MainActor private func handleAcceptedZapRead() {
        switch scanningPurpose {
        case .activation:
            isActivated = true
            saveActivationStatus()
            showAlertWith(message: "JustNoise activated!")
            Analytics.capture("activation_successful", props: [
                "timestamp": Date().timeIntervalSince1970
            ])
        case .sessionToggle:
            toggleAppBlocking()
        case .none:
            setError(.unknown(description: "Unknown scan purpose."))
        }
    }

    @MainActor func toggleAppBlocking() {
        if isAppsBlocked {
            if let startDate = sessionStartDate {
                let elapsed = Date().timeIntervalSince(startDate)
                if !AccidentalStopGuard.allowsStop(elapsedSeconds: elapsed) {
                    let gen = UINotificationFeedbackGenerator()
                    gen.notificationOccurred(.warning)
                    let alertItem = AlertItem(
                        title: Text("Too Early"),
                        message: Text("Please wait a moment — you don’t even lock in less than 3 seconds."),
                        dismissAction: { self.activeAlert = nil }
                    )
                    DispatchQueue.main.async { self.activeAlert = UnifiedAlert.error(alertItem) }
                    logger.warning("Blocked early exit in toggleAppBlocking: elapsed=\(elapsed)s")
                    return
                }
            }
            unblockApplications()
        } else {
            Task {
                do {
                    try await ensureAuthorizationAndSelection()
                    self.blockApplications()
                } catch {
                    self.setError(.unknown(description: error.localizedDescription))
                }
            }
        }
    }

    // MARK: - Start/Stop (manual start still allowed)
    @MainActor func blockApplications() {
        guard let mode = selectedMode else { setError(.invalidModeSelection); return }
        guard mode.selectedApps.hasBlockingTargets else { setError(.invalidModeSelection); return }

        store.shield.applications = mode.selectedApps.applicationTokens
        store.shield.applicationCategories = ShieldSettings.ActivityCategoryPolicy.specific(
            mode.selectedApps.categoryTokens, except: []
        )
        store.shield.webDomains = mode.selectedApps.webDomainTokens
        store.shield.webDomainCategories = ShieldSettings.ActivityCategoryPolicy.specific(
            mode.selectedApps.categoryTokens, except: []
        )

        let now = Date()
        sessionStartDate = now
        elapsedTime = 0
        isAppsBlocked = true
        writeShared(SharedKeys.isAppsBlockedKey, true)
        writeShared(SharedKeys.sessionStartKey, now)
        writeShared(SharedKeys.activeModeIdKey, mode.id.uuidString)
        writeShared(SharedKeys.shieldOwnerKey, "app")
        startTimer(using: now)
        startLiveActivity()

        // Keep one stable analytics ID across the focus start/end pair.
        let sid = UUID().uuidString
        currentSessionId = sid
        sharedDefaults.set(sid, forKey: activeSessionIdKey)
        sharedDefaults.synchronize()

        Analytics.capture("focus_session_started", props: [
            "timestamp": Date().timeIntervalSince1970,
            "session_id": sid,
            "mode": mode.name
        ])

        logger.info("Blocking applied. Start: \(self.sessionStartDate!), mode: \(mode.name)")
    }

    @MainActor func unblockApplications() {
        let modeName = selectedMode?.name ?? "unknown"
        let sid = currentSessionId ?? UUID().uuidString
        let durationSec: Int = {
            if let start = sessionStartDate { return Int(Date().timeIntervalSince(start)) }
            return Int(elapsedTime)
        }()
        Analytics.capture("focus_session_ended", props: [
            "timestamp": Date().timeIntervalSince1970,
            "session_id": sid,
            "duration_sec": durationSec,
            "mode": modeName
        ])

        store.shield.applications = nil
        store.shield.applicationCategories = nil
        store.shield.webDomains = nil
        store.shield.webDomainCategories = nil

        isAppsBlocked = false
        sharedDefaults.set(false, forKey: SharedKeys.isAppsBlockedKey)
        sharedDefaults.removeObject(forKey: SharedKeys.sessionStartKey)
        sharedDefaults.removeObject(forKey: SharedKeys.activeModeIdKey)
        sharedDefaults.removeObject(forKey: SharedKeys.shieldOwnerKey)
        sharedDefaults.removeObject(forKey: activeSessionIdKey) // ✅ clear
        sharedDefaults.synchronize()

        stopTimerAndSaveSession()
        endLiveActivity()
        currentSessionId = nil

        if let sm = selectedMode {
            JNShared.suite.set(sm.id.uuidString, forKey: SharedKeys.preferredModeIdKey)
            JNShared.suite.synchronize()
        }

        if let activeIdStr = JNShared.suite.string(forKey: SharedKeys.activeScheduleIdKey),
           let activeId = UUID(uuidString: activeIdStr),
           let idx = schedules.firstIndex(where: { $0.id == activeId }) {
            var s = schedules[idx]
            if s.repeatWeekdays.isEmpty {
                if s.isEnabled { s.isEnabled = false }
                schedules[idx] = s
                DeviceActivityBridge.stop(scheduleId: s.id)
            }
        }

        if let pendingId = pendingDisableScheduleId,
           let idx = schedules.firstIndex(where: { $0.id == pendingId }) {
            if schedules[idx].isEnabled {
                schedules[idx].isEnabled = false
                DeviceActivityBridge.stop(scheduleId: pendingId)
            }
            pendingDisableScheduleId = nil
        }

        logger.info("All apps, categories, and web domains unblocked.")
    }

    // MARK: - Timer
    @MainActor private func startTimer(using start: Date) {
        sessionStartDate = start
        updateElapsedTime()
        timer?.invalidate()
        let newTimer = Timer(timeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.updateElapsedTime()
        }
        RunLoop.main.add(newTimer, forMode: .common)
        timer = newTimer
        self.logger.info("Timer started.")
    }

    @MainActor private func updateElapsedTime() {
        if let startDate = sessionStartDate {
            self.elapsedTime = max(0, Date().timeIntervalSince(startDate))
        } else {
            self.elapsedTime = 0
        }
    }

    @MainActor private func stopTimerAndSaveSession() {
        timer?.invalidate()
        timer = nil
        updateElapsedTime()
        let sessionObj = Session(startDate: sessionStartDate ?? Date(), duration: elapsedTime, modeName: selectedMode?.name)
        sessionHistory.append(sessionObj)
        saveSessions()
        self.logger.info("Timer stopped. Session saved.")
        sessionStartDate = nil

      
    }

    @Published var schedules: [Schedule] = [] { didSet { saveSchedules() } }

    func loadSchedules() {
        schedules = scheduleStore.load()
    }

    func saveSchedules() {
        scheduleStore.save(schedules)
        if isMutatingSchedules { return }
        DeviceActivityBridge.rebalanceArming(schedules: schedules, allModes: modes, isSessionRunning: isAppsBlocked)
    }

    func deleteSchedule(at offsets: IndexSet) {
        let ids = offsets.compactMap { $0 < schedules.count ? schedules[$0].id : nil }
        deleteSchedules(byIDs: ids)
    }

    func addSchedule(_ schedule: Schedule) {
        schedules.append(schedule)
    }

    func updateSchedule(_ schedule: Schedule) {
        if let idx = schedules.firstIndex(where: { $0.id == schedule.id }) {
            schedules[idx] = schedule
        } else {
            schedules.append(schedule)
        }
    }

    func clearLastFireIfRearming(_ scheduleId: UUID) {
        if let idx = schedules.firstIndex(where: { $0.id == scheduleId }),
           schedules[idx].lastFireDate != nil {
            schedules[idx].lastFireDate = nil
        }
    }

    func deleteSchedules(byIDs ids: [UUID]) {
        guard !ids.isEmpty else { return }

        let suite = JNShared.suite
        let activeIdStr = suite.string(forKey: SharedKeys.activeScheduleIdKey)
        let activeId = activeIdStr.flatMap(UUID.init(uuidString:))
        let owner = suite.string(forKey: SharedKeys.shieldOwnerKey)
        let isRunning = suite.bool(forKey: SharedKeys.isAppsBlockedKey)

        for id in ids {
            if let a = activeId, a == id, isRunning, owner == "app" {
                pendingDisableScheduleId = id
                continue
            }
            DeviceActivityBridge.stop(scheduleId: id)

            if let a = activeId, a == id, !isRunning {
                suite.removeObject(forKey: SharedKeys.selectionDataKey)
                suite.removeObject(forKey: SharedKeys.activeModeIdKey)
                suite.removeObject(forKey: SharedKeys.activeScheduleIdKey)
                suite.removeObject(forKey: SharedKeys.plannedStartKey)
            }
        }
        suite.synchronize()

        isMutatingSchedules = true
        schedules.removeAll { ids.contains($0.id) }
        isMutatingSchedules = false

        DeviceActivityBridge.rebalanceArming(schedules: schedules, allModes: modes, isSessionRunning: isAppsBlocked)
    }

    @MainActor
    func foregroundResync() async {
        let ud = JNShared.suite

        let blocked      = ud.bool(forKey: SharedKeys.isAppsBlockedKey)
        let planned      = ud.integer(forKey: SharedKeys.plannedStartKey)
        let hasStartDate = (ud.object(forKey: SharedKeys.sessionStartKey) != nil)
        let lastApply    = ud.integer(forKey: SharedKeys.lastApplyEpochKey)

        logger.info("RESYNC ▶︎ blocked=\(blocked, privacy: .public) planned=\(planned, privacy: .public) lastApply=\(lastApply, privacy: .public) hasStartDate=\(hasStartDate, privacy: .public)")

        if blocked, sessionStartDate == nil {
            if let start = ud.object(forKey: SharedKeys.sessionStartKey) as? Date {
                let skewAllowance: TimeInterval = 2
                if start <= Date().addingTimeInterval(skewAllowance) {
                    sessionStartDate = start
                    resumeTimer()
                    ud.set("app", forKey: SharedKeys.shieldOwnerKey)
                    ud.synchronize()
                    logger.info("RESYNC ▶︎ adopted sessionStart and ownership transferred to app")
                } else {
                    isAppsBlocked = false
                    logger.error("RESYNC ⚠️ start in future → ignoring adopt, clearing UI block")
                }
            } else {
                logger.error("RESYNC ⚠️ blocked=true but no sessionStartKey")
            }
        }

        if blocked {
            if let modeIdString = ud.string(forKey: SharedKeys.activeModeIdKey),
               let modeId = UUID(uuidString: modeIdString),
               let restoredMode = modes.first(where: { $0.id == modeId }),
               selectedMode?.id != restoredMode.id {
                selectedMode = restoredMode
            }
        }

        // Restore analytics session correlation after returning to foreground.
        if blocked, currentSessionId == nil {
            loadActiveSessionIdIfAny()
        }

        if blocked {
            if Activity<SessionAttributes>.activities.isEmpty {
                startLiveActivity()
            } else if let existing = Activity<SessionAttributes>.activities.first {
                self.liveActivity = existing
            }
        } else {
            endLiveActivity()
        }

        loadSchedules()
        consumeScheduleFireMarkers()
    }

    @MainActor
    func consumeScheduleFireMarkers() {
        let suite = JNShared.suite

        guard
            let idStr = suite.string(forKey: SharedKeys.lastFiredScheduleIdKey),
            let firedId = UUID(uuidString: idStr)
        else {
            return
        }

        let firedEpoch = suite.integer(forKey: SharedKeys.lastFiredEpochKey)
        let firedDate = Date(timeIntervalSince1970: TimeInterval(firedEpoch))

        if let idx = schedules.firstIndex(where: { $0.id == firedId }) {
            var s = schedules[idx]
            s.lastFireDate = firedDate

            if s.repeatWeekdays.isEmpty {
                if s.isEnabled { s.isEnabled = false }
                schedules[idx] = s
                DeviceActivityBridge.stop(scheduleId: s.id)
            } else {
                schedules[idx] = s
            }
        }

        suite.removeObject(forKey: SharedKeys.lastFiredScheduleIdKey)
        suite.removeObject(forKey: SharedKeys.lastFiredEpochKey)
        suite.synchronize()
    }

    // MARK: - Live Activity
    func startLiveActivity() {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        Task {
            if let existing = Activity<SessionAttributes>.activities.first {
                await MainActor.run { self.liveActivity = existing }
                return
            }

            let attributes = SessionAttributes(modeName: selectedMode?.name ?? "JustNoise")
            let initialContentState = SessionAttributes.ContentState(startDate: sessionStartDate ?? Date())
            do {
                let requested = try await Activity<SessionAttributes>.request(
                    attributes: attributes,
                    content: ActivityContent(state: initialContentState, staleDate: nil)
                )
                await MainActor.run { self.liveActivity = requested }
                print("Started Live Activity: \(String(describing: self.liveActivity))")
            } catch {
                print("Error starting Live Activity: \(error.localizedDescription)")
            }
        }
    }

    func endLiveActivity() {
        Task {
            if let remembered = await MainActor.run(body: { self.liveActivity }) {
                let finalState = SessionAttributes.ContentState(startDate: Date())
                await remembered.end(
                    ActivityContent(state: finalState, staleDate: nil),
                    dismissalPolicy: .immediate
                )
            }

            for activity in Activity<SessionAttributes>.activities {
                let finalState = SessionAttributes.ContentState(startDate: Date())
                await activity.end(
                    ActivityContent(state: finalState, staleDate: nil),
                    dismissalPolicy: .immediate
                )
            }

            await MainActor.run { self.liveActivity = nil }
            print("Ended all SessionActivities (defensive cleanup)")
        }
    }

    private func loadActiveSessionIdIfAny() {
        if let sid = sharedDefaults.string(forKey: activeSessionIdKey), !sid.isEmpty {
            currentSessionId = sid
        }
    }

    // MARK: - Alerts & Errors
    private func showAlertWith(message: String) {
        let alertItem = AlertItem(
            title: Text("Notice"),
            message: Text(message),
            dismissAction: { self.activeAlert = nil }
        )
        activeAlert = UnifiedAlert.error(alertItem)
    }

    func setError(_ error: AppError) {
        let alertItem = AlertItem(
            title: Text("Error"),
            message: Text(error.errorDescription ?? "An unexpected error occurred."),
            dismissAction: { self.activeAlert = nil }
        )
        activeAlert = UnifiedAlert.error(alertItem)
        logger.error("Error: \(error.errorDescription ?? "No description")")

        let key = analyticsErrorKey(error)
        Analytics.capture("error_occurred", props: [
            "timestamp": Date().timeIntervalSince1970,
            "error_type": key
        ])
    }

    private func analyticsErrorKey(_ e: AppError) -> String {
        switch e {
        case .nfcSessionFailed:            return "nfc_session_failed"
        case .invalidModeSelection:        return "invalid_mode_selection"
        case .unauthorizedNFCTag:          return "unauthorized_tag"
        case .nfcNotAvailable:             return "nfc_not_available"
        case .alreadyActivated:            return "already_activated"
        case .invalidNFCTag:               return "invalid_nfc_tag"
        case .unknown:                     return "unknown"
        }
    }

    // MARK: - Noise Rewind Availability

    func checkNoiseRewindAvailability() {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-forceNoiseRewindCard") {
            showNoiseRewindCard = !isAppsBlocked
            return
        }
        #endif

        let lastSeen = UserDefaults.standard.object(forKey: lastNoiseRewindSeenWeekStartKey) as? Date

        showNoiseRewindCard = NoiseRewindWeeklyInsightGenerator.shouldShowNoiseRewind(
            sessions: sessionHistory,
            referenceDate: Date(),
            lastSeenWeekStart: lastSeen,
            isSessionActive: isAppsBlocked
        )
    }

    func markNoiseRewindSeen() {
        var cal = Calendar.current
        cal.firstWeekday = 1 // Sunday

        let todayWeekday = cal.component(.weekday, from: Date())
        let startOfToday = cal.startOfDay(for: Date())

        let currentSunday = cal.date(
            byAdding: .day,
            value: -(todayWeekday - 1),
            to: startOfToday
        )!

        let previousSunday = cal.date(
            byAdding: .day,
            value: -7,
            to: currentSunday
        )!

        UserDefaults.standard.set(previousSunday, forKey: lastNoiseRewindSeenWeekStartKey)

        showNoiseRewindCard = false
    }

    func dismissNoiseRewindCardForNow() {
        showNoiseRewindCard = false
    }
    // MARK: - Signal Summary

    func calculateSessionSignalBoost(for duration: TimeInterval) -> Int {
        let minutes = duration / 60

        switch minutes {
        case 0..<5:
            return 0
        case 5..<20:
            return 3
        case 20..<45:
            return 8
        case 45..<90:
            return 16
        case 90..<180:
            return 24
        default:
            return 35
        }
    }
    
    var weeklyNoiseRewindInsight: NoiseRewindWeeklyInsight? {
        NoiseRewindWeeklyInsightGenerator.generate(
            sessions: sessionHistory,
            journals: journalHistory,
            referenceDate: Date()
        )
    }
    // MARK: - Modes & Sessions
    func loadModes() {
        if let data = UserDefaults.standard.data(forKey: "modes") {
            do {
                let decoded = try JSONDecoder().decode([Mode].self, from: data)
                modes = decoded
                logger.info("Loaded user-created modes from storage.")
                return
            } catch {
                logger.error("Failed to decode modes from UserDefaults. Using default modes. Error: \(error.localizedDescription)")
            }
        }
        addDefaultModes() // don’t set selectedMode here
    }

    func saveModes() {
        if let data = try? JSONEncoder().encode(modes) {
            UserDefaults.standard.set(data, forKey: "modes")
        }
    }

    func saveSelectedMode() {
        guard let selectedMode else { return }
        UserDefaults.standard.set(selectedMode.id.uuidString, forKey: selectedModeKey)

        if !isAppsBlocked && !isRestoring {
            JNShared.suite.set(selectedMode.id.uuidString, forKey: SharedKeys.preferredModeIdKey)
            JNShared.suite.synchronize()
            lastLocalModeChangeAt = Date()
        }
    }

    func loadSelectedMode() {
        if let prefStr = JNShared.suite.string(forKey: SharedKeys.preferredModeIdKey),
           let prefId = UUID(uuidString: prefStr),
           let pref = modes.first(where: { $0.id == prefId }) {
            selectedMode = pref
            return
        }
        // 1) Try by persisted UUID
        if let savedID = UserDefaults.standard.string(forKey: selectedModeKey),
           let mode = modes.first(where: { $0.id.uuidString == savedID }) {
            selectedMode = mode
            return
        }
        // 2) Fallback by name
        let savedName = UserDefaults.standard.string(forKey: "selectedModeName")
        if let name = savedName,
           let byName = modes.first(where: { $0.name == name }) {
            selectedMode = byName
            return
        }
        // 3) Last resort: pick the first available
        if selectedMode == nil, let firstMode = modes.first {
            selectedMode = firstMode
        }
        // Persist name for future name-based fallback
        if let sm = selectedMode {
            UserDefaults.standard.set(sm.name, forKey: "selectedModeName")
        }
    }

    func addMode(_ mode: Mode) {
        modes.append(mode)
    }

    func updateMode(_ mode: Mode) {
        if let index = modes.firstIndex(where: { $0.id == mode.id }) {
            modes[index] = mode
        }
    }

    private func addDefaultModes() {
        // Use STABLE UUIDs for defaults to avoid mismatch across launches
        let noiseID  = UUID(uuidString: "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")!
        let focusID  = UUID(uuidString: "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB")!
        let sleepID  = UUID(uuidString: "CCCCCCCC-CCCC-CCCC-CCCC-CCCCCCCCCCCC")!

        let defaultMode = Mode(id: noiseID, name: "Noise", selectedApps: FamilyActivitySelection())
        let focusMode   = Mode(id: focusID, name: "Focus", selectedApps: FamilyActivitySelection())
        let sleepMode   = Mode(id: sleepID, name: "Sleep", selectedApps: FamilyActivitySelection())
        modes = [defaultMode, focusMode, sleepMode]
        saveModes()
    }

    func loadSessions() {
        if let data = UserDefaults.standard.data(forKey: "sessionHistory"),
           let savedSessions = try? JSONDecoder().decode([Session].self, from: data) {
            sessionHistory = savedSessions
        }
        checkNoiseRewindAvailability()
    }

    func saveSessions() {
        if let data = try? JSONEncoder().encode(sessionHistory) {
            UserDefaults.standard.set(data, forKey: "sessionHistory")
        }
        checkNoiseRewindAvailability()
    }

    // MARK: - Journal History
    func loadJournalHistory() {
        if let data = UserDefaults.standard.data(forKey: journalHistoryKey),
           let saved = try? JSONDecoder().decode([JournalEntry].self, from: data) {
            journalHistory = saved.sorted { $0.createdAt < $1.createdAt }
        } else {
            journalHistory = []
        }
    }

    func saveJournalHistory() {
        if let data = try? JSONEncoder().encode(journalHistory) {
            UserDefaults.standard.set(data, forKey: journalHistoryKey)
        }
    }

    private func latestRecentlyEndedSessionIndex(maxAge: TimeInterval = 20 * 60) -> Int? {
        let now = Date()

        return sessionHistory.indices
            .filter { idx in
                let end = sessionHistory[idx].startDate.addingTimeInterval(max(0, sessionHistory[idx].duration))
                return end <= now && now.timeIntervalSince(end) <= maxAge
            }
            .max { lhs, rhs in
                let lEnd = sessionHistory[lhs].startDate.addingTimeInterval(max(0, sessionHistory[lhs].duration))
                let rEnd = sessionHistory[rhs].startDate.addingTimeInterval(max(0, sessionHistory[rhs].duration))
                return lEnd < rEnd
            }
    }
    func saveWeeklyReflectionToHistory(
        text: String,
        prompt: String,
        weekStart: Date,
        weekEnd: Date
    ) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let transcription = TranscriptionResponse(
            notetitle: "Weekly Reflection",
            overview: prompt,
            actionsteps: "",
            challenges: "",
            transcript: trimmed,
            sentiment: "Reflective",
            aifeedback: trimmed
        )

        saveJournalEntry(transcription: transcription)
    }

    @discardableResult
    func saveJournalEntry(
        transcription: TranscriptionResponse,
        audioURL: URL? = nil
    ) -> JournalEntry {
        var linkedSessionId: UUID? = nil

        // Optional compatibility link:
        // if the user reflects shortly after a session ended,
        // link it to that session and attach transcription if still empty.
        if let idx = latestRecentlyEndedSessionIndex() {
            linkedSessionId = sessionHistory[idx].id

            if sessionHistory[idx].transcription == nil {
                sessionHistory[idx].transcription = transcription
            }
            if sessionHistory[idx].audioFileURL == nil, let audioURL {
                sessionHistory[idx].audioFileURL = audioURL
            }
            saveSessions()
        }

        let entry = JournalEntry(
            createdAt: Date(),
            modeName: selectedMode?.name,
            transcription: transcription,
            audioFileURL: audioURL,
            linkedSessionId: linkedSessionId
        )

        journalHistory.append(entry)
        journalHistory.sort { $0.createdAt < $1.createdAt }
        saveJournalHistory()

        logger.info("Saved standalone journal entry.")
        return entry
    }

    // MARK: - Dot System (Day-based)
    private let microSessionThresholdSec: TimeInterval = 60

    private struct DaySlice {
        let date: Date
        let duration: TimeInterval
    }

    private func splitSessionByDay(_ session: Session) -> [DaySlice] {
        let cal = Calendar.current
        let start = session.startDate
        let end = session.startDate.addingTimeInterval(session.duration)

        var slices: [DaySlice] = []
        var cursor = start

        while cursor < end {
            let dayStart = cal.startOfDay(for: cursor)
            let nextDayStart = cal.date(byAdding: .day, value: 1, to: dayStart)!

            let sliceEnd = min(end, nextDayStart)
            let sliceDuration = sliceEnd.timeIntervalSince(cursor)

            if sliceDuration > 0 {
                let includedDuration = sliceDuration < microSessionThresholdSec ? 0 : sliceDuration
                slices.append(
                    DaySlice(
                        date: dayStart,
                        duration: includedDuration
                    )
                )
            }

            cursor = sliceEnd
        }

        return slices
    }

    func dotLevel(totalMinutes: Double) -> DotLevel {
        if totalMinutes >= DotDensityThresholds.greatMinutes { return .dense }
        if totalMinutes >= DotDensityThresholds.goodMinutes { return .medium }
        if totalMinutes >= DotDensityThresholds.lightMinutes { return .light }

        return .empty
    }

    func generateYearSummaries() -> [DaySummary] {
        let cal = Calendar.current
        let now = Date()

        let year = cal.component(.year, from: now)
        let startOfYear = cal.date(from: DateComponents(year: year, month: 1, day: 1))!

        let totalDays = cal.range(of: .day, in: .year, for: now)!.count

        var slicesByDay: [Date: [DaySlice]] = [:]

        for session in sessionHistory {
            let slices = splitSessionByDay(session)
            for slice in slices {
                slicesByDay[slice.date, default: []].append(slice)
            }
        }

        return (0..<totalDays).compactMap { offset in
            guard let date = cal.date(byAdding: .day, value: offset, to: startOfYear) else { return nil }

            let slices = slicesByDay[date] ?? []
            let totalSeconds = slices.reduce(0) { $0 + $1.duration }
            let totalMinutes = totalSeconds / 60

            let level = dotLevel(totalMinutes: totalMinutes)

            return DaySummary(
                date: date,
                totalFocus: totalSeconds,
                dotLevel: level
            )
        }
    }

    func generate90DaySummaries() -> [DaySummary] {
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())

        let pastDays = 60
        let futureDays = 29

        let start = cal.date(byAdding: .day, value: -pastDays, to: today)!
        let end = cal.date(byAdding: .day, value: futureDays, to: today)!

        var slicesByDay: [Date: [DaySlice]] = [:]

        for session in sessionHistory {
            let slices = splitSessionByDay(session)
            for slice in slices {
                slicesByDay[slice.date, default: []].append(slice)
            }
        }

        var summaries: [DaySummary] = []
        var date = start

        while date <= end {
            let slices = slicesByDay[date] ?? []

            let totalSeconds = slices.reduce(0) { $0 + $1.duration }
            let totalMinutes = totalSeconds / 60

            let level = dotLevel(totalMinutes: totalMinutes)

            summaries.append(
                DaySummary(
                    date: date,
                    totalFocus: totalSeconds,
                    dotLevel: level
                )
            )

            date = cal.date(byAdding: .day, value: 1, to: date)!
        }

        return summaries
    }
}

// MARK: - Selection/Auth helpers
private extension NFCViewModel {
    /// Request Screen Time auth if needed and ensure the current selected mode targets something.
    func ensureAuthorizationAndSelection() async throws {
        if AuthorizationCenter.shared.authorizationStatus != .approved {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            guard AuthorizationCenter.shared.authorizationStatus == .approved else {
                throw AppError.unknown(description: "Screen Time permission not granted.")
            }
        }
        if let sm = selectedMode, !sm.selectedApps.hasBlockingTargets {
            throw AppError.invalidModeSelection
        }
    }
}

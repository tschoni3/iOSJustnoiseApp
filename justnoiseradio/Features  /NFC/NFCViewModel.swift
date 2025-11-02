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
import OSLog
import ActivityKit
// ⬇️ Removed: import DeviceActivity

// Rename our alert enum to UnifiedAlert to avoid conflicts.
enum UnifiedAlert: Identifiable {
    case error(AlertItem)
    case reflectionPrompt

    var id: String {
        switch self {
        case .error(let alertItem):
            return "error_\(alertItem.id.uuidString)"
        case .reflectionPrompt:
            return "reflectionPrompt"
        }
    }
}

enum ScanningPurpose {
    case activation
    case sessionToggle
}

class NFCViewModel: NSObject, ObservableObject, NFCNDEFReaderSessionDelegate {
    @Published var message: String?
    @Published var sessionHistory: [Session] = []
    @Published var isAppsBlocked: Bool = false
    @Published var elapsedTime: TimeInterval = 0
    @Published var isActivated: Bool = false
    @Published var showSubscriptionOffer: Bool = false
    @Published var activeAlert: UnifiedAlert?  // Unified alert state
    @Published var pendingInterruptedSummary: Bool = false
    @Published var isHydrated: Bool = false

    @Published var currentStreak: Int = 0
    @Published var longestStreak: Int = UserDefaults.standard.integer(forKey: "jn_longest_streak")

    private var dayTickTimer: Timer?

    // Guard to avoid saving while restoring from disk
    private var isRestoring: Bool = false

    @Published var modes: [Mode] = [] {
        didSet { if !isRestoring { saveModes() } }
    }
    @Published var selectedMode: Mode? {
        didSet { if !isRestoring { saveSelectedMode() } }
    }
    @Published var showVoiceJournal: Bool = false
    @Published var transcriptionHistory: [TranscriptionResponse] = []

    // Emergency Unzap tokens
    @Published var emergencyUnzapCount: Int {
        didSet { UserDefaults.standard.set(emergencyUnzapCount, forKey: emergencyUnzapKey) }
    }

    private let authorizedTagUIDs: Set<String> = [
        "tschoni",
        "Tschoni",
        "ZAP-123456",
    ]

    var timer: Timer?
    var session: NFCNDEFReaderSession?
    var store = ManagedSettingsStore()
    private var sessionStartDate: Date?
    private var unauthorizedTagDetected = false
    private let activeModeIdKey = "activeModeId"
    private let selectedModeKey = "selectedModeID"

    var scanningPurpose: ScanningPurpose?
    private let logger = Logger(subsystem: "com.stilltschoni.justnoiseradioapp", category: "NFCViewModel")
    @Published var liveActivity: Activity<SessionAttributes>?

    // Keys
    private let activationKey = "isActivated"
    private let appGroupID = "group.stilltschoni.Noise"
    private let sessionActiveKey = "isAppsBlocked"
    private let sessionStartKey = "sessionStartDate"
    private let emergencyUnzapKey = "emergencyUnzapCount"

    // Streak keys
    private let longestStreakKey = "jn_longest_streak"
    private let lastStreakCalcKey = "jn_last_streak_calc_yyyyMMdd"

    private let cal = Calendar.current

    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: appGroupID)
    }

    // 🔗 POSTHOG: link reflections to sessions
    @Published var currentSessionId: String? = nil

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
        loadTranscriptions()
        loadSessions()
        loadActivationStatus()
        loadSelectedMode()       // restore last user choice if possible
        isRestoring = false      // from now on, saves are allowed

        // Mirror last known state
        restoreBlockingState()

        // Streaks & daily tick
        recalcStreaks()
        startDayTick()

        // Sticky activation — never regress after real activation
        if !isActivated {
            if let shared = sharedDefaults?.object(forKey: activationKey) as? Bool, shared {
                isActivated = true
            } else if UserDefaults.standard.bool(forKey: activationKey) {
                isActivated = true
            }
            if isActivated { saveActivationStatus() }
        }

        isHydrated = true
        logger.info("Hydration completed. UI may proceed.")
    }

    // MARK: - Activation
    func loadActivationStatus() {
        if let shared = sharedDefaults?.object(forKey: activationKey) as? Bool, shared {
            isActivated = true
        } else if UserDefaults.standard.bool(forKey: activationKey) {
            isActivated = true
        }
    }

    func saveActivationStatus() {
        UserDefaults.standard.set(isActivated, forKey: activationKey)
        sharedDefaults?.set(isActivated, forKey: activationKey)
        sharedDefaults?.synchronize()
    }

    // MARK: - Emergency Unzap
    func useEmergencyUnzap() {
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
    func restoreBlockingState() {
        guard sharedDefaults?.bool(forKey: sessionActiveKey) == true else { return }

        // Recover selected mode for an *active* session (fallback if not already set)
        if selectedMode == nil {
            if let modeIdString = sharedDefaults?.string(forKey: activeModeIdKey),
               let modeId = UUID(uuidString: modeIdString),
               let restoredMode = modes.first(where: { $0.id == modeId }) {
                selectedMode = restoredMode
            }
        }

        isAppsBlocked = true

        if let savedStart = sharedDefaults?.object(forKey: sessionStartKey) as? Date,
           savedStart <= Date() {
            sessionStartDate = savedStart
            resumeTimer()
            logger.info("Restored blocking state at \(savedStart), mode: \(self.selectedMode?.name ?? "nil")")
        } else {
            pendingInterruptedSummary = true
            logger.warning("Active session without valid start date -> will show 'Session Interrupted' UI.")
        }

        if !isActivated {
            isActivated = true
            saveActivationStatus()
        }
    }

    private func resumeTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            DispatchQueue.main.async { self.updateElapsedTime() }
        }
        self.logger.info("Timer resumed from saved session start date.")
    }

    // MARK: - NFC
    func startScanning(purpose: ScanningPurpose) {
        if purpose == .sessionToggle, isAppsBlocked, let start = sessionStartDate {
            let elapsed = Date().timeIntervalSince(start)
            if elapsed < 3 {
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

        scanningPurpose = purpose

        switch purpose {
        case .activation:
            guard !isActivated else { setError(.alreadyActivated); return }
        case .sessionToggle:
            if !isAppsBlocked {
                guard let mode = selectedMode else { setError(.invalidModeSelection); return }
                let noApps = mode.selectedApps.applicationTokens.isEmpty
                let noCategories = mode.selectedApps.categoryTokens.isEmpty
                let noWeb = mode.selectedApps.webDomainTokens.isEmpty
                if noApps && noCategories && noWeb { setError(.invalidModeSelection); return }
            }
        }

        guard NFCNDEFReaderSession.readingAvailable else { setError(.nfcNotAvailable); return }

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

            for message in messages {
                for record in message.records {
                    guard record.payload.count > 3 else { self.setError(.invalidNFCTag); continue }
                    let payloadData = record.payload
                    let payloadSubstring = payloadData.dropFirst(3)
                    if let payload = String(data: Data(payloadSubstring), encoding: .utf8) {
                        switch self.scanningPurpose {
                        case .activation: self.validateTag(payload: payload)
                        case .sessionToggle: self.toggleAppBlocking()
                        case .none: self.setError(.unknown(description: "Unknown scan purpose."))
                        }
                    } else {
                        self.setError(.invalidNFCTag)
                    }
                }
            }

            session.invalidate()
            self.session = nil
            self.logger.info("NFC session closed after first valid read.")
        }
    }

    func readerSessionDidBecomeActive(_ session: NFCNDEFReaderSession) {
        logger.info("NFC session did become active.")
    }

    private func validateTag(payload: String) {
        if authorizedTagUIDs.contains(payload) {
            isActivated = true
            saveActivationStatus()
            showAlertWith(message: "JustNoise activated!")
            Analytics.capture("activation_successful", props: [
                "timestamp": Date().timeIntervalSince1970,
                "nfc_tag_id": payload
            ])
        } else {
            unauthorizedTagDetected = true
            setError(.unauthorizedNFCTag)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                self.session?.invalidate()
                self.session = nil
            }
        }
    }

    func toggleAppBlocking() {
        if isAppsBlocked {
            if let startDate = sessionStartDate {
                let elapsed = Date().timeIntervalSince(startDate)
                if elapsed < 3 {
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
            blockApplications()
        }
    }

    // MARK: - Start/Stop (manual start still allowed)
    func blockApplications() {
        // ⬇️ Removed: scheduling startAt parameter
        guard let mode = selectedMode else { setError(.invalidModeSelection); return }
        guard !selectionIsEmpty(mode.selectedApps) else { setError(.invalidModeSelection); return }

        store.shield.applications = mode.selectedApps.applicationTokens
        store.shield.applicationCategories = ShieldSettings.ActivityCategoryPolicy.specific(
            mode.selectedApps.categoryTokens, except: []
        )
        store.shield.webDomains = mode.selectedApps.webDomainTokens
        store.shield.webDomainCategories = ShieldSettings.ActivityCategoryPolicy.specific(
            mode.selectedApps.categoryTokens, except: []
        )

        isAppsBlocked = true
        sessionStartDate = Date()
        sharedDefaults?.set(true, forKey: sessionActiveKey)
        sharedDefaults?.set(sessionStartDate, forKey: sessionStartKey)
        sharedDefaults?.set(mode.id.uuidString, forKey: activeModeIdKey)
        startTimer(using: sessionStartDate!)
        startLiveActivity()

        let sid = UUID().uuidString
        currentSessionId = sid
        Analytics.capture("focus_session_started", props: [
            "timestamp": Date().timeIntervalSince1970,
            "session_id": sid,
            "mode": mode.name
        ])

        logger.info("Blocking applied. Start: \(self.sessionStartDate!), mode: \(mode.name)")
    }

    func unblockApplications() {
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
        sharedDefaults?.set(false, forKey: sessionActiveKey)
        sharedDefaults?.removeObject(forKey: sessionStartKey)
        sharedDefaults?.removeObject(forKey: activeModeIdKey)

        // ⬇️ Removed: JNOverride/manual grace logic tied to schedules

        stopTimerAndSaveSession()
        endLiveActivity()
        currentSessionId = nil

        logger.info("All apps, categories, and web domains unblocked.")
    }

    // MARK: - Timer
    private func startTimer(using start: Date) {
        sessionStartDate = start
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            DispatchQueue.main.async { self.updateElapsedTime() }
        }
        self.logger.info("Timer started.")
    }

    private func updateElapsedTime() {
        if let startDate = sessionStartDate {
            self.elapsedTime = Date().timeIntervalSince(startDate)
        }
    }

    private func stopTimerAndSaveSession() {
        timer?.invalidate()
        timer = nil
        updateElapsedTime()
        let sessionObj = Session(startDate: sessionStartDate ?? Date(), duration: elapsedTime, modeName: selectedMode?.name)
        sessionHistory.append(sessionObj)
        saveSessions()
        recalcStreaks()
        self.logger.info("Timer stopped. Session saved.")
        sessionStartDate = nil

        DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
            let shouldShow = UserDefaults.standard.object(forKey: "showPostSessionJournalPrompt") as? Bool ?? true
            if shouldShow {
                self.activeAlert = UnifiedAlert.reflectionPrompt
                self.logger.info("activeAlert set to reflectionPrompt")
            } else {
                self.logger.info("reflectionPrompt suppressed by user preference")
            }
        }
    }

    @Published var schedules: [Schedule] = [] { didSet { saveSchedules() } }

    func loadSchedules() {
        if let data = UserDefaults.standard.data(forKey: "schedules"),
           let saved = try? JSONDecoder().decode([Schedule].self, from: data) {
            schedules = saved
        }
    }

    func saveSchedules() {
        if let data = try? JSONEncoder().encode(schedules) {
            UserDefaults.standard.set(data, forKey: "schedules")
        }
    }

    func deleteSchedule(at offsets: IndexSet) {
        schedules.remove(atOffsets: offsets)
        saveSchedules()
    }

    @MainActor
    func foregroundResync() async {
        let ud = JNShared.suite
        let blocked = ud.bool(forKey: SharedKeys.isAppsBlockedKey)
        if blocked != isAppsBlocked { isAppsBlocked = blocked }

        if let modeStr = ud.string(forKey: SharedKeys.activeModeIdKey),
           let uuid = UUID(uuidString: modeStr),
           let m = modes.first(where: { $0.id == uuid }),
           selectedMode?.id != m.id {
            selectedMode = m
        }

        loadSchedules()
    }

    // MARK: - Live Activity
    func startLiveActivity() {
        if ActivityAuthorizationInfo().areActivitiesEnabled {
            let attributes = SessionAttributes(modeName: selectedMode?.name ?? "JustNoise")
            let initialContentState = SessionAttributes.ContentState(startDate: sessionStartDate ?? Date())
            Task {
                do {
                    let requested = try Activity<SessionAttributes>.request(
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
    }

    func endLiveActivity() {
        Task { @MainActor in
            if let liveActivity = self.liveActivity {
                await liveActivity.end(nil, dismissalPolicy: .immediate)
                self.liveActivity = nil
                print("Ended Live Activity")
            }
        }
    }

    func formattedElapsedTime() -> String {
        let totalSeconds = Int(elapsedTime)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
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
        case .audioRecordingFailed:        return "audio_recording_failed"
        case .audioUploadFailed:           return "audio_upload_failed"
        case .transcriptionDecodingFailed: return "transcription_failed"
        case .fileSavingFailed:            return "file_saving_failed"
        case .nfcSessionFailed:            return "nfc_session_failed"
        case .invalidModeSelection:        return "invalid_mode_selection"
        case .networkUnavailable:          return "network_unavailable"
        case .unauthorizedNFCTag:          return "unauthorized_tag"
        case .nfcNotAvailable:             return "nfc_not_available"
        case .activationSessionTimeout:    return "activation_session_timeout"
        case .blockingSessionTimeout:      return "blocking_session_timeout"
        case .alreadyActivated:            return "already_activated"
        case .nfcSessionTimeout:           return "nfc_session_timeout"
        case .invalidNFCTag:               return "invalid_nfc_tag"
        case .unknown:                     return "unknown"
        }
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
        if let selectedMode = selectedMode {
            UserDefaults.standard.set(selectedMode.id.uuidString, forKey: selectedModeKey)
        }
    }

    func loadSelectedMode() {
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
        saveModes()
    }

    func updateMode(_ mode: Mode) {
        if let index = modes.firstIndex(where: { $0.id == mode.id }) {
            modes[index] = mode
            saveModes()
        }
    }

    func deleteMode(at offsets: IndexSet) {
        modes.remove(atOffsets: offsets)
        saveModes()
        // If we deleted the selected one, pick a safe fallback
        if let sm = selectedMode, !modes.contains(where: { $0.id == sm.id }) {
            selectedMode = modes.first
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

    // MARK: - Reflection & Transcriptions
    func handleReflectionResponse(startReflecting: Bool) {
        activeAlert = nil
        if startReflecting { showVoiceJournal = true }
    }

    func saveTranscription(_ transcription: TranscriptionResponse) {
        transcriptionHistory.append(transcription)
        if let encoded = try? JSONEncoder().encode(transcriptionHistory) {
            UserDefaults.standard.set(encoded, forKey: "transcriptionHistory")
        }
    }

    func loadTranscriptions() {
        if let savedData = UserDefaults.standard.data(forKey: "transcriptionHistory"),
           let savedTranscriptions = try? JSONDecoder().decode([TranscriptionResponse].self, from: savedData) {
            transcriptionHistory = savedTranscriptions
        }
    }

    func loadSessions() {
        if let data = UserDefaults.standard.data(forKey: "sessionHistory"),
           let savedSessions = try? JSONDecoder().decode([Session].self, from: data) {
            sessionHistory = savedSessions
        }
        recalcStreaks()
    }

    func saveSessions() {
        if let data = try? JSONEncoder().encode(sessionHistory) {
            UserDefaults.standard.set(data, forKey: "sessionHistory")
        }
    }

    func deleteSession(_ session: Session) {
        if let index = sessionHistory.firstIndex(where: { $0.id == session.id }) {
            sessionHistory.remove(at: index)
            saveSessions()
        }
    }

    func addTranscriptionToLatestSession(transcription: TranscriptionResponse, audioURL: URL? = nil) {
        guard !sessionHistory.isEmpty else {
            self.logger.warning("No session available to attach transcription.")
            return
        }
        let last = sessionHistory.count - 1
        sessionHistory[last].transcription = transcription
        if let audioURL = audioURL {
            sessionHistory[last].audioFileURL = audioURL
        }
        saveSessions()
        self.logger.info("Transcription attached to latest session\(audioURL != nil ? " with audio." : ".")")
    }
}

// MARK: - 🔥 Streaks (helpers)
private extension NFCViewModel {
    func uniqueSessionDays() -> [Date] {
        sessionHistory
            .map { cal.startOfDay(for: $0.startDate) }
            .uniqued()
            .sorted()
    }

    func hasSession(on day: Date) -> Bool {
        let sod = cal.startOfDay(for: day)
        return sessionHistory.contains { cal.isDate(cal.startOfDay(for: $0.startDate), inSameDayAs: sod) }
    }

    func recalcStreaks(now: Date = Date()) {
        let today = cal.startOfDay(for: now)
        var count = 0
        var cursor = today

        while hasSession(on: cursor) {
            count += 1
            guard let prev = cal.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = prev
        }

        currentStreak = count
        if currentStreak > longestStreak {
            longestStreak = currentStreak
            UserDefaults.standard.set(longestStreak, forKey: longestStreakKey)
        }

        let fmt = DateFormatter(); fmt.dateFormat = "yyyyMMdd"
        UserDefaults.standard.set(fmt.string(from: today), forKey: lastStreakCalcKey)
    }

    func startDayTick() {
        dayTickTimer?.invalidate()
        var lastDay = cal.startOfDay(for: Date())
        dayTickTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            let nowDay = self.cal.startOfDay(for: Date())
            if nowDay != lastDay {
                lastDay = nowDay
                self.recalcStreaks(now: Date())
            }
        }
    }

    var hasSessionToday: Bool {
        hasSession(on: Date())
    }
}

// MARK: - Selection/Auth helpers
private extension NFCViewModel {
    func selectionIsEmpty(_ sel: FamilyActivitySelection) -> Bool {
        sel.applicationTokens.isEmpty && sel.categoryTokens.isEmpty && sel.webDomainTokens.isEmpty
    }

    /// Request Screen Time auth if needed and ensure the current selected mode targets something.
    func ensureAuthorizationAndSelection() async throws {
        if AuthorizationCenter.shared.authorizationStatus != .approved {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            guard AuthorizationCenter.shared.authorizationStatus == .approved else {
                throw AppError.unknown(description: "Screen Time permission not granted.")
            }
        }
        if let sm = selectedMode, selectionIsEmpty(sm.selectedApps) {
            throw AppError.invalidModeSelection
        }
    }
}

// MARK: - Small utility
private extension Sequence where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return self.filter { seen.insert($0).inserted }
    }
}

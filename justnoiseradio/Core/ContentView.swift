// ContentView.swift

import SwiftUI
import FamilyControls
import ManagedSettings
import Combine

struct AlertItem: Identifiable {
    let id = UUID()
    let title: Text
    let message: Text?
    let dismissAction: (() -> Void)?
}

struct ContentView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.showPostSessionJournalPrompt) private var showPostSessionJournalPrompt
    @State private var authorizationStatus = AuthorizationCenter.shared.authorizationStatus

    // Keep the enum private
    enum ActiveSheet: Identifiable {
        case appControl, sessionHistory, settings, schedules, voiceJournal
        var id: Int { hashValue }
    }

    @State private var activeSheet: ActiveSheet?
    @State private var lastPresentedSheet: ActiveSheet?
    @State private var mirrorCancellable: AnyCancellable?   // ⬅️ timer
    

    // MARK: - Coach Marks
    @AppStorage("hasCompletedCoachMarks") private var hasCompletedCoachMarks: Bool = false
    @State private var showCoachMarks = false
    @State private var coachStep = 0
    
    // MARK: - UI: Streak Badge (minimal, hides when 0)
    struct StreakBadge: View {
        let streak: Int
        var body: some View {
            if streak > 0 {
                HStack(spacing: 8) {
                    Text("🔥").font(.system(size: 18)).accessibilityHidden(true)
                    Text("\(streak)-day streak")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000))
                        .transition(.opacity.combined(with: .scale))
                        .id(streak)
                }
                .padding(.vertical, 4)
                .padding(.horizontal, 12)
                .background(Capsule().fill(Color.white.opacity(0.06)))
                .overlay(Capsule().stroke(Color.white.opacity(0.08), lineWidth: 1))
                .accessibilityLabel(Text("Streak \(streak) days"))
                .animation(.spring(response: 0.35, dampingFraction: 0.8), value: streak)
            }
        }
    }

    enum TourPhase { case basic, extendedPreStart, extendedRunning, extendedPostStop, extendedFinished, none }
    @State private var tourPhase: TourPhase = .basic
    @State private var showStartFirstSessionPrompt = false
    @State private var wasBlocked = false
    @State private var showCongrats = false
    
    struct RoundedCorner: Shape {
        var radius: CGFloat = 0.0
        var corners: UIRectCorner = .allCorners
        func path(in rect: CGRect) -> Path {
            let path = UIBezierPath(
                roundedRect: rect,
                byRoundingCorners: corners,
                cornerRadii: CGSize(width: radius, height: radius)
            )
            return Path(path.cgPath)
        }
    }
    
    var body: some View {
        NavigationStack {
            mainScreen()
                .toolbar { toolbarContent() }

                // ✅ One sheet only — switch inside
                .sheet(item: $activeSheet, onDismiss: {
                    // Always ensure flags are reset so we never bounce back in
                    nfcViewModel.showVoiceJournal = false

                    // If we just dismissed VoiceJournal, decide where to go next.
                    if lastPresentedSheet == .voiceJournal {
                        defer { lastPresentedSheet = nil }

                        if tourPhase == .extendedPostStop {
                            // Guided onboarding → open History with congrats
                            DispatchQueue.main.async {
                                showCongrats = true
                                tourPhase = .extendedFinished
                                activeSheet = .sessionHistory
                            }
                        }
                        return
                    }

                    // If we dismissed something else in guided flow *after* journaling, keep the intended route.
                    if tourPhase == .extendedPostStop, activeSheet == nil {
                        DispatchQueue.main.async {
                            showCongrats = true
                            tourPhase = .extendedFinished
                            activeSheet = .sessionHistory
                        }
                    }
                }) { sheet in
                    switch sheet {
                    case .appControl:
                        ModesView().environmentObject(nfcViewModel)
                            .presentationDetents([.large])

                    case .sessionHistory:
                        HistoryWrapperView(showCongrats: $showCongrats) {
                            SessionHistoryView().environmentObject(nfcViewModel)
                        }
                        .presentationDetents([.large])

                    case .settings:
                        SettingsView().environmentObject(nfcViewModel)
                            .presentationDetents([.large])

                    case .schedules:
                        SchedulesView().environmentObject(nfcViewModel)
                            .presentationDetents([.medium, .large])

                    case .voiceJournal:
                        VoiceJournalView(onFlowEnded: {
                            lastPresentedSheet = .voiceJournal
                        })
                        .environmentObject(nfcViewModel)
                        .presentationDetents([.large])
                        .interactiveDismissDisabled(false)
                    }
                }

                // Alerts
                .alert(item: $nfcViewModel.activeAlert) { unifiedAlert in
                    switch unifiedAlert {
                    case .error(let alertItem):
                        return Alert(
                            title: alertItem.title,
                            message: alertItem.message,
                            dismissButton: .default(Text("OK"), action: alertItem.dismissAction)
                        )
                    case .reflectionPrompt:
                        return Alert(
                            title: Text("Ready to reflect?"),
                            message: Text("60 sek of Brain dump is a great way to process your thoughts and emotions. Ready to get started?"),
                            primaryButton: .default(Text("Start")) {
                                nfcViewModel.handleReflectionResponse(startReflecting: true)
                                presentVoiceJournal()
                            },
                            secondaryButton: .cancel(Text("Skip")) {
                                nfcViewModel.handleReflectionResponse(startReflecting: false)
                            }
                        )
                    }
                }
                .alert("Start your first session now?", isPresented: $showStartFirstSessionPrompt) {
                    Button("Not now", role: .cancel) { tourPhase = .none }
                    Button("Yes, guide me") {
                        tourPhase = .extendedPreStart
                        coachStep = 0
                        showCoachMarks = true
                    }
                } message: {
                    Text("We’ll guide you to pick a mode, start, stop, and review it in History.")
                }
        }
        // Coach marks overlay host
        .overlayPreferenceValue(CoachMarkFramesKey.self) { frames in
            let setToShow = CoachMarksFactory.marks(for: tourPhase)
            CoachMarksOverlay(
                isPresented: $showCoachMarks,
                stepIndex: $coachStep,
                marks: setToShow,
                frames: frames,
                onFinish: {
                    if tourPhase == .basic {
                        hasCompletedCoachMarks = true
                        showStartFirstSessionPrompt = true
                    } else {
                        showCoachMarks = false
                    }
                }
            )
        }
        .onAppear(perform: onAppear)
        .onDisappear(perform: onDisappear) // ⬅️ make sure timer is cancelled
        .onChange(of: AuthorizationCenter.shared.authorizationStatus) { _, newStatus in
            authorizationStatus = newStatus
        }
        .onChange(of: nfcViewModel.isAppsBlocked) { _, newValue in
            handleBlockStateChange(newValue)
        }
        .onChange(of: hasCompletedCoachMarks) { _, newValue in
            if newValue == false && !nfcViewModel.isAppsBlocked {
                tourPhase = .basic
                coachStep = 0
                showCoachMarks = true
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .didReceiveDeepLink)) { note in
            guard let type = note.userInfo?["type"] as? DeepLinkType else { return }
            switch type {
            case .startSession:
                if let modeId = note.userInfo?["modeId"] as? UUID,
                   let m = nfcViewModel.modes.first(where: { $0.id == modeId }) {
                    nfcViewModel.selectedMode = m
                }
            case .streakSave:
                if nfcViewModel.selectedMode == nil {
                    nfcViewModel.selectedMode = nfcViewModel.modes.first
                }
            }
        }
    }
}

// MARK: - Top-level UI building blocks
private extension ContentView {
    @ViewBuilder
    func mainScreen() -> some View {
        ZStack(alignment: .bottom) {
            backgroundColor.ignoresSafeArea()
            if authorizationStatus == .approved {
                approvedColumn()
            } else {
                notApprovedColumn()
            }
            if !nfcViewModel.isAppsBlocked {
                HistorySectionView(activeSheet: $activeSheet)
                    .environmentObject(nfcViewModel)
                    .coachMarkTarget(id: "history")
            }
        }
    }
    
    @ViewBuilder
    func approvedColumn() -> some View {
        VStack(spacing: 20) {
            Text(nfcViewModel.isAppsBlocked ? "Tap to Unlock" : "Tap to Lock")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(foregroundColor)
                .padding(.top, 80)

            scanButton()
                .frame(width: 220, height: 220)
                .padding(.top, 20)
                .coachMarkTarget(id: "scan")
            
            if !nfcViewModel.isAppsBlocked {
                modePickerBlock()
            } else {
                lockedInfoCard()
            }
            Spacer()
        }
        .padding(.bottom, 80)
    }
    
    @ViewBuilder
    func notApprovedColumn() -> some View {
        VStack(spacing: 20) {
            Text("Tap to Lock")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(foregroundColor)
                .padding(.top, 80)
            
            NFCScanButton(action: { requestAuthorization() },
                          isBlocked: nfcViewModel.isAppsBlocked)
            .frame(width: 220, height: 220)
            .padding(.top, 20)
            
            Spacer()
        }
        .padding(.bottom, 80)
    }
    
    @ViewBuilder
    func scanButton() -> some View {
        NFCScanButton(
            action: {
                if authorizationStatus == .approved {
                    nfcViewModel.startScanning(purpose: .sessionToggle)
                } else {
                    requestAuthorization()
                }
            },
            isBlocked: nfcViewModel.isAppsBlocked,
            longPressAction: {
                if !nfcViewModel.isAppsBlocked { nfcViewModel.blockApplications() }
            }
        )
    }
    
    @ViewBuilder
    func modePickerBlock() -> some View {
        Text("Select a mode")
            .font(.system(size: 14, weight: .regular))
            .foregroundColor(Color(#colorLiteral(red: 0.4549, green: 0.4549, blue: 0.4549, alpha: 1)))
            .padding(.top, 30)
        
        Button {
            activeSheet = .appControl
        } label: {
            HStack(spacing: 2) {
                Text(nfcViewModel.selectedMode?.name ?? "Select a Mode")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                Image(systemName: "chevron.down")
                    .font(.system(size: 14, weight: .regular))
                    .foregroundColor(.white)
            }
            .padding(10)
            .frame(width: 220, height: 38)
            .overlay(
                RoundedRectangle(cornerRadius: 19)
                    .stroke(Color(#colorLiteral(red: 0.8667, green: 1.0, blue: 0.0, alpha: 1.0)), lineWidth: 0.5)
            )
        }
        .coachMarkTarget(id: "mode")
    }
    
    @ViewBuilder
    func lockedInfoCard() -> some View {
        VStack(spacing: 10) {
            Text("Locked in for")
                .font(.caption)
                .foregroundColor(.white)
                .textCase(.uppercase)
                .padding(.bottom, 2)
                .fixedSize()
            
            Text("\(nfcViewModel.formattedElapsedTime())")
                .font(.custom("Technology-Bold", size: 52))
                .foregroundColor(Color(red: 0.843, green: 0.980, blue: 0.000))
                .tracking(1)
                .multilineTextAlignment(.center)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .fixedSize()
                .frame(width: 180)
            
            if let modeName = nfcViewModel.selectedMode?.name {
                Text(modeName)
                    .font(.caption)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .textCase(.uppercase)
                    .fixedSize()
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(red: 0.094, green: 0.094, blue: 0.102))
                .shadow(color: Color.black.opacity(0.4), radius: 5, x: 0, y: 3)
        )
        .frame(width: 300, height: 140)
        .fixedSize()
        .padding(.top, 30)
    }
}

// MARK: - Toolbar
private extension ContentView {
    @ToolbarContentBuilder
    func toolbarContent() -> some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Image("Justnoise_logo_nav")
                .resizable()
                .scaledToFit()
                .frame(height: 15)
        }
        ToolbarItem(placement: .navigationBarLeading) {
            if !nfcViewModel.isAppsBlocked {
                Button { activeSheet = .schedules } label: {
                    Image(systemName: "calendar")
                        .imageScale(.large)
                        .foregroundColor(.white)
                }
                .coachMarkToolbarTarget(id: "schedule")
            }
        }

        ToolbarItem(placement: .navigationBarTrailing) {
            Button { activeSheet = .settings } label: {
                Image(systemName: "gear").imageScale(.large).foregroundColor(nfcViewModel.isAppsBlocked ? .black : .white)
            }
            .coachMarkToolbarTarget(id: "settings")
        }
    }
}

// MARK: - Coach marks set builder
private enum CoachMarksFactory {
    static func marks(for phase: ContentView.TourPhase) -> [CoachMark] {
        switch phase {
        case .basic:
            return [
                CoachMark(targetID: "scan", title: "Tap to Start/Stop",
                          message: "This is your Zap button. Tap to start focus, tap again to end.",
                          padding: 8, offset: CGSize(width: 0, height: 20)),
                CoachMark(targetID: "mode", title: "Choose a Mode",
                          message: "Pick which apps/sites you want blocked before you start."),
                CoachMark(targetID: "settings", title: "Settings",
                          message: "Manage account, subscription, and app preferences here."),
                CoachMark(targetID: "schedule", title: "Schedules",
                          message: "Plan automatic focus sessions. Set start times and modes, and we’ll block for you.",
                          offset: CGSize(width: 0, height: 6)),
                CoachMark(targetID: "history", title: "Your History",
                          message: "Swipe up or tap here to review past sessions.")
            ]
        case .extendedPreStart:
            return [
                CoachMark(targetID: "mode", title: "Pick a Mode",
                          message: "Choose which apps and sites to block for this session."),
                CoachMark(targetID: "scan", title: "Start Your Session",
                          message: "Tap the Zap button to begin.", offset: CGSize(width: 0, height: 16))
            ]
        case .extendedRunning:
            return [ CoachMark(targetID: "scan", title: "Finish When You’re Done",
                               message: "Tap the Zap button again to end the session.") ]
        case .extendedPostStop:
            return [ CoachMark(targetID: "scan", title: "Reflect on Your Session",
                               message: "We’ll prompt you to reflect now. Start a quick voice journal to capture your thoughts.",
                               offset: CGSize(width: 0, height: 12)) ]
        case .extendedFinished, .none:
            return []
        }
    }
}

// MARK: - Behavior (lifecycle & state changes)
private extension ContentView {
    func onAppear() {
        updateAuthorizationStatus()
        if authorizationStatus != .approved { requestAuthorization() }
        if !hasCompletedCoachMarks && !nfcViewModel.isAppsBlocked {
            tourPhase = .basic
            coachStep = 0
            showCoachMarks = true
        }
        startAppGroupMirror() // ⬅️ FIX: invoke the function
    }
    
    func startAppGroupMirror() {
        // Poll every 3s; cheap + reliable. If you prefer, move to BGTask later.
        mirrorCancellable?.cancel()
        mirrorCancellable = Timer
            .publish(every: 3.0, on: .main, in: .common)
            .autoconnect()
            .sink { _ in
                mirrorOnceFromAppGroup()
            }
        
        // Immediate sync on appear to catch any running interval
        mirrorOnceFromAppGroup()
    }

    func onDisappear() {
        mirrorCancellable?.cancel()
        mirrorCancellable = nil
    }
    
    func handleBlockStateChange(_ blocked: Bool) {
        let started = (!wasBlocked && blocked)
        let ended   = (wasBlocked && !blocked)
        wasBlocked = blocked

        if blocked { showCoachMarks = false }

        switch tourPhase {
        case .extendedPreStart where started:
            tourPhase = .extendedRunning
            coachStep = 0
            showCoachMarks = true

        case .extendedRunning where ended:
            tourPhase = .extendedPostStop
            coachStep = 0
            showCoachMarks = true
            DispatchQueue.main.async {
                if showPostSessionJournalPrompt.wrappedValue {
                    nfcViewModel.activeAlert = .reflectionPrompt
                }
            }

        default:
            break
        }
    }

    // 🔹 Read App Group → apply to UI/ViewModel
    func mirrorOnceFromAppGroup() {
        let ud = JNShared.suite
        let blocked = ud.bool(forKey: SharedKeys.isAppsBlockedKey)
        let modeStr = ud.string(forKey: SharedKeys.activeModeIdKey)

        var changed = false
        if blocked != nfcViewModel.isAppsBlocked {
            nfcViewModel.isAppsBlocked = blocked
            changed = true
        }
        if let modeStr, let uuid = UUID(uuidString: modeStr),
           let m = nfcViewModel.modes.first(where: { $0.id == uuid }),
           nfcViewModel.selectedMode?.id != m.id {
            nfcViewModel.selectedMode = m
            changed = true
        }
        if changed {
            Task { await nfcViewModel.foregroundResync() }
        }
    }

    // 🔹 Centralized presenter
    func presentVoiceJournal() {
        lastPresentedSheet = .voiceJournal
        nfcViewModel.showVoiceJournal = true
        activeSheet = .voiceJournal
    }
}

// MARK: - Colors & auth
private extension ContentView {
    var backgroundColor: Color {
        nfcViewModel.isAppsBlocked ? .white : Color(#colorLiteral(red: 0.082, green: 0.082, blue: 0.082, alpha: 1.0))
    }
    var foregroundColor: Color {
        nfcViewModel.isAppsBlocked ? .black : .white
    }
    
    func requestAuthorization() {
        Task {
            do {
                try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                await MainActor.run {
                    updateAuthorizationStatus()
                    if authorizationStatus != .approved {
                        nfcViewModel.setError(.unknown(description: "Authorization denied. Enable in settings or tap Scan again."))
                    }
                }
            } catch {
                await MainActor.run {
                    nfcViewModel.setError(.unknown(description: "Authorization failed: \(error.localizedDescription)"))
                }
            }
        }
    }
    
    @MainActor
    func updateAuthorizationStatus() {
        authorizationStatus = AuthorizationCenter.shared.authorizationStatus
    }
}

// MARK: - Bottom bar section (uses unified sheet)
extension ContentView {
    struct HistorySectionView: View {
        @EnvironmentObject var nfcViewModel: NFCViewModel
        @Binding var activeSheet: ActiveSheet?
        var body: some View {
            VStack {
                Spacer()
                Color.black
                    .frame(height: 160)
                    .clipShape(
                        RoundedCorner(radius: 20, corners: [.topLeft, .topRight])
                    )
                    .overlay(
                        VStack(spacing: 8) {
                            Capsule()
                                .fill(Color.gray.opacity(0.4))
                                .frame(width: 36, height: 5)
                                .padding(.top, 10)
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text("HISTORY")
                                        .font(.custom("Technology-Bold", size: 36))
                                        .foregroundColor(.white)
                                    Spacer()
                                }
                                ContentView.StreakBadge(streak: nfcViewModel.currentStreak)
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 10)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    )
                    .onTapGesture { activeSheet = .sessionHistory }
                    .gesture(
                        DragGesture(minimumDistance: 20)
                            .onEnded { value in
                                if value.translation.height < 0 { activeSheet = .sessionHistory }
                            }
                    )
            }
            .frame(maxWidth: .infinity)
            .edgesIgnoringSafeArea(.bottom)
        }
    }
    
    struct HistoryWrapperView<Content: View>: View {
        @Binding var showCongrats: Bool
        let content: () -> Content
        var body: some View {
            content()
                .alert("Nice work! 🎉", isPresented: $showCongrats) {
                    Button("Done", role: .cancel) { }
                } message: {
                    Text("You’ve completed your first guided session and review.")
                }
        }
    }
}

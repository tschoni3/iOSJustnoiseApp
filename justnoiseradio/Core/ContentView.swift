// ContentView.swift

import SwiftUI
import FamilyControls
import ManagedSettings
import Combine
import UIKit

struct AlertItem: Identifiable {
    let id = UUID()
    let title: Text
    let message: Text?
    let dismissAction: (() -> Void)?
}

enum MainTab: Hashable {
    case zap
    case capture
}

struct MainTabView: View {
    @State private var selectedTab: MainTab = .zap

    init() {
        configureTabBarAppearance()
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            ContentView(selectedTab: $selectedTab)
            .tabItem {
                Label("Zap", systemImage: "bolt.horizontal")
                    .environment(\.symbolVariants, selectedTab == .zap ? .fill : .none)
            }
            .tag(MainTab.zap)

            SignalTabView()
            .tabItem {
                Image(selectedTab == .capture ? "SignalTabFilled" : "SignalTabOutline")
                    .renderingMode(.template)
                Text("Capture")
            }
            .tag(MainTab.capture)
        }
    }

    private func configureTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(red: 0.08, green: 0.08, blue: 0.08, alpha: 0.98)
        appearance.shadowColor = UIColor.white.withAlphaComponent(0.08)

        let selectedColor = UIColor.white
        let normalColor = UIColor.white.withAlphaComponent(0.3)
        let appearances = [
            appearance.stackedLayoutAppearance,
            appearance.inlineLayoutAppearance,
            appearance.compactInlineLayoutAppearance
        ]

        appearances.forEach { itemAppearance in
            itemAppearance.selected.iconColor = selectedColor
            itemAppearance.selected.titleTextAttributes = [.foregroundColor: selectedColor]
            itemAppearance.normal.iconColor = normalColor
            itemAppearance.normal.titleTextAttributes = [.foregroundColor: normalColor]
        }

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
        UITabBar.appearance().tintColor = selectedColor
        UITabBar.appearance().unselectedItemTintColor = normalColor
    }
}

private struct SignalTabView: View {
    @EnvironmentObject private var nfcViewModel: NFCViewModel
    @State private var activeSheet: SignalTabSheet?

    private enum SignalTabSheet: Identifiable {
        case sessionHistory
        case schedules

        var id: Int { hashValue }
    }

    var body: some View {
        NavigationStack {
            SignalTapeView()
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button { activeSheet = .schedules } label: {
                            Image(systemName: "calendar")
                                .imageScale(.large)
                                .foregroundColor(.white)
                        }
                    }

                    ToolbarItem(placement: .principal) {
                        JustnoiseToolbarLogo()
                    }

                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button { activeSheet = .sessionHistory } label: {
                            HistoryToolbarIcon()
                        }
                    }
                }
                .sheet(item: $activeSheet) { sheet in
                    switch sheet {
                    case .sessionHistory:
                        SessionHistoryView()
                            .environmentObject(nfcViewModel)
                            .presentationDetents([.large])
                    case .schedules:
                        SchedulesView()
                            .environmentObject(nfcViewModel)
                            .presentationDetents([.medium, .large])
                    }
                }
                .toolbarBackground(.hidden, for: .navigationBar)
                .toolbarTitleDisplayMode(.inline)
        }
        .environmentObject(nfcViewModel)
    }
}

private struct JustnoiseToolbarLogo: View {
    var foregroundColor: Color = .white

    var body: some View {
        Image("Justnoise_logo_nav")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(height: 15)
            .foregroundColor(foregroundColor)
    }
}

private struct HistoryToolbarIcon: View {
    var body: some View {
        Image(systemName: "circle.grid.2x2.fill")
            .imageScale(.large)
            .foregroundColor(.white)
    }
}

enum JustnoiseHeroMetrics {
    static let titleFontSize: CGFloat = 50
    static let subtitleFontSize: CGFloat = 48
    static let containerHeight: CGFloat = 118
    static let topPadding: CGFloat = 8
    static let horizontalPadding: CGFloat = 24
    static let zapButtonSlotSize: CGFloat = 240
    static let zapButtonImageSize: CGFloat = 220
    static let zapStateSlotHeight: CGFloat = 84
    static let zapTopInset: CGFloat = 0
    static let zapStateSlotSpacing: CGFloat = 26
    static let zapBottomButtonPadding: CGFloat = 12
    static let zapBottomActionReservedHeight: CGFloat = 132
    static let zapHeroToButtonMinGap: CGFloat = 48
    static let zapHeroToButtonMaxGap: CGFloat = 88
    static let toolbarPlaceholderSize: CGFloat = 36
}

struct JustnoiseHeroHeader: View {
    let title: String
    let subtitle: String
    let titleColor: Color
    let subtitleColor: Color
    var subtitleFontSize: CGFloat = JustnoiseHeroMetrics.subtitleFontSize

    var body: some View {
        VStack(alignment: .leading, spacing: -8) {
            Text(title)
                .font(.system(size: JustnoiseHeroMetrics.titleFontSize, weight: .heavy))
                .foregroundColor(titleColor)
                .minimumScaleFactor(0.7)

            Text(subtitle)
                .font(.system(size: subtitleFontSize, weight: .black))
                .foregroundColor(subtitleColor)
                .minimumScaleFactor(0.72)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct JustnoiseHeroSection: View {
    let title: String
    let subtitle: String
    let titleColor: Color
    let subtitleColor: Color
    var subtitleFontSize: CGFloat = JustnoiseHeroMetrics.subtitleFontSize

    var body: some View {
        JustnoiseHeroHeader(
            title: title,
            subtitle: subtitle,
            titleColor: titleColor,
            subtitleColor: subtitleColor,
            subtitleFontSize: subtitleFontSize
        )
        .frame(
            maxWidth: .infinity,
            minHeight: JustnoiseHeroMetrics.containerHeight,
            maxHeight: JustnoiseHeroMetrics.containerHeight,
            alignment: .topLeading
        )
        .padding(.top, JustnoiseHeroMetrics.topPadding)
        .padding(.horizontal, JustnoiseHeroMetrics.horizontalPadding)
    }
}

struct ContentView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Binding var selectedTab: MainTab
    @State private var authorizationStatus = AuthorizationCenter.shared.authorizationStatus
    @State private var showHistoryCongrats = false

    // Keep the enum private
    enum ActiveSheet: Identifiable {
        case appControl, sessionHistory, schedules
        var id: Int { hashValue }
    }

    @State private var activeSheet: ActiveSheet?
    @State private var mirrorCancellable: AnyCancellable?   // ⬅️ timer
    @State private var showNoiseRewind = false
    @State private var showSessionComplete = false
    @State private var completedSessionDuration: TimeInterval = 0
    @State private var completedSessionModeName: String = "Focus"

    // MARK: - Coach Marks
    @AppStorage("hasCompletedCoachMarks") private var hasCompletedCoachMarks: Bool = false
    @State private var showCoachMarks = false
    @State private var coachStep = 0
    


    enum TourPhase { case basic, extendedPreStart, extendedRunning, extendedPostStop, extendedFinished, none }
    @State private var tourPhase: TourPhase = .basic
    @State private var showStartFirstSessionPrompt = false
    @State private var wasBlocked = false
    
    var body: some View {
        rootStack
    }

    private var rootStack: some View {
        NavigationStack {
            mainScreen()
                .toolbar { toolbarContent() }
                .toolbarTitleDisplayMode(.inline)
                .sheet(item: $activeSheet) { sheet in
                    contentForSheet(sheet)
                }
                .alert(item: $nfcViewModel.activeAlert) { unifiedAlert in
                    alertForUnified(unifiedAlert)
                }
                .sheet(isPresented: $showSessionComplete) {
                    SessionCompleteCard(
                        modeName: completedSessionModeName,
                        duration: completedSessionDuration,
                        signalBoost: nfcViewModel.calculateSessionSignalBoost(for: completedSessionDuration),
                        onReflect: {
                            routeSessionReflectionToSignal()
                        },
                        onDone: {
                            showSessionComplete = false
                        }
                    )
                    .presentationDetents([.height(360)])
                }
                .sheet(isPresented: $showNoiseRewind) {
                    if let insight = nfcViewModel.weeklyNoiseRewindInsight {
                        NoiseRewindWeeklyView(insight: insight)
                            .environmentObject(nfcViewModel)
                    } else {
                        VStack(spacing: 16) {
                            Text("Not enough signal yet.")
                                .font(.title2.weight(.bold))

                            Text("Complete a few protected sessions to reveal your weekly pattern.")
                                .font(.body)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)

                            Button("Done") {
                                showNoiseRewind = false
                            }
                            .buttonStyle(.borderedProminent)
                            .padding(.top, 8)
                        }
                        .padding(28)
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
        ZStack {
            backgroundColor.ignoresSafeArea()
            zapColumn()
            if authorizationStatus == .approved && nfcViewModel.showNoiseRewindCard && !nfcViewModel.isAppsBlocked {
                noiseRewindReadyOverlay()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    .transition(.scale(scale: 0.94).combined(with: .opacity))
                    .zIndex(4)
            }
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.86), value: nfcViewModel.showNoiseRewindCard)
    }
    
    @ViewBuilder
    func zapColumn() -> some View {
        GeometryReader { geometry in
            let heroBlockHeight =
                JustnoiseHeroMetrics.containerHeight +
                JustnoiseHeroMetrics.topPadding
            let bottomReservedHeight = max(
                geometry.safeAreaInsets.bottom + JustnoiseHeroMetrics.zapBottomActionReservedHeight,
                JustnoiseHeroMetrics.zapBottomActionReservedHeight
            )
            let availableFlexibleGap = max(
                0,
                geometry.size.height -
                heroBlockHeight -
                bottomReservedHeight -
                JustnoiseHeroMetrics.zapButtonSlotSize -
                JustnoiseHeroMetrics.zapStateSlotHeight -
                JustnoiseHeroMetrics.zapStateSlotSpacing
            )
            let heroToButtonGap = min(
                max(
                    availableFlexibleGap * 0.5,
                    JustnoiseHeroMetrics.zapHeroToButtonMinGap
                ),
                JustnoiseHeroMetrics.zapHeroToButtonMaxGap
            )
            let buttonTop = heroBlockHeight + heroToButtonGap
            let stateSlotTop =
                buttonTop +
                JustnoiseHeroMetrics.zapButtonSlotSize +
                JustnoiseHeroMetrics.zapStateSlotSpacing

            ZStack(alignment: .top) {
                zapHeader()
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                    .padding(.top, JustnoiseHeroMetrics.zapTopInset)

                scanButton()
                    .frame(
                        width: JustnoiseHeroMetrics.zapButtonSlotSize,
                        height: JustnoiseHeroMetrics.zapButtonSlotSize
                    )
                    .padding(.top, buttonTop)
                    .coachMarkTarget(id: "scan")

                zapStateSlotPlaceholder()
                    .frame(
                        maxWidth: .infinity,
                        minHeight: JustnoiseHeroMetrics.zapStateSlotHeight,
                        maxHeight: JustnoiseHeroMetrics.zapStateSlotHeight,
                        alignment: .top
                    )
                    .padding(.top, stateSlotTop)

                if nfcViewModel.isAppsBlocked {
                    unzapButton()
                        .padding(.bottom, max(
                            geometry.safeAreaInsets.bottom + JustnoiseHeroMetrics.zapBottomButtonPadding,
                            JustnoiseHeroMetrics.zapBottomButtonPadding
                        ))
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
            }
        }
    }

    @ViewBuilder
    func unzapButton() -> some View {
        Button {
            nfcViewModel.startScanning(purpose: .sessionToggle)
        } label: {
            Text("Unzap Phone")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.yellow.opacity(0.9))
                .cornerRadius(30)
                .padding(.horizontal, 28)
        }
    }

    @ViewBuilder
    func noiseRewindReadyOverlay() -> some View {
        ZStack(alignment: .center) {
            Color.black.opacity(0.42)
                .ignoresSafeArea()

            noiseRewindReadyCard()
                .padding(.horizontal, 24)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        .ignoresSafeArea()
    }

    @ViewBuilder
    func noiseRewindReadyCard() -> some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Your Noise Rewind is ready")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 0)

                Button {
                    nfcViewModel.dismissNoiseRewindCardForNow()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white.opacity(0.78))
                        .frame(width: 34, height: 34)
                        .background(Color.white.opacity(0.10))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }

            Text("Review your focus rhythm from the past week. A quiet weekly ritual, not another interruption.")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(.white.opacity(0.62))
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)

            Button {
                nfcViewModel.markNoiseRewindSeen()
                showNoiseRewind = true
            } label: {
                HStack(spacing: 8) {
                    Text("View Rewind")
                        .font(.system(size: 16, weight: .bold))

                    Image(systemName: "arrow.up.right")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(Color(#colorLiteral(red: 0.8667, green: 1.0, blue: 0.0, alpha: 1.0)))
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(24)
        .frame(maxWidth: 360)
        .background(
            RoundedRectangle(cornerRadius: 28)
                .fill(Color.white.opacity(0.08))
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 28)
                .stroke(Color.white.opacity(0.16), lineWidth: 1)
        )
        .shadow(color: Color(#colorLiteral(red: 0.8667, green: 1.0, blue: 0.0, alpha: 1.0)).opacity(0.22), radius: 34, x: 0, y: 10)
        .shadow(color: .black.opacity(0.42), radius: 34, x: 0, y: 22)
        .overlay(alignment: .topTrailing) {
            Circle()
                .fill(Color(#colorLiteral(red: 0.8667, green: 1.0, blue: 0.0, alpha: 1.0)).opacity(0.16))
                .frame(width: 170, height: 170)
                .blur(radius: 44)
                .offset(x: 54, y: -76)
                .allowsHitTesting(false)
        }
        .clipped()
    }
    
    @ViewBuilder
    func scanButton() -> some View {
        VStack(spacing: 6) {
            Image("zap_button")
                .resizable()
                .scaledToFit()
                .frame(
                    width: JustnoiseHeroMetrics.zapButtonImageSize,
                    height: JustnoiseHeroMetrics.zapButtonImageSize
                )
                .opacity(1.0)
                .onTapGesture {
                    if authorizationStatus == .approved {
                        nfcViewModel.startScanning(purpose: .sessionToggle)
                    } else {
                        requestAuthorization()
                    }
                }
                .onLongPressGesture {
                    Task {
                        if !nfcViewModel.isAppsBlocked {
                            if AuthorizationCenter.shared.authorizationStatus != .approved {
                                do {
                                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                                } catch {
                                    await MainActor.run {
                                        nfcViewModel.setError(.unknown(description: "Authorization failed: \(error.localizedDescription)"))
                                    }
                                    return
                                }
                                if AuthorizationCenter.shared.authorizationStatus != .approved {
                                    await MainActor.run {
                                        nfcViewModel.setError(.unknown(description: "Authorization denied. Enable in Settings."))
                                    }
                                    return
                                }
                            }
                            nfcViewModel.blockApplications()
                        }
                    }
                }
        }
    }

    @ViewBuilder
    func zapHeader() -> some View {
        JustnoiseHeroSection(
            title: zapHeroTitle,
            subtitle: zapHeroSubtitle,
            titleColor: foregroundColor,
            subtitleColor: heroSubtitleColor,
            subtitleFontSize: JustnoiseHeroMetrics.subtitleFontSize
        )
    }

    @ViewBuilder
    func zapStateSlotPlaceholder() -> some View {
        if authorizationStatus == .approved && !nfcViewModel.isAppsBlocked {
            modePickerBlock()
                .frame(maxWidth: .infinity, alignment: .top)
        } else if authorizationStatus == .approved,
                  nfcViewModel.isAppsBlocked,
                  let modeName = nfcViewModel.selectedMode?.name {
            Text(modeName)
                .font(.system(size: 24, weight: .semibold))
                .foregroundColor(.black)
                .frame(maxWidth: .infinity, alignment: .top)
        } else {
            Color.clear
        }
    }
    
    @ViewBuilder
    func modePickerBlock() -> some View {
        VStack(spacing: 8) {
            Text("Select a mode")
                .font(.system(size: 14, weight: .regular))
                .foregroundColor(Color(#colorLiteral(red: 0.4549, green: 0.4549, blue: 0.4549, alpha: 1)))

            Button {
                activeSheet = .appControl
            } label: {
                HStack(spacing: 2) {
                    Text(nfcViewModel.selectedMode?.name ?? "Select a Mode")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.92))
                    Image(systemName: "chevron.down")
                        .font(.system(size: 14, weight: .regular))
                        .foregroundColor(Color.black.opacity(0.78))
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .frame(width: 220, height: 38)
                .background(
                    Capsule()
                        .fill(Color.white)
                )
                .shadow(color: .black.opacity(0.22), radius: 10, x: 0, y: 6)
                .shadow(color: .white.opacity(0.28), radius: 1, x: 0, y: -1)
                .overlay(
                    Capsule()
                        .stroke(Color.black.opacity(0.08), lineWidth: 1)
                )
            }
            .coachMarkTarget(id: "mode")
        }
    }
}



private extension ContentView {
    @ViewBuilder
    func contentForSheet(_ sheet: ActiveSheet) -> some View {
        switch sheet {
        case .appControl:
            ModesView().environmentObject(nfcViewModel)
                .presentationDetents([.large])
        case .sessionHistory:
            HistoryWrapperView(showCongrats: $showHistoryCongrats) {
                SessionHistoryView()
                    .environmentObject(nfcViewModel)
            }
            .presentationDetents([.large])
        case .schedules:
            SchedulesView().environmentObject(nfcViewModel)
                .presentationDetents([.medium, .large])
        }
    }
}

// MARK: - Toolbar
private extension ContentView {
    @ToolbarContentBuilder
    func toolbarContent() -> some ToolbarContent {
        ToolbarItem(placement: .principal) {
            JustnoiseToolbarLogo(foregroundColor: nfcViewModel.isAppsBlocked ? .black : .white)
        }
        ToolbarItem(placement: .navigationBarLeading) {
            if !nfcViewModel.isAppsBlocked {
                Button {
                    activeSheet = .schedules
                } label: {
                    Image(systemName: "calendar")
                        .imageScale(.large)
                        .foregroundColor(.white)
                }
                .coachMarkToolbarTarget(id: "schedule")
            }
        }

        ToolbarItem(placement: .navigationBarTrailing) {
            if !nfcViewModel.isAppsBlocked {
                Button {
                    activeSheet = .sessionHistory
                } label: {
                    HistoryToolbarIcon()
                }
            }
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
                CoachMark(targetID: "schedule", title: "Schedules",
                          message: "Plan automatic focus sessions. Set start times and modes, and we’ll block for you.",
                          offset: CGSize(width: 0, height: 6))
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
                               message: "We’ll take you to Capture so you can record a short reflection and generate a Signal.",
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
        startAppGroupMirror() //
        nfcViewModel.checkNoiseRewindAvailability()
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
        
        if ended {
            completedSessionDuration = nfcViewModel.elapsedTime
            completedSessionModeName = nfcViewModel.selectedMode?.name ?? "Focus"

            if completedSessionDuration >= 20 * 60 {
                showSessionComplete = true
            }
        }

        switch tourPhase {
        case .extendedPreStart where started:
            tourPhase = .extendedRunning
            coachStep = 0
            showCoachMarks = true

        case .extendedRunning where ended:
            if completedSessionDuration >= 20 * 60 {
                tourPhase = .extendedPostStop
                coachStep = 0
                showCoachMarks = true
            } else {
                routeSessionReflectionToSignal()
            }

        default:
            break
        }
    }

    // 🔹 Read App Group → apply to UI/ViewModel
    func mirrorOnceFromAppGroup() {
        let ud = JNShared.suite
        let blocked = ud.bool(forKey: SharedKeys.isAppsBlockedKey)

        if let t = nfcViewModel.lastLocalModeChangeAt, Date().timeIntervalSince(t) < 1.0 {
            return
        }

        var newSelected: Mode? = nil
        if blocked {
            if let s = ud.string(forKey: SharedKeys.activeModeIdKey),
               let id = UUID(uuidString: s),
               let m = nfcViewModel.modes.first(where: { $0.id == id }) {
                newSelected = m
            }
        } else {
            if let s = ud.string(forKey: SharedKeys.preferredModeIdKey),
               let id = UUID(uuidString: s),
               let m = nfcViewModel.modes.first(where: { $0.id == id }) {
                newSelected = m
            } else if let saved = UserDefaults.standard.string(forKey: "selectedModeID"),
                      let m = nfcViewModel.modes.first(where: { $0.id.uuidString == saved }) {
                newSelected = m
            }
        }

        var changed = false
        if blocked != nfcViewModel.isAppsBlocked {
            nfcViewModel.isAppsBlocked = blocked
            changed = true
        }
        if let m = newSelected, nfcViewModel.selectedMode?.id != m.id {
            nfcViewModel.selectedMode = m
            changed = true
        }
        if changed { Task { await nfcViewModel.foregroundResync() } }
    }

    func routeSessionReflectionToSignal() {
        let shouldDismissSummary = showSessionComplete
        showSessionComplete = false
        showHistoryCongrats = false
        showCoachMarks = false
        activeSheet = nil

        if tourPhase == .extendedPostStop || tourPhase == .extendedRunning {
            tourPhase = .extendedFinished
        }

        if shouldDismissSummary {
            DispatchQueue.main.async {
                selectedTab = .capture
            }
        } else {
            selectedTab = .capture
        }
    }
}

private extension ContentView {
    func alertForUnified(_ unifiedAlert: UnifiedAlert) -> Alert {
        switch unifiedAlert {
        case .error(let alertItem):
            return Alert(
                title: alertItem.title,
                message: alertItem.message,
                dismissButton: .default(Text("OK"), action: alertItem.dismissAction)
            )
        }
    }
}

// MARK: - Colors & auth
private extension ContentView {
    var zapHeroTitle: String {
        nfcViewModel.isAppsBlocked ? "Phone Zapped" : "Zap to Block"
    }

    var zapHeroSubtitle: String {
        nfcViewModel.isAppsBlocked ? paddedElapsedTime : "Distractions"
    }

    var paddedElapsedTime: String {
        let totalSeconds = Int(nfcViewModel.elapsedTime)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    }

    var backgroundColor: Color {
        nfcViewModel.isAppsBlocked ? .white : Color(#colorLiteral(red: 0.082, green: 0.082, blue: 0.082, alpha: 1.0))
    }
    var foregroundColor: Color {
        nfcViewModel.isAppsBlocked ? .black : .white
    }
    var heroSubtitleColor: Color {
        nfcViewModel.isAppsBlocked ? Color.black.opacity(0.16) : Color.white.opacity(0.12)
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

// MARK: - Shared wrappers
extension ContentView {
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

struct SessionCompleteCard: View {
    let modeName: String
    let duration: TimeInterval
    let signalBoost: Int
    let onReflect: () -> Void
    let onDone: () -> Void

    var formattedDuration: String {
        let totalMinutes = Int(duration / 60)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60

        if hours > 0 {
            return "\(hours)h \(minutes)m protected"
        } else {
            return "\(minutes)m protected"
        }
    }

    var body: some View {
        VStack(spacing: 24) {

            Capsule()
                .fill(Color.secondary.opacity(0.25))
                .frame(width: 42, height: 5)
                .padding(.top, 8)

            VStack(spacing: 8) {
                Text("Session Complete")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
                    .textCase(.uppercase)
                    .tracking(1.2)

                Text(modeName)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.primary)
            }

            VStack(spacing: 6) {
                Text(formattedDuration)
                    .font(.system(size: 34, weight: .bold))
                    .foregroundColor(.primary)

                Text("Your focus was protected.")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.secondary)
            }

            VStack(spacing: 6) {
                Text("Signal Strengthened")
                    .font(.system(size: 18, weight: .semibold))

                Text("+\(signalBoost)% stronger signal")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 14)
            .padding(.horizontal, 22)
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(Color.primary.opacity(0.06))
            )

            HStack(spacing: 12) {
                Button("Reflect") {
                    onReflect()
                }
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color.primary.opacity(0.15), lineWidth: 1)
                )

                Button("Done") {
                    onDone()
                }
                .font(.system(size: 16, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 18)
                        .fill(Color.primary)
                )
                .foregroundColor(Color(UIColor.systemBackground))
            }
            .padding(.top, 4)
        }
        .padding(28)
    }
}

// OnboardingView.swift

import SwiftUI
import AVKit
import FamilyControls

struct OnboardingView: View {
    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()
                Image("zap_button")
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: 200, maxHeight: 200)
                    .accessibilityHidden(true)
                Text("Make space for you.")
                    .font(.largeTitle.bold())
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("Do more for what matters.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Spacer()
                NavigationLink {
                    FirstModeSetupView()
                } label: {
                    Text("Setup your Zap")
                        .font(.headline)
                        .foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(.white, in: Capsule())
                }
            }
            .padding(24)
            .navigationTitle("Welcome to Justnoise")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct FirstModeSetupView: View {
    @EnvironmentObject private var nfcViewModel: NFCViewModel
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @State private var modeName = ""
    @State private var selection = FamilyActivitySelection()
    @State private var editingModeID: UUID?
    @State private var loadedDraft = false
    @State private var showPicker = false
    @State private var requestingAuthorization = false
    @State private var errorMessage: String?

    private var canSave: Bool {
        !modeName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && selection.hasBlockingTargets
            && !requestingAuthorization
            && !nfcViewModel.isAppsBlocked
    }

    var body: some View {
        Form {
            Section {
                Text("What do you want to focus on?")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("A mode is a set of apps you block during focus time.")
                    .foregroundStyle(.secondary)
            }
            Section("Mode name") {
                TextField("e.g. Focus, Sleep, Study, Work", text: $modeName)
                    .accessibilityLabel("Mode name")
                    .textInputAutocapitalization(.words)
            }
            Section {
                Button {
                    Task { await chooseApps() }
                } label: {
                    HStack {
                        Label("Choose apps to block", systemImage: "apps.iphone")
                        Spacer()
                        if requestingAuthorization { ProgressView() }
                    }
                }
                .disabled(requestingAuthorization || nfcViewModel.isAppsBlocked)
                if selection.hasBlockingTargets {
                    Text("\(selection.applicationTokens.count) apps · \(selection.categoryTokens.count) categories · \(selection.webDomainTokens.count) websites")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } footer: {
                Text("Justnoise needs Screen Time access to block the apps you choose.")
            }
            if let errorMessage {
                Section {
                    Text(errorMessage).foregroundStyle(.red)
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            UIApplication.shared.open(url)
                        }
                    }
                }
            }
            if nfcViewModel.isAppsBlocked {
                Text("End your current focus session before changing your mode.")
            }
        }
        .navigationTitle("Create your first mode")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Button("Save mode & continue", action: saveMode)
                .font(.headline)
                .foregroundStyle(.black)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(canSave ? Color.white : Color.gray, in: Capsule())
                .disabled(!canSave)
                .padding(24)
                .background(.bar)
        }
        .familyActivityPicker(isPresented: $showPicker, selection: $selection)
        .onAppear {
            guard !loadedDraft else { return }
            loadedDraft = true
            // Preserve an already configured draft when returning to setup.
            if let mode = nfcViewModel.selectedMode, mode.selectedApps.hasBlockingTargets {
                editingModeID = mode.id
                selection = mode.selectedApps
                modeName = mode.name
            }
        }
    }

    @MainActor
    private func chooseApps() async {
        guard !requestingAuthorization, !nfcViewModel.isAppsBlocked else { return }
        requestingAuthorization = true
        errorMessage = nil
        defer { requestingAuthorization = false }
        do {
            if AuthorizationCenter.shared.authorizationStatus != .approved {
                try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            }
            guard AuthorizationCenter.shared.authorizationStatus == .approved else {
                errorMessage = String(localized: "Allow Screen Time access to choose apps to block.")
                return
            }
            showPicker = true
        } catch {
            errorMessage = String(localized: "Screen Time access wasn’t granted. Try again to choose apps to block.")
        }
    }

    private func saveMode() {
        guard canSave else { return }
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            errorMessage = String(localized: "Allow Screen Time access before saving your mode.")
            return
        }
        let mode = Mode(
            id: editingModeID ?? UUID(),
            name: modeName.trimmingCharacters(in: .whitespacesAndNewlines),
            selectedApps: selection
        )
        if nfcViewModel.modes.contains(where: { $0.id == mode.id }) {
            nfcViewModel.updateMode(mode)
        } else {
            nfcViewModel.addMode(mode)
        }
        nfcViewModel.selectedMode = mode
        hasCompletedOnboarding = true
    }
}

/// Shared by first-run onboarding and the Settings replay action.
struct OnboardingVideoOverlay: View {
    var continueTitle: LocalizedStringKey = "Continue"
    var onContinue: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var player: AVQueuePlayer?
    @State private var playerLooper: AVPlayerLooper?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button { dismiss() } label: {
                    Image(systemName: "xmark")
                        .font(.body.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.08), in: Circle())
                }
                .accessibilityLabel("Close guide")
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            .padding(.bottom, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("How it works")
                            .font(.largeTitle.bold())
                            .accessibilityAddTraits(.isHeader)
                    }
                    .fixedSize(horizontal: false, vertical: true)

                    Group {
                        if let player {
                            OnboardingVideoSurface(player: player)
                                .aspectRatio(1350.0 / 1080.0, contentMode: .fit)
                                .accessibilityHidden(true)
                        } else {
                            Text("The video is unavailable. You can continue setup.")
                                .font(.callout)
                                .foregroundStyle(.secondary)
                                .padding(24)
                                .frame(maxWidth: .infinity, minHeight: 180)
                        }
                    }
                    .background(.white.opacity(0.04))
                    .clipShape(RoundedRectangle(cornerRadius: 24))
                    .overlay {
                        RoundedRectangle(cornerRadius: 24)
                            .strokeBorder(.white.opacity(0.1), lineWidth: 1)
                    }

                    VStack(spacing: 20) {
                        instructionStep(1, title: "Tap", detail: "Tap the Zap button in the app.")
                        instructionStep(2, title: "Hold", detail: "Hold the top of your iPhone against Zap.")
                        instructionStep(3, title: "Done", detail: "Wait for the tone. You’re all set.")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxWidth: 520)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity)
            }
            .scrollIndicators(.hidden)
        }
        .foregroundStyle(.white)
        .background(Color.black.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            Button {
                onContinue?()
                dismiss()
            } label: {
                Text(continueTitle)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, minHeight: 24)
                    .padding(.vertical, 16)
                    .padding(.horizontal, 24)
                    .foregroundStyle(.black)
                    .background(.white, in: Capsule())
            }
            .frame(maxWidth: 520)
            .padding(.horizontal, 24)
            .padding(.top, 16)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity)
            .background(Color.black)
        }
        .onAppear {
            guard player == nil,
                  let url = Bundle.main.url(forResource: "JN_onboarding_intro", withExtension: "mp4") else { return }
            let introduction = AVQueuePlayer()
            playerLooper = AVPlayerLooper(player: introduction, templateItem: AVPlayerItem(url: url))
            player = introduction
            introduction.play()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { player?.play() }
            else { player?.pause() }
        }
        .onDisappear {
            player?.pause()
            playerLooper?.disableLooping()
            playerLooper = nil
            player?.removeAllItems()
            player = nil
        }
    }

    private func instructionStep(_ number: Int, title: LocalizedStringKey, detail: LocalizedStringKey) -> some View {
        HStack(alignment: .top, spacing: 16) {
            Text(number.formatted())
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .frame(width: 32, height: 32)
                .background(.white.opacity(0.1), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.65))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

}

/// Renders the video directly, without transport, scrubbing, or fullscreen controls.
private struct OnboardingVideoSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerSurface {
        let view = PlayerSurface()
        view.isUserInteractionEnabled = false
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ uiView: PlayerSurface, context: Context) {
        uiView.playerLayer.player = player
    }

    static func dismantleUIView(_ uiView: PlayerSurface, coordinator: ()) {
        uiView.playerLayer.player = nil
    }

    final class PlayerSurface: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}

// MARK: - Preview
struct OnboardingView_Previews: PreviewProvider {
    static var previews: some View {
        OnboardingView()
    }
}

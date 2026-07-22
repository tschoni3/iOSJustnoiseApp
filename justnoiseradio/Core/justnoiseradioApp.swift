//
//  JustNoiseApp.swift
//
import SwiftUI
import UserNotifications
import AppTrackingTransparency
import AdSupport
import PostHog
import FamilyControls

@main
struct JustNoiseApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @StateObject private var nfcViewModel        = NFCViewModel()
    @StateObject private var signalStore         = SignalStore()
    @StateObject private var subscriptionManager = SubscriptionManager()
    @StateObject private var authSessionStore    = AuthSessionStore()

    @AppStorage("hasCompletedOnboarding") var hasCompletedOnboarding = false
    @AppStorage(LocalAccountDataCleaner.cleanupFallbackDefaultsKey)
    private var hasAccountCleanupFallback = false

    @State private var showPasswordUpdate = false
    @State private var resetToken: String?
    @State private var accountCleanupError: String?
    @State private var isRetryingAccountCleanup = false

    init() {
        let config = PostHogConfig(
            apiKey: "phc_getaoYoT7aGkEbiDW8oRlFijWJhLIIgJhYjquD6Be3e",
            host:  "https://us.i.posthog.com"
        )
        config.captureApplicationLifecycleEvents = true
        config.captureScreenViews = true
        #if DEBUG
        config.flushAt = 1
        config.flushIntervalSeconds = 5
        config.debug = true
        #else
        config.flushAt = 20
        config.flushIntervalSeconds = 10
        #endif
        let accountCleanupPending = LocalAccountDataCleaner().hasPendingCleanup
        JustNoiseAnalyticsRuntime.prepareSharedColdLaunch(
            config: config,
            accountCleanupPending: accountCleanupPending
        )
    }

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {

                if let accountCleanupError {
                    accountCleanupGate(message: accountCleanupError)
                } else if hasAccountCleanupFallback,
                          nfcViewModel.isHydrated,
                          signalStore.isHydrated {
                    accountCleanupGate(
                        message: "Account cleanup must finish before JustNoise can continue."
                    )
                } else if hasAccountCleanupFallback
                            || !nfcViewModel.isHydrated
                            || !signalStore.isHydrated {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        ProgressView(
                            hasAccountCleanupFallback
                                ? "Finishing account cleanup…"
                                : "Loading JustNoise…"
                        )
                            .foregroundStyle(.white)
                    }
                    .task {
                        await prepareAccountDataForUse()
                    }

                } else if !hasCompletedOnboarding {
                    OnboardingView()
                        .environmentObject(nfcViewModel)
                        .environmentObject(subscriptionManager)

                } else if !nfcViewModel.isActivated {
                    NFCActivationView()
                        .environmentObject(nfcViewModel)
                        .environmentObject(subscriptionManager)

                } else if authSessionStore.state == .resolving {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        ProgressView("Loading JustNoise…")
                            .foregroundStyle(.white)
                    }

                } else if authSessionStore.state == .signedOut {
                    NavigationView {
                        SignInView()
                            .environmentObject(subscriptionManager)
                    }

                } else if let account = authSessionStore.state.authenticatedAccount {
                    AuthenticatedContainerView(account: account)
                        .environmentObject(nfcViewModel)
                        .environmentObject(subscriptionManager)
                }
            }
                .preferredColorScheme(.dark)
                .onAppear {
                Task {
                    let center = AuthorizationCenter.shared
                    if center.authorizationStatus != .approved {
                        do { try await center.requestAuthorization(for: .individual) } catch {}
                    }

                    print(center.authorizationStatus == .approved
                          ? "✅ ScreenTime authorization granted"
                          : "❌ ScreenTime authorization denied or not approved")
                }

                Task { await subscriptionManager.updateSubscriptionStatus() }

                if LocalAccountDataCleaner().hasPendingCleanup == false {
                    NotificationManager.shared.requestAuthorization()
                    NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
                    NotificationManager.shared.scheduleDailyStreakSave()
                    NotificationManager.shared.cancelNoiseRewindNotifications()
                }

                if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        requestTrackingPermission()
                    }
                }
            }
                .onChange(of: scenePhase) { _, newPhase in
                    guard newPhase == .active else { return }
                    guard LocalAccountDataCleaner().hasPendingCleanup == false else { return }

                    if !nfcViewModel.isHydrated {
                        nfcViewModel.hydrateOnLaunch()
                    }

                    if !signalStore.isHydrated {
                        signalStore.hydrateOnLaunch()
                    }

                    Task {
                        await nfcViewModel.foregroundResync()
                        nfcViewModel.checkNoiseRewindAvailability()
                    }
            }
            .fullScreenCover(isPresented: $showPasswordUpdate) {
                PasswordUpdateView(token: resetToken)
                    .environmentObject(SupabaseManager.shared)
            }
            .environmentObject(SupabaseManager.shared)
            .environmentObject(authSessionStore)
            .environmentObject(signalStore)
            .onOpenURL { url in
                guard url.scheme == "justnoise" else { return }
                switch url.host {
                case "open":
                    break
                case "reset-password":
                    if let token = URLComponents(url: url, resolvingAgainstBaseURL: false)?
                        .queryItems?
                        .first(where: { $0.name == "access_token" || $0.name == "token" })?
                        .value {
                        resetToken = token
                        showPasswordUpdate = true
                    }
                default:
                    break
                }
            }
        }
    }

    @MainActor
    private func prepareAccountDataForUse() async {
        guard isRetryingAccountCleanup == false else { return }
        isRetryingAccountCleanup = true
        accountCleanupError = nil
        defer { isRetryingAccountCleanup = false }

        switch await retryPendingAccountDeletionCleanupIfNeeded() {
        case .notNeeded:
            if nfcViewModel.isHydrated == false {
                nfcViewModel.hydrateOnLaunch()
            }
            if signalStore.isHydrated == false {
                signalStore.hydrateOnLaunch()
            }
        case .completed:
            // Cleanup reset both stores to an empty in-memory boundary. Rehydrating here
            // could restore partially deleted data if a future cleanup surface regresses.
            break
        case .blocked(let message):
            accountCleanupError = message
        }
    }

    @MainActor
    private func retryPendingAccountDeletionCleanupIfNeeded() async -> StartupAccountCleanupOutcome {
        let cleaner = LocalAccountDataCleaner()
        guard cleaner.hasPendingCleanup else { return .notNeeded }

        let coordinator = AccountDeletionCoordinator(
            remoteDeleter: AccountDeletionService(
                functionURL: SupabaseManager.shared.deleteAccountFunctionURL
            ),
            cleaner: cleaner,
            systemEffects: LiveAccountDeletionSystemEffects(
                nfcViewModel: nfcViewModel,
                signalStore: signalStore
            ),
            identityResetter: LiveAccountDeletionIdentityResetter(),
            authSignOut: LiveAccountDeletionAuthSignOut(),
            setSignedOut: {
                authSessionStore.transitionToSignedOutAfterConfirmedDeletion()
            }
        )

        do {
            try await coordinator.retryPendingLocalCleanupIfNeeded()
            return .completed
        } catch {
            // The marker remains and product data stays behind this blocking gate.
            return .blocked(error.localizedDescription)
        }
    }

    @ViewBuilder
    private func accountCleanupGate(message: String) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 18) {
                Text("Finishing account cleanup")
                    .font(.headline)
                    .foregroundStyle(.white)
                Text(message)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.72))
                Button("Retry") {
                    Task { await prepareAccountDataForUse() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(
                    isRetryingAccountCleanup
                        || authSessionStore.state.authenticatedAccount != nil
                )
            }
            .padding(32)
        }
    }
}

private enum StartupAccountCleanupOutcome {
    case notNeeded
    case completed
    case blocked(String)
}

func requestTrackingPermission() {
    ATTrackingManager.requestTrackingAuthorization { _ in }
}

// MARK: - No-survey Authenticated Container
struct AuthenticatedContainerView: View {
    let account: AuthenticatedAccount
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @EnvironmentObject var subscriptionManager: SubscriptionManager

    var body: some View {
        MainTabView()
            .onAppear {
                DeviceActivityBridge.resumeAfterAccountSignIn()
                nfcViewModel.resumeAfterAccountSignIn()
                Task { await subscriptionManager.updateSubscriptionStatus() }

                var props: [String: Any] = [:]
                if let email = account.email { props["email"] = email }
                JustNoiseAnalyticsRuntime.shared.identify(
                    account.id.uuidString,
                    userProperties: props
                )
            }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationManager.shared
        return true
    }
}

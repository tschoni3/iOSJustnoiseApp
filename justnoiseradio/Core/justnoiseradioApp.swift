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

    @AppStorage("hasCompletedOnboarding") var hasCompletedOnboarding = false
    @AppStorage("isSignedIn")             var isSignedIn            = false

    @State private var showPasswordUpdate = false
    @State private var resetToken: String?

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
        PostHogSDK.shared.setup(config)
    }

    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {

                if !nfcViewModel.isHydrated || !signalStore.isHydrated {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        ProgressView("Loading JustNoise…")
                            .foregroundStyle(.white)
                    }
                    .task {
                        nfcViewModel.hydrateOnLaunch()
                        signalStore.hydrateOnLaunch()
                    }

                } else if !hasCompletedOnboarding {
                    OnboardingView()
                        .environmentObject(nfcViewModel)
                        .environmentObject(subscriptionManager)

                } else if !nfcViewModel.isActivated {
                    NFCActivationView()
                        .environmentObject(nfcViewModel)
                        .environmentObject(subscriptionManager)

                } else if !isSignedIn {
                    NavigationView {
                        SignInView()
                            .environmentObject(subscriptionManager)
                    }

                } else {
                    AuthenticatedContainerView()
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

                NotificationManager.shared.requestAuthorization()
                NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
                NotificationManager.shared.scheduleDailyStreakSave()
                NotificationManager.shared.cancelNoiseRewindNotifications()

                if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        requestTrackingPermission()
                    }
                }
            }
                .onChange(of: scenePhase) { _, newPhase in
                    guard newPhase == .active else { return }

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
            .environmentObject(signalStore)
            .onOpenURL { url in
                guard url.scheme == "justnoise" else { return }
                switch url.host {
                case "open":
                    if SupabaseManager.shared.client.auth.currentUser != nil {
                        isSignedIn = true
                    }
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
}

func requestTrackingPermission() {
    ATTrackingManager.requestTrackingAuthorization { _ in }
}

// MARK: - No-survey Authenticated Container
struct AuthenticatedContainerView: View {
    @EnvironmentObject var subscriptionManager: SubscriptionManager

    var body: some View {
        MainTabView()
            .onAppear {
                Task { await subscriptionManager.updateSubscriptionStatus() }

                if let user = SupabaseManager.shared.client.auth.currentUser {
                    var props: [String: Any] = [:]
                    if let email = user.email { props["email"] = email }
                    PostHogSDK.shared.identify(user.id.uuidString, userProperties: props)
                }
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

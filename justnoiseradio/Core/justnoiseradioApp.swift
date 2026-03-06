//
//  JustNoiseApp.swift
//
import SwiftUI
import UserNotifications
import AppTrackingTransparency
import AdSupport
import StoreKit
import PostHog
import FamilyControls

@main
struct JustNoiseApp: App {

    // AppDelegate for notification delegation
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @StateObject private var nfcViewModel        = NFCViewModel()
    @StateObject private var subscriptionManager = SubscriptionManager()

    @AppStorage("hasCompletedOnboarding") var hasCompletedOnboarding = false
    @AppStorage("isSignedIn")             var isSignedIn            = false

    // 🔥 Always treat survey as completed (disable fully)
    @AppStorage("hasCompletedSurvey") var hasCompletedSurvey = true
    
    @AppStorage("showPostSessionJournalPrompt") var showPostSessionJournalPrompt: Bool = true

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
                // 🚪 Block UI until protected storage is hydrated
                if !nfcViewModel.isHydrated {
                    ZStack {
                        Color.black.ignoresSafeArea()
                        ProgressView("Loading JustNoise…")
                            .foregroundStyle(.white)
                    }
                    .task { nfcViewModel.hydrateOnLaunch() } // idempotent

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

            // Global setup
            .onAppear {
                Task {
                    // Screen Time auth (needed for ManagedSettings)
                    let center = AuthorizationCenter.shared
                    if center.authorizationStatus != .approved {
                        do { try await center.requestAuthorization(for: .individual) } catch {
                        }
                    }
                    print(center.authorizationStatus == .approved
                          ? "✅ ScreenTime authorization granted"
                          : "❌ ScreenTime authorization denied or not approved")
                }

                Task { await subscriptionManager.updateSubscriptionStatus() }

                // Local notifications (keep – not DeviceActivity)
                NotificationManager.shared.requestAuthorization()
                NotificationManager.shared.scheduleDailyPreSessionNudgeIfNeeded()
                NotificationManager.shared.scheduleDailyStreakSave()

                // ATT
                if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        requestTrackingPermission()
                    }
                }

                // ⛔️ REMOVED: Any DeviceActivityBridge resync/arming
                // ⛔️ REMOVED: Any JNShared/JNPayloadStore debug mirrors
            }

            // Foreground refresh
            .onChange(of: scenePhase) { _, newPhase in
                guard newPhase == .active else { return }

                if !nfcViewModel.isHydrated {
                    nfcViewModel.hydrateOnLaunch()
                }

                Task {
                    await nfcViewModel.foregroundResync()
                }

                // ⛔️ REMOVED: DeviceActivityBridge.resyncAll(...)
                // ⛔️ REMOVED: AppGroup mirror debug for DA extension
            }

            // ⛔️ REMOVED: onChange of isHydrated that armed DeviceActivity monitors

            // Deep links + password reset
            .fullScreenCover(isPresented: $showPasswordUpdate) {
                PasswordUpdateView(token: resetToken)
                    .environmentObject(SupabaseManager.shared)
            }
            .environmentObject(SupabaseManager.shared)
            .environment(\.showPostSessionJournalPrompt, $showPostSessionJournalPrompt)
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

// MARK: - Environment key for post-session journal prompt preference
private struct ShowPostSessionJournalPromptKey: EnvironmentKey {
    static let defaultValue: Binding<Bool> = .constant(true)
}

extension EnvironmentValues {
    var showPostSessionJournalPrompt: Binding<Bool> {
        get { self[ShowPostSessionJournalPromptKey.self] }
        set { self[ShowPostSessionJournalPromptKey.self] = newValue }
    }
}

// MARK: - Tracking permission helper
func requestTrackingPermission() {
    ATTrackingManager.requestTrackingAuthorization { _ in }
}

// MARK: - Authenticated container
struct AuthenticatedContainerView: View {
    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @AppStorage("hasCompletedSurvey") var hasCompletedSurvey = false
    @State private var showSurvey = false

    var body: some View {
        ContentView()
            .onAppear {
                if !hasCompletedSurvey { showSurvey = true }
                Task { await subscriptionManager.updateSubscriptionStatus() }

                // Identify signed-in user for PostHog
                if let user = SupabaseManager.shared.client.auth.currentUser {
                    var props: [String: Any] = [:]
                    if let email = user.email { props["email"] = email }
                    PostHogSDK.shared.identify(user.id.uuidString, userProperties: props)
                }
            }
            .fullScreenCover(isPresented: $showSurvey) {
                SurveyView(showSurvey: $showSurvey)
            }
    }
}

// MARK: - AppDelegate for notifications & deep links from notifications
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = NotificationManager.shared
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        let userInfo = response.notification.request.content.userInfo
        guard let deeplink = userInfo["deeplink"] as? String,
              let type = DeepLinkType(rawValue: deeplink) else { return }

        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .didReceiveDeepLink,
                                            object: nil,
                                            userInfo: ["type": type])
        }
    }
}

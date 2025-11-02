//
//  SettingsView.swift
//

import SwiftUI
import StoreKit
import UIKit
import Supabase

struct SettingsView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @EnvironmentObject var subscriptionManager: SubscriptionManager
    @Environment(\.presentationMode) var presentationMode
    @Environment(\.openURL) private var openURL
    
    @AppStorage("userName") var userName: String = ""
    @AppStorage("userLanguage") var userLanguage: String = "Auto detect"
    @AppStorage("isSignedIn") var isSignedIn: Bool = false
    @AppStorage("hasCompletedCoachMarks") private var hasCompletedCoachMarks: Bool = false
    @AppStorage("showPostSessionJournalPrompt") private var showPostSessionJournalPrompt: Bool = true
    
    @State private var showEmergencyConfirm = false

    // Delete state
    @State private var showDeleteConfirm = false
    @State private var isDeleting = false
    @State private var deleteError: String?

    private var email: String {
        SupabaseManager.shared.client.auth.currentUser?.email ?? "Not Available"
    }
    private var subscriptionStatus: String {
        subscriptionManager.isProActive ? "JustNoise Pro" : "Free"
    }

    private let deleter = AccountDeletionService(
        functionURL: SupabaseManager.shared.deleteAccountFunctionURL
    )
    
    // MARK: - URLs
    private let urlAboutUs   = URL(string: "https://store.justnoise.shop/pages/about-us")!
    private let urlPrivacy   = URL(string: "https://store.justnoise.shop/policies/privacy-policy")!
    private let urlTerms     = URL(string: "https://store.justnoise.shop/policies/terms-of-service")!
    private let urlCommunity = URL(string: "https://www.skool.com/momentum-hub-4710")!
    
    var body: some View {
        NavigationView {
            Form {
                // 1) ACCOUNT
                Section(header: Text("ACCOUNT")) {
                    HStack {
                        Label("Name", systemImage: "person.fill")
                        Spacer()
                        TextField("Enter your name", text: $userName)
                            .multilineTextAlignment(.trailing)
                            .foregroundColor(.blue)
                    }
                    HStack {
                        Label("Email", systemImage: "envelope.fill")
                        Spacer()
                        Text(email).foregroundColor(.blue)
                    }
                    HStack {
                        Label("Subscription", systemImage: "plus.square.fill")
                        Spacer()
                        Text(subscriptionStatus).foregroundColor(.blue)
                    }
                    HStack {
                        Label("Language", systemImage: "globe")
                        Spacer()
                        Picker("", selection: $userLanguage) {
                            Text("Auto").tag("")
                            Text("English").tag("en")
                            Text("Deutsch").tag("de")
                            Text("Español").tag("es")
                        }
                        .pickerStyle(MenuPickerStyle())
                        .foregroundColor(.blue)
                    }
                }
                
                // 2) PREFERENCES (PROMPTS)
                Section(header: Text("PREFERENCES")) {
                    Toggle(isOn: $showPostSessionJournalPrompt) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Post-session journaling prompt")
                            Text("Show the reflection prompt after ending a session")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                // 3) ONBOARDING
                Section(header: Text("ONBOARDING")) {
                    Button {
                        hasCompletedCoachMarks = false
                        presentationMode.wrappedValue.dismiss()
                    } label: {
                        Label("Re-run Onboarding Tour", systemImage: "sparkles")
                    }
                }

                // 4) SUPPORT
                Section(header: Text("SUPPORT")) {
                    Button {
                        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene {
                            AppStore.requestReview(in: windowScene)
                        }
                    } label: {
                        Label("Write a Review", systemImage: "bubble.left.and.bubble.right.fill")
                    }
                    Link(destination: urlCommunity) {
                        Label("Community", systemImage: "person.3.fill")
                    }
                }
                
                // 5) ABOUT
                Section(header: Text("ABOUT JUSTNOISE")) {
                    Link(destination: urlAboutUs) {
                        Label("About us", systemImage: "info.circle.fill")
                    }
                    Link(destination: urlPrivacy) {
                        Label("Privacy Policy", systemImage: "lock.fill")
                    }
                    Link(destination: urlTerms) {
                        Label("Terms of Use", systemImage: "doc.text.fill")
                    }
                }
                
                // 6) EMERGENCY
                Section(header: Text("EMERGENCY")) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Emergency Unzap").font(.headline)
                        Text("Use this if you lost your Zap. Each use consumes a token. \(nfcViewModel.emergencyUnzapCount) left.")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Button(action: { showEmergencyConfirm = true }) {
                            Text("Use Emergency Unzap").frame(maxWidth: .infinity)
                        }
                        .disabled(nfcViewModel.emergencyUnzapCount == 0)
                        .foregroundColor(.white)
                        .padding()
                        .background(nfcViewModel.emergencyUnzapCount > 0 ? Color.red : Color.gray)
                        .cornerRadius(8)
                        .alert(isPresented: $showEmergencyConfirm) {
                            Alert(
                                title: Text("Confirm Emergency Unzap"),
                                message: Text("This will use one of your emergency tokens (\(nfcViewModel.emergencyUnzapCount) left). Do you want to continue?"),
                                primaryButton: .destructive(Text("Yes, Unzap")) {
                                    nfcViewModel.useEmergencyUnzap()   // ← plain call, no $
                                },
                                secondaryButton: .cancel()
                            )
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                

                // 7) DANGER ZONE — Delete Account
                Section(footer:
                    Text("Permanently deletes your account and personal data. This cannot be undone.")
                        .foregroundColor(.secondary)
                ) {
                    Button(role: .destructive) {
                        showDeleteConfirm = true
                    } label: {
                        Label("Delete Account", systemImage: "trash.fill")
                    }
                    .disabled(isDeleting)
                    .alert("Delete Account?", isPresented: $showDeleteConfirm) {
                        Button("Cancel", role: .cancel) {}
                        Button("Delete", role: .destructive) { Task { await deleteAccountFlow() } }
                    } message: {
                        Text("This action is permanent and cannot be undone.")
                    }
                }
                
                // 8) SIGN OUT
                Section {
                    Button("Sign out") {
                        Task {
                            do {
                                try await SupabaseManager.shared.signOut()
                                isSignedIn = false
                            } catch {
                                print("Error during sign out: \(error)")
                            }
                        }
                    }
                    .foregroundColor(.red)
                }
            }
            .navigationTitle("Settings")
            .navigationBarItems(trailing: Button("Done") {
                presentationMode.wrappedValue.dismiss()
            })
            .alert("Error", isPresented: .constant(deleteError != nil)) {
                Button("OK") { deleteError = nil }
            } message: { Text(deleteError ?? "") }
        }
    }

    // MARK: - Delete Flow
    private func deleteAccountFlow() async {
        guard !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }

        do {
            let token = await SupabaseManager.shared.currentAccessToken()
            print("DELETE using token isNil:", token == nil)
            try await deleter.deleteAccount(accessToken: token)

            // local clean-up if you cached anything
            UserDefaults.standard.removeObject(forKey: "sessionHistory")
            UserDefaults.standard.removeObject(forKey: "transcriptionHistory")
            UserDefaults.standard.synchronize()

            // sign out and go back to auth
            try? await SupabaseManager.shared.client.auth.signOut()
            isSignedIn = false
            presentationMode.wrappedValue.dismiss()
        } catch {
            deleteError = error.localizedDescription
        }
    }
}

struct SettingsView_Previews: PreviewProvider {
    static var previews: some View {
        SettingsView()
            .environmentObject(NFCViewModel())
            .environmentObject(SubscriptionManager())
    }
}

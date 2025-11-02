// SuperbaseManager.swift

import Foundation
import Supabase

struct UserProfileUpdate: Encodable {
    let id: UUID
    let name: String
    let age: String
    let language: String
    let occupation: String
    let main_goal: String
    let biggest_distraction: String
}

class SupabaseManager: ObservableObject {
    static let shared = SupabaseManager()
    
    let client: SupabaseClient
    let supabaseURL: URL
    let supabaseKey: String
    
    private init() {
        // Replace these with your actual Supabase project URL and Key
        self.supabaseURL = URL(string: "https://zusnyjctxdnnxghpbhki.supabase.co")!
        self.supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp1c255amN0eGRubnhnaHBiaGtpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg3NDczMDQsImV4cCI6MjA1NDMyMzMwNH0.3WP_jwfBagzxc9qGle68bPWL47ePA_dXaWsXG1lT1No"
        client = SupabaseClient(supabaseURL: self.supabaseURL, supabaseKey: self.supabaseKey)
    }
}

// MARK: - SupabaseManager Extensions
extension SupabaseManager {
    func signOut() async throws {
        try await client.auth.signOut()
    }
    
    // Upsert user profile (unchanged)
    func upsertUserProfile(name: String, age: String, language: String, occupation: String, mainGoal: String, biggestDistraction: String) async throws {
        guard let user = client.auth.currentUser else {
            throw NSError(domain: "SupabaseManager",
                          code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "User is not authenticated."])
        }
        
        let profileUpdate = UserProfileUpdate(
            id: user.id,
            name: name,
            age: age,
            language: language,
            occupation: occupation,
            main_goal: mainGoal,
            biggest_distraction: biggestDistraction
        )
        
        try await client
            .from("profiles")
            .upsert(profileUpdate)
            .execute()
    }
    
    // Request a password reset email with a custom redirect
    func resetPassword(for email: String, redirectTo: String) async throws {
        guard let authURL = URL(string: "\(self.supabaseURL.absoluteString)/auth/v1/recover") else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: authURL)
        request.httpMethod = "POST"
        let body: [String: Any] = [
            "email": email,
            "redirect_to": redirectTo
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(self.supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: "ResetPassword", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
        }
        if httpResponse.statusCode != 200 {
            let message = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw NSError(domain: "ResetPassword", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: message])
        }
    }
    
    // Final step to update the password once user has the token
    func updatePassword(with token: String, newPassword: String) async throws {
        guard let updateURL = URL(string: "\(self.supabaseURL.absoluteString)/auth/v1/reset") else {
            throw URLError(.badURL)
        }
        var request = URLRequest(url: updateURL)
        request.httpMethod = "POST"
        let body: [String: Any] = [
            "type": "recovery",
            "access_token": token,
            "password": newPassword
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.addValue(self.supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NSError(domain: "UpdatePassword", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid response"])
        }
        if httpResponse.statusCode != 200 {
            let message = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw NSError(domain: "UpdatePassword", code: httpResponse.statusCode, userInfo: [NSLocalizedDescriptionKey: message])
        }
    }
}

// MARK: - Helpers for deletion flow (append to SupabaseManager.swift)
extension SupabaseManager {
    /// Fresh access token (handles SDK differences).
    func currentAccessToken() async -> String? {
        if let session = try? await client.auth.session {
            return session.accessToken        // non-optional in newer SDKs
        }
        if let session = client.auth.currentSession {
            return session.accessToken
        }
        return nil
    }

    /// Centralized Edge Function URL
    var deleteAccountFunctionURL: URL {
        URL(string: "https://zusnyjctxdnnxghpbhki.supabase.co/functions/v1/delete-account")!
    }
}

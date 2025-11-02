// SignInView.swift

import SwiftUI
import AuthenticationServices
import Supabase
import GoogleSignIn
import GoogleSignInSwift

// MARK: - String Extension for Validations
extension String {
    var isValidEmail: Bool {
        // Basic regex for email validation
        let emailRegEx = "[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}"
        return NSPredicate(format: "SELF MATCHES %@", emailRegEx).evaluate(with: self)
    }
    
    var isStrongPassword: Bool {
        // Require a minimum of 6 characters; adjust criteria as needed.
        return self.count >= 6
    }
}

// MARK: - Custom Button Style
struct AuthButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity)
            .padding()
            .background(configuration.isPressed ? Color.blue.opacity(0.7) : Color.blue)
            .foregroundColor(.white)
            .cornerRadius(100)
    }
}

// MARK: - Google Sign In Manager
class GoogleSignInManager: NSObject, ObservableObject {
    static let shared = GoogleSignInManager()
    
    func signIn() async throws {
        guard let windowScene = await UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let presentingVC = await windowScene.windows.first?.rootViewController else {
            throw NSError(domain: "GoogleSignIn", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "No presenting view controller found."])
        }
        
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: "60378924357-at4eeqslt180ca999e97i9tt785qt4rb.apps.googleusercontent.com")
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presentingVC)
        guard let idToken = result.user.idToken?.tokenString else {
            throw NSError(domain: "GoogleSignIn", code: -1,
                          userInfo: [NSLocalizedDescriptionKey: "No idToken found."])
        }
        
        let accessToken = result.user.accessToken.tokenString
        let nonce = decodeNonce(from: idToken)
        
        try await SupabaseManager.shared.client.auth.signInWithIdToken(
            credentials: .init(
                provider: .google,
                idToken: idToken,
                accessToken: accessToken,
                nonce: nonce
            )
        )
        
        print("Successfully signed in with Google")
    }
    
    private func decodeNonce(from idToken: String) -> String? {
        let segments = idToken.split(separator: ".")
        guard segments.count > 1 else { return nil }
        let payloadSegment = segments[1]
        var base64String = String(payloadSegment)
        let remainder = base64String.count % 4
        if remainder > 0 {
            base64String = base64String.padding(toLength: base64String.count + 4 - remainder, withPad: "=", startingAt: 0)
        }
        guard let data = Data(base64Encoded: base64String),
              let json = try? JSONSerialization.jsonObject(with: data, options: []),
              let dict = json as? [String: Any] else { return nil }
        return dict["nonce"] as? String
    }
}

// MARK: - Google Sign In Button (Reusable)
struct GoogleSignInButton: View {
    var title: String
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image("google_icon") // Ensure you add your google_icon asset
                    .resizable()
                    .frame(width: 14, height: 14)
                Text(title)
                    .font(.headline)
            }
            .foregroundColor(.black)
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color.white)
            .cornerRadius(8)
        }
    }
}

// MARK: - Sign In Screen
struct SignInView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var isProcessing = false
    @State private var authMessage: String?
    @State private var showForgotPassword = false
    
    @AppStorage("isSignedIn") var isSignedIn: Bool = false
    @EnvironmentObject var supabaseManager: SupabaseManager
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 21/255, green: 21/255, blue: 21/255)
                    .ignoresSafeArea()
                VStack(spacing: 16) {
                    Spacer().frame(height: 40)
                    
                    // App Icon
                    Image("Icon")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 84, height: 84)
                        .cornerRadius(16)
                    
                    // Header
                    VStack(spacing: 4) {
                        Text("Log in to Justnoise")
                            .font(.title)
                            .bold()
                            .foregroundColor(.white)
                        HStack {
                            Text("Don't have an account?")
                                .foregroundColor(.white)
                                .font(.subheadline)
                            NavigationLink(destination: SignUpView()) {
                                Text("Sign up.")
                                    .foregroundColor(.blue)
                                    .font(.subheadline)
                            }
                        }
                    }
                    .padding(.top, 8)
                    
                    Spacer().frame(height: 20)
                    
                    // Email & Password Fields
                    VStack(spacing: 12) {
                        TextField("Email", text: $email)
                            .foregroundColor(.white)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .padding()
                            .background(Color(.systemGray2).opacity(0.2))
                            .cornerRadius(8)
                        
                        SecureField("Password", text: $password)
                            .foregroundColor(.white)
                            .padding()
                            .background(Color(.systemGray2).opacity(0.2))
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                    
                    // Forgot Password Link
                    Button {
                        showForgotPassword = true
                    } label: {
                        Text("Forgot password?")
                            .font(.footnote)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .sheet(isPresented: $showForgotPassword) {
                        ForgotPasswordView(email: $email)
                            .environmentObject(supabaseManager)
                    }
                    
                    // Primary Action Button for Sign In
                    Button(action: {
                        Task { await handleSignIn() }
                    }) {
                        if isProcessing {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding()
                        } else {
                            Text("Continue")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .frame(height: 25)
                        }
                    }
                    .buttonStyle(AuthButtonStyle())
                    .padding(.horizontal)
                    
                    // Display authentication messages
                    if let authMessage = authMessage {
                        Text(authMessage)
                            .foregroundColor(authMessage.contains("failed") ? .red : .green)
                            .font(.caption)
                            .padding(.top, 4)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    // Divider
                    HStack {
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 1)
                            .padding(.horizontal)
                        Text("or")
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 1)
                            .padding(.horizontal)
                    }
                    .padding(.vertical, 10)
                    
                    // Alternative Login Methods
                    VStack(spacing: 12) {
                        GoogleSignInButton(title: "Login with Google") {
                            Task {
                                do {
                                    try await GoogleSignInManager.shared.signIn()
                                    isSignedIn = true
                                } catch {
                                    print("Google Sign In error: \(error)")
                                    authMessage = "Google Sign In failed: \(error.localizedDescription)"
                                }
                            }
                        }
                        .frame(height: 45)
                        .padding(.horizontal)
                        
                        SignInWithAppleButton { request in
                            request.requestedScopes = [.email, .fullName]
                        } onCompletion: { result in
                            Task {
                                do {
                                    guard let credential = try result.get().credential as? ASAuthorizationAppleIDCredential,
                                          let idTokenData = credential.identityToken,
                                          let idToken = String(data: idTokenData, encoding: .utf8) else { return }
                                    
                                    try await supabaseManager.client.auth.signInWithIdToken(
                                        credentials: .init(provider: .apple, idToken: idToken)
                                    )
                                    print("Apple Sign In succeeded")
                                    isSignedIn = true
                                } catch {
                                    print("Apple Sign In error: \(error)")
                                    authMessage = "Apple Sign In failed: \(error.localizedDescription)"
                                }
                            }
                        }
                        .signInWithAppleButtonStyle(.white)
                        .frame(height: 45)
                        .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                    // Footer Legal Disclaimer
                    Text("By signing in, you agree to our terms of use, and privacy policy.")
                        .font(.footnote)
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.horizontal, 50)
                        .padding(.bottom, 20)
                        .multilineTextAlignment(.center)
                }
            }
            .navigationBarHidden(true)
        }
    }
    
    // MARK: - Handle Sign In
    func handleSignIn() async {
        isProcessing = true
        authMessage = nil
        
        guard !email.isEmpty, !password.isEmpty else {
            authMessage = "Please enter both email and password."
            isProcessing = false
            return
        }
        
        do {
            try await supabaseManager.client.auth.signIn(email: email, password: password)
            print("Sign In succeeded.")
            isSignedIn = true
        } catch {
            authMessage = "Sign In failed: \(error.localizedDescription)"
            print("Sign In error: \(error)")
        }
        
        isProcessing = false
    }
}

// MARK: - Sign Up Screen
struct SignUpView: View {
    @State private var email = ""
    @State private var password = ""
    @State private var isProcessing = false
    @State private var authMessage: String?
    @State private var showForgotPassword = false
    
    @AppStorage("isSignedIn") var isSignedIn: Bool = false
    @EnvironmentObject var supabaseManager: SupabaseManager
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 21/255, green: 21/255, blue: 21/255)
                    .ignoresSafeArea()
                VStack(spacing: 16) {
                    Spacer().frame(height: 40)
                    
                    // App Icon
                    Image("Icon")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 84, height: 84)
                        .cornerRadius(16)
                    
                    // Header
                    VStack(spacing: 4) {
                        Text("Create a Justnoise account")
                            .font(.title)
                            .bold()
                            .foregroundColor(.white)
                        HStack {
                            Text("Already have an account?")
                                .foregroundColor(.white)
                                .font(.subheadline)
                            Button(action: {
                                dismiss()
                            }) {
                                Text("Log in.")
                                    .foregroundColor(.blue)
                                    .font(.subheadline)
                            }
                        }
                    }
                    .padding(.top, 8)
                    
                    Spacer().frame(height: 20)
                    
                    // Email & Password Fields
                    VStack(spacing: 12) {
                        TextField("Email", text: $email)
                            .foregroundColor(.white)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .padding()
                            .background(Color(.systemGray2).opacity(0.2))
                            .cornerRadius(8)
                        
                        SecureField("Password", text: $password)
                            .foregroundColor(.white)
                            .padding()
                            .background(Color(.systemGray2).opacity(0.2))
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                    
                    // Forgot Password Link
                    Button {
                        showForgotPassword = true
                    } label: {
                        Text("Forgot password?")
                            .font(.footnote)
                            .foregroundColor(.white.opacity(0.8))
                    }
                    .sheet(isPresented: $showForgotPassword) {
                        ForgotPasswordView(email: $email)
                            .environmentObject(supabaseManager)
                    }
                    
                    // Primary Action Button for Sign Up
                    Button(action: {
                        Task { await handleSignUp() }
                    }) {
                        if isProcessing {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding()
                        } else {
                            Text("Create account")
                                .frame(maxWidth: .infinity)
                                .padding()
                                .frame(height: 25)
                        }
                    }
                    .buttonStyle(AuthButtonStyle())
                    .padding(.horizontal)
                    
                    // Display authentication messages
                    if let authMessage = authMessage {
                        Text(authMessage)
                            .foregroundColor(authMessage.contains("failed") ? .red : .green)
                            .font(.caption)
                            .padding(.top, 4)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    // Divider
                    HStack {
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 1)
                            .padding(.horizontal)
                        Text("or")
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 1)
                            .padding(.horizontal)
                    }
                    .padding(.vertical, 10)
                    
                    // Alternative Sign-Up Methods
                    VStack(spacing: 12) {
                        GoogleSignInButton(title: "Sign up with Google") {
                            Task {
                                do {
                                    try await GoogleSignInManager.shared.signIn()
                                    isSignedIn = true
                                } catch {
                                    print("Google Sign In error: \(error)")
                                    authMessage = "Google Sign In failed: \(error.localizedDescription)"
                                }
                            }
                        }
                        .frame(height: 45)
                        .padding(.horizontal)
                        
                        SignInWithAppleButton { request in
                            request.requestedScopes = [.email, .fullName]
                        } onCompletion: { result in
                            Task {
                                do {
                                    guard let credential = try result.get().credential as? ASAuthorizationAppleIDCredential,
                                          let idTokenData = credential.identityToken,
                                          let idToken = String(data: idTokenData, encoding: .utf8) else { return }
                                    
                                    try await supabaseManager.client.auth.signInWithIdToken(
                                        credentials: .init(provider: .apple, idToken: idToken)
                                    )
                                    print("Apple Sign In succeeded")
                                    isSignedIn = true
                                } catch {
                                    print("Apple Sign In error: \(error)")
                                    authMessage = "Apple Sign In failed: \(error.localizedDescription)"
                                }
                            }
                        }
                        .signInWithAppleButtonStyle(.white)
                        .frame(height: 45)
                        .padding(.horizontal)
                    }
                    
                    Spacer()
                    
                    // Footer Legal Disclaimer
                    Text("By signing up, you agree to our terms of use, and privacy policy.")
                        .font(.footnote)
                        .foregroundColor(.white.opacity(0.6))
                        .padding(.horizontal, 50)
                        .padding(.bottom, 20)
                        .multilineTextAlignment(.center)
                }
            }
            .navigationBarHidden(true)
        }
    }
    
    // MARK: - Handle Sign Up with Validations
    func handleSignUp() async {
        isProcessing = true
        authMessage = nil

        // Validate input
        guard !email.isEmpty, !password.isEmpty else {
            authMessage = "Please enter both email and password."
            isProcessing = false
            return
        }
        
        guard email.isValidEmail else {
            authMessage = "Please enter a valid email address."
            isProcessing = false
            return
        }
        
        guard password.isStrongPassword else {
            authMessage = "Password is too weak. Please choose a stronger password (minimum 6 characters)."
            isProcessing = false
            return
        }
        
        do {
            // Execute the RPC call to check for duplicate email
            let duplicateResponse: PostgrestResponse<Bool> = try await supabaseManager.client
                .rpc("check_duplicate_user", params: ["p_email": email])
                .execute()
            
            // Use the returned Bool directly
            let duplicateCheck = duplicateResponse.value
            
            if duplicateCheck {
                authMessage = "An account with this email already exists."
                isProcessing = false
                return
            }
            
            // Proceed with sign-up if no duplicate exists
            let response = try await supabaseManager.client.auth.signUp(email: email, password: password)
            
            if response.session == nil {
                print("Sign Up succeeded. Verify email.")
                authMessage = "Sign up successful. Check your email for a verification link."
            } else {
                print("Sign Up returned a session.")
                isSignedIn = true
            }
        } catch {
            authMessage = "Sign Up failed: \(error.localizedDescription)"
            print("Sign Up error: \(error)")
        }
        
        isProcessing = false
    }
}

// MARK: - Forgot Password View
struct ForgotPasswordView: View {
    @Binding var email: String
    @EnvironmentObject var supabaseManager: SupabaseManager
    @Environment(\.dismiss) var dismiss
    
    @State private var isProcessing = false
    @State private var statusMessage: String?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Reset your password")
                    .font(.title2)
                    .padding(.top)
                
                TextField("Email", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .padding(.horizontal)
                
                if let statusMessage = statusMessage {
                    Text(statusMessage)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }
                
                Button {
                    Task {
                        isProcessing = true
                        
                        // Validate that the email field is not empty and is formatted correctly.
                        guard !email.isEmpty else {
                            statusMessage = "Please enter your email address."
                            isProcessing = false
                            return
                        }
                        guard email.isValidEmail else {
                            statusMessage = "Please enter a valid email address."
                            isProcessing = false
                            return
                        }
                        
                        do {
                            // Call the RPC function to check if the email exists.
                            let response: PostgrestResponse<Bool> = try await supabaseManager.client
                                .rpc("check_duplicate_user", params: ["p_email": email])
                                .execute()
                            
                            // Use the returned Bool directly
                            let userExists = response.value
                            
                            // If no account is found, update the status and stop.
                            if !userExists {
                                statusMessage = "No account found with that email."
                                isProcessing = false
                                return
                            }
                            
                            // Email exists, so proceed with sending the reset email.
                            try await supabaseManager.resetPassword(for: email, redirectTo: "justnoise://reset-password")
                            statusMessage = "A reset email has been sent. Check your inbox."
                        } catch {
                            statusMessage = "Failed to send reset email: \(error.localizedDescription)"
                        }
                        
                        isProcessing = false
                    }
                } label: {
                    if isProcessing {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding()
                    } else {
                        Text("Send Reset Email")
                            .frame(maxWidth: .infinity)
                            .padding()
                    }
                }
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)
                .padding(.horizontal)
                
                Spacer()
            }
            .navigationTitle("Forgot Password")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Preview Providers
struct SignInView_Previews: PreviewProvider {
    static var previews: some View {
        SignInView()
            .environmentObject(SupabaseManager.shared)
    }
}

struct SignUpView_Previews: PreviewProvider {
    static var previews: some View {
        SignUpView()
            .environmentObject(SupabaseManager.shared)
    }
}

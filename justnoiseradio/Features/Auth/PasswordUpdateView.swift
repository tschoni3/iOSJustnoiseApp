//  PasswordUpdateView.swift
//

import SwiftUI

struct PasswordUpdateView: View {
    let token: String?
    @State private var newPassword = ""
    @State private var confirmPassword = ""
    @State private var isProcessing = false
    @State private var statusMessage: String?
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var supabaseManager: SupabaseManager

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                Text("Reset Your Password")
                    .font(.title)
                    .padding()

                SecureField("New Password", text: $newPassword)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)

                SecureField("Confirm New Password", text: $confirmPassword)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(8)

                if let statusMessage = statusMessage {
                    Text(statusMessage)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                }

                Button {
                    Task {
                        guard !newPassword.isEmpty, !confirmPassword.isEmpty else {
                            statusMessage = "Please fill in all fields."
                            return
                        }
                        guard newPassword == confirmPassword else {
                            statusMessage = "Passwords do not match."
                            return
                        }
                        guard newPassword.isStrongPassword else {
                            statusMessage = "Password is too weak (min 6 chars)."
                            return
                        }
                        guard let token = token else {
                            statusMessage = "Invalid reset token."
                            return
                        }
                        isProcessing = true
                        do {
                            try await supabaseManager.updatePassword(with: token, newPassword: newPassword)
                            statusMessage = "Your password has been updated successfully."
                            // Pause briefly to let the user read the message, then dismiss.
                            try await Task.sleep(nanoseconds: 2_000_000_000)
                            dismiss()
                        } catch {
                            statusMessage = "Failed to update password: \(error.localizedDescription)"
                        }
                        isProcessing = false
                    }
                } label: {
                    if isProcessing {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding()
                    } else {
                        Text("Update Password")
                            .frame(maxWidth: .infinity)
                            .padding()
                    }
                }
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(8)

                Spacer()
            }
            .padding()
            .navigationBarTitle("Reset Password", displayMode: .inline)
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

struct PasswordUpdateView_Previews: PreviewProvider {
    static var previews: some View {
        PasswordUpdateView(token: "sampleToken")
            .environmentObject(SupabaseManager.shared)
    }
}

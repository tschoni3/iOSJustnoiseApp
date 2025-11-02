// TranscriptionOverlayView.swift

import SwiftUI
// If you're using the Analytics helper file in the same target, no extra import is needed.

struct TranscriptionOverlayView: View {
    let transcription: TranscriptionResponse?
    let selectedMode: String
    var onFinishTapped: () -> Void
    var onDoneTapped: () -> Void

    @EnvironmentObject var nfcViewModel: NFCViewModel

    @State private var isLiked: Bool = false
    @State private var typingSpeed: Double = 50

    var body: some View {
        NavigationView {
            if let transcription = transcription {
                VStack(alignment: .leading, spacing: 0) {
                    // Typing Text with Background
                    ScrollView {
                        ZStack(alignment: .topLeading) {
                            Text(transcription.aifeedback)
                                .font(.title)
                                .bold()
                                .lineSpacing(5)
                                .foregroundColor(.white.opacity(0.2))
                                .multilineTextAlignment(.leading)
                                .padding()

                            TypingText(text: transcription.aifeedback, typingSpeed: typingSpeed)
                                .font(.title)
                                .bold()
                                .lineSpacing(5)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.leading)
                                .padding()
                        }
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                    }
                    .frame(maxHeight: .infinity, alignment: .topLeading)

                    // Actions
                    VStack(spacing: 10) {
                        Button(action: {
                            isLiked.toggle()
                            Analytics.capture("ai_output_liked", props: [
                                "timestamp": Date().timeIntervalSince1970,
                                "session_id": nfcViewModel.currentSessionId ?? "",
                                "output_id": transcription.id, // ← fixed (no optional chaining)
                                "liked": isLiked
                            ])
                        }) {
                            HStack {
                                Image(systemName: isLiked ? "heart.fill" : "heart")
                                    .foregroundColor(isLiked ? .red : .gray)
                                Text(isLiked ? "Liked" : "Like")
                                    .font(.body)
                                    .foregroundColor(.white)
                            }
                            .padding()
                            .frame(maxWidth: .infinity)
                        }

                        Button(action: {
                            onFinishTapped() // parent will close overlay; onDismiss will route
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                nfcViewModel.showSubscriptionOffer = true
                            }
                        }) {
                            Text("Finish")
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 50)
                                .background(Color.white)
                                .foregroundColor(.black)
                                .cornerRadius(50)
                                .padding(.horizontal)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 20)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(Color(red: 21/255, green: 21/255, blue: 21/255).edgesIgnoringSafeArea(.all))
                .toolbar {
                    ToolbarItem(placement: .principal) {
                        VStack(spacing: 4) {
                            Text(selectedMode)
                                .font(.headline)
                                .foregroundColor(.white)
                            Text(transcription.notetitle.trimmingCharacters(in: .init(charactersIn: "\"")))
                                .font(.body)
                                .lineLimit(1)
                                .truncationMode(.tail)
                                .foregroundColor(.white)
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Done") {
                            onDoneTapped()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                nfcViewModel.showSubscriptionOffer = true
                            }
                        }
                        .foregroundColor(.white)
                    }
                }
            } else {
                EmptyView()
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}

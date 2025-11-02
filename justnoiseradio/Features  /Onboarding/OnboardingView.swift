// OnboardingView.swift

import SwiftUI
import AVKit

struct OnboardingView: View {
    @AppStorage("hasCompletedOnboarding") var hasCompletedOnboarding: Bool = false
    @State private var currentPage = 0
    private let totalPages = 3

    var body: some View {
        NavigationView {
            VStack {
                // TabView for Onboarding Pages (3 pages)
                TabView(selection: $currentPage) {
                    OnboardingPage(
                        mediaType: .video,
                        mediaName: "JN_video1",
                        title: "Select Mode",
                        description: "Press the Customize Apps button to choose which apps stay unblocked."
                    )
                    .tag(0)

                    OnboardingPage(
                        mediaType: .video,
                        mediaName: "JN_video2",
                        title: "Stay Focused",
                        description: "Get in the zone—distractions off, focus mode on."
                    )
                    .tag(1)

                    OnboardingPage(
                        mediaType: .video,
                        mediaName: "JN_video3",
                        title: "Reset & Reflect",
                        description: "After each session, take a moment to capture your thoughts, track your progress, and reset your mind for what’s next."
                    )
                    .tag(2)
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                .animation(.easeInOut, value: currentPage)
                .navigationTitle("How to Use")
                .navigationBarTitleDisplayMode(.inline)

                // Custom Page Indicator
                HStack(spacing: 8) {
                    ForEach(0..<totalPages, id: \.self) { index in
                        Circle()
                            .fill(index == currentPage ? Color.white : Color.gray.opacity(0.5))
                            .frame(width: 10, height: 10)
                            .animation(.easeInOut, value: currentPage)
                            .accessibilityHidden(true)
                    }
                }
                .padding(.bottom, 20)
                
                // Fixed Action Button
                Button(action: {
                    if currentPage < totalPages - 1 {
                        withAnimation {
                            currentPage += 1
                        }
                    } else {
                        hasCompletedOnboarding = true
                    }
                }) {
                    Text(buttonTitle())
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.white)
                        .foregroundColor(.black)
                        .cornerRadius(50)
                        .shadow(color: Color.black.opacity(0.2), radius: 5, x: 0, y: 2)
                }
                .padding(.horizontal, 30)
                .padding(.bottom, 20)
                .accessibilityLabel(buttonTitle())
            }
            .background(Color(UIColor.systemGroupedBackground).ignoresSafeArea())
        }
    }
    
    // Determine Button Title Based on Current Page
    private func buttonTitle() -> String {
        switch currentPage {
        case 0:
            return "Get Started"
        case totalPages - 1:
            return "Start Activating"
        default:
            return "Continue"
        }
    }
}

// MARK: - Media Type Enum
enum MediaType {
    case image
    case video
    case none
}

// MARK: - Reusable Onboarding Page
struct OnboardingPage: View {
    let mediaType: MediaType
    let mediaName: String?
    let title: String
    let description: String
    
    var body: some View {
        VStack(spacing: 30) {
            Spacer()
            
            // Title
            Text(title)
                .font(.largeTitle)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .foregroundColor(.primary)
                .accessibilityAddTraits(.isHeader)
            
            // Display Image or Video
            if let mediaName = mediaName {
                if mediaType == .image {
                    Image(uiImage: UIImage(named: mediaName) ?? UIImage(systemName: "questionmark.circle")!)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 250)
                        .foregroundColor(.teal)
                        .accessibilityHidden(true)
                } else if mediaType == .video {
                    VideoPlayerView(videoName: mediaName)
                        .frame(height: 250)
                        .cornerRadius(12)
                        .shadow(radius: 5)
                }
            }
            
            // Description
            Text(description)
                .font(.body)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
                .padding(.horizontal, 30)
            
            Spacer()
        }
        .padding(.vertical, 40)
    }
}

// MARK: - Video Player View with Looping
struct VideoPlayerView: View {
    let videoName: String
    @State private var player: AVPlayer?
    
    var body: some View {
        if let url = Bundle.main.url(forResource: videoName, withExtension: "mp4") {
            VideoPlayer(player: player)
                .onAppear {
                    if player == nil {  // Ensure it initializes only once
                        let newPlayer = AVPlayer(url: url)
                        newPlayer.isMuted = true  // Optional: mute for silent looping
                        newPlayer.actionAtItemEnd = .none  // Prevent the player from stopping
                        
                        // Add observer to loop the video
                        NotificationCenter.default.addObserver(
                            forName: .AVPlayerItemDidPlayToEndTime,
                            object: newPlayer.currentItem,
                            queue: .main
                        ) { _ in
                            newPlayer.seek(to: .zero)
                            newPlayer.play()
                        }
                        
                        newPlayer.play()
                        player = newPlayer
                    }
                }
                .onDisappear {
                    // Remove observer when view disappears to prevent memory leaks
                    if let currentItem = player?.currentItem {
                        NotificationCenter.default.removeObserver(
                            self,
                            name: .AVPlayerItemDidPlayToEndTime,
                            object: currentItem
                        )
                    }
                }
        } else {
            Text("Video not found")
                .foregroundColor(.red)
                .font(.headline)
        }
    }
}

// MARK: - Preview
struct OnboardingView_Previews: PreviewProvider {
    static var previews: some View {
        OnboardingView()
    }
}

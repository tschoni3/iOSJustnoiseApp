//
//  SurveyView.swift
//

import SwiftUI
import AVKit

// Custom video player wrapper that disables controls.
struct AutoplayVideoPlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    
    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = false
        controller.videoGravity = .resizeAspectFill
        return controller
    }
    
    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        // No updates needed.
    }
}

struct SurveyView: View {
    // Binding passed from the parent view to control survey presentation.
    @Binding var showSurvey: Bool

    // Survey steps:
    // 0: Nickname (Required)
    // 1: Main goal (Required, multi-select)
    // 2: Biggest distraction (Required, multi-select)
    // 3: Age (Optional)
    // 4: Occupation (Optional)
    // 5: Thank you
    @State private var step: Int = 0
    
    // Input values
    @State private var name: String = ""
    @State private var selectedMainGoals: Set<String> = []
    @State private var selectedDistractions: Set<String> = []
    @State private var selectedAgeRange: String = ""
    @State private var selectedOccupation: String = ""
    
    // Options for each question.
    private let mainGoalOptions = [
        "🔥 Improve focus & avoid distractions",
        "⏳ Manage time better",
        "🎨 Boost creativity",
        "📈 Increase productivity & efficiency",
        "🤔 Just exploring"
    ]
    
    private let distractionOptions = [
        "📱 Social media & phone notifications",
        "📩 Emails & messages",
        "🧠 Overthinking & procrastination",
        "🚪 Interruptions from people",
        "😴 Fatigue & lack of motivation"
    ]
    
    private let ageRanges = ["18-21", "21-28", "28-35", "35-42", "42+"]
    private let occupationOptions = ["Student", "Professional", "Entrepreneur", "Artist", "Other"]
    
    // Persistent settings via AppStorage.
    @AppStorage("hasCompletedSurvey") var hasCompletedSurvey: Bool = false
    @AppStorage("userName") var userName: String = ""
    @AppStorage("userLanguage") var userLanguage: String = "Auto detect"
    
    // Error handling
    @State private var errorMessage: String?
    
    // Total progress steps (steps 0-4 are inputs; step 5 is the thank-you screen)
    private let totalSteps: Double = 5
    
    // AVPlayer for the thank-you video.
    @State private var player: AVPlayer = {
        guard let url = Bundle.main.url(forResource: "Thank_You_Video", withExtension: "mp4") else {
            fatalError("Thank_You_Video.mp4 not found in bundle.")
        }
        return AVPlayer(url: url)
    }()
    
    var body: some View {
        NavigationView {
            ZStack {
                if step < 5 {
                    VStack {
                        // Header: Display current question.
                        Text(currentQuestion)
                            .font(.largeTitle)
                            .bold()
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, 10)
                            .padding(.horizontal)
                        
                        // Main content: Input view.
                        VStack {
                            inputView
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.horizontal)
                        .padding(.top, 20)
                        
                        // Buttons area.
                        VStack(spacing: 16) {
                            if step == 3 || step == 4 {
                                Button("Skip") {
                                    withAnimation { step += 1 }
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                            
                            Button(action: handleNext) {
                                ZStack(alignment: .leading) {
                                    GeometryReader { geometry in
                                        let progress = CGFloat(step) / CGFloat(totalSteps)
                                        Rectangle()
                                            .fill(Color.blue)
                                            .frame(width: geometry.size.width * progress)
                                            .cornerRadius(10)
                                    }
                                    .allowsHitTesting(false)
                                    
                                    Text("Next")
                                        .bold()
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .foregroundColor(.white)
                                }
                                .frame(height: 50)
                                .background(Color.gray.opacity(0.3))
                                .cornerRadius(10)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.bottom, 30)
                    }
                } else {
                    // Thank-you screen with the autoplaying video.
                    thankYouView
                }
            }
            .navigationBarTitle("", displayMode: .inline)
            .navigationBarItems(leading:
                Group {
                    if step > 0 && step < 5 {
                        backButton
                    } else {
                        Color.clear.frame(width: 70, height: 44)
                    }
                }
            )
            .navigationBarHidden(step == 5)
            .alert("Error", isPresented: Binding<Bool>(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage ?? "")
            }
            .animation(.easeInOut, value: step)
        }
    }
    
    // Thank-you screen using our custom video player.
    private var thankYouView: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)
            
            VStack(alignment: .leading, spacing: 0) {
                AutoplayVideoPlayer(player: player)
                    .onAppear {
                        player.play()
                    }
                    .frame(height: UIScreen.main.bounds.height * 0.6)
                    .edgesIgnoringSafeArea(.top)
                
                VStack(alignment: .leading, spacing: 14) {
                    Text("Thank you")
                        .font(.largeTitle.bold())
                        .foregroundColor(.white)
                    
                    Text("You're all set! JustNoise is ready for you—time to focus and make the most of it.")
                        .font(.body)
                        .foregroundColor(.white)
                }
                .padding(.horizontal)
                .padding(.top, 4)
                
                Spacer()
                
                Button(action: handleNext) {
                    Text("Finish")
                        .bold()
                        .foregroundColor(.black)
                        .padding()
                        .frame(maxWidth: .infinity)
                        .background(Color.white)
                        .cornerRadius(8)
                }
                .padding(.horizontal)
                .padding(.bottom, 30)
            }
        }
    }
    
    // Back button.
    private var backButton: some View {
        Button(action: {
            withAnimation { step -= 1 }
        }) {
            HStack {
                Image(systemName: "chevron.left")
                Text("Back")
            }
        }
    }
    
    // Dynamic question text.
    private var currentQuestion: String {
        switch step {
        case 0:
            return "Enter your Nickname"
        case 1:
            return "What’s your main goal with JustNoise?"
        case 2:
            return "What’s your biggest distraction?"
        case 3:
            return "How old are you?"
        case 4:
            return "What best describes you?"
        default:
            return ""
        }
    }
    
    // Main input content for steps 0-4.
    @ViewBuilder
    private var inputView: some View {
        switch step {
        case 0:
            TextField("Your name", text: $name)
                .padding(18)
                .background(Color.gray.opacity(0.2))
                .foregroundColor(.white)
                .cornerRadius(8)
        case 1:
            VStack(alignment: .leading, spacing: 8) {
                Text("Select at least one option.")
                    .font(.subheadline)
                    .padding(.bottom, 8)
                    .foregroundColor(.gray)
                ForEach(mainGoalOptions, id: \.self) { goal in
                    multiChoiceRow(title: goal,
                                   isSelected: selectedMainGoals.contains(goal)) {
                        if selectedMainGoals.contains(goal) {
                            selectedMainGoals.remove(goal)
                        } else {
                            selectedMainGoals.insert(goal)
                        }
                    }
                }
            }
        case 2:
            VStack(alignment: .leading, spacing: 8) {
                Text("Select at least one option.")
                    .foregroundColor(.gray)
                    .font(.subheadline)
                    .padding(.bottom, 8)
                ForEach(distractionOptions, id: \.self) { distraction in
                    multiChoiceRow(title: distraction,
                                   isSelected: selectedDistractions.contains(distraction)) {
                        if selectedDistractions.contains(distraction) {
                            selectedDistractions.remove(distraction)
                        } else {
                            selectedDistractions.insert(distraction)
                        }
                    }
                }
            }
        case 3:
            VStack(alignment: .leading, spacing: 8) {
                ForEach(ageRanges, id: \.self) { range in
                    radioButtonRow(title: range,
                                   isSelected: selectedAgeRange == range) {
                        selectedAgeRange = range
                    }
                }
            }
        case 4:
            VStack(alignment: .leading, spacing: 8) {
                ForEach(occupationOptions, id: \.self) { occupation in
                    radioButtonRow(title: occupation,
                                   isSelected: selectedOccupation == occupation) {
                        selectedOccupation = occupation
                    }
                }
            }
        default:
            EmptyView()
        }
    }
    
    // UI Helper: Radio button row.
    private func radioButtonRow(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.body)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark")
                }
            }
            .padding()
            .background(isSelected ? Color.blue.opacity(0.2) : Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? Color.blue : Color.gray, lineWidth: 1)
            )
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(8)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // UI Helper: Multi-choice row.
    private func multiChoiceRow(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(.body)
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.blue)
                } else {
                    Image(systemName: "circle")
                        .foregroundColor(.gray)
                }
            }
            .padding()
            .background(isSelected ? Color.blue.opacity(0.2) : Color.clear)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? Color.blue : Color.gray, lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // Action handler.
    private func handleNext() {
        switch step {
        case 0:
            guard !name.trimmingCharacters(in: .whitespaces).isEmpty else {
                errorMessage = "Please enter your nickname."
                return
            }
            step += 1
        case 1:
            guard !selectedMainGoals.isEmpty else {
                errorMessage = "Please select at least one main goal with JustNoise."
                return
            }
            step += 1
        case 2:
            guard !selectedDistractions.isEmpty else {
                errorMessage = "Please select at least one biggest distraction."
                return
            }
            step += 1
        case 3:
            step += 1
        case 4:
            step += 1
        case 5:
            userName = name
            hasCompletedSurvey = true
            
            let ageToStore = selectedAgeRange.isEmpty ? "Not Provided" : selectedAgeRange
            let occupationToStore = selectedOccupation.isEmpty ? "Not Provided" : selectedOccupation
            let mainGoalsToStore = selectedMainGoals.isEmpty ? "Not Provided" : selectedMainGoals.joined(separator: ", ")
            let distractionsToStore = selectedDistractions.isEmpty ? "Not Provided" : selectedDistractions.joined(separator: ", ")
            
            Task {
                do {
                    try await SupabaseManager.shared.upsertUserProfile(
                        name: name,
                        age: ageToStore,
                        language: "Auto detect",
                        occupation: occupationToStore,
                        mainGoal: mainGoalsToStore,
                        biggestDistraction: distractionsToStore
                    )
                    showSurvey = false
                } catch {
                    errorMessage = "Failed to update your profile: \(error.localizedDescription)"
                }
            }
        default:
            break
        }
    }
}

struct SurveyView_Previews: PreviewProvider {
    static var previews: some View {
        SurveyView(showSurvey: .constant(true))
    }
}

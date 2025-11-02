// SessionHistoryView.swift

import SwiftUI

struct SessionHistoryView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var selectedSession: Session?
    @State private var displayedError: IdentifiedErrorMessage?
    
    // Deletion state
    @State private var selectedForDeletion: Session?
    @State private var showDeleteAlert = false
    
    struct IdentifiedErrorMessage: Identifiable {
        let id = UUID()
        let message: String
    }
    
    private let customDarkColor = Color(red: 14/255, green: 14/255, blue: 13/255)
    private let customDarkColor1 = Color(red: 24/255, green: 24/255, blue: 24/255)
    
    private var totalSessions: Int { nfcViewModel.sessionHistory.count }
    private var totalTime: TimeInterval { nfcViewModel.sessionHistory.reduce(0) { $0 + $1.duration } }
    private var formattedTotalTime: String {
        let s = Int(totalTime); return String(format: "%d:%02d", s/3600, (s%3600)/60)
    }
    private var allSessions: [Session] {
        nfcViewModel.sessionHistory.sorted { $0.startDate > $1.startDate }
    }
    private var formattedAllTime: String {
        let s = Int(totalTime); return String(format: "%d:%02d", s/3600, (s%3600)/60)
    }
    private let columns = Array(repeating: GridItem(.flexible()), count: 4)
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color.white.edgesIgnoringSafeArea(.all)
                VStack(spacing: 0) {
                    ZStack(alignment: .topLeading) {
                        customDarkColor
                            .clipShape(RoundedCorners(radius: 30, corners: [.bottomLeft, .bottomRight]))
                            .frame(height: 200)
                        VStack(alignment: .leading, spacing: 16) {
                            Text("HISTORY")
                                .font(.custom("Technology-Bold", size: 48))
                                .foregroundColor(.white)
                            HStack(spacing: 16) {
                                SummaryCard(title: "COMPLETED SESSIONS", value: "\(totalSessions)")
                                SummaryCard(title: "SAVED TIME", value: formattedTotalTime)
                            }
                        }
                        .padding(.top, 10)
                        .padding(.horizontal)
                    }
                    
                    VStack(alignment: .leading, spacing: 16) {
                        Text("RECENTLY")
                            .font(.custom("Technology-Bold", size: 48))
                            .foregroundColor(.black)
                            .padding(.top, 10)
                        
                        ScrollView {
                            LazyVGrid(columns: columns, spacing: 16) {
                                Button(action: { dismiss() }) {
                                    CircleButton(label: "+", subtitle: "New Session", isNew: true, isHighlighted: false, isDimmed: false)
                                }
                                
                                ForEach(allSessions) { session in
                                    let hasJournal = (session.transcription != nil || session.audioFileURL != nil)

                                    if hasJournal {
                                        NavigationLink(value: session) {
                                            CircleButton(
                                                label: session.formattedDuration,
                                                subtitle: session.modeName ?? "Noise",
                                                isNew: false, isHighlighted: true, isDimmed: false
                                            )
                                        }
                                        .contextMenu {
                                            Button(role: .destructive) {
                                                selectedForDeletion = session
                                                showDeleteAlert = true
                                            } label: {
                                                Label("Delete", systemImage: "trash")
                                            }
                                        }
                                    } else {
                                        CircleButton(
                                            label: session.formattedDuration,
                                            subtitle: session.modeName ?? "Noise",
                                            isNew: false, isHighlighted: false, isDimmed: true
                                        )
                                        .contextMenu {
                                            Button(role: .destructive) {
                                                selectedForDeletion = session
                                                showDeleteAlert = true
                                            } label: {
                                                Label("Delete", systemImage: "trash")
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(.top, 8)
                        }
                    }
                    .padding()
                    .background(Color.white)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(customDarkColor, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .tint(.white)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack {
                        Spacer(); Spacer()
                        Capsule().fill(Color.white.opacity(0.4)).frame(width: 80, height: 5)
                        Spacer()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { dismiss() } label: {
                        Image("Justnoise_logo")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 30, height: 30)
                            .foregroundColor(.white)
                            .padding(10)
                            .background(Circle().fill(customDarkColor1))
                    }
                }
            }
            // Error alert
            .alert(item: $displayedError) { item in
                Alert(title: Text("Error"),
                      message: Text(item.message),
                      dismissButton: .default(Text("OK")))
            }
            // Delete alert
            .alert("Delete this session?",
                   isPresented: $showDeleteAlert,
                   presenting: selectedForDeletion) { session in
                Button("Delete", role: .destructive) {
                    deleteSession(session)
                }
                Button("Cancel", role: .cancel) {
                    selectedForDeletion = nil
                }
            } message: { _ in
                Text("This will permanently remove the session from your history.")
            }
            // Navigation
            .navigationDestination(for: Session.self) { session in
                SessionDetailView(session: session)
                    .preferredColorScheme(.dark)
            }
        }
    }
    
    // MARK: - Helpers
    private func deleteSession(_ session: Session) {
        if let idx = nfcViewModel.sessionHistory.firstIndex(where: { $0.id == session.id }) {
            UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
            nfcViewModel.sessionHistory.remove(at: idx)
        } else {
            displayedError = IdentifiedErrorMessage(message: "Couldn’t find this session to delete.")
        }
        selectedForDeletion = nil
    }
}

// MARK: - RoundedCorners, SummaryCard, CircleButton
struct RoundedCorners: Shape {
    var radius: CGFloat
    var corners: UIRectCorner
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

struct SummaryCard: View {
    let title: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.caption).foregroundColor(.gray)
            Text(value).font(.custom("Technology-Bold", size: 48)).foregroundColor(.white)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 19/255, green: 19/255, blue: 18/255))
        .cornerRadius(10)
        .shadow(color: .black.opacity(0.5), radius: 5, x: 0, y: 4)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.1), lineWidth: 1))
    }
}

struct CircleButton: View {
    let label: String
    let subtitle: String
    let isNew: Bool
    let isHighlighted: Bool
    let isDimmed: Bool
    init(label: String, subtitle: String, isNew: Bool = false, isHighlighted: Bool = false, isDimmed: Bool = false) {
        self.label = label; self.subtitle = subtitle; self.isNew = isNew; self.isHighlighted = isHighlighted; self.isDimmed = isDimmed
    }
    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle()
                    .fill(isNew ? Color.gray.opacity(0.2) : Color.black)
                    .frame(width: 80, height: 80)
                    .overlay(
                        Group {
                            if isHighlighted {
                                Circle().stroke(Color.blue, lineWidth: 4)
                                Circle().inset(by: 2).stroke(Color.white.opacity(0.9), lineWidth: 2)
                            }
                        }
                    )
                    .shadow(color: Color.black.opacity(0.35), radius: 3, x: 0, y: 3)
                Text(label)
                    .font(.custom("Digital-7 Mono", size: isNew ? 24 : 14))
                    .foregroundColor(isNew ? .gray : (isDimmed ? Color.white.opacity(1.0) : .white))
            }
            Text(subtitle)
                .font(.caption)
                .foregroundColor(isDimmed ? .gray.opacity(0.7) : .gray)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(width: 80)
        }
    }
}

// MARK: - Preview
struct SessionHistoryView_Previews: PreviewProvider {
    static var previews: some View {
        let mockTranscription = TranscriptionResponse(
            notetitle: "Deep Work Reflection",
            overview: "Today was focused and productive. Completed main tasks.",
            actionsteps: "1. Review notes\n2. Plan tomorrow",
            challenges: "1. Distraction\n2. Fatigue",
            transcript: "Spoke about main achievements and mindset.",
            sentiment: "Positive",
            aifeedback: "Great structure and momentum!"
        )
        
        let mockViewModel = NFCViewModel()
        
        let sessionWithJournal = Session(startDate: Date().addingTimeInterval(-3600), duration: 1800, modeName: "Focus", transcription: mockTranscription)
        
        mockViewModel.sessionHistory = [
            sessionWithJournal,
            Session(startDate: Date().addingTimeInterval(-7200), duration: 2400, modeName: "Default"),
            Session(startDate: Date().addingTimeInterval(-10800), duration: 1500, modeName: "Work")
        ]
        return SessionHistoryView()
            .environmentObject(mockViewModel)
    }
}


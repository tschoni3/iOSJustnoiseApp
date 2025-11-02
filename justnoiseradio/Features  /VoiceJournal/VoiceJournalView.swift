//
//  VoiceJournalView.swift
//  JustNoise
//

import SwiftUI
import AVFoundation
import PostHog   // ✅ Import PostHog to use Analytics
// Make sure you also created Analytics.swift in your project

enum JournalMode: String, CaseIterable {
    case voice = "Voice"
    case text  = "Text"
}

struct VoiceJournalView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.dismiss) private var dismiss

    var onFlowEnded: () -> Void = {}

    @StateObject private var audioRecorder = AudioRecorder()

    @State private var transcriptionResult: TranscriptionResponse?
    @State private var isRecording = false
    @State private var isProcessing = false
    @State private var showOverlay = false
    @State private var showProcessingScreen = false
    @State private var errorMessage: String?

    private let minimumRecordingDuration: TimeInterval = 5.0
    @State private var recordingStartTime: Date? = nil

    @State private var journalMode: JournalMode = .voice
    @State private var textInput: String = ""
    private let minimumTextCharacters: Int = 12
    @FocusState private var textEditorFocused: Bool

    @AppStorage("userName") var userName: String = ""

    @State private var uploadTask: URLSessionDataTask?
    @State private var reflectionStartTime: Date? = nil  // ← NEW: start of journaling (both modes)


    var body: some View {
        ZStack {
            Color(red: 21/255, green: 21/255, blue: 21/255).edgesIgnoringSafeArea(.all)

            if showProcessingScreen {
                ProcessingView(onCancel: cancelProcessing)
                    .transition(.opacity)
            } else {
                VStack(spacing: 20) {
                    // Mode Toggle
                    if !(journalMode == .voice && isRecording) {
                        Picker("", selection: $journalMode) {
                            ForEach(JournalMode.allCases, id: \.self) { mode in
                                Text(mode.rawValue).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 24)
                        .padding(.top, 12)
                        .disabled(isRecording)
                    }

                    if journalMode == .voice {
                        if !isRecording {
                            Text("Tap to Record")
                                .foregroundColor(.white)
                                .font(.system(size: 22, weight: .bold))
                                .padding(.top, 60)
                                .transition(.opacity)
                        }
                    } else {
                        Text("Write your reflection")
                            .foregroundColor(.white)
                            .font(.system(size: 22, weight: .bold))
                            .padding(.top, 60)
                    }

                    Spacer(minLength: 0)

                    if journalMode == .voice {
                        // Voice UI
                        RippleButton(
                            action: { isRecording ? stopRecording() : startRecording() },
                            isActive: isRecording,
                            activeColor: Color(red: 53/255, green: 53/255, blue: 53/255),
                            inactiveColor: Color(red: 53/255, green: 53/255, blue: 53/255),
                            logoImageName: "Justnoise_logo"
                        )
                        .frame(width: 150, height: 150)
                        .shadow(color: Color.black.opacity(0.5), radius: 10, x: 0, y: 4)

                        Spacer()

                        if isRecording {
                            VStack(spacing: 20) {
                                Text("Listening Noise")
                                    .foregroundColor(.white)
                                    .font(.title2.bold())
                                    .padding(.bottom, 40)

                                GeometryReader { geometry in
                                    ZStack {
                                        Button(action: { cancelRecording() }) {
                                            Image(systemName: "xmark.circle.fill")
                                                .resizable().frame(width: 50, height: 50)
                                                .foregroundColor(.white)
                                        }
                                        .position(x: geometry.size.width / 2,
                                                  y: geometry.size.height / 2)

                                        Button(action: { sendRecording() }) {
                                            Image(systemName: "paperplane.circle.fill")
                                                .resizable().frame(width: 50, height: 50)
                                                .foregroundColor(.white)
                                        }
                                        .position(x: geometry.size.width - 60,
                                                  y: geometry.size.height / 2)
                                    }
                                }
                                .frame(height: 70)
                                .padding(.bottom, 30)
                            }
                        } else {
                            Text("Take a moment to stop and think about your day. What went well? What was tough? What did you learn?\n\nTip: Speak for at least 5 seconds for a better transcription.")
                                .foregroundColor(.gray)
                                .font(.footnote)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                                .padding(.bottom, 20)
                        }
                    } else {
                        // Text UI
                        VStack(spacing: 12) {
                            ZStack(alignment: .topLeading) {
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(Color(red: 37/255, green: 37/255, blue: 38/255))
                                if textInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    Text(examplePlaceholder)
                                        .foregroundColor(.white.opacity(0.35))
                                        .font(.body)
                                        .padding(16)
                                        .allowsHitTesting(false)
                                }
                                TextEditor(text: $textInput)
                                    .scrollContentBackground(.hidden)
                                    .foregroundColor(.white)
                                    .padding(12)
                                    .frame(minHeight: 200, maxHeight: 280)
                                    .focused($textEditorFocused)
                            }
                            .padding(.horizontal, 24)

                            HStack {
                                Text("\(textInput.count) chars")
                                    .foregroundColor(textInput.count < minimumTextCharacters ? .red : .gray)
                                    .font(.caption)
                                Spacer()
                                Button {
                                    sendTextEntry()
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "paperplane.fill")
                                        Text("Send")
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(RoundedRectangle(cornerRadius: 24)
                                        .stroke(Color.white, lineWidth: 1))
                                }
                                .disabled(textInput.trimmingCharacters(in: .whitespacesAndNewlines).count < minimumTextCharacters)
                                .opacity(textInput.trimmingCharacters(in: .whitespacesAndNewlines).count < minimumTextCharacters ? 0.5 : 1.0)
                            }
                            .padding(.horizontal, 24)
                            .padding(.bottom, 12)

                            Text("Tip: Be specific. What happened? How did it feel? What’s the next tiny step?")
                                .foregroundColor(.gray)
                                .font(.footnote)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                        }
                        Spacer()
                    }
                }
            }
        }
        .navigationBarTitle("Reflect", displayMode: .inline)
        .onAppear {
            // 🧠 Track reflection opened
            Analytics.capture("reflection_opened", props: [
                "timestamp": Date().timeIntervalSince1970,
                "session_id": nfcViewModel.currentSessionId ?? ""
            ])
            if reflectionStartTime == nil { reflectionStartTime = Date() }

        }
        .toolbar {
            if journalMode == .text && textEditorFocused {
                ToolbarItemGroup(placement: .keyboard) {
                    Button {
                        journalMode = .voice
                        textEditorFocused = false
                    } label: {
                        Label("Switch to Voice", systemImage: "mic.fill")
                    }
                    Spacer()
                    Button("Done") {
                        textEditorFocused = false
                    }
                }
            }
        }
        .sheet(isPresented: $showOverlay, onDismiss: {
            endFlow()
        }) {
            TranscriptionOverlayView(
                transcription: transcriptionResult,
                selectedMode: nfcViewModel.selectedMode?.name ?? "Default",
                onFinishTapped: { showOverlay = false },
                onDoneTapped: { showOverlay = false }
            )
            .environmentObject(nfcViewModel)
        }
        .alert(isPresented: Binding<Bool>(
            get: { self.errorMessage != nil },
            set: { if !$0 { self.errorMessage = nil } }
        )) {
            Alert(title: Text("Error"),
                  message: Text(errorMessage ?? ""),
                  dismissButton: .default(Text("OK")))
        }
    }

    // MARK: - Voice Recording

    private func startRecording() {
        guard nfcViewModel.selectedMode != nil else {
            self.errorMessage = "Please select a mode before starting a voice journal."
            return
        }
        audioRecorder.startRecording()
        isRecording = true
        recordingStartTime = Date()
        if reflectionStartTime == nil { reflectionStartTime = recordingStartTime } // ← ensure non-nil
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func stopRecording() {
        audioRecorder.stopRecording { result in
            DispatchQueue.main.async {
                self.isRecording = false
                switch result {
                case .success(let url):
                    let duration = checkRecordingDuration()
                    if duration < minimumRecordingDuration {
                        self.errorMessage = "That was too short. Please record at least 5 seconds."
                        return
                    }
                    self.showProcessingScreen = true
                    uploadAudio(url: url)
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
            }
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func cancelRecording() {
        audioRecorder.stopRecording { _ in
            DispatchQueue.main.async { self.isRecording = false }
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func sendRecording() {
        audioRecorder.stopRecording { result in
            DispatchQueue.main.async {
                self.isRecording = false
                switch result {
                case .success(let url):
                    let duration = checkRecordingDuration()
                    if duration < minimumRecordingDuration {
                        self.errorMessage = "That was too short. Please record at least 5 seconds."
                        return
                    }
                    self.showProcessingScreen = true
                    uploadAudio(url: url)
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
            }
        }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func checkRecordingDuration() -> TimeInterval {
        guard let start = recordingStartTime else { return 0 }
        return Date().timeIntervalSince(start)
    }

    // MARK: - Upload: Audio

    private func uploadAudio(url: URL) {
        isProcessing = true

        guard let audioData = try? Data(contentsOf: url) else {
            self.errorMessage = "Failed to read audio data."
            self.isProcessing = false
            self.showProcessingScreen = false
            return
        }

        let endpoint = "https://swift-5e8ce9b2e6d0.herokuapp.com/transcribe"
        guard let requestURL = URL(string: endpoint) else {
            self.errorMessage = "Invalid backend URL."
            self.isProcessing = false
            self.showProcessingScreen = false
            return
        }

        var request = URLRequest(url: requestURL)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let selectedModeName = nfcViewModel.selectedMode?.name ?? "Default"
        let selectedLanguage = UserDefaults.standard.string(forKey: "userLanguage") ?? ""

        var body = Data()
        let filename = "voicejournal.wav"
        let mimeType = "audio/wav"

        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"audio\"; filename=\"\(filename)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(audioData)
        body.append("\r\n".data(using: .utf8)!)

        body.append(formField(named: "user_name", value: userName, boundary: boundary))
        body.append(formField(named: "selected_mode", value: selectedModeName, boundary: boundary))
        body.append(formField(named: "language", value: selectedLanguage, boundary: boundary))
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        uploadTask = URLSession.shared.dataTask(with: request) { data, _, error in
            handleServerResponse(data: data, error: error, audioURL: url)
        }
        uploadTask?.resume()
    }

    // MARK: - Upload: Text

    private func sendTextEntry() {
        guard nfcViewModel.selectedMode != nil else {
            self.errorMessage = "Please select a mode before journaling."
            return
        }
        let trimmed = textInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= minimumTextCharacters else {
            self.errorMessage = "Write at least \(minimumTextCharacters) characters."
            return
        }
        
        if reflectionStartTime == nil { reflectionStartTime = Date() } // ← NEW


        isProcessing = true
        showProcessingScreen = true

        let endpoint = "https://swift-5e8ce9b2e6d0.herokuapp.com/transcribe"
        guard let requestURL = URL(string: endpoint) else {
            self.errorMessage = "Invalid backend URL."
            self.isProcessing = false
            self.showProcessingScreen = false
            return
        }

        var request = URLRequest(url: requestURL)
        request.httpMethod = "POST"
        let boundary = UUID().uuidString
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        let selectedModeName = nfcViewModel.selectedMode?.name ?? "Default"
        let selectedLanguage = UserDefaults.standard.string(forKey: "userLanguage") ?? ""

        var body = Data()
        body.append(formField(named: "text", value: trimmed, boundary: boundary))
        body.append(formField(named: "user_name", value: userName, boundary: boundary))
        body.append(formField(named: "selected_mode", value: selectedModeName, boundary: boundary))
        body.append(formField(named: "language", value: selectedLanguage, boundary: boundary))
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        uploadTask = URLSession.shared.dataTask(with: request) { data, _, error in
            handleServerResponse(data: data, error: error, audioURL: nil)
        }
        uploadTask?.resume()
    }

    // MARK: - Shared networking helpers

    private func formField(named name: String, value: String, boundary: String) -> Data {
        var d = Data()
        d.append("--\(boundary)\r\n".data(using: .utf8)!)
        d.append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".data(using: .utf8)!)
        d.append("\(value)\r\n".data(using: .utf8)!)
        return d
    }

    private func handleServerResponse(data: Data?, error: Error?, audioURL: URL?) {
        DispatchQueue.main.async {
            self.isProcessing = false
            self.showProcessingScreen = false
            self.uploadTask = nil

            if let error = error {
                self.errorMessage = error.localizedDescription
                return
            }
            guard let data = data else {
                self.errorMessage = "No data received from server."
                return
            }
            do {
                let decoder = JSONDecoder()
                let transcription = try decoder.decode(TranscriptionResponse.self, from: data)
                self.transcriptionResult = transcription
                self.nfcViewModel.saveTranscription(transcription)

                // 💾 Track reflection_saved (both voice & text)
                let start = self.reflectionStartTime ?? self.recordingStartTime ?? Date()
                let duration = Date().timeIntervalSince(start)
                Analytics.capture("reflection_saved", props: [
                    "timestamp": Date().timeIntervalSince1970,
                    "session_id": self.nfcViewModel.currentSessionId ?? "",
                    "journal_duration_sec": Int(duration),
                    "has_audio": (audioURL != nil)
                ])
                // Reset for next time
                self.reflectionStartTime = nil
                self.recordingStartTime = nil

                if let audioURL = audioURL {
                    saveAudioFile(url: audioURL) { savedURL in
                        if let savedURL = savedURL {
                            self.nfcViewModel.addTranscriptionToLatestSession(
                                transcription: transcription,
                                audioURL: savedURL
                            )
                        } else {
                            self.errorMessage = "Failed to save audio file."
                        }
                        self.showOverlay = true
                    }
                } else {
                    self.nfcViewModel.addTranscriptionToLatestSession(transcription: transcription)
                    self.textInput = ""
                    self.showOverlay = true
                }

            } catch {
                self.errorMessage = "Failed to decode response: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Cancel

    private func cancelProcessing() {
        uploadTask?.cancel()
        uploadTask = nil
        self.isProcessing = false
        self.showProcessingScreen = false
        self.errorMessage = "Processing was canceled."
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    // MARK: - Save Audio

    private func saveAudioFile(url: URL, completion: @escaping (URL?) -> Void) {
        let fileManager = FileManager.default
        guard let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            completion(nil); return
        }
        let timestamp = Int(Date().timeIntervalSince1970)
        let destinationURL = documentsURL.appendingPathComponent("voicejournal_\(timestamp).wav")
        do {
            try fileManager.copyItem(at: url, to: destinationURL)
            completion(destinationURL)
        } catch {
            print("Error saving audio file: \(error.localizedDescription)")
            completion(nil)
        }
    }

    // MARK: - Flow Termination
    private func endFlow() {
        nfcViewModel.showVoiceJournal = false
        onFlowEnded()
        dismiss()
    }

    private var examplePlaceholder: String {
        """
        Example: I felt scattered for the first 15 minutes, but after blocking Instagram I got into flow and finished the outline. I’m proud I shipped the draft. Next time I’ll start with a 5-min brain dump to warm up.
        """
    }
}


// MARK: - ProcessingView (inlined so it's always in scope)
struct ProcessingView: View {
    var onCancel: () -> Void
    @State private var gradientOffset: CGFloat = -400

    var body: some View {
        VStack {
            Spacer()
            
            VStack(spacing: 30) {
                Text("Turning down the noise... Clarity is on the way.")
                    .foregroundColor(.white)
                    .font(.title2.bold())
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                
                // Progress Bar
                ZStack(alignment: .leading) {
                    Rectangle()
                        .frame(height: 8)
                        .foregroundColor(Color(red: 37/255, green: 37/255, blue: 38/255))
                        .cornerRadius(4)
                    
                    GeometryReader { geometry in
                        Rectangle()
                            .fill(
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        Color(red: 215/255, green: 250/255, blue: 0/255),
                                        Color.white.opacity(0.0),
                                        Color(red: 215/255, green: 250/255, blue: 0/255)
                                    ]),
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: geometry.size.width * 6, height: 8)
                            .cornerRadius(4)
                            .offset(x: gradientOffset)
                            .blur(radius: 1.5)
                            .animation(
                                Animation.linear(duration: 4.0)
                                    .repeatForever(autoreverses: false),
                                value: gradientOffset
                            )
                    }
                    .mask(
                        Rectangle()
                            .frame(height: 8)
                            .cornerRadius(4)
                    )
                }
                .padding(.horizontal, 40)
                .frame(height: 8)
                .onAppear {
                    startAnimation(screenWidth: UIScreen.main.bounds.width - 80)
                }
            }
            
            Spacer()
            
            Button(action: {
                onCancel()
            }) {
                Text("Cancel")
                    .foregroundColor(.white)
                    .font(.body)
                    .padding()
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .overlay(
                        RoundedRectangle(cornerRadius: 50)
                            .stroke(Color.white, lineWidth: 1)
                    )
            }
            .padding(.horizontal, 40)
            .padding(.bottom, 30)
        }
        .padding()
        .background(Color(red: 21/255, green: 21/255, blue: 21/255))
        .cornerRadius(20)
        .shadow(radius: 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.6))
        .edgesIgnoringSafeArea(.all)
        .transition(.opacity)
    }
    
    private func startAnimation(screenWidth: CGFloat) {
        gradientOffset = -screenWidth
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation {
                self.gradientOffset = screenWidth
            }
        }
    }
}


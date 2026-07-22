//
//  CaptureTapeView.swift
//  justnoise
//

import SwiftUI
import AVFoundation

private enum SignalTapeMetrics {
    static let waveformHeightScale: CGFloat = 1.1
    static let markerStemWidth: CGFloat = 3
    static let markerTopPaddingRecording: CGFloat = 4
    static let markerTopPaddingIdle: CGFloat = 10
    static let markerBottomPaddingRecording: CGFloat = 8
    static let markerBottomPaddingIdle: CGFloat = 16
    static let controlSpacing: CGFloat = 14
}

struct SignalTapeView: View {
    @EnvironmentObject private var nfcViewModel: NFCViewModel
    @EnvironmentObject private var signalStore: SignalStore

    @StateObject private var audioRecorder = AudioRecorder()
    @StateObject private var tapePlayer = SignalTapePlayer()

    @State private var isRecording = false
    @State private var recordingStartedAt: Date?
    @State private var recordingDuration: TimeInterval = 0
    @State private var recordingTimer: Timer?
    @State private var scrubProgress: TimeInterval = 0
    @State private var isScrubbing = false
    @State private var hasUserPositionedTape = false
    @State private var liveMeterSamples: [CGFloat] = []
    @State private var activeRecordingMarkerID = UUID()
    @State private var waveformSamplesByClipID: [UUID: [CGFloat]] = [:]
    @State private var waveformGenerationToken = UUID()
    @State private var errorMessage: String?
    @State private var showSignalTimeline = false

    private let minimumCaptureDuration: TimeInterval = 0.8
    private let shellBackground = Color(#colorLiteral(red: 0.082, green: 0.082, blue: 0.082, alpha: 1.0))

    private var clips: [CaptureClip] {
        signalStore.captureClips
    }

    private var storedTotalDuration: TimeInterval {
        clips.reduce(0) { $0 + $1.duration }
    }

    private var displayedTotalDuration: TimeInterval {
        storedTotalDuration + (isRecording ? recordingDuration : 0)
    }

    private var storedTimelineItems: [SignalTimelineItem] {
        var cursor: TimeInterval = 0

        return clips.map { clip in
            defer { cursor += clip.duration }
            return SignalTimelineItem(
                clip: clip,
                startTime: cursor,
                waveformSamples: waveformSamplesByClipID[clip.id]
            )
        }
    }

    private var liveWaveformSamples: [CGFloat] {
        condensedLiveWaveformSamples(from: liveMeterSamples)
    }

    private var recordingTimelineItem: SignalTimelineItem? {
        guard isRecording else { return nil }

        return SignalTimelineItem(
            liveID: activeRecordingMarkerID,
            createdAt: recordingStartedAt ?? .now,
            startTime: storedTotalDuration,
            duration: max(recordingDuration, 0.65),
            waveformSamples: liveWaveformSamples
        )
    }

    private var timelineItems: [SignalTimelineItem] {
        var items = storedTimelineItems

        if let recordingTimelineItem {
            items.append(recordingTimelineItem)
        }

        return items
    }

    private var waveformBars: [SignalWaveBar] {
        timelineItems.flatMap { item in
            item.waveformBars
        }
    }

    private var defaultRestingProgress: TimeInterval {
        storedTotalDuration
    }

    private var activeProgress: TimeInterval {
        if isRecording {
            return displayedTotalDuration
        }

        if tapePlayer.hasPlaybackPosition {
            return min(tapePlayer.playbackProgress, max(storedTotalDuration, 0))
        }

        return min(max(scrubProgress, 0), max(storedTotalDuration, 0))
    }

    private var currentItem: SignalTimelineItem? {
        guard !timelineItems.isEmpty else { return nil }

        let safeProgress = min(max(activeProgress, 0), max(displayedTotalDuration, 0))
        return timelineItems.last(where: { safeProgress >= $0.startTime }) ?? timelineItems.first
    }

    private var currentMarkerNumber: Int {
        guard
            let currentItem,
            let index = timelineItems.firstIndex(where: { $0.id == currentItem.id })
        else {
            return max(timelineItems.count, 1)
        }

        return index + 1
    }




    private var selectedClipDateText: String {
        if isRecording {
            return Date.now.formatted(.dateTime.month(.abbreviated).day())
        }

        if let currentItem {
            return currentItem.createdAt.formatted(.dateTime.month(.abbreviated).day())
        }

        return ""
    }

    private var tapeStatusText: String {
        if isRecording {
            return formattedClock(recordingDuration)
        }

        if isScrubbing || tapePlayer.hasPlaybackPosition {
            return "\(formattedClock(activeProgress)) / \(formattedTapeDuration(storedTotalDuration))"
        }

        return "\(clips.count) captures"
    }

    private var heroTitle: String {
        (clips.isEmpty && !isRecording) ? "Capture" : "Captured"
    }

    private var heroSubtitle: String {
        (clips.isEmpty && !isRecording) ? "Noise" : formattedTapeDuration(displayedTotalDuration)
    }

    private var heroSubtitleFontSize: CGFloat {
        JustnoiseHeroMetrics.subtitleFontSize
    }

    private var signalButtonIndicatorState: SignalControlIndicatorState {
        signalStore.signalControlIndicatorState
    }

    private var selectedModeName: String? {
        nfcViewModel.selectedMode?.name
    }

    var body: some View {
        ZStack {
            signalBackground

            VStack(alignment: .leading, spacing: 20) {
                JustnoiseHeroSection(
                    title: heroTitle,
                    subtitle: heroSubtitle,
                    titleColor: .white,
                    subtitleColor: Color.white.opacity(0.12),
                    subtitleFontSize: heroSubtitleFontSize
                )

                GeometryReader { geometry in
                    let tapeHeight = min(max(geometry.size.height * 0.42, 320), 380)
                    let bottomClearance = max(geometry.safeAreaInsets.bottom + 90, 108)

                    VStack(alignment: .leading, spacing: 20) {
                        tapeCard(height: tapeHeight)

    

                        controlsRow

                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, bottomClearance)
                }
            }
        }
        .onAppear {
            scrubProgress = storedTotalDuration
            hasUserPositionedTape = true
            refreshWaveforms()
            signalStore.captureSurfaceDidAppear(selectedModeName: selectedModeName)
        }
        .onChange(of: clips.map(\.id)) { _, _ in
            refreshWaveforms()
        }
        .onChange(of: clips.last?.id) { _, _ in
            guard !hasUserPositionedTape else { return }
            guard !isRecording, !tapePlayer.isPlaying, !tapePlayer.canResume, !isScrubbing else { return }

            scrubProgress = defaultRestingProgress
        }
        .onChange(of: tapePlayer.lastErrorMessage) { _, newValue in
            guard let newValue else { return }
            errorMessage = newValue
        }
        .onDisappear {
            waveformGenerationToken = UUID()
            recordingTimer?.invalidate()
            recordingTimer = nil
            tapePlayer.stop()
            liveMeterSamples = []

            if isRecording {
                audioRecorder.stopRecording { _ in }
                isRecording = false
            }
        }
        .alert(
            isPresented: Binding<Bool>(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )
        ) {
            Alert(
                title: Text("Signal"),
                message: Text(errorMessage ?? ""),
                dismissButton: .default(Text("OK"))
            )
        }
        .sheet(isPresented: $showSignalTimeline) {
            SignalTimelineSheet()
                .environmentObject(nfcViewModel)
                .environmentObject(signalStore)
        }
    }

    private var signalBackground: some View {
        ZStack {
            shellBackground

            LinearGradient(
                colors: [
                    Color.white.opacity(0.07),
                    Color.white.opacity(0.018),
                    Color.clear
                ],
                startPoint: .top,
                endPoint: .bottom
            )

            RadialGradient(
                colors: [
                    Color.white.opacity(0.05),
                    Color.clear
                ],
                center: .topTrailing,
                startRadius: 8,
                endRadius: 380
            )

            Circle()
                .fill(Color.white.opacity(0.045))
                .frame(width: 280, height: 280)
                .blur(radius: 46)
                .offset(x: 150, y: -150)
        }
        .ignoresSafeArea()
    }

    private func tapeCard(height: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 34, style: .continuous)
                .fill(Color(red: 0.07, green: 0.07, blue: 0.07))
                .overlay(
                    RoundedRectangle(cornerRadius: 34, style: .continuous)
                        .stroke(Color.white.opacity(0.03), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.30), radius: 20, x: 0, y: 14)

            VStack(spacing: 0) {
                if timelineItems.isEmpty {
                    emptyTapeState
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    SignalTapeTimelineView(
                        items: timelineItems,
                        bars: waveformBars,
                        activeProgress: activeProgress,
                        totalDuration: storedTotalDuration,
                        markerNumber: currentMarkerNumber,
                        isRecording: isRecording,
                        onScrubBegan: handleScrubBegan,
                        onScrubChanged: handleScrubChanged,
                        onScrubEnded: handleScrubEnded
                    )
                    .padding(.horizontal, 24)
                    .padding(.top, 22)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                if !clips.isEmpty || isRecording {
                    HStack {
                        if !selectedClipDateText.isEmpty {
                            Text(selectedClipDateText)
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.white.opacity(0.16))
                        }

                        Spacer()

                        Text(tapeStatusText)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white.opacity(0.22))
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 22)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
        }
        .frame(height: height)
    }

    private var emptyTapeState: some View {
        ZStack {
            Rectangle()
                .fill(Color.white.opacity(0.16))
                .frame(height: 1)
                .padding(.horizontal, 36)

            SignalCenterMarker(
                isRecording: false,
                label: "001"
            )
        }
    }

    private var controlsRow: some View {
        SignalSquareControlsRow(spacing: SignalTapeMetrics.controlSpacing) {
            Button(action: toggleTapePlayback) {
                SignalControlTile(
                    background: Color.white.opacity(0.08),
                    shadowColor: .black.opacity(0.34)
                ) {
                    Image(systemName: tapePlayer.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 34, weight: .bold))
                        .foregroundColor(.white.opacity(clips.isEmpty ? 0.3 : 0.95))
                }
            }
            .buttonStyle(.plain)
            .disabled(clips.isEmpty || isRecording)
            .accessibilityLabel("Play capture")

            Button(action: toggleRecording) {
                SignalControlTile(
                    background: isRecording
                        ? Color(red: 0.20, green: 0.08, blue: 0.07)
                        : Color.white.opacity(0.08),
                    shadowColor: isRecording
                        ? Color(red: 1.0, green: 0.2, blue: 0.12).opacity(0.20)
                        : .black.opacity(0.34)
                ) {
                    if isRecording {
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(Color(red: 1.0, green: 0.2, blue: 0.12))
                            .frame(width: 28, height: 28)
                    } else {
                        Circle()
                            .fill(Color(red: 1.0, green: 0.2, blue: 0.12))
                            .frame(width: 36, height: 36)
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isRecording ? "Stop recording" : "Start recording")

            Button {
                showSignalTimeline = true
            } label: {
                SignalControlTile(
                    background: Color.white.opacity(0.08),
                    shadowColor: .black.opacity(0.34)
                ) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 32, weight: .semibold))
                        .foregroundColor(.white.opacity(0.92))
                }
                .overlay(alignment: .topTrailing) {
                    SignalControlIndicatorBadge(state: signalButtonIndicatorState)
                        .padding(12)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open Signal Timeline")
        }
    }

    private func handleScrubBegan() {
        isScrubbing = true
        hasUserPositionedTape = true

        if tapePlayer.isPlaying || tapePlayer.canResume {
            let pausedProgress = tapePlayer.playbackProgress
            tapePlayer.stop()
            scrubProgress = pausedProgress
        }
    }

    private func handleScrubChanged(_ progress: TimeInterval) {
        scrubProgress = progress
    }

    private func handleScrubEnded(_ progress: TimeInterval) {
        scrubProgress = progress
        isScrubbing = false
    }

    private func toggleTapePlayback() {
        guard !clips.isEmpty else { return }
        guard !isRecording else { return }

        if tapePlayer.isPlaying {
            tapePlayer.pause()
            return
        }

        if tapePlayer.canResume {
            tapePlayer.resume()
            return
        }

        let startProgress: TimeInterval
        if !hasUserPositionedTape, let currentItem {
            startProgress = currentItem.startTime
        } else if activeProgress >= max(storedTotalDuration - 0.05, 0), storedTotalDuration > 0.05 {
            startProgress = currentItem?.startTime ?? 0
        } else {
            startProgress = max(activeProgress, 0)
        }

        tapePlayer.play(clips: clips, startingAt: startProgress)
    }

    private func toggleRecording() {
        if isRecording {
            stopCapture()
        } else {
            startCapture()
        }
    }

    private func startCapture() {
        if tapePlayer.isPlaying || tapePlayer.canResume {
            tapePlayer.stop()
        }

        do {
            try audioRecorder.startRecording()
        } catch {
            errorMessage = error.localizedDescription
            return
        }

        recordingStartedAt = Date()
        recordingDuration = 0
        isRecording = true
        isScrubbing = false
        activeRecordingMarkerID = UUID()
        liveMeterSamples = []
        appendLiveMeterSample()

        recordingTimer?.invalidate()
        recordingTimer = Timer.scheduledTimer(withTimeInterval: 0.06, repeats: true) { _ in
            guard let recordingStartedAt else { return }
            recordingDuration = Date().timeIntervalSince(recordingStartedAt)
            appendLiveMeterSample()
        }

        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func stopCapture() {
        recordingTimer?.invalidate()
        recordingTimer = nil

        audioRecorder.stopRecording { result in
            DispatchQueue.main.async {
                self.isRecording = false
                let capturedLiveSamples = self.liveWaveformSamples

                let duration = self.recordingStartedAt.map { Date().timeIntervalSince($0) } ?? self.recordingDuration
                self.recordingStartedAt = nil
                self.recordingDuration = 0
                self.liveMeterSamples = []

                switch result {
                case .success(let url):
                    guard duration >= self.minimumCaptureDuration else {
                        self.deleteTemporaryRecording(at: url)
                        self.errorMessage = "Recording was too short."
                        return
                    }

                    let clip: CaptureClip
                    do {
                        clip = try self.signalStore.saveCaptureClip(
                            audioURL: url,
                            duration: duration,
                            selectedModeName: self.selectedModeName
                        )
                    } catch {
                        self.deleteTemporaryRecording(at: url)
                        self.errorMessage = "Capture could not be saved."
                        return
                    }

                    if !capturedLiveSamples.isEmpty {
                        self.waveformSamplesByClipID[clip.id] = capturedLiveSamples
                    }
                    self.signalStore.beginSignalAnalysis(
                        for: clip,
                        selectedModeName: self.selectedModeName
                    )
                    self.hasUserPositionedTape = true
                    self.scrubProgress = self.storedTotalDuration
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
            }
        }

        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func deleteTemporaryRecording(at url: URL) {
        try? FileManager.default.removeItem(at: url)
    }

    private func appendLiveMeterSample() {
        let level = audioRecorder.currentNormalizedPowerLevel()
        let sample = liveMeterSamples.last.map { ($0 * 0.34) + (level * 0.66) } ?? level
        liveMeterSamples.append(sample)

        if liveMeterSamples.count > 240 {
            liveMeterSamples = stride(from: 0, to: liveMeterSamples.count, by: 2).map { index in
                let nextIndex = min(index + 1, liveMeterSamples.count - 1)
                return (liveMeterSamples[index] + liveMeterSamples[nextIndex]) / 2
            }
        }
    }

    private func condensedLiveWaveformSamples(from samples: [CGFloat]) -> [CGFloat] {
        guard !samples.isEmpty else { return [] }

        let targetCount = max(22, min(96, Int((max(recordingDuration, 1.0) * 11).rounded())))
        guard samples.count > targetCount else { return samples }

        let bucketSize = max(Double(samples.count) / Double(targetCount), 1)

        return (0..<targetCount).compactMap { index in
            let start = Int((Double(index) * bucketSize).rounded(.down))
            let end = min(Int((Double(index + 1) * bucketSize).rounded(.down)), samples.count)
            guard start < end else { return samples[min(start, samples.count - 1)] }

            let bucket = samples[start..<end]
            return bucket.max() ?? 0
        }
    }

    private func refreshWaveforms() {
        let clipsSnapshot = clips
        let generationToken = UUID()
        waveformGenerationToken = generationToken

        DispatchQueue.global(qos: .utility).async {
            var loaded: [UUID: [CGFloat]] = [:]

            for clip in clipsSnapshot {
                loaded[clip.id] = SignalWaveformExtractor.normalizedSamples(
                    from: clip.audioFileURL,
                    targetCount: SignalWaveformExtractor.targetBarCount(for: clip.duration)
                )
            }

            DispatchQueue.main.async {
                guard self.waveformGenerationToken == generationToken else { return }
                let currentIDs = Set(self.clips.map(\.id))
                self.waveformSamplesByClipID = loaded.filter { currentIDs.contains($0.key) }
            }
        }
    }

    private func formattedTapeDuration(_ duration: TimeInterval) -> String {
        let totalSeconds = max(Int(duration.rounded()), 0)
        let hours = totalSeconds / 3600
        let minutes = (totalSeconds % 3600) / 60
        let seconds = totalSeconds % 60

        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }

        if minutes > 0 {
            return "\(minutes)m \(seconds)s"
        }

        return "\(seconds)s"
    }

    private func formattedClock(_ duration: TimeInterval) -> String {
        let totalSeconds = max(Int(duration.rounded()), 0)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}




private struct SignalTimelineItem: Identifiable {
    let id: UUID
    let createdAt: Date
    let duration: TimeInterval
    let clipID: UUID
    let startTime: TimeInterval
    let waveformSamples: [CGFloat]?
    let isLive: Bool

    init(clip: CaptureClip, startTime: TimeInterval, waveformSamples: [CGFloat]?) {
        self.id = clip.id
        self.createdAt = clip.createdAt
        self.duration = clip.duration
        self.clipID = clip.id
        self.startTime = startTime
        self.waveformSamples = waveformSamples
        self.isLive = false
    }

    init(
        liveID: UUID,
        createdAt: Date,
        startTime: TimeInterval,
        duration: TimeInterval,
        waveformSamples: [CGFloat]
    ) {
        self.id = liveID
        self.createdAt = createdAt
        self.duration = duration
        self.clipID = liveID
        self.startTime = startTime
        self.waveformSamples = waveformSamples
        self.isLive = true
    }

    var waveformBars: [SignalWaveBar] {
        let amplitudes = waveformSamples?.isEmpty == false
            ? waveformSamples!
            : SignalWaveformExtractor.fallbackSamples(
                count: SignalWaveformExtractor.targetBarCount(for: duration),
                seed: clipID.uuidString
            )

        return amplitudes.enumerated().map { index, amplitude in
            let barCount = amplitudes.count
            let fraction = barCount == 1 ? 0.5 : Double(index) / Double(barCount - 1)
            let time = startTime + (duration * fraction)
            let normalizedAmplitude = max(min(amplitude, 1), 0)
            let contour = isLive
                ? 0.94 + (0.06 * abs(sin(fraction * .pi)))
                : 0.86 + (0.14 * abs(sin(fraction * .pi)))
            let gatedAmplitude: CGFloat
            if isLive {
                gatedAmplitude = normalizedAmplitude < 0.025 ? 0 : max(normalizedAmplitude, 0.06)
            } else {
                gatedAmplitude = normalizedAmplitude < 0.085 ? 0 : normalizedAmplitude
            }
            let height = gatedAmplitude == 0
                ? 0
                : ((isLive ? 14 : 10) + (pow(gatedAmplitude, isLive ? 0.82 : 0.9) * (isLive ? 120 : 126) * contour))
                    * SignalTapeMetrics.waveformHeightScale

            return SignalWaveBar(
                time: time,
                height: height,
                clipID: clipID
            )
        }
    }
}

private struct SignalWaveBar: Identifiable {
    let time: TimeInterval
    let height: CGFloat
    let clipID: UUID

    var id: String {
        "\(clipID.uuidString)-\(Int((time * 1000).rounded()))"
    }
}

private struct SignalTapeTimelineView: View {
    let items: [SignalTimelineItem]
    let bars: [SignalWaveBar]
    let activeProgress: TimeInterval
    let totalDuration: TimeInterval
    let markerNumber: Int
    let isRecording: Bool
    let onScrubBegan: () -> Void
    let onScrubChanged: (TimeInterval) -> Void
    let onScrubEnded: (TimeInterval) -> Void

    @State private var dragStartProgress: TimeInterval?

    private let horizontalInset: CGFloat = 28

    var body: some View {
        GeometryReader { geometry in
            let usableWidth = max(geometry.size.width - (horizontalInset * 2), 1)
            let centerX = geometry.size.width / 2
            let halfWindowDuration = visibleHalfWindowDuration(for: totalDuration)
            let visibleMin = activeProgress - halfWindowDuration
            let visibleMax = activeProgress + halfWindowDuration
            let totalVisibleDuration = max(visibleMax - visibleMin, 0.0001)
            let secondsPerPoint = totalVisibleDuration / usableWidth
            let rawVisibleBars = bars.filter { $0.time >= visibleMin && $0.time <= visibleMax && $0.height > 0 }
            let visibleBars = bucketedBars(
                from: rawVisibleBars,
                visibleMin: visibleMin,
                visibleMax: visibleMax,
                usableWidth: usableWidth
            )

            ZStack {
                Rectangle()
                    .fill(Color.white.opacity(0.18))
                    .frame(height: 1)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)

                ForEach(visibleBars) { bar in
                    Capsule(style: .continuous)
                        .fill(Color.white.opacity(opacity(for: bar.clipID)))
                        .frame(width: 4, height: bar.height)
                        .position(
                            x: centerX + CGFloat((bar.time - activeProgress) / secondsPerPoint),
                            y: geometry.size.height / 2
                        )
                }

                SignalCenterMarker(
                    isRecording: isRecording,
                    label: isRecording ? "REC" : String(format: "%03d", markerNumber)
                )
                .position(x: centerX, y: geometry.size.height / 2)
            }
            .allowsHitTesting(!isRecording)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 1)
                    .onChanged { value in
                        if dragStartProgress == nil {
                            dragStartProgress = activeProgress
                            onScrubBegan()
                        }

                        let startProgress = dragStartProgress ?? activeProgress
                        let delta = -Double(value.translation.width) * secondsPerPoint
                        let progress = clampedProgress(startProgress + delta)
                        onScrubChanged(progress)
                    }
                    .onEnded { value in
                        let startProgress = dragStartProgress ?? activeProgress
                        let delta = -Double(value.translation.width) * secondsPerPoint
                        let progress = clampedProgress(startProgress + delta)
                        dragStartProgress = nil
                        onScrubEnded(progress)
                    }
            )
            .simultaneousGesture(
                SpatialTapGesture()
                    .onEnded { value in
                        onScrubBegan()

                        let deltaPoints = value.location.x - centerX
                        let delta = Double(deltaPoints) * secondsPerPoint
                        let progress = clampedProgress(activeProgress + delta)

                        onScrubChanged(progress)
                        onScrubEnded(progress)
                    }
            )
        }
    }

    private func opacity(for clipID: UUID) -> Double {
        if let activeClipID = items.last(where: { activeProgress >= $0.startTime })?.clipID {
            return clipID == activeClipID ? 0.98 : 0.62
        }

        return 0.62
    }

    private func visibleHalfWindowDuration(for totalDuration: TimeInterval) -> TimeInterval {
        let scaled = max(totalDuration * 0.18, Double(max(items.count, 1)) * 2.2)
        return min(max(scaled, 4.4), 11.5)
    }

    private func clampedProgress(_ progress: TimeInterval) -> TimeInterval {
        min(max(progress, 0), max(totalDuration, 0))
    }

    private func bucketedBars(
        from bars: [SignalWaveBar],
        visibleMin: TimeInterval,
        visibleMax: TimeInterval,
        usableWidth: CGFloat
    ) -> [SignalWaveBar] {
        guard !items.isEmpty else { return [] }

        let slotCount = max(Int(usableWidth / 10.0), 22)
        let slotDuration = max((visibleMax - visibleMin) / Double(slotCount), 0.0001)
        var peakHeights = Array(repeating: CGFloat.zero, count: slotCount)
        var clipIDs = Array<UUID?>(repeating: nil, count: slotCount)

        for bar in bars {
            let rawIndex = Int(((bar.time - visibleMin) / slotDuration).rounded(.down))
            let index = min(max(rawIndex, 0), slotCount - 1)

            peakHeights[index] = max(peakHeights[index], bar.height)
            clipIDs[index] = bar.clipID
        }

        let softenedHeights = smoothedHeights(peakHeights)

        return (0..<slotCount).compactMap { index in
            let centeredTime = visibleMin + (Double(index) + 0.5) * slotDuration
            guard let owningItem = owningItem(at: centeredTime) else { return nil }

            let rawHeight = softenedHeights[index]
            let displayHeight = rawHeight > 0
                ? max(rawHeight, (owningItem.isLive ? 12 : 14) * SignalTapeMetrics.waveformHeightScale)
                : (owningItem.isLive ? 0 : 7 * SignalTapeMetrics.waveformHeightScale)

            return SignalWaveBar(
                time: centeredTime,
                height: displayHeight,
                clipID: clipIDs[index] ?? owningItem.clipID
            )
        }
    }

    private func owningItem(at time: TimeInterval) -> SignalTimelineItem? {
        items.last(where: { time >= $0.startTime && time <= ($0.startTime + $0.duration) })
    }

    private func smoothedHeights(_ heights: [CGFloat]) -> [CGFloat] {
        guard heights.count > 2 else { return heights }

        return heights.indices.map { index in
            let lowerBound = max(index - 1, 0)
            let upperBound = min(index + 1, heights.count - 1)
            let window = heights[lowerBound...upperBound]
            let average = window.reduce(CGFloat.zero, +) / CGFloat(window.count)
            return max(heights[index], average * 0.92)
        }
    }
}

private struct SignalCenterMarker: View {
    let isRecording: Bool
    let label: String

    var body: some View {
        VStack(spacing: 0) {
            Circle()
                .fill(Color(red: 1.0, green: 0.2, blue: 0.12))
                .frame(width: isRecording ? 20 : 18, height: isRecording ? 20 : 18)

            Rectangle()
                .fill(Color(red: 1.0, green: 0.2, blue: 0.12))
                .frame(width: SignalTapeMetrics.markerStemWidth)
                .padding(.top, 2)

            Spacer(minLength: 0)

            Text(label)
                .font(.system(size: 18, weight: .black, design: .rounded))
                .foregroundColor(Color(red: 1.0, green: 0.2, blue: 0.12))
        }
        .padding(.top, isRecording ? SignalTapeMetrics.markerTopPaddingRecording : SignalTapeMetrics.markerTopPaddingIdle)
        .padding(.bottom, isRecording ? SignalTapeMetrics.markerBottomPaddingRecording : SignalTapeMetrics.markerBottomPaddingIdle)
    }
}

private struct SignalControlTile<Content: View>: View {
    let background: Color
    let shadowColor: Color
    @ViewBuilder let content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)

        ZStack {
            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            shape
                .fill(background)
                .overlay(
                    LinearGradient(
                        colors: [
                            Color.white.opacity(0.12),
                            Color.white.opacity(0.02),
                            Color.clear
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .clipShape(shape)
                )
        )
        .overlay(
            shape
                .stroke(Color.white.opacity(0.04), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.34), radius: 8, x: 0, y: 6)
        .shadow(color: shadowColor, radius: 12, x: 0, y: 12)
    }
}

private struct SignalControlIndicatorBadge: View {
    let state: SignalControlIndicatorState

    var body: some View {
        switch state {
        case .idle:
            EmptyView()
        case .reviewing:
            SignalOrbitBadge(size: 18)
        case .delayed:
            Circle()
                .fill(Color.white.opacity(0.26))
                .frame(width: 12, height: 12)
        case .ready(let count):
            if count <= 1 {
                Circle()
                    .fill(Color(red: 1.0, green: 0.23, blue: 0.22))
                    .frame(width: 12, height: 12)
            } else {
                Text(count > 9 ? "9+" : "\(count)")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, count > 9 ? 5 : 0)
                    .frame(minWidth: 18, minHeight: 18)
                    .background(
                        Capsule(style: .continuous)
                            .fill(Color(red: 1.0, green: 0.23, blue: 0.22))
                    )
            }
        }
    }
}

private struct SignalOrbitBadge: View {
    let size: CGFloat

    @State private var isAnimating = false

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.16), lineWidth: 1.5)

            Circle()
                .trim(from: 0.08, to: 0.72)
                .stroke(
                    Color.white.opacity(0.92),
                    style: StrokeStyle(lineWidth: 1.8, lineCap: .round)
                )
                .rotationEffect(.degrees(isAnimating ? 360 : 0))
        }
        .frame(width: size, height: size)
        .onAppear {
            withAnimation(.linear(duration: 1.0).repeatForever(autoreverses: false)) {
                isAnimating = true
            }
        }
    }
}

private struct SignalSquareControlsRow: Layout {
    let spacing: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        guard !subviews.isEmpty else { return .zero }

        let fallbackWidth = subviews.reduce(CGFloat.zero) { width, subview in
            width + subview.sizeThatFits(.unspecified).width
        } + spacing * CGFloat(max(subviews.count - 1, 0))
        let rowWidth = proposal.width ?? fallbackWidth

        return CGSize(width: rowWidth, height: squareSide(for: rowWidth, itemCount: subviews.count))
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let side = squareSide(for: bounds.width, itemCount: subviews.count)
        var x = bounds.minX
        let y = bounds.midY - side / 2

        for subview in subviews {
            subview.place(
                at: CGPoint(x: x, y: y),
                proposal: ProposedViewSize(width: side, height: side)
            )
            x += side + spacing
        }
    }

    private func squareSide(for rowWidth: CGFloat, itemCount: Int) -> CGFloat {
        guard itemCount > 0 else { return 0 }

        let totalSpacing = spacing * CGFloat(max(itemCount - 1, 0))
        return max((rowWidth - totalSpacing) / CGFloat(itemCount), 0)
    }
}

private final class SignalTapePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isPlaying = false
    @Published var playbackProgress: TimeInterval = 0
    @Published var lastErrorMessage: String?

    private var queuedClips: [CaptureClip] = []
    private var currentIndex = 0
    private var audioPlayer: AVAudioPlayer?
    private var progressTimer: Timer?
    private var baseProgress: TimeInterval = 0

    var canResume: Bool {
        !isPlaying && audioPlayer != nil
    }

    var hasPlaybackPosition: Bool {
        playbackProgress > 0
    }

    func play(clips: [CaptureClip], startingAt progress: TimeInterval) {
        guard !clips.isEmpty else { return }

        stop(resetProgress: false)
        lastErrorMessage = nil
        queuedClips = clips

        let clampedProgress = min(max(progress, 0), clips.reduce(0) { $0 + $1.duration })
        let start = playbackStart(for: clampedProgress, in: clips)

        currentIndex = start.index
        baseProgress = start.baseProgress
        playbackProgress = clampedProgress
        playCurrentClip(startTime: start.offsetInClip)
    }

    func pause() {
        guard isPlaying else { return }
        audioPlayer?.pause()
        syncProgress()
        stopProgressTimer()
        isPlaying = false
    }

    func resume() {
        guard let audioPlayer else { return }

        do {
            try activatePlaybackSession()

            guard audioPlayer.play() else {
                throw NSError(
                    domain: "SignalTapePlayer",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Playback could not start."]
                )
            }

            isPlaying = true
            startProgressTimer()
        } catch {
            lastErrorMessage = error.localizedDescription
            stop()
        }
    }

    func stop() {
        stop(resetProgress: true)
    }

    private func stop(resetProgress: Bool) {
        stopProgressTimer()
        audioPlayer?.stop()
        audioPlayer = nil
        queuedClips = []
        currentIndex = 0
        isPlaying = false
        baseProgress = 0
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)

        if resetProgress {
            playbackProgress = 0
        }
    }

    private func playbackStart(for progress: TimeInterval, in clips: [CaptureClip]) -> (index: Int, baseProgress: TimeInterval, offsetInClip: TimeInterval) {
        var cursor: TimeInterval = 0

        for (index, clip) in clips.enumerated() {
            let end = cursor + clip.duration

            if progress < end || index == clips.count - 1 {
                return (index, cursor, max(0, progress - cursor))
            }

            cursor = end
        }

        return (0, 0, 0)
    }

    private func playCurrentClip(startTime: TimeInterval) {
        guard queuedClips.indices.contains(currentIndex) else {
            finishPlayback()
            return
        }

        let clip = queuedClips[currentIndex]

        do {
            try activatePlaybackSession()

            let player = try AVAudioPlayer(contentsOf: clip.audioFileURL)
            player.delegate = self
            player.currentTime = min(startTime, max(player.duration - 0.01, 0))
            player.prepareToPlay()

            guard player.play() else {
                throw NSError(
                    domain: "SignalTapePlayer",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "Playback could not start."]
                )
            }

            audioPlayer = player
            isPlaying = true
            syncProgress()
            startProgressTimer()
        } catch {
            lastErrorMessage = error.localizedDescription
            stop()
        }
    }

    private func finishPlayback() {
        stopProgressTimer()
        audioPlayer = nil
        isPlaying = false
        playbackProgress = queuedClips.reduce(0) { $0 + $1.duration }
    }

    private func startProgressTimer() {
        stopProgressTimer()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            self?.syncProgress()
        }
    }

    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    private func syncProgress() {
        playbackProgress = baseProgress + (audioPlayer?.currentTime ?? 0)
    }

    private func activatePlaybackSession() throws {
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playback, mode: .default)
        try audioSession.setActive(true)
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard flag else {
            lastErrorMessage = "Playback stopped unexpectedly."
            stop()
            return
        }

        stopProgressTimer()

        if queuedClips.indices.contains(currentIndex) {
            baseProgress += queuedClips[currentIndex].duration
            playbackProgress = baseProgress
        }

        currentIndex += 1

        if queuedClips.indices.contains(currentIndex) {
            playCurrentClip(startTime: 0)
        } else {
            finishPlayback()
        }
    }
}

private enum SignalWaveformExtractor {
    static func targetBarCount(for duration: TimeInterval) -> Int {
        max(18, min(84, Int((duration * 8.5).rounded())))
    }

    static func normalizedSamples(from url: URL, targetCount: Int) -> [CGFloat] {
        guard targetCount > 0 else { return [] }

        do {
            let file = try AVAudioFile(forReading: url)
            let format = file.processingFormat
            let frameCount = AVAudioFrameCount(file.length)

            guard frameCount > 0,
                  let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount) else {
                return fallbackSamples(count: targetCount, seed: url.lastPathComponent)
            }

            try file.read(into: buffer)

            let frameLength = Int(buffer.frameLength)
            guard frameLength > 0 else {
                return fallbackSamples(count: targetCount, seed: url.lastPathComponent)
            }

            let channelCount = Int(format.channelCount)
            let chunkSize = max(frameLength / targetCount, 1)
            var samples: [CGFloat] = []
            samples.reserveCapacity(targetCount)

            for chunkStart in stride(from: 0, to: frameLength, by: chunkSize) {
                let chunkEnd = min(chunkStart + chunkSize, frameLength)
                let amplitude = rmsAmplitude(
                    in: buffer,
                    channelCount: channelCount,
                    range: chunkStart..<chunkEnd
                )
                samples.append(CGFloat(amplitude))

                if samples.count == targetCount {
                    break
                }
            }

            guard let peak = samples.max(), peak > 0.0001 else {
                return Array(repeating: 0.08, count: max(samples.count, targetCount))
            }

            let normalized = samples.map { max(min($0 / peak, 1), 0) }
            let smoothed = smooth(normalized)
            if smoothed.count >= targetCount {
                return Array(smoothed.prefix(targetCount))
            }

            return smoothed + Array(repeating: smoothed.last ?? 0.08, count: targetCount - smoothed.count)
        } catch {
            return fallbackSamples(count: targetCount, seed: url.lastPathComponent)
        }
    }

    static func fallbackSamples(count: Int, seed: String) -> [CGFloat] {
        let hashedSeed = seed.unicodeScalars.reduce(UInt64(0)) { partialResult, scalar in
            (partialResult &* 1103515245) &+ UInt64(scalar.value) &+ 12345
        }

        let samples = (0..<max(count, 1)).map { index in
            let fraction = count <= 1 ? 0.5 : Double(index) / Double(count - 1)
            let sway = 0.35 + (0.65 * abs(sin((fraction * .pi * 1.6) + Double(hashedSeed % 17) * 0.12)))
            let flicker = 0.24 + (0.76 * abs(sin((fraction * .pi * Double((hashedSeed % 5) + 2)) + Double(index) * 0.08)))
            return CGFloat(sway * flicker)
        }

        return smooth(samples)
    }

    private static func rmsAmplitude(
        in buffer: AVAudioPCMBuffer,
        channelCount: Int,
        range: Range<Int>
    ) -> Double {
        guard !range.isEmpty else { return 0 }
        let safeChannelCount = max(channelCount, 1)

        switch buffer.format.commonFormat {
        case .pcmFormatFloat32:
            guard let data = buffer.floatChannelData else { return 0 }
            return normalizedRMS(range: range, channelCount: safeChannelCount) { channel, frame in
                Double(abs(data[channel][frame]))
            }
        case .pcmFormatInt16:
            guard let data = buffer.int16ChannelData else { return 0 }
            return normalizedRMS(range: range, channelCount: safeChannelCount) { channel, frame in
                Double(abs(Float(data[channel][frame]) / Float(Int16.max)))
            }
        case .pcmFormatInt32:
            guard let data = buffer.int32ChannelData else { return 0 }
            return normalizedRMS(range: range, channelCount: safeChannelCount) { channel, frame in
                Double(abs(Float(data[channel][frame]) / Float(Int32.max)))
            }
        default:
            return 0
        }
    }

    private static func normalizedRMS(
        range: Range<Int>,
        channelCount: Int,
        sampleAt: (Int, Int) -> Double
    ) -> Double {
        var sum: Double = 0
        var frameCount = 0

        for frame in range {
            var frameAmplitude: Double = 0

            for channel in 0..<channelCount {
                frameAmplitude += sampleAt(channel, frame)
            }

            frameAmplitude /= Double(channelCount)
            sum += frameAmplitude * frameAmplitude
            frameCount += 1
        }

        guard frameCount > 0 else { return 0 }
        return sqrt(sum / Double(frameCount))
    }

    private static func smooth(_ samples: [CGFloat]) -> [CGFloat] {
        guard samples.count > 2 else { return samples }

        return samples.indices.map { index in
            let lowerBound = max(index - 1, 0)
            let upperBound = min(index + 1, samples.count - 1)
            let window = samples[lowerBound...upperBound]
            let average = window.reduce(CGFloat.zero, +) / CGFloat(window.count)

            if average < 0.09 {
                return average * 0.25
            }

            return average
        }
    }
}

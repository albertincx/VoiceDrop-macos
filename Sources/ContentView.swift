import SwiftUI
import AVFoundation

struct ContentView: View {
    @ObservedObject private var loc = Localization.shared
    @StateObject private var recorder = AudioRecorder()
    @StateObject private var player = AudioPlayer()
    @StateObject private var transcriber = TranscriptionService()
    @StateObject private var deviceManager = AudioDeviceManager()

    @State private var selectedTab: Int = 0
    @State private var showSettings: Bool = false

    var body: some View {
        VStack(spacing: 0) {
            // Top Bar
            headerView

            Divider()

            ScrollView {
                VStack(spacing: 20) {
                    // Noise Suppression Control Card
                    noiseSuppressionCard

                    // Main Recording Deck
                    recordingDeckView

                    // Active Recording Player & Drag-and-Drop Card
                    if let url = recorder.currentRecordingURL {
                        dragAndDropSection(fileURL: url)
                    }

                    // Local Speech-to-Text Section
                    if recorder.currentRecordingURL != nil || !transcriber.transcribedText.isEmpty {
                        transcriptionSection
                    }

                    // Recent History
                    if recorder.recentRecordings.count > 1 {
                        recentHistorySection
                    }
                }
                .padding(20)
            }
        }
        .frame(minWidth: 580, idealWidth: 640, minHeight: 680, idealHeight: 760)
        .background(Color(NSColor.windowBackgroundColor))
        .onChange(of: recorder.currentRecordingURL) { _, newURL in
            if let url = newURL {
                player.loadAudio(url: url)
            }
        }
        .onAppear {
            recorder.onRecordingFinished = { url in
                player.loadAudio(url: url)
            }
            if let first = recorder.recentRecordings.first {
                recorder.currentRecordingURL = first
                player.loadAudio(url: first)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .toggleRecordingShortcut)) { _ in
            recorder.toggleRecording()
        }
    }

    // MARK: - Header
    private var headerView: some View {
        HStack {
            HStack(spacing: 10) {
                ZStack {
                    LinearGradient(colors: [Color.blue, Color.purple], startPoint: .topLeading, endPoint: .bottomTrailing)
                        .frame(width: 36, height: 36)
                        .clipShape(RoundedRectangle(cornerRadius: 8))

                    Image(systemName: "mic.fill")
                        .foregroundColor(.white)
                        .font(.system(size: 16, weight: .bold))
                }

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(loc.t(.appTitle))
                            .font(.system(size: 16, weight: .bold))

                        Text("v1.7.0 • \(loc.t(.buildLabel)): 2026-11-05")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.green)
                            .cornerRadius(4)
                    }

                    Text(loc.t(.headerSubtitle))
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }

            Spacer()

            HStack(spacing: 10) {
                // UI Language Switcher
                Picker("", selection: $loc.currentLanguage) {
                    ForEach(AppLanguage.allCases) { lang in
                        Text(lang.displayName).tag(lang)
                    }
                }
                .pickerStyle(.segmented)
                .frame(width: 175)

                Button(action: {
                    recorder.selectExternalFile()
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "plus.circle")
                        Text(loc.t(.chooseFile))
                    }
                    .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .help(loc.t(.chooseFileHelp))

                Button(action: {
                    NSWorkspace.shared.open(recorder.recordingsDirectory)
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "folder")
                        Text(loc.t(.recordingsFolder))
                    }
                    .font(.system(size: 12))
                }
                .buttonStyle(.bordered)
                .help(loc.t(.recordingsFolderHelp))
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(Color(NSColor.controlBackgroundColor).opacity(0.6))
    }

    // MARK: - Noise Suppression Status Card
    private var noiseSuppressionCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 8) {
                    Circle()
                        .fill(recorder.noiseSuppressionEnabled ? Color.green : Color.orange)
                        .frame(width: 8, height: 8)
                        .shadow(color: recorder.noiseSuppressionEnabled ? Color.green.opacity(0.8) : Color.clear, radius: 4)

                    Text(loc.t(.studioNoiseSuppression))
                        .font(.system(size: 13, weight: .semibold))
                }

                Spacer()

                Text(loc.t(.wienerFilterSubtitle))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
            }

            HStack(spacing: 16) {
                Toggle(loc.t(.autoDenoiseToggle), isOn: $recorder.noiseSuppressionEnabled)
                    .toggleStyle(.checkbox)
                    .font(.system(size: 12))

                Spacer()

                Picker(loc.t(.denoiseStrengthLabel), selection: $recorder.denoiseStrength) {
                    Text(loc.t(.strengthMild)).tag(1.2)
                    Text(loc.t(.strengthStudio)).tag(1.8)
                    Text(loc.t(.strengthMaximum)).tag(2.5)
                }
                .pickerStyle(.menu)
                .frame(width: 200)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.6))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.gray.opacity(0.15), lineWidth: 1)
                )
        )
    }

    // MARK: - Recording Deck
    private var recordingDeckView: some View {
        VStack(spacing: 16) {
            if !recorder.micPermissionGranted {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.yellow)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(loc.t(.micPermissionRequiredTitle))
                            .font(.system(size: 12, weight: .semibold))
                        Text(loc.t(.micPermissionRequiredSubtitle))
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    Spacer()

                    Button(action: {
                        recorder.resetAndRequestMicPermission()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text(loc.t(.resetAndRequestPrompt))
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)

                    Button(loc.t(.macOSSettings)) {
                        recorder.openSystemSettingsForMic()
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
                .padding(10)
                .background(Color.yellow.opacity(0.15))
                .cornerRadius(8)
            }

            if let error = recorder.errorMessage {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundColor(.red)
                    .padding(8)
                    .background(Color.red.opacity(0.1))
                    .cornerRadius(8)
            }

            // Microphone Selector Bar
            HStack(spacing: 8) {
                Image(systemName: "mic.fill.badge.plus")
                    .foregroundColor(.blue)
                    .font(.system(size: 13))

                Text(loc.t(.micLabel))
                    .font(.system(size: 12, weight: .semibold))

                Spacer()

                Picker("", selection: Binding(
                    get: { deviceManager.selectedDeviceID },
                    set: { newID in deviceManager.selectDevice(id: newID) }
                )) {
                    ForEach(deviceManager.availableDevices) { dev in
                        Text(dev.localizedDisplayName(for: loc.currentLanguage)).tag(dev.id)
                    }
                }
                .pickerStyle(.menu)
                .frame(maxWidth: 260)

                Button(action: {
                    deviceManager.refreshDevices()
                }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 12))
                }
                .buttonStyle(.plain)
                .help(loc.t(.refreshMicsHelp))

                Button(action: {
                    recorder.resetAndRequestMicPermission()
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "hand.raised.slash")
                        Text(loc.t(.resetMicAccessButton))
                    }
                    .font(.system(size: 11))
                }
                .buttonStyle(.bordered)
                .help(loc.t(.resetMicAccessHelp))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(NSColor.controlBackgroundColor).opacity(0.85))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.gray.opacity(0.2), lineWidth: 1)
            )

            // Big Record Button
            ZStack {
                // Pulsing ring when recording
                if recorder.isRecording {
                    Circle()
                        .stroke(Color.red.opacity(0.35), lineWidth: 8)
                        .frame(width: 108, height: 108)
                        .scaleEffect(1.0 + CGFloat(recorder.currentAudioLevel) * 0.3)
                        .animation(.easeOut(duration: 0.1), value: recorder.currentAudioLevel)
                }

                Button(action: {
                    recorder.toggleRecording()
                }) {
                    ZStack {
                        Circle()
                            .fill(recorder.isRecording ? Color.red : Color.blue)
                            .frame(width: 88, height: 88)
                            .shadow(color: (recorder.isRecording ? Color.red : Color.blue).opacity(0.4), radius: 10, x: 0, y: 5)

                        VStack(spacing: 4) {
                            Image(systemName: recorder.isRecording ? "stop.fill" : "mic.fill")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)

                            Text(recorder.isRecording ? loc.t(.stopButton) : loc.t(.recordButton))
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                }
                .buttonStyle(.plain)
            }

            // Duration Display
            Text(formattedDuration(recorder.recordingDuration))
                .font(.system(size: 28, weight: .medium, design: .monospaced))
                .foregroundColor(recorder.isRecording ? .red : .primary)

            // Waveform visualizer
            WaveformVisualizer(levels: recorder.audioWaveform, isRecording: recorder.isRecording)

            Text(recorder.isRecording ? loc.t(.recordingActiveHint) : loc.t(.recordingIdleHint))
                .font(.system(size: 12))
                .foregroundColor(.secondary)

            // Spacebar shortcut hint
            HStack(spacing: 6) {
                Text(loc.t(.spaceKey))
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(NSColor.controlColor))
                    .cornerRadius(4)
                Text(recorder.isRecording ? loc.t(.spacebarHintRecording) : loc.t(.spacebarHintIdle))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.7))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(recorder.isRecording ? Color.red.opacity(0.4) : Color.gray.opacity(0.15), lineWidth: 1.5)
                )
        )
    }

    // MARK: - Drag and Drop + Audio Player Section
    private func dragAndDropSection(fileURL: URL) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(loc.t(.savedAudioTitle), systemImage: "arrow.down.doc")
                    .font(.system(size: 14, weight: .semibold))

                Spacer()

                Text(loc.t(.losslessWavTag))
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
            }

            // The Draggable Card for Antigravity, Telegram, etc.
            DraggableAudioCard(fileURL: fileURL, mp3URL: recorder.currentMP3URL, onCopyPath: {
                let pasteboard = NSPasteboard.general
                pasteboard.clearContents()
                pasteboard.setString(fileURL.path, forType: .string)
            }, onCopyMP3: {
                recorder.copyMP3ToClipboard()
            })

            // Actions Bar: Denoise & Copy MP3
            HStack(spacing: 10) {
                Button(action: {
                    recorder.applyDenoise(fileURL: fileURL, strength: recorder.denoiseStrength) { cleanURL in
                        player.loadAudio(url: cleanURL)
                    }
                }) {
                    HStack(spacing: 6) {
                        if recorder.isDenoising {
                            ProgressView()
                                .controlSize(.small)
                            Text(loc.t(.denoisingProgress))
                        } else {
                            Image(systemName: "sparkles")
                            Text(recorder.isDenoised ? loc.t(.denoiseApplied) : loc.t(.denoiseButton))
                        }
                    }
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(recorder.isDenoised ? .green : .accentColor)
                }
                .buttonStyle(.bordered)
                .disabled(recorder.isDenoising)

                Button(action: {
                    recorder.copyMP3ToClipboard()
                }) {
                    HStack(spacing: 6) {
                        if recorder.isConvertingMP3 {
                            ProgressView()
                                .controlSize(.small)
                            Text(loc.t(.convertingMP3))
                        } else {
                            Image(systemName: recorder.copiedMP3Feedback ? "checkmark.circle.fill" : "music.note")
                                .foregroundColor(recorder.copiedMP3Feedback ? .green : .orange)
                            Text(recorder.copiedMP3Feedback ? loc.t(.copiedMP3Done) : loc.t(.copyAsMP3Button))
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(recorder.copiedMP3Feedback ? .green : .primary)
                        }
                    }
                }
                .buttonStyle(.bordered)
                .disabled(recorder.isConvertingMP3)
                .help(loc.t(.copyMP3Help))

                Spacer()

                if recorder.isDenoised {
                    Text(loc.t(.cleanVoiceTag))
                        .font(.system(size: 11))
                        .foregroundColor(.green)
                }
            }

            // In-line Player Preview
            if let err = player.playbackError {
                Text(err)
                    .font(.system(size: 11))
                    .foregroundColor(.red)
            }

            HStack(spacing: 12) {
                Button(action: {
                    player.togglePlayPause()
                }) {
                    Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(.blue)
                }
                .buttonStyle(.plain)

                VStack(spacing: 4) {
                    Slider(value: Binding(
                        get: { player.currentTime },
                        set: { player.seek(to: $0) }
                    ), in: 0...max(player.duration, 0.01))

                    HStack {
                        Text(formattedDuration(player.currentTime))
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(formattedDuration(player.duration))
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                }

                Button(action: {
                    player.loadAudio(url: fileURL)
                }) {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 13))
                }
                .buttonStyle(.plain)
                .help(loc.t(.reloadAudioHelp))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(NSColor.controlBackgroundColor).opacity(0.5))
            .cornerRadius(10)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.8))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.blue.opacity(0.3), lineWidth: 1)
                )
        )
    }

    // MARK: - Local Whisper Transcription Section
    private var transcriptionSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(loc.t(.localSpeechRecognitionTitle), systemImage: "sparkles")
                    .font(.system(size: 14, weight: .semibold))

                Spacer()

                // Spoken Language picker
                Picker(loc.t(.speechLanguageLabel), selection: $transcriber.selectedLanguage) {
                    ForEach(transcriber.languageKeys, id: \.self) { key in
                        Text(transcriber.languageDisplayName(for: key, lang: loc.currentLanguage)).tag(key)
                    }
                }
                .pickerStyle(.menu)
                .frame(width: 220)
            }

            // Model selector & Transcribe Button
            HStack(spacing: 12) {
                Picker(loc.t(.modelLabel), selection: $transcriber.selectedModel) {
                    ForEach(transcriber.modelKeys, id: \.self) { key in
                        Text(transcriber.modelDisplayName(for: key, lang: loc.currentLanguage)).tag(key)
                    }
                }
                .pickerStyle(.menu)

                Spacer()

                if let currentURL = recorder.currentRecordingURL {
                    Button(action: {
                        transcriber.transcribe(fileURL: currentURL)
                    }) {
                        HStack(spacing: 6) {
                            if transcriber.isTranscribing {
                                ProgressView()
                                    .controlSize(.small)
                                Text(loc.t(.transcribingProgress))
                            } else {
                                Image(systemName: "waveform.and.magnifyingglass")
                                Text(loc.t(.transcribeButton))
                            }
                        }
                        .font(.system(size: 13, weight: .semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(transcriber.isTranscribing)
                }
            }

            // Status or Error
            if let err = transcriber.errorMessage {
                Text(err)
                    .font(.system(size: 12))
                    .foregroundColor(.red)
            } else {
                HStack(spacing: 6) {
                    Circle()
                        .fill(transcriber.isTranscribing ? Color.orange : Color.green)
                        .frame(width: 6, height: 6)
                    Text(transcriber.statusMessage)
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }

            // Transcription Text Area
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(loc.t(.transcriptionResultLabel))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.secondary)

                    Spacer()

                    // Copy Button
                    Button(action: {
                        transcriber.copyToClipboard()
                    }) {
                        HStack(spacing: 5) {
                            Image(systemName: transcriber.copiedFeedback ? "checkmark" : "doc.on.doc")
                                .foregroundColor(transcriber.copiedFeedback ? .green : .primary)
                            Text(transcriber.copiedFeedback ? loc.t(.copiedTextDone) : loc.t(.copyTextButton))
                                .fontWeight(transcriber.copiedFeedback ? .bold : .regular)
                                .foregroundColor(transcriber.copiedFeedback ? .green : .primary)
                        }
                        .font(.system(size: 12))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(transcriber.copiedFeedback ? Color.green.opacity(0.15) : Color(NSColor.controlBackgroundColor))
                        .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                    .disabled(transcriber.transcribedText.isEmpty)
                    .help(loc.t(.copyTextButton))
                }

                TextEditor(text: $transcriber.transcribedText)
                    .font(.system(size: 13, design: .default))
                    .frame(minHeight: 100, maxHeight: 180)
                    .padding(6)
                    .background(Color(NSColor.textBackgroundColor))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.gray.opacity(0.2), lineWidth: 1)
                    )
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.8))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.purple.opacity(0.3), lineWidth: 1)
                )
        )
    }

    // MARK: - History Section
    private var recentHistorySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.t(.recentRecordingsTitle))
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)

            ForEach(recorder.recentRecordings.prefix(5), id: \.self) { fileURL in
                HStack {
                    Image(systemName: "waveform")
                        .foregroundColor(fileURL == recorder.currentRecordingURL ? .blue : .secondary)

                    Text(fileURL.lastPathComponent)
                        .font(.system(size: 12, weight: fileURL == recorder.currentRecordingURL ? .bold : .regular))
                        .lineLimit(1)

                    Spacer()

                    Button(action: {
                        recorder.currentRecordingURL = fileURL
                        player.loadAudio(url: fileURL)
                    }) {
                        Text(loc.t(.selectRecordingButton))
                            .font(.system(size: 11))
                    }
                    .buttonStyle(.bordered)
                }
                .padding(.vertical, 4)
                .padding(.horizontal, 8)
                .background(fileURL == recorder.currentRecordingURL ? Color.blue.opacity(0.08) : Color.clear)
                .cornerRadius(6)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(NSColor.controlBackgroundColor).opacity(0.5))
        )
    }

    private func formattedDuration(_ time: TimeInterval) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        let tenths = Int((time.truncatingRemainder(dividingBy: 1)) * 10)
        return String(format: "%02d:%02d.%d", minutes, seconds, tenths)
    }
}

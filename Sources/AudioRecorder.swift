import Foundation
import AVFoundation
import AppKit
import Combine
import UniformTypeIdentifiers

class AudioRecorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published var isRecording: Bool = false
    @Published var isDenoising: Bool = false
    @Published var isConvertingMP3: Bool = false
    @Published var isConvertingOGG: Bool = false
    @Published var copiedMP3Feedback: Bool = false
    @Published var copiedOGGFeedback: Bool = false
    @Published var copiedWAVFeedback: Bool = false
    @Published var recordingDuration: TimeInterval = 0.0
    @Published var currentAudioLevel: Float = 0.0
    @Published var audioWaveform: [Float] = Array(repeating: 0.1, count: 24)
    @Published var currentRecordingURL: URL?
    @Published var currentMP3URL: URL?
    @Published var currentOGGURL: URL?
    @Published var recentRecordings: [URL] = []
    @Published var recordingDurations: [URL: TimeInterval] = [:]
    @Published var noiseSuppressionEnabled: Bool = true
    @Published var denoiseStrength: Double = 1.8 // 1.2 (Mild), 1.8 (Studio), 2.5 (Strong)
    @Published var isDenoised: Bool = false
    @Published var errorMessage: String?
    @Published var micPermissionGranted: Bool = true

    var onBeforeStartRecording: (() -> Void)?
    var onRecordingFinished: ((URL) -> Void)?

    private var audioRecorder: AVAudioRecorder?
    private var meterTimer: Timer?
    private var recordingTimer: Timer?
    private var recordingStartTime: Date?
    private var hasReceivedAudioSignal: Bool = false
    private var silentTicks: Int = 0
    private var previousRecordingURL: URL?

    var recordingsDirectory: URL {
        let bundleFolder = Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("Recordings", isDirectory: true)
        if FileManager.default.fileExists(atPath: bundleFolder.path) {
            return bundleFolder
        }

        let home = FileManager.default.homeDirectoryForCurrentUser
        let appSupport = home.appendingPathComponent("Library/Application Support/VoiceDrop/Recordings", isDirectory: true)
        try? FileManager.default.createDirectory(at: appSupport, withIntermediateDirectories: true, attributes: nil)
        return appSupport
    }

    override init() {
        super.init()
        checkPermission()
        loadRecentRecordings()
    }

    func checkPermission() {
        if #available(macOS 14.0, *) {
            let status = AVAudioApplication.shared.recordPermission
            self.micPermissionGranted = (status == .granted || status == .undetermined)
        } else {
            let status = AVCaptureDevice.authorizationStatus(for: .audio)
            self.micPermissionGranted = (status == .authorized || status == .notDetermined)
        }
    }

    func openSystemSettingsForMic() {
        if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone") {
            NSWorkspace.shared.open(url)
        }
    }

    func resetAndRequestMicPermission() {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.voicedrop.app"
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/tccutil")
        p.arguments = ["reset", "Microphone", bundleID]
        try? p.run()
        p.waitUntilExit()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.requestMicrophoneAccess { granted in
                DispatchQueue.main.async {
                    self.micPermissionGranted = granted
                    if !granted {
                        self.openSystemSettingsForMic()
                    }
                }
            }
        }
    }

    func loadRecentRecordings() {
        do {
            let files = try FileManager.default.contentsOfDirectory(at: recordingsDirectory, includingPropertiesForKeys: [.creationDateKey], options: [.skipsHiddenFiles])
            // Keep only MP3 files in history (no WAV)
            let sorted = files.filter { $0.pathExtension.lowercased() == "mp3" }
                .sorted {
                    let date1 = (try? $0.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date.distantPast
                    let date2 = (try? $1.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date.distantPast
                    return date1 > date2
                }
            DispatchQueue.main.async {
                self.recentRecordings = sorted
                if self.currentRecordingURL == nil, let first = sorted.first {
                    self.currentRecordingURL = first
                    self.currentMP3URL = first
                    let oggCandidate = first.deletingPathExtension().appendingPathExtension("ogg")
                    self.currentOGGURL = FileManager.default.fileExists(atPath: oggCandidate.path) ? oggCandidate : nil
                }
                self.loadDurationsIfNeeded(for: sorted)
            }
        } catch {
            print("Failed to load recordings: \(error)")
        }
    }

    private func loadDurationsIfNeeded(for urls: [URL]) {
        let missing = urls.filter { self.recordingDurations[$0] == nil }
        guard !missing.isEmpty else { return }

        DispatchQueue.global(qos: .utility).async { [weak self] in
            var computed: [URL: TimeInterval] = [:]
            for url in missing {
                let asset = AVURLAsset(url: url)
                let seconds = CMTimeGetSeconds(asset.duration)
                if seconds.isFinite && seconds >= 0 {
                    computed[url] = seconds
                }
                // Flush in small batches so visible rows update immediately
                if computed.count >= 20 {
                    let batch = computed
                    computed.removeAll()
                    DispatchQueue.main.async {
                        self?.recordingDurations.merge(batch) { _, new in new }
                    }
                }
            }
            if !computed.isEmpty {
                let batch = computed
                DispatchQueue.main.async {
                    self?.recordingDurations.merge(batch) { _, new in new }
                }
            }
        }
    }

    func deleteRecording(_ url: URL) {
        let baseURL = url.deletingPathExtension()
        let wavURL = baseURL.appendingPathExtension("wav")
        let mp3URL = baseURL.appendingPathExtension("mp3")
        let oggURL = baseURL.appendingPathExtension("ogg")
        let candidates = Set([url, wavURL, mp3URL, oggURL])

        for candidate in candidates {
            if FileManager.default.fileExists(atPath: candidate.path) {
                try? FileManager.default.removeItem(at: candidate)
            }
        }

        let wasCurrent = (currentRecordingURL != nil && candidates.contains(currentRecordingURL!))
            || (currentMP3URL != nil && candidates.contains(currentMP3URL!))
            || (currentOGGURL != nil && candidates.contains(currentOGGURL!))

        DispatchQueue.main.async {
            self.recentRecordings.removeAll { candidates.contains($0) }
            if wasCurrent {
                if let next = self.recentRecordings.first {
                    self.currentRecordingURL = next
                    self.currentMP3URL = next
                    let oggCandidate = next.deletingPathExtension().appendingPathExtension("ogg")
                    self.currentOGGURL = FileManager.default.fileExists(atPath: oggCandidate.path) ? oggCandidate : nil
                } else {
                    self.currentRecordingURL = nil
                    self.currentMP3URL = nil
                    self.currentOGGURL = nil
                }
                self.isDenoised = false
            }
            self.loadRecentRecordings()
        }
    }

    func requestMicrophoneAccess(completion: @escaping (Bool) -> Void) {
        if #available(macOS 14.0, *) {
            let perm = AVAudioApplication.shared.recordPermission
            if perm == .granted {
                completion(true)
            } else if perm == .denied {
                completion(false)
            } else {
                AVAudioApplication.requestRecordPermission { granted in
                    DispatchQueue.main.async {
                        self.micPermissionGranted = granted
                        completion(granted)
                    }
                }
            }
        } else {
            AVCaptureDevice.requestAccess(for: .audio) { granted in
                DispatchQueue.main.async {
                    self.micPermissionGranted = granted
                    completion(granted)
                }
            }
        }
    }

    func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            requestMicrophoneAccess { [weak self] granted in
                guard let self = self else { return }
                if granted {
                    self.startRecording()
                } else {
                    self.micPermissionGranted = false
                    self.errorMessage = Localization.shared.t(.micPermissionDeniedError)
                }
            }
        }
    }

    func startRecording() {
        guard !isRecording else { return }
        onBeforeStartRecording?()
        errorMessage = nil
        isDenoised = false
        previousRecordingURL = currentRecordingURL
        currentMP3URL = nil
        currentOGGURL = nil
        hasReceivedAudioSignal = false
        silentTicks = 0

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        let timestamp = dateFormatter.string(from: Date())
        let fileURL = recordingsDirectory.appendingPathComponent("Voice_\(timestamp).wav")

        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 16000.0,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false
        ]

        do {
            let recorder = try AVAudioRecorder(url: fileURL, settings: settings)
            recorder.delegate = self
            recorder.isMeteringEnabled = true
            let prepared = recorder.prepareToRecord()
            guard prepared else {
                errorMessage = "Не удалось подготовить запись аудио."
                return
            }

            let started = recorder.record()
            guard started else {
                errorMessage = "Не удалось запустить запись с микрофона."
                return
            }

            self.audioRecorder = recorder
            self.currentRecordingURL = fileURL
            self.isRecording = true
            self.recordingStartTime = Date()
            self.recordingDuration = 0.0

            self.recordingTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
                guard let self = self, let start = self.recordingStartTime else { return }
                self.recordingDuration = Date().timeIntervalSince(start)
            }

            self.meterTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
                guard let self = self, let recorder = self.audioRecorder, recorder.isRecording else { return }
                recorder.updateMeters()
                let avg = recorder.averagePower(forChannel: 0)
                let peak = recorder.peakPower(forChannel: 0)
                let normalized = min(max((avg + 50.0) / 50.0, 0.0), 1.0)
                self.currentAudioLevel = normalized

                if peak > -120.0 {
                    self.hasReceivedAudioSignal = true
                    self.silentTicks = 0
                    if self.errorMessage == Localization.shared.t(.noMicSignalWarning) {
                        self.errorMessage = nil
                    }
                } else if !self.hasReceivedAudioSignal {
                    self.silentTicks += 1
                    if self.silentTicks == 30 {
                        self.errorMessage = Localization.shared.t(.noMicSignalWarning)
                    }
                }

                var wf = self.audioWaveform
                wf.removeFirst()
                wf.append(max(0.08, normalized))
                self.audioWaveform = wf
            }
        } catch {
            errorMessage = "Ошибка записи: \(error.localizedDescription)"
        }
    }

    private func isWAVFileCompletelySilent(_ url: URL) -> Bool {
        guard let data = try? Data(contentsOf: url), data.count > 44 else {
            return true
        }
        // Check PCM samples after the 44-byte WAV header
        return data.dropFirst(44).allSatisfy { $0 == 0 }
    }

    func stopRecording() {
        guard isRecording else { return }

        recordingTimer?.invalidate()
        recordingTimer = nil
        meterTimer?.invalidate()
        meterTimer = nil

        let savedURL = self.currentRecordingURL
        audioRecorder?.stop()
        audioRecorder = nil

        DispatchQueue.main.async {
            self.isRecording = false
            self.currentAudioLevel = 0.0
            self.audioWaveform = Array(repeating: 0.1, count: 24)

            guard let url = savedURL else { return }

            // Guard against silent/zero-filled recordings (e.g., revoked TCC mic permission or dead input device)
            if self.isWAVFileCompletelySilent(url) {
                try? FileManager.default.removeItem(at: url)
                self.currentRecordingURL = self.previousRecordingURL ?? self.recentRecordings.first
                if let current = self.currentRecordingURL {
                    let mp3Candidate = current.deletingPathExtension().appendingPathExtension("mp3")
                    self.currentMP3URL = FileManager.default.fileExists(atPath: mp3Candidate.path) ? mp3Candidate : nil
                    let oggCandidate = current.deletingPathExtension().appendingPathExtension("ogg")
                    self.currentOGGURL = FileManager.default.fileExists(atPath: oggCandidate.path) ? oggCandidate : nil
                }
                self.errorMessage = Localization.shared.t(.silentRecordingDiscardedError)
                self.loadRecentRecordings()
                return
            }

            if self.noiseSuppressionEnabled {
                self.applyDenoise(fileURL: url, strength: self.denoiseStrength) { [weak self] cleanURL in
                    guard let self = self else { return }
                    self.currentRecordingURL = cleanURL
                    self.isDenoised = true
                    self.loadRecentRecordings()
                    self.onRecordingFinished?(cleanURL)
                    self.convertToMP3(fileURL: cleanURL) { _ in }
                }
            } else {
                self.loadRecentRecordings()
                self.currentRecordingURL = url
                self.onRecordingFinished?(url)
                self.convertToMP3(fileURL: url) { _ in }
            }
        }
    }

    private func findPythonExecutable() -> String? {
        let home = FileManager.default.homeDirectoryForCurrentUser.path
        let candidates = [
            "\(home)/.local/share/uv/tools/whisper-ctranslate2/bin/python3",
            "/opt/homebrew/bin/python3",
            "/usr/local/bin/python3",
            "/opt/anaconda3/bin/python3",
            "\(home)/anaconda3/bin/python3",
            "\(home)/miniconda3/bin/python3",
            "/usr/bin/python3"
        ]
        return candidates.first(where: { FileManager.default.isExecutableFile(atPath: $0) })
    }

    private func findDenoiseScript() -> String? {
        if let bundlePath = Bundle.main.path(forResource: "denoise", ofType: "py") {
            return bundlePath
        }
        let devScript = Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("denoise.py").path
        if FileManager.default.fileExists(atPath: devScript) {
            return devScript
        }
        if FileManager.default.fileExists(atPath: "denoise.py") {
            return URL(fileURLWithPath: "denoise.py").path
        }
        return nil
    }

    private func findConvertMP3Script() -> String? {
        if let bundlePath = Bundle.main.path(forResource: "convert_mp3", ofType: "py") {
            return bundlePath
        }
        let devScript = Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("convert_mp3.py").path
        if FileManager.default.fileExists(atPath: devScript) {
            return devScript
        }
        if FileManager.default.fileExists(atPath: "convert_mp3.py") {
            return URL(fileURLWithPath: "convert_mp3.py").path
        }
        return nil
    }

    private func findConvertOGGScript() -> String? {
        if let bundlePath = Bundle.main.path(forResource: "convert_ogg", ofType: "py") {
            return bundlePath
        }
        let devScript = Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("convert_ogg.py").path
        if FileManager.default.fileExists(atPath: devScript) {
            return devScript
        }
        if FileManager.default.fileExists(atPath: "convert_ogg.py") {
            return URL(fileURLWithPath: "convert_ogg.py").path
        }
        return nil
    }

    func applyDenoise(fileURL: URL, strength: Double, completion: @escaping (URL) -> Void) {
        guard let pythonPath = findPythonExecutable(),
              let scriptPath = findDenoiseScript() else {
            completion(fileURL)
            return
        }

        DispatchQueue.main.async {
            self.isDenoising = true
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let process = Process()
            process.executableURL = URL(fileURLWithPath: pythonPath)
            process.arguments = [scriptPath, fileURL.path, fileURL.path, String(strength)]

            do {
                try process.run()
                process.waitUntilExit()
            } catch {
                print("Denoise execution failed: \(error)")
            }

            DispatchQueue.main.async {
                self.isDenoising = false
                self.isDenoised = true
                completion(fileURL)
            }
        }
    }

    func convertToMP3(fileURL: URL, completion: @escaping (URL?) -> Void) {
        let mp3URL = fileURL.deletingPathExtension().appendingPathExtension("mp3")
        if FileManager.default.fileExists(atPath: mp3URL.path) {
            DispatchQueue.main.async {
                self.currentMP3URL = mp3URL
                completion(mp3URL)
            }
            return
        }

        guard let pythonPath = findPythonExecutable(),
              let scriptPath = findConvertMP3Script() else {
            completion(nil)
            return
        }

        DispatchQueue.main.async {
            self.isConvertingMP3 = true
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let process = Process()
            process.executableURL = URL(fileURLWithPath: pythonPath)
            process.arguments = [scriptPath, fileURL.path, mp3URL.path]

            do {
                try process.run()
                process.waitUntilExit()
            } catch {
                print("MP3 conversion failed: \(error)")
            }

            DispatchQueue.main.async {
                self.isConvertingMP3 = false
                if FileManager.default.fileExists(atPath: mp3URL.path) {
                    self.currentMP3URL = mp3URL
                    self.loadRecentRecordings()
                    completion(mp3URL)
                } else {
                    completion(nil)
                }
            }
        }
    }

    func copyMP3ToClipboard() {
        guard let url = currentRecordingURL else { return }

        let mp3Candidate = url.deletingPathExtension().appendingPathExtension("mp3")
        if FileManager.default.fileExists(atPath: mp3Candidate.path) {
            self.currentMP3URL = mp3Candidate
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.writeObjects([mp3Candidate as NSURL])
            triggerCopiedMP3Feedback()
            return
        }

        convertToMP3(fileURL: url) { [weak self] resultURL in
            guard let self = self, let mp3 = resultURL else { return }
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.writeObjects([mp3 as NSURL])
            self.triggerCopiedMP3Feedback()
        }
    }

    private func triggerCopiedMP3Feedback() {
        DispatchQueue.main.async {
            self.copiedMP3Feedback = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.copiedMP3Feedback = false
        }
    }

    func convertToOGG(fileURL: URL, completion: @escaping (URL?) -> Void) {
        if fileURL.pathExtension.lowercased() == "ogg", FileManager.default.fileExists(atPath: fileURL.path) {
            DispatchQueue.main.async {
                self.currentOGGURL = fileURL
                completion(fileURL)
            }
            return
        }

        let oggURL = fileURL.deletingPathExtension().appendingPathExtension("ogg")
        if FileManager.default.fileExists(atPath: oggURL.path) {
            DispatchQueue.main.async {
                self.currentOGGURL = oggURL
                completion(oggURL)
            }
            return
        }

        guard let pythonPath = findPythonExecutable(),
              let scriptPath = findConvertOGGScript() else {
            completion(nil)
            return
        }

        // Prefer lossless WAV sibling as source if available
        let wavSibling = fileURL.deletingPathExtension().appendingPathExtension("wav")
        let sourceURL = FileManager.default.fileExists(atPath: wavSibling.path) ? wavSibling : fileURL

        DispatchQueue.main.async {
            self.isConvertingOGG = true
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            let process = Process()
            process.executableURL = URL(fileURLWithPath: pythonPath)
            process.arguments = [scriptPath, sourceURL.path, oggURL.path]

            do {
                try process.run()
                process.waitUntilExit()
            } catch {
                print("OGG conversion failed: \(error)")
            }

            DispatchQueue.main.async {
                self.isConvertingOGG = false
                if FileManager.default.fileExists(atPath: oggURL.path) {
                    self.currentOGGURL = oggURL
                    completion(oggURL)
                } else {
                    completion(nil)
                }
            }
        }
    }

    func copyOGGToClipboard() {
        guard let url = currentRecordingURL else { return }

        if url.pathExtension.lowercased() == "ogg", FileManager.default.fileExists(atPath: url.path) {
            self.currentOGGURL = url
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.writeObjects([url as NSURL])
            triggerCopiedOGGFeedback()
            return
        }

        let oggCandidate = url.deletingPathExtension().appendingPathExtension("ogg")
        if FileManager.default.fileExists(atPath: oggCandidate.path) {
            self.currentOGGURL = oggCandidate
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.writeObjects([oggCandidate as NSURL])
            triggerCopiedOGGFeedback()
            return
        }

        convertToOGG(fileURL: url) { [weak self] resultURL in
            guard let self = self, let ogg = resultURL else { return }
            let pasteboard = NSPasteboard.general
            pasteboard.clearContents()
            pasteboard.writeObjects([ogg as NSURL])
            self.triggerCopiedOGGFeedback()
        }
    }

    private func triggerCopiedOGGFeedback() {
        DispatchQueue.main.async {
            self.copiedOGGFeedback = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.copiedOGGFeedback = false
        }
    }

    func copyWAVToClipboard() {
        guard let url = currentRecordingURL else { return }
        let wavCandidate = url.deletingPathExtension().appendingPathExtension("wav")
        let targetURL = FileManager.default.fileExists(atPath: wavCandidate.path) ? wavCandidate : url
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.writeObjects([targetURL as NSURL])
        triggerCopiedWAVFeedback()
    }

    private func triggerCopiedWAVFeedback() {
        DispatchQueue.main.async {
            self.copiedWAVFeedback = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.copiedWAVFeedback = false
        }
    }

    func copyDefaultAudioToClipboard() {
        // Default audio copy format is MP3 as requested
        copyMP3ToClipboard()
    }

    func selectExternalFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        var types: [UTType] = [.audio, .wav, .mp3, .mpeg4Audio]
        if let oggType = UTType(filenameExtension: "ogg") {
            types.append(oggType)
        }
        panel.allowedContentTypes = types
        if panel.runModal() == .OK, let url = panel.url {
            self.currentRecordingURL = url
            let mp3Candidate = url.deletingPathExtension().appendingPathExtension("mp3")
            self.currentMP3URL = FileManager.default.fileExists(atPath: mp3Candidate.path) ? mp3Candidate : nil
            let oggCandidate = url.deletingPathExtension().appendingPathExtension("ogg")
            self.currentOGGURL = FileManager.default.fileExists(atPath: oggCandidate.path) ? oggCandidate : nil
            self.isDenoised = false
            self.loadRecentRecordings()
            self.onRecordingFinished?(url)
            if url.pathExtension.lowercased() == "wav" {
                self.convertToMP3(fileURL: url) { _ in }
            }
        }
    }
}

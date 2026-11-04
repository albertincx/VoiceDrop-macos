import Foundation
import AVFoundation
import AppKit
import Combine

class AudioRecorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published var isRecording: Bool = false
    @Published var isDenoising: Bool = false
    @Published var recordingDuration: TimeInterval = 0.0
    @Published var currentAudioLevel: Float = 0.0
    @Published var audioWaveform: [Float] = Array(repeating: 0.1, count: 24)
    @Published var currentRecordingURL: URL?
    @Published var recentRecordings: [URL] = []
    @Published var noiseSuppressionEnabled: Bool = true
    @Published var denoiseStrength: Double = 1.8 // 1.2 (Mild), 1.8 (Studio), 2.5 (Strong)
    @Published var isDenoised: Bool = false
    @Published var errorMessage: String?
    @Published var micPermissionGranted: Bool = true

    var onRecordingFinished: ((URL) -> Void)?

    private var audioRecorder: AVAudioRecorder?
    private var meterTimer: Timer?
    private var recordingTimer: Timer?
    private var recordingStartTime: Date?

    var recordingsDirectory: URL {
        // First check relative directory next to the bundle / project
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

    func loadRecentRecordings() {
        do {
            let files = try FileManager.default.contentsOfDirectory(at: recordingsDirectory, includingPropertiesForKeys: [.creationDateKey], options: [.skipsHiddenFiles])
            let sorted = files.filter { ["wav", "m4a", "mp3"].contains($0.pathExtension.lowercased()) }
                .sorted {
                    let date1 = (try? $0.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date.distantPast
                    let date2 = (try? $1.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date.distantPast
                    return date1 > date2
                }
            DispatchQueue.main.async {
                self.recentRecordings = sorted
                if self.currentRecordingURL == nil, let first = sorted.first {
                    self.currentRecordingURL = first
                }
            }
        } catch {
            print("Failed to load recordings: \(error)")
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
                    self.errorMessage = "Микрофон отключен в настройках системы. Нажмите 'Настройки macOS' для включения."
                    self.startRecording()
                }
            }
        }
    }

    func startRecording() {
        guard !isRecording else { return }
        errorMessage = nil
        isDenoised = false

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
                let normalized = min(max((avg + 50.0) / 50.0, 0.0), 1.0)
                self.currentAudioLevel = normalized

                var wf = self.audioWaveform
                wf.removeFirst()
                wf.append(max(0.08, normalized))
                self.audioWaveform = wf
            }
        } catch {
            errorMessage = "Ошибка записи: \(error.localizedDescription)"
        }
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

            if self.noiseSuppressionEnabled {
                self.applyDenoise(fileURL: url, strength: self.denoiseStrength) { [weak self] cleanURL in
                    guard let self = self else { return }
                    self.currentRecordingURL = cleanURL
                    self.isDenoised = true
                    self.loadRecentRecordings()
                    self.onRecordingFinished?(cleanURL)
                }
            } else {
                self.loadRecentRecordings()
                self.currentRecordingURL = url
                self.onRecordingFinished?(url)
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

    func selectExternalFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.audio, .wav, .mp3, .mpeg4Audio]
        if panel.runModal() == .OK, let url = panel.url {
            self.currentRecordingURL = url
            self.isDenoised = false
            self.loadRecentRecordings()
            self.onRecordingFinished?(url)
        }
    }
}

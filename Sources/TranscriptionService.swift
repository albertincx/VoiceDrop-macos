import Foundation
import AppKit
import Combine

class TranscriptionService: ObservableObject {
    @Published var isTranscribing: Bool = false
    @Published var transcribedText: String = ""
    @Published var detectedLanguage: String = ""
    @Published var statusMessage: String = Localization.shared.t(.statusReadyToTranscribe)
    @Published var errorMessage: String?
    @Published var copiedFeedback: Bool = false
    @Published var selectedModel: String = "large-v3-turbo"
    @Published var selectedLanguage: String = "auto"

    let modelKeys = ["large-v3-turbo", "tiny"]
    let languageKeys = ["auto", "en", "ru", "de", "es", "fr"]

    func modelDisplayName(for key: String, lang: AppLanguage = Localization.shared.currentLanguage) -> String {
        switch key {
        case "large-v3-turbo":
            return lang == .en ? "Whisper large-v3-turbo (Local Saved Model)" : "Whisper large-v3-turbo (Сохраненная локальная модель)"
        case "tiny":
            return lang == .en ? "Whisper tiny (Fast & Lightweight)" : "Whisper tiny (Быстрая легковесная модель)"
        default:
            return key
        }
    }

    func languageDisplayName(for key: String, lang: AppLanguage = Localization.shared.currentLanguage) -> String {
        switch key {
        case "auto":
            return lang == .en ? "Auto-detect" : "Автоопределение"
        case "ru":
            return lang == .en ? "Russian (RU)" : "Русский (RU)"
        case "en":
            return lang == .en ? "English (EN)" : "English (EN)"
        case "de":
            return "Deutsch"
        case "es":
            return "Español"
        case "fr":
            return "Français"
        default:
            return key
        }
    }

    private var currentProcess: Process?

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

        for path in candidates {
            if FileManager.default.isExecutableFile(atPath: path) {
                return path
            }
        }
        return nil
    }

    private func findTranscribeScript() -> String? {
        if let bundlePath = Bundle.main.path(forResource: "transcribe", ofType: "py") {
            return bundlePath
        }
        let devScript = Bundle.main.bundleURL.deletingLastPathComponent().appendingPathComponent("transcribe.py").path
        if FileManager.default.fileExists(atPath: devScript) {
            return devScript
        }
        if FileManager.default.fileExists(atPath: "transcribe.py") {
            return URL(fileURLWithPath: "transcribe.py").path
        }
        return nil
    }

    func transcribe(fileURL: URL) {
        guard !isTranscribing else { return }
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            let isEn = Localization.shared.currentLanguage == .en
            self.errorMessage = isEn ? "Audio file not found: \(fileURL.lastPathComponent)" : "Файл аудио не найден: \(fileURL.lastPathComponent)"
            return
        }

        guard let pythonPath = findPythonExecutable() else {
            let isEn = Localization.shared.currentLanguage == .en
            self.errorMessage = isEn ? "Python with faster-whisper not found." : "Не найден Python с faster-whisper."
            return
        }

        guard let scriptPath = findTranscribeScript() else {
            let isEn = Localization.shared.currentLanguage == .en
            self.errorMessage = isEn ? "transcribe.py script not found." : "Скрипт распознавания transcribe.py не найден."
            return
        }

        DispatchQueue.main.async {
            self.isTranscribing = true
            self.errorMessage = nil
            self.statusMessage = Localization.shared.t(.statusLoadingModel(self.selectedModel))
        }

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }

            let process = Process()
            self.currentProcess = process
            process.executableURL = URL(fileURLWithPath: pythonPath)
            process.arguments = [
                scriptPath,
                fileURL.path,
                self.selectedLanguage,
                self.selectedModel
            ]

            var env = ProcessInfo.processInfo.environment
            env["PYTHONUNBUFFERED"] = "1"
            process.environment = env

            let outPipe = Pipe()
            process.standardOutput = outPipe
            process.standardError = outPipe // Combine stdout and stderr to avoid pipe deadlock

            do {
                try process.run()
                DispatchQueue.main.async {
                    self.statusMessage = Localization.shared.t(.statusTranscribing)
                }

                let data = outPipe.fileHandleForReading.readDataToEndOfFile()
                process.waitUntilExit()

                let rawOutput = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

                // Locate JSON line (the last line containing { ... })
                var jsonString: String?
                let lines = rawOutput.components(separatedBy: .newlines)
                for line in lines.reversed() {
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    if trimmed.hasPrefix("{") && trimmed.hasSuffix("}") {
                        jsonString = trimmed
                        break
                    }
                }

                if let jStr = jsonString, let jData = jStr.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: jData, options: []) as? [String: Any] {
                    if let error = json["error"] as? String {
                        DispatchQueue.main.async {
                            self.isTranscribing = false
                            self.errorMessage = error
                            self.statusMessage = Localization.shared.currentLanguage == .en ? "Transcription error" : "Ошибка распознавания"
                        }
                        return
                    }

                    let text = (json["text"] as? String) ?? ""
                    let lang = (json["language"] as? String) ?? ""

                    DispatchQueue.main.async {
                        self.isTranscribing = false
                        self.transcribedText = text
                        self.detectedLanguage = lang
                        self.statusMessage = Localization.shared.t(.statusSuccess(lang))
                    }
                } else if !rawOutput.isEmpty {
                    DispatchQueue.main.async {
                        self.isTranscribing = false
                        self.transcribedText = rawOutput
                        self.statusMessage = Localization.shared.t(.statusCompleted)
                    }
                } else {
                    DispatchQueue.main.async {
                        self.isTranscribing = false
                        let isEn = Localization.shared.currentLanguage == .en
                        self.errorMessage = isEn ? "Model returned empty text" : "Модель не вернула текст (пустой вывод)"
                        self.statusMessage = isEn ? "Empty result" : "Пустой результат"
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self.isTranscribing = false
                    let isEn = Localization.shared.currentLanguage == .en
                    self.errorMessage = isEn ? "Process execution failed: \(error.localizedDescription)" : "Сбой запуска процесса: \(error.localizedDescription)"
                    self.statusMessage = isEn ? "Error" : "Ошибка"
                }
            }
        }
    }

    func cancelTranscription() {
        currentProcess?.terminate()
        currentProcess = nil
        DispatchQueue.main.async {
            self.isTranscribing = false
            self.statusMessage = Localization.shared.t(.statusCancelled)
        }
    }

    func copyToClipboard() {
        guard !transcribedText.isEmpty else { return }
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(transcribedText, forType: .string)

        DispatchQueue.main.async {
            self.copiedFeedback = true
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            self?.copiedFeedback = false
        }
    }
}

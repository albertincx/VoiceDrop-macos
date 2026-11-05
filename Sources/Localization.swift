import Foundation
import Combine

enum AppLanguage: String, CaseIterable, Identifiable {
    case en = "en"
    case ru = "ru"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .en: return "🇬🇧 English"
        case .ru: return "🇷🇺 Русский"
        }
    }
}

class Localization: ObservableObject {
    static let shared = Localization()

    @Published var currentLanguage: AppLanguage {
        didSet {
            UserDefaults.standard.set(currentLanguage.rawValue, forKey: "appLanguage")
        }
    }

    init() {
        // Default to English as requested: "по умолчанию английский"
        let saved = UserDefaults.standard.string(forKey: "appLanguage") ?? "en"
        self.currentLanguage = AppLanguage(rawValue: saved) ?? .en
    }

    func setLanguage(_ lang: AppLanguage) {
        self.currentLanguage = lang
    }

    func t(_ key: Key) -> String {
        return key.localized(for: currentLanguage)
    }

    enum Key {
        case appTitle
        case headerSubtitle
        case buildLabel
        case chooseFile
        case chooseFileHelp
        case recordingsFolder
        case recordingsFolderHelp

        case studioNoiseSuppression
        case wienerFilterSubtitle
        case autoDenoiseToggle
        case denoiseStrengthLabel
        case strengthMild
        case strengthStudio
        case strengthMaximum

        case micPermissionRequiredTitle
        case micPermissionRequiredSubtitle
        case resetAndRequestPrompt
        case macOSSettings
        case micLabel
        case refreshMicsHelp
        case resetMicAccessButton
        case resetMicAccessHelp

        case recordButton
        case stopButton
        case recordingActiveHint
        case recordingIdleHint
        case spacebarHintRecording
        case spacebarHintIdle

        case savedAudioTitle
        case losslessWavTag
        case dragHint
        case copyWavHelp
        case copyMP3Help
        case showInFinderHelp
        case denoiseButton
        case denoiseApplied
        case denoisingProgress
        case copyAsMP3Button
        case copiedMP3Done
        case convertingMP3
        case cleanVoiceTag
        case reloadAudioHelp

        case localSpeechRecognitionTitle
        case speechLanguageLabel
        case modelLabel
        case modelLarge
        case modelTiny
        case transcribeButton
        case transcribingProgress
        case transcriptionResultLabel
        case copyTextButton
        case copiedTextDone

        case recentRecordingsTitle
        case selectRecordingButton

        case spaceKey
        case statusReadyToTranscribe
        case statusLoadingModel(String)
        case statusTranscribing
        case statusSuccess(String)
        case statusCompleted
        case statusCancelled
        case copiedBadge

        func localized(for lang: AppLanguage) -> String {
            switch self {
            case .spaceKey:
                return lang == .en ? "␣ Space" : "␣ Пробел"
            case .statusReadyToTranscribe:
                return lang == .en ? "Ready to transcribe" : "Готов к распознаванию"
            case .statusLoadingModel(let name):
                return lang == .en ? "Loading local Whisper model (\(name))..." : "Загрузка локальной модели Whisper (\(name))..."
            case .statusTranscribing:
                return lang == .en ? "Transcribing speech with local neural network..." : "Распознавание речи локальной нейросетью..."
            case .statusSuccess(let code):
                return lang == .en ? "Successfully transcribed (\(code.uppercased()))" : "Успешно распознано (\(code.uppercased()))"
            case .statusCompleted:
                return lang == .en ? "Transcription completed" : "Распознавание завершено"
            case .statusCancelled:
                return lang == .en ? "Transcription cancelled" : "Распознавание отменено"
            case .copiedBadge:
                return lang == .en ? "Copied!" : "Скопирован!"
            case .appTitle:
                return "VoiceDrop"
            case .headerSubtitle:
                return lang == .en
                    ? "Noise-suppressed recording, Drag & Drop, and Local Whisper AI"
                    : "Запись с шумоподавлением, Drag&Drop и Whisper"
            case .buildLabel:
                return lang == .en ? "Build" : "Сборка"
            case .chooseFile:
                return lang == .en ? "Open File..." : "Выбрать файл..."
            case .chooseFileHelp:
                return lang == .en
                    ? "Open any audio file (WAV, MP3, M4A) to play or transcribe"
                    : "Выбрать любой аудиофайл (WAV, MP3, M4A) для воспроизведения или распознавания"
            case .recordingsFolder:
                return lang == .en ? "Recordings Folder" : "Папка записей"
            case .recordingsFolderHelp:
                return lang == .en
                    ? "Open recordings directory in Finder"
                    : "Открыть папку с сохранёнными аудиозаписями в Finder"

            case .studioNoiseSuppression:
                return lang == .en
                    ? "Studio Spectral Noise Suppression"
                    : "Студийное спектральное шумоподавление"
            case .wienerFilterSubtitle:
                return lang == .en
                    ? "Wiener DSP Filter (Removes hum, hiss & fan noise)"
                    : "Wiener DSP Filter (Удаление шипения и шума)"
            case .autoDenoiseToggle:
                return lang == .en
                    ? "Auto-denoise on recording complete"
                    : "Авто-шумоподавление при записи"
            case .denoiseStrengthLabel:
                return lang == .en ? "Denoise Intensity:" : "Сила очистки:"
            case .strengthMild:
                return lang == .en ? "Mild (1.2)" : "Мягкая (1.2)"
            case .strengthStudio:
                return lang == .en ? "Studio (1.8)" : "Студийная (1.8)"
            case .strengthMaximum:
                return lang == .en ? "Maximum (2.5)" : "Максимальная (2.5)"

            case .micPermissionRequiredTitle:
                return lang == .en
                    ? "Microphone access is required"
                    : "Требуется разрешение на использование микрофона"
            case .micPermissionRequiredSubtitle:
                return lang == .en
                    ? "macOS is blocking microphone access. Click the button to reset and show prompt."
                    : "macOS блокирует микрофон. Нажмите кнопку справа для сброса и вызова окна."
            case .resetAndRequestPrompt:
                return lang == .en ? "Reset & Show Prompt" : "Сбросить и запросить окно"
            case .macOSSettings:
                return lang == .en ? "macOS Settings" : "Настройки macOS"
            case .micLabel:
                return lang == .en ? "Microphone:" : "Микрофон:"
            case .refreshMicsHelp:
                return lang == .en ? "Refresh microphone list" : "Обновить список микрофонов"
            case .resetMicAccessButton:
                return lang == .en ? "Reset Access" : "Сброс доступа"
            case .resetMicAccessHelp:
                return lang == .en
                    ? "Reset microphone permissions in macOS and re-trigger system permission dialog"
                    : "Сбросить доступ к микрофону в macOS и заново вызвать всплывающее системное окно"

            case .recordButton:
                return lang == .en ? "Record" : "Запись"
            case .stopButton:
                return lang == .en ? "Stop" : "Стоп"
            case .recordingActiveHint:
                return lang == .en
                    ? "Recording in progress. Noise suppression active."
                    : "Говорите в микрофон. Шумоподавление активно."
            case .recordingIdleHint:
                return lang == .en
                    ? "Click Record or press Spacebar to start"
                    : "Нажмите кнопку или пробел для начала записи"
            case .spacebarHintRecording:
                return lang == .en
                    ? "— press to stop recording"
                    : "— нажать, чтобы остановить запись"
            case .spacebarHintIdle:
                return lang == .en
                    ? "— press to start recording"
                    : "— нажать, чтобы начать запись"

            case .savedAudioTitle:
                return lang == .en
                    ? "Saved Voice Recording (Ready to Drag & Drop)"
                    : "Сохранённая аудиозапись (Готова к Drag & Drop)"
            case .losslessWavTag:
                return "WAV 16kHz Mono (Lossless)"
            case .dragHint:
                return lang == .en
                    ? "Drag from here into Antigravity, Telegram, or Finder"
                    : "Потяните отсюда мышкой в Antigravity или Finder"
            case .copyWavHelp:
                return lang == .en
                    ? "Copy WAV audio file to clipboard (Cmd+C)"
                    : "Скопировать WAV в буфер обмена (Cmd+C)"
            case .copyMP3Help:
                return lang == .en
                    ? "Copy MP3 audio file to clipboard"
                    : "Скопировать MP3 в буфер обмена"
            case .showInFinderHelp:
                return lang == .en ? "Reveal in Finder" : "Показать в Finder"
            case .denoiseButton:
                return lang == .en ? "Denoise Audio" : "Очистить от шума"
            case .denoiseApplied:
                return lang == .en ? "Denoise applied ✓" : "Шумоподавление применено ✓"
            case .denoisingProgress:
                return lang == .en ? "Removing noise..." : "Удаление шума..."
            case .copyAsMP3Button:
                return lang == .en ? "Copy as MP3" : "Скопировать как MP3"
            case .copiedMP3Done:
                return lang == .en ? "MP3 copied to clipboard!" : "MP3 скопирован в буфер!"
            case .convertingMP3:
                return lang == .en ? "Converting to MP3..." : "Конвертация в MP3..."
            case .cleanVoiceTag:
                return lang == .en ? "Clean Voice" : "Чистый голос"
            case .reloadAudioHelp:
                return lang == .en ? "Reload audio file" : "Перезагрузить аудиофайл"

            case .localSpeechRecognitionTitle:
                return lang == .en
                    ? "Speech-to-Text (Local Whisper Model)"
                    : "Распознавание текста (Локальная модель)"
            case .speechLanguageLabel:
                return lang == .en ? "Spoken Language:" : "Язык аудио:"
            case .modelLabel:
                return lang == .en ? "Model:" : "Модель:"
            case .modelLarge:
                return lang == .en
                    ? "Whisper large-v3-turbo (Local Saved Model)"
                    : "Whisper large-v3-turbo (Сохраненная локальная модель)"
            case .modelTiny:
                return lang == .en
                    ? "Whisper tiny (Fast & Lightweight)"
                    : "Whisper tiny (Быстрая легковесная модель)"
            case .transcribeButton:
                return lang == .en ? "Transcribe Speech" : "Распознать текст"
            case .transcribingProgress:
                return lang == .en ? "Transcribing..." : "Распознавание..."
            case .transcriptionResultLabel:
                return lang == .en ? "Transcription Result:" : "Результат распознавания:"
            case .copyTextButton:
                return lang == .en ? "Copy Text" : "Копировать"
            case .copiedTextDone:
                return lang == .en ? "Copied!" : "Скопировано!"

            case .recentRecordingsTitle:
                return lang == .en ? "Recent Recordings:" : "Недавние записи:"
            case .selectRecordingButton:
                return lang == .en ? "Select" : "Выбрать"
            }
        }
    }
}

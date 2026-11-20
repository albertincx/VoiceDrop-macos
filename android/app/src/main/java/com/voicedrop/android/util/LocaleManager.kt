package com.voicedrop.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val flag: String) {
    EN("en", "English", "🇺🇸"),
    RU("ru", "Русский", "🇷🇺")
}

data class Strings(
    val appSubtitle: String,
    val historyTooltip: String,
    val minimizeTooltip: String,
    val permissionTitle: String,
    val permissionDesc: String,
    val permissionAllow: String,
    val audioInputSource: String,
    val micDefault: String,
    val micVoiceRecognition: String,
    val micCamcorder: String,
    val micUnprocessed: String,
    val statusStudioReady: String,
    val statusRecording: String,
    val tapToRecord: String,
    val tapToStop: String,
    val latestRecording: String,
    val copyMp3: String,
    val copyWav: String,
    val copied: String,
    val share: String,
    val shareMp3: String,
    val shareLosslessWav: String,
    val saveToDownloads: String,
    val pasteHint: String,
    val copyAndMinimize: String,
    val sttSectionHeader: String,
    val sttTitle: String,
    val sttSubtitle: String,
    val sttOfflineBadge: String,
    val sttClear: String,
    val sttTapToDictate: String,
    val sttListening: String,
    val sttReady: String,
    val sttPlaceholder: String,
    val sttCopyText: String,
    val sttShareText: String,
    val sttCopyTextMinimize: String,
    val sttNoText: String,
    val historyTitle: String,
    val historyFiles: (Int) -> String,
    val historyEmpty: String,
    val delete: String,
    val toastMp3Copied: String,
    val toastWavCopied: String,
    val toastTextCopied: String,
    val recognizeAudio: String,
    val recognizing: String,
    val reRecognize: String,
    val sttPrompt: String,
    val toastTextRecognized: String,
    val sttNoSpeechDetected: String,
    val sttRecognitionError: String,
    val useLocalModel: String,
    val useLocalModelDesc: String,
    val localModelMissingToast: String,
    val openVoiceSettings: String
)

val EnglishStrings = Strings(
    appSubtitle = "High-Quality MP3 & WAV Voice Studio",
    historyTooltip = "Recordings History",
    minimizeTooltip = "Minimize App",
    permissionTitle = "Microphone Permission Required",
    permissionDesc = "VoiceDrop needs microphone access to record high-quality audio.",
    permissionAllow = "Allow",
    audioInputSource = "Audio Input Source",
    micDefault = "Standard Microphone",
    micVoiceRecognition = "Voice Recognition (Clean)",
    micCamcorder = "Camcorder Mic",
    micUnprocessed = "Raw Unprocessed Audio",
    statusStudioReady = "STUDIO READY",
    statusRecording = "RECORDING MP3 + WAV",
    tapToRecord = "Tap to Record Voice",
    tapToStop = "Tap to Stop & Encode MP3",
    latestRecording = "LATEST RECORDING",
    copyMp3 = "Copy MP3",
    copyWav = "Copy WAV",
    copied = "Copied!",
    share = "Share",
    shareMp3 = "Share MP3",
    shareLosslessWav = "Share Lossless WAV",
    saveToDownloads = "Save to Downloads",
    pasteHint = "💡 Paste directly into chat via clipboard",
    copyAndMinimize = "Copy & Minimize",
    sttSectionHeader = "SPEECH-TO-TEXT (OFFLINE AI)",
    sttTitle = "Local Speech-to-Text",
    sttSubtitle = "On-Device Neural Recognition",
    sttOfflineBadge = "Offline Model",
    sttClear = "Clear",
    sttTapToDictate = "Tap mic to dictate",
    sttListening = "Listening on-device...",
    sttReady = "Local model ready",
    sttPlaceholder = "Recognized speech will appear here... Tap the mic above to speak, or type to edit.",
    sttCopyText = "Copy Text",
    sttShareText = "Share",
    sttCopyTextMinimize = "Copy text & minimize",
    sttNoText = "No text to copy",
    historyTitle = "Recent Recordings",
    historyFiles = { count -> "$count files" },
    historyEmpty = "No recordings yet. Record audio to see them here!",
    delete = "Delete",
    toastMp3Copied = "📋 MP3 copied! Ready to paste into chat",
    toastWavCopied = "📋 Lossless WAV copied to clipboard!",
    toastTextCopied = "📋 Text copied to clipboard!",
    recognizeAudio = "Recognize Audio",
    recognizing = "Recognizing with local model...",
    reRecognize = "Re-recognize",
    sttPrompt = "Speak or dictate message to transcribe into text",
    toastTextRecognized = "✓ Speech recognized! Ready to copy text",
    sttNoSpeechDetected = "No speech detected. Please speak into the mic.",
    sttRecognitionError = "Speech recognition unavailable. Check Google Voice services.",
    useLocalModel = "Use local offline model",
    useLocalModelDesc = "On-device recognition without internet (requires offline language pack)",
    localModelMissingToast = "Offline model not installed. Open Voice Settings to download language pack, or uncheck for Google Cloud.",
    openVoiceSettings = "Voice Settings"
)

val RussianStrings = Strings(
    appSubtitle = "Студия качественной записи MP3 и WAV",
    historyTooltip = "История записей",
    minimizeTooltip = "Свернуть приложение",
    permissionTitle = "Требуется доступ к микрофону",
    permissionDesc = "VoiceDrop необходим микрофон для качественной записи звука.",
    permissionAllow = "Разрешить",
    audioInputSource = "Источник звука",
    micDefault = "Стандартный микрофон",
    micVoiceRecognition = "Распознавание речи (Чистый звук)",
    micCamcorder = "Микрофон камеры",
    micUnprocessed = "Без обработки (Raw PCM)",
    statusStudioReady = "СТУДИЯ ГОТОВА",
    statusRecording = "ЗАПИСЬ MP3 + WAV",
    tapToRecord = "Нажмите для записи голоса",
    tapToStop = "Нажмите для остановки и MP3",
    latestRecording = "ПОСЛЕДНЯЯ ЗАПИСЬ",
    copyMp3 = "Копировать MP3",
    copyWav = "Копировать WAV",
    copied = "Скопировано!",
    share = "Поделиться",
    shareMp3 = "Поделиться MP3",
    shareLosslessWav = "Поделиться Lossless WAV",
    saveToDownloads = "Сохранить в загрузки",
    pasteHint = "💡 Вставьте прямо в чат через буфер обмена",
    copyAndMinimize = "Скопировать и свернуть",
    sttSectionHeader = "РАСПОЗНАВАНИЕ РЕЧИ (OFFLINE AI)",
    sttTitle = "Распознавание речи",
    sttSubtitle = "Локальная нейросеть устройства",
    sttOfflineBadge = "Офлайн модель",
    sttClear = "Очистить",
    sttTapToDictate = "Нажмите микрофон для диктовки",
    sttListening = "Слушаю на устройстве...",
    sttReady = "Локальная модель готова",
    sttPlaceholder = "Распознанный текст появится здесь... Нажмите микрофон выше или начните вводить.",
    sttCopyText = "Копировать текст",
    sttShareText = "Поделиться",
    sttCopyTextMinimize = "Скопировать текст и свернуть",
    sttNoText = "Нет текста для копирования",
    historyTitle = "Недавние записи",
    historyFiles = { count ->
        val mod10 = count % 10
        val mod100 = count % 100
        val word = when {
            mod100 in 11..14 -> "файлов"
            mod10 == 1 -> "файл"
            mod10 in 2..4 -> "файла"
            else -> "файлов"
        }
        "$count $word"
    },
    historyEmpty = "Записей пока нет. Запишите аудио, чтобы увидеть их здесь!",
    delete = "Удалить",
    toastMp3Copied = "📋 MP3 скопирован! Готов к вставке в чат",
    toastWavCopied = "📋 Lossless WAV скопирован в буфер обмена!",
    toastTextCopied = "📋 Текст скопирован в буфер обмена!",
    recognizeAudio = "Распознать аудио",
    recognizing = "Распознавание речи...",
    reRecognize = "Перераспознать",
    sttPrompt = "Продиктуйте или повторите текст для расшифровки",
    toastTextRecognized = "✓ Речь распознана! Текст готов к копированию",
    sttNoSpeechDetected = "Речь не обнаружена. Пожалуйста, говорите в микрофон.",
    sttRecognitionError = "Распознавание недоступно. Проверьте службы речи Google.",
    useLocalModel = "Использовать локальную модель",
    useLocalModelDesc = "Офлайн-распознавание на устройстве (требуется офлайн языковой пакет)",
    localModelMissingToast = "Офлайн-модель не скачана. Загрузите языковой пакет в настройках речи или отключите галочку.",
    openVoiceSettings = "Настройки речи"
)

val LocalAppStrings = compositionLocalOf { EnglishStrings }

class LocaleManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("voicedrop_prefs", Context.MODE_PRIVATE)

    // English is the default language, as requested
    private val _currentLanguage = MutableStateFlow(loadLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _strings = MutableStateFlow(getStringsForLanguage(_currentLanguage.value))
    val strings: StateFlow<Strings> = _strings.asStateFlow()

    private fun loadLanguage(): AppLanguage {
        val saved = prefs.getString("selected_language", null)
        if (saved != null) {
            return AppLanguage.values().find { it.code == saved } ?: AppLanguage.EN
        }
        // Default to English. If user system is specifically Russian and no preference was saved, use English default as requested
        return AppLanguage.EN
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("selected_language", language.code).apply()
        _currentLanguage.value = language
        _strings.value = getStringsForLanguage(language)
    }

    fun toggleLanguage() {
        val nextLang = if (_currentLanguage.value == AppLanguage.EN) AppLanguage.RU else AppLanguage.EN
        setLanguage(nextLang)
    }

    private fun getStringsForLanguage(language: AppLanguage): Strings {
        return when (language) {
            AppLanguage.EN -> EnglishStrings
            AppLanguage.RU -> RussianStrings
        }
    }
}

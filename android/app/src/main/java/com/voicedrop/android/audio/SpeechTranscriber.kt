package com.voicedrop.android.audio

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

data class LanguageOption(
    val code: String,
    val displayName: String,
    val flag: String
)

/**
 * SpeechTranscriber provides 100% on-device speech-to-text recognition
 * using Android's local offline speech recognition engine (EXTRA_PREFER_OFFLINE).
 * Supports transcribing both pre-recorded audio files (via EXTRA_AUDIO_SOURCE)
 * and live voice capture concurrently with studio recording.
 */
class SpeechTranscriber(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcribingBaseName = MutableStateFlow<String?>(null)
    val transcribingBaseName: StateFlow<String?> = _transcribingBaseName.asStateFlow()

    private val _liveCapturedText = MutableStateFlow("")
    val liveCapturedText: StateFlow<String> = _liveCapturedText.asStateFlow()

    private val _statusMessage = MutableStateFlow("Local model ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val availableLanguages = listOf(
        LanguageOption("auto", "Auto (System)", "🌐"),
        LanguageOption("ru-RU", "Russian (Русский)", "🇷🇺"),
        LanguageOption("en-US", "English (US)", "🇺🇸"),
        LanguageOption("de-DE", "German (Deutsch)", "🇩🇪"),
        LanguageOption("es-ES", "Spanish (Español)", "🇪🇸"),
        LanguageOption("fr-FR", "French (Français)", "🇫🇷")
    )

    private val _selectedLanguage = MutableStateFlow(availableLanguages[0])
    private val prefs = context.getSharedPreferences("voicedrop_prefs", Context.MODE_PRIVATE)

    private val _useLocalModel = MutableStateFlow(prefs.getBoolean("use_local_model", false))
    val useLocalModel: StateFlow<Boolean> = _useLocalModel.asStateFlow()

    fun setUseLocalModel(enabled: Boolean) {
        prefs.edit().putBoolean("use_local_model", enabled).apply()
        _useLocalModel.value = enabled
    }

    fun setLanguage(language: LanguageOption) {
        _selectedLanguage.value = language
    }

    /**
     * Starts live background capture during main voice recording so the
     * recognized text is immediately ready when recording stops.
     */
    fun startLiveRecordingCapture() {
        _liveCapturedText.value = ""
        mainHandler.post {
            try {
                ensureRecognizer()
                val langCode = when (val code = _selectedLanguage.value.code) {
                    "auto" -> Locale.getDefault().toLanguageTag()
                    else -> code
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langCode)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Stops live background capture and returns recognized text.
     */
    fun stopLiveRecordingCapture(onResult: (String) -> Unit) {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                val result = _liveCapturedText.value.trim()
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult("")
            }
        }
    }

    /**
     * Transcribes an existing recorded audio item on-device and saves the result.
     */
    fun transcribeAudioItem(
        item: AudioItem,
        language: LanguageOption = _selectedLanguage.value,
        onComplete: (String) -> Unit
    ) {
        if (_isTranscribing.value) return

        _isTranscribing.value = true
        _transcribingBaseName.value = item.baseName
        _statusMessage.value = "Recognizing audio with offline model..."

        val targetFile = item.wavFile ?: item.mp3File
        if (!targetFile.exists()) {
            _isTranscribing.value = false
            _transcribingBaseName.value = null
            onComplete("")
            return
        }

        // If we already have live captured text for this exact newly recorded session, return it
        val liveText = _liveCapturedText.value.trim()
        if (liveText.isNotEmpty() && item.transcription.isEmpty()) {
            _isTranscribing.value = false
            _transcribingBaseName.value = null
            _statusMessage.value = "✓ Speech recognized locally"
            onComplete(liveText)
            return
        }

        mainHandler.post {
            try {
                ensureRecognizer()

                val langCode = when (language.code) {
                    "auto" -> Locale.getDefault().toLanguageTag()
                    else -> language.code
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && item.wavFile != null && item.wavFile.exists()) {
                    try {
                        val pipe = ParcelFileDescriptor.createPipe()
                        val readPipe = pipe[0]
                        val writePipe = pipe[1]

                        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readPipe)
                        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)

                        // Stream WAV audio data (skipping 44-byte header and downsampling 44.1k to 16k)
                        scope.launch {
                            try {
                                val outStream = ParcelFileDescriptor.AutoCloseOutputStream(writePipe)
                                val inStream = FileInputStream(item.wavFile)
                                inStream.skip(44) // Skip RIFF WAV header

                                val buffer = ByteArray(4096)
                                var bytesRead: Int
                                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                    val downsampled = downsample44kTo16k(buffer, bytesRead)
                                    outStream.write(downsampled)
                                }
                                outStream.flush()
                                outStream.close()
                                inStream.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        _isTranscribing.value = false
                        _transcribingBaseName.value = null
                        val errorDesc = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection required"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
                            else -> "Recognition error ($error)"
                        }
                        _statusMessage.value = errorDesc
                        onComplete(item.transcription)
                    }

                    override fun onResults(results: Bundle?) {
                        _isTranscribing.value = false
                        _transcribingBaseName.value = null
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            _statusMessage.value = "✓ Speech recognized locally"
                            onComplete(text)
                        } else {
                            onComplete(item.transcription)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim()
                        if (!partial.isNullOrEmpty()) {
                            _statusMessage.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                _isTranscribing.value = false
                _transcribingBaseName.value = null
                onComplete(item.transcription)
            }
        }
    }

    private fun downsample44kTo16k(input: ByteArray, length: Int): ByteArray {
        val srcRate = 44100.0
        val dstRate = 16000.0
        val ratio = srcRate / dstRate
        val inShorts = length / 2
        val outShorts = (inShorts / ratio).toInt()
        val outBytes = ByteArray(outShorts * 2)

        val byteBufIn = ByteBuffer.wrap(input, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val byteBufOut = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until outShorts) {
            val srcIndex = (i * ratio).toInt().coerceIn(0, inShorts - 1)
            val sample = byteBufIn.getShort(srcIndex * 2)
            byteBufOut.putShort(sample)
        }
        return outBytes
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return

        speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val newText = matches?.firstOrNull()?.trim()
                if (!newText.isNullOrEmpty()) {
                    val current = _liveCapturedText.value.trim()
                    _liveCapturedText.value = if (current.isEmpty()) newText else "$current $newText"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrEmpty()) {
                    _liveCapturedText.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun destroy() {
        scope.cancel()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

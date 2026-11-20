package com.voicedrop.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

enum class MicSource(val label: String, val audioSource: Int) {
    DEFAULT("Standard Mic", MediaRecorder.AudioSource.MIC),
    VOICE_RECOGNITION("Voice Speech (Tuned)", MediaRecorder.AudioSource.VOICE_RECOGNITION),
    CAMCORDER("Camcorder Mic", MediaRecorder.AudioSource.CAMCORDER),
    UNPROCESSED(
        "Studio Raw (Unprocessed)",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
    );

    fun getLocalizedLabel(strings: com.voicedrop.android.util.Strings): String {
        return when (this) {
            DEFAULT -> strings.micDefault
            VOICE_RECOGNITION -> strings.micVoiceRecognition
            CAMCORDER -> strings.micCamcorder
            UNPROCESSED -> strings.micUnprocessed
        }
    }
}

class AudioRecorder(private val context: Context) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _amplitude = MutableStateFlow(0.0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _latestRecording = MutableStateFlow<AudioItem?>(null)
    val latestRecording: StateFlow<AudioItem?> = _latestRecording.asStateFlow()

    private val _selectedMicSource = MutableStateFlow(MicSource.DEFAULT)
    val selectedMicSource: StateFlow<MicSource> = _selectedMicSource.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun setMicSource(source: MicSource) {
        if (!_isRecording.value) {
            _selectedMicSource.value = source
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = max(minBufSize * 2, 4096)

        try {
            audioRecord = AudioRecord(
                _selectedMicSource.value.audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord?.release()
            audioRecord = null
            return false
        }

        _isRecording.value = true
        _recordingDurationMs.value = 0L

        // Prepare output directories & dual output files (MP3 + WAV)
        val recordingsDir = File(context.getExternalFilesDir(null), "VoiceDrop").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val baseName = "Voice_$timeStamp"
        val mp3File = File(recordingsDir, "$baseName.mp3")
        val wavFile = File(recordingsDir, "$baseName.wav")

        recordingJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(1024)
            val mp3Encoder = Mp3Encoder(sampleRate = sampleRate, channels = 1, bitRateKbps = 192, quality = 5)
            val wavWriter = WavWriter(wavFile, sampleRate = sampleRate, channels = 1, bitsPerSample = 16)
            val mp3OutputStream = FileOutputStream(mp3File)

            val startTime = SystemClock.elapsedRealtime()

            // Timer update job
            val timerJob = launch {
                while (isActive && _isRecording.value) {
                    _recordingDurationMs.value = SystemClock.elapsedRealtime() - startTime
                    delay(50)
                }
            }

            try {
                while (isActive && _isRecording.value) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                    if (readCount > 0) {
                        // Calculate RMS amplitude for real-time visualizer
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += shortBuffer[i] * shortBuffer[i]
                        }
                        val rms = sqrt(sum / readCount)
                        val db = if (rms > 1.0) 20 * log10(rms / 32768.0) else -60.0
                        val normAmp = ((db + 60.0) / 60.0).toFloat().coerceIn(0.0f, 1.0f)
                        _amplitude.value = normAmp

                        // 1. Write PCM to WAV file
                        wavWriter.write(shortBuffer, readCount)

                        // 2. Encode PCM to MP3 file (with volume scaling fix)
                        mp3Encoder.encode(shortBuffer, readCount, mp3OutputStream)
                    } else if (readCount < 0) {
                        break
                    }
                }
            } finally {
                timerJob.cancel()
                try {
                    wavWriter.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                try {
                    mp3Encoder.close(mp3OutputStream)
                    mp3OutputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val finalDuration = SystemClock.elapsedRealtime() - startTime
                _recordingDurationMs.value = finalDuration
                _amplitude.value = 0.0f

                if (mp3File.exists() && mp3File.length() > 0) {
                    val mp3Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        mp3File
                    )
                    val wavUri = if (wavFile.exists() && wavFile.length() > 0) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            wavFile
                        )
                    } else null

                    val txtFile = File(recordingsDir, "$baseName.txt")
                    val existingTranscription = if (txtFile.exists()) txtFile.readText().trim() else ""

                    val audioItem = AudioItem(
                        baseName = baseName,
                        mp3File = mp3File,
                        mp3Uri = mp3Uri,
                        wavFile = if (wavFile.exists()) wavFile else null,
                        wavUri = wavUri,
                        durationMs = finalDuration,
                        formattedDuration = formatDuration(finalDuration),
                        formattedMp3Size = formatFileSize(mp3File.length()),
                        formattedWavSize = if (wavFile.exists()) formatFileSize(wavFile.length()) else "",
                        timestamp = mp3File.lastModified(),
                        transcription = existingTranscription
                    )
                    _latestRecording.value = audioItem
                }
            }
        }

        return true
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        _isRecording.value = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val tenths = (millis % 1000) / 100
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun getRecentRecordings(): List<AudioItem> {
        val recordingsDir = File(context.getExternalFilesDir(null), "VoiceDrop")
        if (!recordingsDir.exists() || !recordingsDir.isDirectory) return emptyList()

        val mp3Files = recordingsDir.listFiles { file -> file.isFile && file.name.endsWith(".mp3") } ?: return emptyList()

        return mp3Files.sortedByDescending { it.lastModified() }.map { mp3 ->
            val baseName = mp3.nameWithoutExtension
            val wav = File(recordingsDir, "$baseName.wav")
            val mp3Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                mp3
            )
            val wavUri = if (wav.exists()) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    wav
                )
            } else null

            val txtFile = File(recordingsDir, "$baseName.txt")
            val transcription = if (txtFile.exists()) txtFile.readText().trim() else ""

            AudioItem(
                baseName = baseName,
                mp3File = mp3,
                mp3Uri = mp3Uri,
                wavFile = if (wav.exists()) wav else null,
                wavUri = wavUri,
                durationMs = 0L,
                formattedDuration = "",
                formattedMp3Size = formatFileSize(mp3.length()),
                formattedWavSize = if (wav.exists()) formatFileSize(wav.length()) else "",
                timestamp = mp3.lastModified(),
                transcription = transcription
            )
        }
    }

    fun saveTranscription(item: AudioItem, text: String) {
        try {
            val recordingsDir = File(context.getExternalFilesDir(null), "VoiceDrop")
            val txtFile = File(recordingsDir, "${item.baseName}.txt")
            txtFile.writeText(text)
            item.transcription = text
            if (_latestRecording.value?.baseName == item.baseName) {
                _latestRecording.value = item.copy(transcription = text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

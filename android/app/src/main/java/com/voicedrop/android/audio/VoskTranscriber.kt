package com.voicedrop.android.audio

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * VoskTranscriber provides 100% on-device offline speech recognition using
 * the embedded Kaldi/Vosk model. It decodes recorded WAV audio directly
 * without any Google UI, internet connection, or external service dependencies.
 */
class VoskTranscriber(private val context: Context) {

    private var ruModel: Model? = null
    private var enModel: Model? = null

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _isDownloadingEn = MutableStateFlow(false)
    val isDownloadingEn: StateFlow<Boolean> = _isDownloadingEn.asStateFlow()

    private val _downloadEnProgress = MutableStateFlow(0f)
    val downloadEnProgress: StateFlow<Float> = _downloadEnProgress.asStateFlow()

    private val _statusText = MutableStateFlow("Vosk local model ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun isRussianModelReady(): Boolean {
        val targetDir = File(context.filesDir, "model-ru")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")
        return (readyFlag.exists() && finalMdl.exists()) || ruModel != null
    }

    fun isEnglishModelReady(): Boolean {
        val targetDir = File(context.filesDir, "model-en")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")
        return (readyFlag.exists() && finalMdl.exists()) || enModel != null
    }

    suspend fun prepareRussianModel(): Boolean = withContext(Dispatchers.IO) {
        if (ruModel != null) return@withContext true
        val targetDir = File(context.filesDir, "model-ru")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")

        if (!readyFlag.exists() || !finalMdl.exists()) {
            _isExtracting.value = true
            _statusText.value = "Unpacking Russian local model..."
            try {
                targetDir.mkdirs()
                copyAssetFolder(context.assets, "model-ru", targetDir)
                readyFlag.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
                _isExtracting.value = false
                _statusText.value = "Failed to unpack model: ${e.message}"
                return@withContext false
            } finally {
                _isExtracting.value = false
            }
        }

        return@withContext try {
            ruModel = Model(targetDir.absolutePath)
            _statusText.value = "Russian local model ready"
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _statusText.value = "Error loading model: ${e.message}"
            false
        }
    }

    suspend fun downloadEnglishModel(onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (enModel != null) return@withContext true
        val targetDir = File(context.filesDir, "model-en")
        val readyFlag = File(targetDir, ".ready")
        if (readyFlag.exists()) {
            return@withContext try {
                enModel = Model(targetDir.absolutePath)
                true
            } catch (e: Exception) {
                false
            }
        }

        _isDownloadingEn.value = true
        _statusText.value = "Downloading English offline model (40 MB)..."
        val zipUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

        try {
            val url = URL(zipUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val totalBytes = connection.contentLength.toFloat()
            var downloadedBytes = 0L

            val tempZip = File(context.cacheDir, "model-en.zip")
            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val prog = downloadedBytes / totalBytes
                            _downloadEnProgress.value = prog
                            onProgress(prog)
                        }
                    }
                }
            }

            // Extract zip
            targetDir.mkdirs()
            ZipInputStream(FileInputStream(tempZip)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val cleanName = entry.name.substringAfter("/")
                    if (cleanName.isNotEmpty()) {
                        val outFile = File(targetDir, cleanName)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                zipIn.copyTo(out)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            tempZip.delete()
            readyFlag.createNewFile()

            enModel = Model(targetDir.absolutePath)
            _statusText.value = "English local model ready"
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _statusText.value = "Download failed: ${e.message}"
            false
        } finally {
            _isDownloadingEn.value = false
        }
    }

    /**
     * Transcribes a recorded WAV file directly on-device using the offline model.
     * Downsamples 44.1 kHz 16-bit PCM to 16 kHz and returns decoded text.
     */
    suspend fun transcribeWav(
        wavFile: File,
        isRussian: Boolean = true,
        onProgress: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (!wavFile.exists()) return@withContext ""

        val model = if (isRussian) {
            if (!prepareRussianModel()) return@withContext ""
            ruModel
        } else {
            if (!isEnglishModelReady()) {
                if (!downloadEnglishModel { }) return@withContext ""
            }
            enModel
        } ?: return@withContext ""

        return@withContext try {
            val recognizer = Recognizer(model, 16000.0f)
            recognizer.use { rec ->
                FileInputStream(wavFile).use { inStream ->
                    inStream.skip(44) // Skip RIFF WAV header (44 bytes)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        val pcm16k = downsample44kTo16k(buffer, bytesRead)
                        if (rec.acceptWaveForm(pcm16k, pcm16k.size)) {
                            val partialJson = rec.result
                            val partialText = try {
                                JSONObject(partialJson).optString("text", "")
                            } catch (e: Exception) {
                                ""
                            }
                            if (partialText.isNotBlank()) {
                                onProgress?.invoke(partialText)
                            }
                        }
                    }
                }
                val finalJson = rec.finalResult
                val recognizedText = try {
                    JSONObject(finalJson).optString("text", "")
                } catch (e: Exception) {
                    ""
                }
                recognizedText.trim()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toFile: File) {
        val files = assetManager.list(fromAssetPath) ?: return
        if (files.isEmpty()) {
            assetManager.open(fromAssetPath).use { input ->
                FileOutputStream(toFile).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            toFile.mkdirs()
            for (file in files) {
                val subAsset = "$fromAssetPath/$file"
                val subTarget = File(toFile, file)
                copyAssetFolder(assetManager, subAsset, subTarget)
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

    fun destroy() {
        try {
            ruModel?.close()
            enModel?.close()
            ruModel = null
            enModel = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

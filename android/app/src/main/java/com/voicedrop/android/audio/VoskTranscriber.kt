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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

/**
 * VoskTranscriber provides 100% on-device offline speech recognition using
 * Vosk/Kaldi models. Models are downloaded on-demand and cached in internal storage,
 * keeping the initial APK lightweight (< 15 MB) while offering full offline capability.
 */
class VoskTranscriber(private val context: Context) {

    companion object {
        const val VOSK_RU_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        const val VOSK_EN_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }

    private var ruModel: Model? = null
    private var enModel: Model? = null

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _statusText = MutableStateFlow("Vosk local model ready")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun isRussianModelReady(): Boolean {
        if (ruModel != null) return true
        val targetDir = File(context.filesDir, "model-ru")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")
        return readyFlag.exists() && finalMdl.exists()
    }

    fun isEnglishModelReady(): Boolean {
        if (enModel != null) return true
        val targetDir = File(context.filesDir, "model-en")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")
        return readyFlag.exists() && finalMdl.exists()
    }

    suspend fun prepareRussianModel(onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (ruModel != null) return@withContext true
        val targetDir = File(context.filesDir, "model-ru")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")

        if (readyFlag.exists() && finalMdl.exists()) {
            return@withContext try {
                ruModel = Model(targetDir.absolutePath)
                _statusText.value = "Russian local model ready"
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        // 1. Fallback: Check if bundled in assets
        if (hasAssetFolder("model-ru")) {
            _isExtracting.value = true
            _statusText.value = "Unpacking Russian local model..."
            try {
                targetDir.mkdirs()
                copyAssetFolder(context.assets, "model-ru", targetDir)
                readyFlag.createNewFile()
                ruModel = Model(targetDir.absolutePath)
                _statusText.value = "Russian local model ready"
                return@withContext true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isExtracting.value = false
            }
        }

        // 2. Download from web on first use
        return@withContext downloadModel(
            url = VOSK_RU_MODEL_URL,
            targetDirName = "model-ru",
            languageLabel = "Russian",
            onProgress = onProgress
        ) { model ->
            ruModel = model
        }
    }

    suspend fun prepareEnglishModel(onProgress: ((Float) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (enModel != null) return@withContext true
        val targetDir = File(context.filesDir, "model-en")
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")

        if (readyFlag.exists() && finalMdl.exists()) {
            return@withContext try {
                enModel = Model(targetDir.absolutePath)
                _statusText.value = "English local model ready"
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        return@withContext downloadModel(
            url = VOSK_EN_MODEL_URL,
            targetDirName = "model-en",
            languageLabel = "English",
            onProgress = onProgress
        ) { model ->
            enModel = model
        }
    }

    private suspend fun downloadModel(
        url: String,
        targetDirName: String,
        languageLabel: String,
        onProgress: ((Float) -> Unit)? = null,
        onLoaded: (Model) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, targetDirName)
        val readyFlag = File(targetDir, ".ready")
        val finalMdl = File(targetDir, "am/final.mdl")

        if (readyFlag.exists() && finalMdl.exists()) {
            return@withContext try {
                val model = Model(targetDir.absolutePath)
                onLoaded(model)
                true
            } catch (e: Exception) {
                false
            }
        }

        _isDownloading.value = true
        _downloadProgress.value = 0f
        _statusText.value = "Downloading $languageLabel offline model (~45 MB)..."

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.instanceFollowRedirects = true
            connection.connect()

            val totalBytes = connection.contentLength.toFloat()
            var downloadedBytes = 0L

            val tempZip = File(context.cacheDir, "$targetDirName.zip")
            connection.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val prog = (downloadedBytes / totalBytes).coerceIn(0f, 1f)
                            _downloadProgress.value = prog
                            onProgress?.invoke(prog)
                        }
                    }
                }
            }

            _isExtracting.value = true
            _statusText.value = "Unpacking $languageLabel model..."
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

            val model = Model(targetDir.absolutePath)
            onLoaded(model)
            _statusText.value = "$languageLabel offline model ready"
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _statusText.value = "Model download error: ${e.message}"
            false
        } finally {
            _isDownloading.value = false
            _isExtracting.value = false
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

        val modelReady = if (isRussian) {
            prepareRussianModel()
        } else {
            prepareEnglishModel()
        }
        if (!modelReady) return@withContext ""

        val model = (if (isRussian) ruModel else enModel) ?: return@withContext ""

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

    private fun hasAssetFolder(path: String): Boolean {
        return try {
            val list = context.assets.list(path)
            list != null && list.isNotEmpty() && list.contains("am")
        } catch (e: Exception) {
            false
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

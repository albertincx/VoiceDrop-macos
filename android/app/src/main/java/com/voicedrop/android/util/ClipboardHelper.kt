package com.voicedrop.android.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.voicedrop.android.audio.AudioItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ClipboardHelper {

    /**
     * Copies MP3 format audio file to the system clipboard.
     * Web chats (Telegram Web, Slack, Chrome, ChatGPT) can paste it directly as an MP3 audio file.
     */
    fun copyMp3ToClipboard(context: Context, item: AudioItem): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false

            val mimeTypes = arrayOf("audio/mpeg", "audio/mp3", "audio/*")
            val clipDescription = ClipDescription("VoiceDrop MP3: ${item.mp3File.name}", mimeTypes)
            val clipItem = ClipData.Item(item.mp3Uri)
            val clipData = ClipData(clipDescription, clipItem)

            clipboard.setPrimaryClip(clipData)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(
                    context,
                    "📋 MP3 copied! Ready to paste into chat",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to copy MP3: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies WAV format lossless audio file to the system clipboard.
     */
    fun copyWavToClipboard(context: Context, item: AudioItem): Boolean {
        val wavUri = item.wavUri
        if (wavUri == null || item.wavFile == null || !item.wavFile.exists()) {
            Toast.makeText(context, "WAV format not available for this recording", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false

            val mimeTypes = arrayOf("audio/wav", "audio/x-wav", "audio/*")
            val clipDescription = ClipDescription("VoiceDrop WAV: ${item.wavFile.name}", mimeTypes)
            val clipItem = ClipData.Item(wavUri)
            val clipData = ClipData(clipDescription, clipItem)

            clipboard.setPrimaryClip(clipData)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(
                    context,
                    "📋 Lossless WAV copied to clipboard!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to copy WAV: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Copies recognized / transcribed text to the system clipboard.
     */
    fun copyTextToClipboard(context: Context, text: String): Boolean {
        if (text.isBlank()) {
            Toast.makeText(context, "No text to copy", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            val clip = ClipData.newPlainText("VoiceDrop Transcription", text)
            clipboard.setPrimaryClip(clip)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(
                    context,
                    "📋 Text copied to clipboard!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to copy text: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Opens Android System Share sheet for sending recognized text.
     */
    fun shareText(context: Context, text: String) {
        if (text.isBlank()) return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "VoiceDrop Transcription")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Text via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot share text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens Android System Share sheet for sending either MP3 or WAV audio
     */
    fun shareAudio(context: Context, item: AudioItem, preferWav: Boolean = false) {
        try {
            val file = if (preferWav && item.wavFile != null && item.wavFile.exists()) item.wavFile else item.mp3File
            val uri = if (preferWav && item.wavUri != null) item.wavUri else item.mp3Uri
            val mimeType = if (preferWav) "audio/wav" else "audio/mpeg"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TEXT, "Audio recorded via VoiceDrop")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Send Audio via"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Exports recordings (MP3 and/or WAV) to the device's public Downloads directory.
     */
    fun saveToDownloads(context: Context, item: AudioItem): Boolean {
        return try {
            val filesToExport = mutableListOf<Pair<File, String>>()
            if (item.mp3File.exists()) {
                filesToExport.add(item.mp3File to "audio/mpeg")
            }
            if (item.wavFile != null && item.wavFile.exists()) {
                filesToExport.add(item.wavFile to "audio/wav")
            }

            for ((file, mime) in filesToExport) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VoiceDrop")
                    }
                    val resolver = context.contentResolver
                    val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                    resolver.openOutputStream(targetUri)?.use { out ->
                        FileInputStream(file).use { input -> input.copyTo(out) }
                    }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val voiceDropDir = File(downloadsDir, "VoiceDrop").apply { mkdirs() }
                    val targetFile = File(voiceDropDir, file.name)
                    FileInputStream(file).use { input ->
                        FileOutputStream(targetFile).use { out -> input.copyTo(out) }
                    }
                }
            }

            Toast.makeText(
                context,
                "✓ Saved to Downloads/VoiceDrop/ (${item.baseName})",
                Toast.LENGTH_LONG
            ).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

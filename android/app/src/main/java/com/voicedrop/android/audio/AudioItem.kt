package com.voicedrop.android.audio

import android.net.Uri
import java.io.File

data class AudioItem(
    val baseName: String,
    val mp3File: File,
    val mp3Uri: Uri,
    val wavFile: File?,
    val wavUri: Uri?,
    val durationMs: Long,
    val formattedDuration: String,
    val formattedMp3Size: String,
    val formattedWavSize: String,
    val timestamp: Long,
    var transcription: String = ""
)

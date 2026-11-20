package com.voicedrop.android.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * High-performance 16-bit PCM Mono WAV writer.
 * Writes standard RIFF WAVE header with accurate sample counts and little-endian encoding.
 */
class WavWriter(
    private val outputFile: File,
    private val sampleRate: Int = 44100,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16
) {
    private val raf = RandomAccessFile(outputFile, "rw")
    private var totalAudioBytes: Long = 0

    init {
        // Reserve 44-byte RIFF/WAVE header
        raf.write(ByteArray(44))
    }

    fun write(samples: ShortArray, readCount: Int) {
        if (readCount <= 0) return
        val byteBuffer = ByteArray(readCount * 2)
        var byteIdx = 0
        for (i in 0 until readCount) {
            val s = samples[i].toInt()
            byteBuffer[byteIdx++] = (s and 0xFF).toByte()
            byteBuffer[byteIdx++] = ((s shr 8) and 0xFF).toByte()
        }
        raf.write(byteBuffer)
        totalAudioBytes += byteBuffer.size
    }

    fun close() {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val totalDataLen = totalAudioBytes
        val totalFileLen = totalDataLen + 36

        raf.seek(0)
        // 0..3 "RIFF"
        raf.writeBytes("RIFF")
        // 4..7 Chunk size (little endian)
        raf.writeInt(Integer.reverseBytes(totalFileLen.toInt()))
        // 8..11 "WAVE"
        raf.writeBytes("WAVE")
        // 12..15 "fmt "
        raf.writeBytes("fmt ")
        // 16..19 Subchunk1Size = 16 for PCM
        raf.writeInt(Integer.reverseBytes(16))
        // 20..21 AudioFormat = 1 (PCM)
        raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        // 22..23 NumChannels
        raf.writeShort(java.lang.Short.reverseBytes(channels).toInt())
        // 24..27 SampleRate
        raf.writeInt(Integer.reverseBytes(sampleRate))
        // 28..31 ByteRate
        raf.writeInt(Integer.reverseBytes(byteRate))
        // 32..33 BlockAlign
        raf.writeShort(java.lang.Short.reverseBytes(blockAlign).toInt())
        // 34..35 BitsPerSample
        raf.writeShort(java.lang.Short.reverseBytes(bitsPerSample).toInt())
        // 36..39 "data"
        raf.writeBytes("data")
        // 40..43 Subchunk2Size
        raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))

        raf.close()
    }
}

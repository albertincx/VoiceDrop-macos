package com.voicedrop.android.audio

import de.sciss.jump3r.mp3.*
import de.sciss.jump3r.mpg.*
import java.io.OutputStream

/**
 * Pure Kotlin/Java MP3 encoder wrapping Jump3r LAME engine.
 * Encodes 16-bit PCM mono audio into standard 192 kbps MP3 ADTS frames.
 * Does not require NDK or C/C++ binaries, 100% portable across all Android architectures.
 */
class Mp3Encoder(
    private val sampleRate: Int = 44100,
    private val channels: Int = 1,
    private val bitRateKbps: Int = 192,
    private val quality: Int = 5
) {
    private val lame = Lame()
    private val gfp: LameGlobalFlags
    private val mp3Buffer = ByteArray((1.25 * sampleRate + 7200).toInt())
    private var isInitialized = false

    init {
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val mpg = MPGLib()
        val gaud = GetAudio()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, p)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        gfp = lame.lame_init()
        gfp.num_channels = channels
        gfp.in_samplerate = sampleRate
        gfp.out_samplerate = sampleRate
        gfp.brate = bitRateKbps
        gfp.quality = quality
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.STEREO

        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = true

        val res = lame.lame_init_params(gfp)
        if (res >= 0) {
            isInitialized = true
        }
    }

    /**
     * Encodes 16-bit PCM short samples to MP3 bytes and writes to OutputStream.
     * Note: lame_encode_buffer_int internally multiplies each integer sample by (1.0f / 65536.0f),
     * so 16-bit short samples MUST be shifted left by 16 bits (samples[i].toInt() shl 16)
     * to preserve full dynamic range and prevent attenuation/silence.
     */
    fun encode(samples: ShortArray, readCount: Int, outputStream: OutputStream): Int {
        if (!isInitialized || readCount <= 0) return 0
        val intSamples = IntArray(readCount) { i -> samples[i].toInt() shl 16 }
        val encodedBytes = lame.lame_encode_buffer_int(
            gfp,
            intSamples,
            intSamples,
            readCount,
            mp3Buffer,
            0,
            mp3Buffer.size
        )
        if (encodedBytes > 0) {
            outputStream.write(mp3Buffer, 0, encodedBytes)
        }
        return encodedBytes
    }

    /**
     * Flushes remaining frames and closes encoder
     */
    fun close(outputStream: OutputStream) {
        if (!isInitialized) return
        val flushBytes = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
        if (flushBytes > 0) {
            outputStream.write(mp3Buffer, 0, flushBytes)
        }
        outputStream.flush()
        lame.lame_close(gfp)
        isInitialized = false
    }
}

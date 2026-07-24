package com.nothingai.capture.stt

import android.content.Context
import java.io.File

/**
 * On-device speech-to-text backed by whisper.cpp (vendored, compiled via the NDK).
 *
 * Fully offline: the quantized model ships in assets and is copied to app storage
 * on first use. Transcription runs entirely on the CPU with no network access.
 */
class WhisperTranscriber(private val context: Context) {

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }

        private const val MODEL_ASSET = "models/ggml-base-q5_1.bin"
        private const val MODEL_FILE = "ggml-base-q5_1.bin"
    }

    // JNI — signatures must match the exports in whisper_jni.cpp exactly.
    private external fun initContext(modelPath: String): Long
    private external fun fullTranscribe(ctx: Long, numThreads: Int, audioData: FloatArray)
    private external fun getTextSegmentCount(ctx: Long): Int
    private external fun getTextSegment(ctx: Long, index: Int): String
    private external fun freeContext(ctx: Long)

    /** Copies the bundled model asset into filesDir/models on first call; returns the file. */
    fun ensureModel(): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val out = File(dir, MODEL_FILE)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(MODEL_ASSET).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return out
    }

    /**
     * Blocking transcription of a 16 kHz mono PCM16 WAV. Handles both a plain
     * 44-byte header (as produced by AudioRecorder) and files carrying extra RIFF
     * chunks before `data` (e.g. the bundled JFK sample has a LIST chunk).
     * Returns the trimmed transcript.
     */
    fun transcribe(wav: File): String {
        val ctx = initContext(ensureModel().absolutePath)
        check(ctx != 0L) { "whisper init failed" }
        try {
            val samples = readWavToFloat(wav)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            fullTranscribe(ctx, threads, samples)
            val sb = StringBuilder()
            for (i in 0 until getTextSegmentCount(ctx)) sb.append(getTextSegment(ctx, i))
            return sb.toString().trim()
        } finally {
            freeContext(ctx)
        }
    }

    /**
     * Decodes little-endian PCM16 samples to normalized floats. Locates the RIFF
     * `data` chunk by walking the chunk list (falling back to a 44-byte header),
     * so files with extra chunks (LIST, fact, ...) before the audio are read correctly.
     */
    private fun readWavToFloat(wav: File): FloatArray {
        val bytes = wav.readBytes()
        val dataChunk = findDataChunk(bytes)
        val pcmStart = dataChunk.first
        // Clamp to the file and to an even byte count (whole PCM16 samples).
        val pcmEnd = minOf(pcmStart + dataChunk.second, bytes.size)
        val sampleCount = ((pcmEnd - pcmStart) / 2).coerceAtLeast(0)
        val out = FloatArray(sampleCount)
        var j = pcmStart
        for (i in 0 until sampleCount) {
            val lo = bytes[j].toInt() and 0xff
            val hi = bytes[j + 1].toInt()
            val s = (hi shl 8) or lo
            out[i] = s / 32768f
            j += 2
        }
        return out
    }

    /** Returns (offset, length) of the PCM `data` chunk, or (44, rest) if not found. */
    private fun findDataChunk(bytes: ByteArray): Pair<Int, Int> {
        // Valid RIFF/WAVE header is 12 bytes; chunks follow from offset 12.
        if (bytes.size < 12 ||
            bytes[0].toInt() != 'R'.code || bytes[1].toInt() != 'I'.code ||
            bytes[2].toInt() != 'F'.code || bytes[3].toInt() != 'F'.code
        ) {
            return 44 to (bytes.size - 44).coerceAtLeast(0)
        }
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = (bytes[pos + 4].toInt() and 0xff) or
                ((bytes[pos + 5].toInt() and 0xff) shl 8) or
                ((bytes[pos + 6].toInt() and 0xff) shl 16) or
                ((bytes[pos + 7].toInt() and 0xff) shl 24)
            val body = pos + 8
            if (id == "data") return body to size
            // Chunks are word-aligned: an odd size is padded with one byte.
            pos = body + size + (size and 1)
        }
        return 44 to (bytes.size - 44).coerceAtLeast(0)
    }
}

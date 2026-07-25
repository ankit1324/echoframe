package com.nothingai.capture.stt

import android.content.Context
import java.io.File

/**
 * On-device speech-to-text backed by whisper.cpp (vendored, compiled via the NDK).
 *
 * Models are NOT bundled with the app. Each quantized model (tiny/base/small/large)
 * is downloaded on demand by [ModelDownloaderWorker] into `filesDir/models/` when the
 * user picks it (Settings or the first-run setup wizard). Transcription itself still
 * runs entirely on-device/CPU with no network access — only fetching the model weights
 * requires connectivity. If the currently-selected model hasn't been downloaded yet,
 * [getModelFile] throws [ModelNotDownloadedException] instead of transcribing.
 */
class WhisperTranscriber(private val context: Context) {

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }

    // JNI — signatures must match the exports in whisper_jni.cpp exactly.
    private external fun initContext(modelPath: String): Long
    private external fun fullTranscribe(ctx: Long, numThreads: Int, audioData: FloatArray, language: String?)
    private external fun getTextSegmentCount(ctx: Long): Int
    private external fun getTextSegment(ctx: Long, index: Int): String
    private external fun freeContext(ctx: Long)

    // Reuse a single context across transcriptions so the model stays hot in RAM.
    // Free it on process death; a new context is re-created automatically if needed.
    private var cachedContext: Long = 0
    private var cachedModelPath: String = ""
    @Synchronized private fun getContext(): Long {
        val path = getModelFile().absolutePath
        if (cachedContext != 0L && cachedModelPath == path) return cachedContext
        if (cachedContext != 0L) {
            try { freeContext(cachedContext) } catch (_: Exception) {}
        }
        cachedContext = initContext(path)
        cachedModelPath = path
        return cachedContext
    }

    /**
     * Resolves the currently-selected model (pref `whisper_model`, default "tiny") to its
     * file in `filesDir/models/`. The model is downloaded on demand by
     * [ModelDownloaderWorker] — never bundled — so this only returns a file that has
     * actually completed downloading.
     *
     * [ModelDownloaderWorker] only ever creates the final path via an atomic rename of a
     * fully-downloaded temp file, so mere existence of the file is sufficient proof of a
     * complete download — a process death or low-storage failure mid-download leaves only
     * the (ignored) temp file behind, never a truncated file at the final path.
     *
     * @throws ModelNotDownloadedException if the selected model's file doesn't exist yet.
     */
    fun getModelFile(): File {
        val prefs = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
        val modelId = prefs.getString("whisper_model", "tiny") ?: "tiny"
        val dir = File(context.filesDir, "models").apply { mkdirs() }

        val requestedFile = File(dir, "ggml-$modelId.bin")
        if (requestedFile.exists()) return requestedFile

        throw ModelNotDownloadedException(modelId)
    }

    /**
     * Blocking transcription of a 16 kHz mono PCM16 WAV. Handles both a plain
     * 44-byte header (as produced by AudioRecorder) and files carrying extra RIFF
     * chunks before `data` (e.g. the bundled JFK sample has a LIST chunk).
     * Returns the trimmed transcript.
     */
    fun transcribe(wav: File): String {
        val ctx = getContext()
        check(ctx != 0L) { "whisper init failed" }
        try {
            val samples = readWavToFloat(wav)
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            fullTranscribe(ctx, threads, samples, selectedLanguage())
            val sb = StringBuilder()
            for (i in 0 until getTextSegmentCount(ctx)) sb.append(getTextSegment(ctx, i))
            return sb.toString().trim()
        } finally {
            // Context is kept alive in cachedContext
        }
    }

    /**
     * Maps the `language` setting to a whisper language code: "English" locks
     * decoding to "en" (faster, more accurate for pure English); anything else —
     * including the default "Multilingual (Hinglish)" — returns null so whisper
     * auto-detects the spoken language.
     */
    private fun selectedLanguage(): String? {
        val prefs = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
        return if (prefs.getString("language", "Multilingual (Hinglish)") == "English") "en" else null
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

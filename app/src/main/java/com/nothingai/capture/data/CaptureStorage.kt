package com.nothingai.capture.data

import android.content.Context
import android.graphics.Bitmap
import com.nothingai.capture.util.Wav
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CaptureStorage(context: Context) {
    private val root = File(context.filesDir, "captures")
    private fun ensureDir(id: String): File = File(root, id).apply { mkdirs() }

    fun dir(id: String): File = File(root, id)
    fun screenshotFile(id: String) = File(ensureDir(id), SCREENSHOT_NAME)
    fun audioFile(id: String) = File(ensureDir(id), AUDIO_NAME)
    fun transcriptFile(id: String) = File(ensureDir(id), TRANSCRIPT_NAME)

    /** Creates the capture directory. Call once off the main thread before writing into it. */
    fun prepareDir(id: String): File = ensureDir(id)

    // Read-only path accessors. Unlike the three above, these never create the directory, so they
    // are safe on the main thread and never leave an empty dir behind for a capture being discarded.
    fun screenshotPath(id: String) = File(dir(id), SCREENSHOT_NAME)
    fun audioPath(id: String) = File(dir(id), AUDIO_NAME)

    /** True when a screenshot was actually written (not merely a zero-length placeholder). */
    fun hasScreenshot(id: String): Boolean = screenshotPath(id).length() > 0

    /**
     * True when audio was actually recorded. Deliberately checks length, not existence: the recorder
     * writes a WAV header before it opens the microphone, so a mic failure still leaves a file.
     */
    fun hasAudio(id: String): Boolean = Wav.hasAudio(audioPath(id).length())

    /** True when this capture has anything worth showing or analysing. */
    fun hasPayload(id: String): Boolean = hasScreenshot(id) || hasAudio(id)

    fun saveScreenshot(id: String, bitmap: Bitmap) {
        val destination = screenshotFile(id)
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        FileOutputStream(temporary).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Screenshot encoding failed" }
        }
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    fun saveTranscript(id: String, text: String) {
        val destination = transcriptFile(id)
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(text)
        try {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    fun deleteCapture(id: String) { dir(id).deleteRecursively() }

    private companion object {
        const val SCREENSHOT_NAME = "screenshot.png"
        const val AUDIO_NAME = "audio.wav"
        const val TRANSCRIPT_NAME = "transcript.txt"
    }
}

package com.nothingai.capture.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

class CaptureStorage(context: Context) {
    private val root = File(context.filesDir, "captures")
    private fun ensureDir(id: String): File = File(root, id).apply { mkdirs() }

    fun dir(id: String): File = File(root, id)
    fun screenshotFile(id: String) = File(ensureDir(id), "screenshot.png")
    fun audioFile(id: String) = File(ensureDir(id), "audio.wav")
    fun transcriptFile(id: String) = File(ensureDir(id), "transcript.txt")

    fun saveScreenshot(id: String, bitmap: Bitmap) {
        FileOutputStream(screenshotFile(id)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun saveTranscript(id: String, text: String) { transcriptFile(id).writeText(text) }
    fun deleteCapture(id: String) { dir(id).deleteRecursively() }
}

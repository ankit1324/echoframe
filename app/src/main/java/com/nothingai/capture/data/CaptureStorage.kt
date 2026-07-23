package com.nothingai.capture.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

class CaptureStorage(context: Context) {
    private val root = File(context.filesDir, "captures")

    fun dir(id: String): File = File(root, id)
    fun screenshotFile(id: String) = File(dir(id), "screenshot.png")
    fun audioFile(id: String) = File(dir(id), "audio.wav")
    fun transcriptFile(id: String) = File(dir(id), "transcript.txt")

    fun saveScreenshot(id: String, bitmap: Bitmap) {
        dir(id).mkdirs()
        FileOutputStream(screenshotFile(id)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    fun saveTranscript(id: String, text: String) {
        dir(id).mkdirs()
        transcriptFile(id).writeText(text)
    }
    fun deleteCapture(id: String) { dir(id).deleteRecursively() }
}

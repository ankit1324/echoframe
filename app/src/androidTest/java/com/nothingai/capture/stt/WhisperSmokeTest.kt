package com.nothingai.capture.stt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WhisperSmokeTest {

    @Test
    fun transcribesDownloadedTinyModelClip() {
        // App-under-test context: serves filesDir (models are downloaded on demand, never
        // bundled, so this test seeds one directly) and the notes_settings prefs that
        // WhisperTranscriber.getModelFile() reads to pick a model.
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        // Instrumentation (test APK) context: serves the androidTest assets — the jfk_16k.wav
        // clip, and a real ggml-tiny-q5_1 model standing in for a completed on-demand download.
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // Seed filesDir/models/ggml-tiny.bin exactly as ModelDownloaderWorker would, and
        // select it, so WhisperTranscriber resolves it via the same path production uses
        // instead of throwing ModelNotDownloadedException.
        val modelsDir = File(appCtx.filesDir, "models").apply { mkdirs() }
        val modelFile = File(modelsDir, "ggml-tiny.bin")
        if (!modelFile.exists()) {
            val tmp = File(modelsDir, "ggml-tiny.bin.tmp")
            testCtx.assets.open("ggml-tiny-q5_1.bin").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            check(tmp.renameTo(modelFile)) { "failed to stage tiny model for test" }
        }
        appCtx.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
            .edit().putString("whisper_model", "tiny").apply()

        val wav = File(appCtx.cacheDir, "jfk.wav")
        testCtx.assets.open("jfk_16k.wav").use { i -> wav.outputStream().use { i.copyTo(it) } }

        val text = WhisperTranscriber(appCtx).transcribe(wav).lowercase()

        assertThat(text).contains("country")
    }
}

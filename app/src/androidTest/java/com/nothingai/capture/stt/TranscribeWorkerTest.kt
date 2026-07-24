package com.nothingai.capture.stt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStatus
import com.nothingai.capture.data.CaptureStorage
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class TranscribeWorkerTest {
    @Test fun transcribesAndMarksDone() = runBlocking {
        // App-under-test context: serves filesDir/CaptureStorage/Room, matching what
        // TranscribeWorker itself will use via applicationContext.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Instrumentation (test APK) context: serves the androidTest asset jfk_16k.wav
        // (it lives in app/src/androidTest/assets, not the app's own assets — see
        // WhisperSmokeTest for the same appCtx/testCtx split).
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        // Models are downloaded on demand, never bundled, so seed filesDir/models/ggml-tiny.bin
        // exactly as ModelDownloaderWorker would (from the androidTest-only ggml-tiny-q5_1.bin
        // asset) and select it, matching the same model-resolution path production uses.
        val modelsDir = File(ctx.filesDir, "models").apply { mkdirs() }
        val modelFile = File(modelsDir, "ggml-tiny.bin")
        if (!modelFile.exists()) {
            val tmp = File(modelsDir, "ggml-tiny.bin.tmp")
            testCtx.assets.open("ggml-tiny-q5_1.bin").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            check(tmp.renameTo(modelFile)) { "failed to stage tiny model for test" }
        }
        ctx.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
            .edit().putString("whisper_model", "tiny").apply()

        val id = "20260101-000009-000"
        val storage = CaptureStorage(ctx)
        // seed a real 16k mono wav from test asset
        testCtx.assets.open("jfk_16k.wav").use { i -> storage.audioFile(id).outputStream().use { i.copyTo(it) } }
        val dao = CaptureDatabase.get(ctx).captureDao()
        dao.upsert(Capture(id, 1, true, 1000, null, CaptureStatus.PENDING))

        val worker = TestListenableWorkerBuilder<TranscribeWorker>(ctx)
            .setInputData(workDataOf(TranscribeWorker.KEY_ID to id)).build()
        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
        val c = dao.get(id)!!
        assertThat(c.status).isEqualTo(CaptureStatus.DONE)
        assertThat(c.transcript!!.lowercase()).contains("country")
        assertThat(storage.transcriptFile(id).exists()).isTrue()
    }
}

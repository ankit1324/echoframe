package com.nothingai.capture.stt

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

class TranscribeWorkerTest {
    @Test
    fun savesAudioFallbackWhenScreenshotIsUnavailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "20260101-000009-000"
        val storage = CaptureStorage(context)
        storage.deleteCapture(id)
        storage.audioFile(id).writeBytes(byteArrayOf(1))
        val dao = CaptureDatabase.get(context).captureDao()
        dao.upsert(Capture(id, 1, false, 1000, null, CaptureStatus.PENDING))

        val worker = TestListenableWorkerBuilder<TranscribeWorker>(context)
            .setInputData(workDataOf(TranscribeWorker.KEY_ID to id))
            .build()

        assertThat(worker.doWork()).isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(dao.get(id)?.transcript).isEqualTo("Screenshot unavailable. Audio recording saved.")
        assertThat(storage.transcriptFile(id).readText()).isEqualTo("Screenshot unavailable. Audio recording saved.")
        assertThat(dao.get(id)?.status).isEqualTo(CaptureStatus.DONE)
    }
}

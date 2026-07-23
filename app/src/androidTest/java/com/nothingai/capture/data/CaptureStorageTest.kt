package com.nothingai.capture.data

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureStorageTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = CaptureStorage(ctx)

    @Test fun savesScreenshotAndTranscriptUnderCaptureDir() {
        val id = "20260101-000001-000"
        val bmp = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        storage.saveScreenshot(id, bmp)
        storage.saveTranscript(id, "hello")
        assertThat(storage.screenshotFile(id).exists()).isTrue()
        assertThat(storage.transcriptFile(id).readText()).isEqualTo("hello")
        assertThat(storage.dir(id).path).endsWith("captures/$id")
    }

    @Test fun deleteRemovesWholeDir() {
        val id = "20260101-000002-000"
        storage.saveTranscript(id, "x")
        storage.deleteCapture(id)
        assertThat(storage.dir(id).exists()).isFalse()
    }

    @Test fun audioFileParentDirExists() {
        val id = "20260101-000003-000"
        val f = storage.audioFile(id)
        assertThat(f.parentFile!!.exists()).isTrue()
    }
}

package com.nothingai.capture.stt

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
    fun transcribesBundledClip() {
        // App-under-test context: serves the bundled model asset + filesDir.
        val appCtx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Instrumentation (test APK) context: serves the androidTest asset jfk_16k.wav.
        val testCtx = InstrumentationRegistry.getInstrumentation().context

        val wav = File(appCtx.cacheDir, "jfk.wav")
        testCtx.assets.open("jfk_16k.wav").use { i -> wav.outputStream().use { i.copyTo(it) } }

        val text = WhisperTranscriber(appCtx).transcribe(wav).lowercase()

        assertThat(text).contains("country")
    }
}

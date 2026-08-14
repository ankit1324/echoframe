package com.nothingai.capture.assistant

import com.google.common.truth.Truth.assertThat
import com.nothingai.capture.util.Wav
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHeaderTest {
    @Test fun headerIs44BytesWithRiffAndDataChunks() {
        val h = Wav.header(pcmByteCount = 32000, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        assertThat(h.size).isEqualTo(44)
        assertThat(String(h.copyOfRange(0, 4))).isEqualTo("RIFF")
        assertThat(String(h.copyOfRange(8, 12))).isEqualTo("WAVE")
        assertThat(String(h.copyOfRange(36, 40))).isEqualTo("data")
    }

    @Test fun headerEncodesSizesAndRateLittleEndian() {
        val h = Wav.header(pcmByteCount = 32000, sampleRate = 16000, channels = 1, bitsPerSample = 16)
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(bb.getInt(4)).isEqualTo(36 + 32000)   // ChunkSize
        assertThat(bb.getInt(24)).isEqualTo(16000)       // SampleRate
        assertThat(bb.getInt(28)).isEqualTo(16000 * 1 * 16 / 8) // ByteRate
        assertThat(bb.getInt(40)).isEqualTo(32000)       // Subchunk2Size
    }
}

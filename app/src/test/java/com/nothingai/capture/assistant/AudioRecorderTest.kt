package com.nothingai.capture.assistant

import com.nothingai.capture.util.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure-helper coverage for [AudioRecorder]. The recording loop itself needs a real AudioRecord,
 * so only the byte/duration math and the read-result predicate are exercised here.
 */
class AudioRecorderTest {

    @Test
    fun clampRejectsNonPositiveByteCounts() {
        assertEquals(0, Wav.clampPcmByteCount(0L))
        assertEquals(0, Wav.clampPcmByteCount(-1L))
        assertEquals(0, Wav.clampPcmByteCount(Long.MIN_VALUE))
    }

    @Test
    fun clampPassesThroughRepresentableByteCounts() {
        assertEquals(1, Wav.clampPcmByteCount(1L))
        assertEquals(32000, Wav.clampPcmByteCount(32000L))
        assertEquals(Wav.MAX_PCM_BYTES.toInt(), Wav.clampPcmByteCount(Wav.MAX_PCM_BYTES))
    }

    @Test
    fun clampSaturatesInsteadOfOverflowingToNegative() {
        // A plain toInt() here wraps negative and writes a nonsense file size into the header.
        assertEquals(Wav.MAX_PCM_BYTES.toInt(), Wav.clampPcmByteCount(Wav.MAX_PCM_BYTES + 1L))
        assertEquals(Wav.MAX_PCM_BYTES.toInt(), Wav.clampPcmByteCount(Long.MAX_VALUE))
        assertTrue(Wav.clampPcmByteCount(Long.MAX_VALUE) > 0)
    }

    @Test
    fun headerAtMaxPayloadKeepsSizeFieldsPositive() {
        val h = Wav.header(Wav.clampPcmByteCount(Long.MAX_VALUE), sampleRate = 16000)
        assertEquals(Wav.HEADER_BYTES, h.size)
        val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Int.MAX_VALUE, bb.getInt(4))                     // ChunkSize = 36 + payload
        assertEquals(Wav.MAX_PCM_BYTES.toInt(), bb.getInt(40))        // Subchunk2Size
    }

    @Test
    fun headerLengthIsStableAcrossPayloadSizes() {
        assertEquals(Wav.HEADER_BYTES, Wav.header(0).size)
        assertEquals(Wav.HEADER_BYTES, Wav.header(1).size)
        assertEquals(Wav.HEADER_BYTES, Wav.header(Int.MAX_VALUE - 36).size)
    }

    @Test
    fun negativeReadResultsAreFatal() {
        // ERROR, ERROR_BAD_VALUE, ERROR_INVALID_OPERATION, ERROR_DEAD_OBJECT.
        assertTrue(PcmRead.isFatal(-1))
        assertTrue(PcmRead.isFatal(-2))
        assertTrue(PcmRead.isFatal(-3))
        assertTrue(PcmRead.isFatal(-6))
        assertTrue(PcmRead.isFatal(Int.MIN_VALUE))
    }

    @Test
    fun emptyAndPartialReadsAreNotFatal() {
        assertFalse(PcmRead.isFatal(0))
        assertFalse(PcmRead.isFatal(1))
        assertFalse(PcmRead.isFatal(1280))
    }

    @Test
    fun durationIsZeroWhenNoPcmWasCaptured() {
        assertEquals(0L, RecordingDuration.ms(capturedBytes = 0L, elapsedMs = 5_000L))
        assertEquals(0L, RecordingDuration.ms(capturedBytes = 0L, elapsedMs = 0L))
    }

    @Test
    fun durationIsElapsedWallClockWhenPcmWasCaptured() {
        assertEquals(5_000L, RecordingDuration.ms(capturedBytes = 32_000L, elapsedMs = 5_000L))
        assertEquals(1L, RecordingDuration.ms(capturedBytes = 1L, elapsedMs = 1L))
    }

    @Test
    fun durationNeverGoesNegativeOnClockAdjustment() {
        assertEquals(0L, RecordingDuration.ms(capturedBytes = 32_000L, elapsedMs = -400L))
    }
}

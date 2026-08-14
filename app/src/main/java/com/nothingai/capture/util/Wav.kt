package com.nothingai.capture.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal WAV (RIFF/PCM) header encoding.
 *
 * Lives in `util` rather than `assistant` because both the recorder that writes these files and the
 * data layer that has to judge whether a file contains any audio need [HEADER_BYTES].
 */
object Wav {
    const val HEADER_BYTES = 44

    /**
     * Largest PCM payload the 32-bit RIFF size fields can describe: ChunkSize is
     * `36 + pcmByteCount` and must still fit in a signed int.
     */
    const val MAX_PCM_BYTES = (Int.MAX_VALUE - 36).toLong()

    fun header(pcmByteCount: Int, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val bb = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcmByteCount)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1)
        bb.putShort(channels.toShort()); bb.putInt(sampleRate); bb.putInt(byteRate)
        bb.putShort((channels * bitsPerSample / 8).toShort()); bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray()); bb.putInt(pcmByteCount)
        return bb.array()
    }

    /**
     * Narrows a running byte counter to what [header] can express. A plain `toInt()` would wrap
     * negative past 2GB of PCM and write a header describing a nonsense file size.
     */
    fun clampPcmByteCount(pcmByteCount: Long): Int = when {
        pcmByteCount <= 0L -> 0
        pcmByteCount > MAX_PCM_BYTES -> MAX_PCM_BYTES.toInt()
        else -> pcmByteCount.toInt()
    }

    /**
     * True when a WAV holds more than just its header. [com.nothingai.capture.assistant.AudioRecorder]
     * writes the placeholder header *before* it opens the microphone, so the file exists even when
     * recording never started — existence alone proves nothing.
     */
    fun hasAudio(fileLengthBytes: Long): Boolean = fileLengthBytes > HEADER_BYTES
}

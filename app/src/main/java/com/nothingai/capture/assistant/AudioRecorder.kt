package com.nothingai.capture.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Wav {
    fun header(pcmByteCount: Int, sampleRate: Int = 16000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcmByteCount)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1)
        bb.putShort(channels.toShort()); bb.putInt(sampleRate); bb.putInt(byteRate)
        bb.putShort((channels * bitsPerSample / 8).toShort()); bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray()); bb.putInt(pcmByteCount)
        return bb.array()
    }
}

/** 16kHz mono PCM16 recorder -> WAV. Started/stopped from the assistant session. */
class AudioRecorder(private val sampleRate: Int = 16000) {
    @Volatile private var recording = false
    private var thread: Thread? = null
    private var pcmBytes = 0L
    private var startMs = 0L
    private var endMs = 0L

    @Suppress("MissingPermission")
    fun start(outFile: File) {
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
        )
        recording = true
        startMs = System.currentTimeMillis()
        pcmBytes = 0
        thread = Thread {
            try {
                RandomAccessFile(outFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(Wav.header(0, sampleRate)) // placeholder, patched on stop
                    val buf = ByteArray(minBuf)
                    record.startRecording()
                    while (recording) {
                        val n = record.read(buf, 0, buf.size)
                        if (n > 0) { raf.write(buf, 0, n); pcmBytes += n }
                    }
                    // patch sizes - success path only
                    raf.seek(0); raf.write(Wav.header(pcmBytes.toInt(), sampleRate))
                }
            } finally {
                record.stop(); record.release()
            }
        }.also { it.start() }
    }

    /** @return duration in ms */
    fun stop(): Long {
        recording = false
        thread?.join()
        endMs = System.currentTimeMillis()
        return endMs - startMs
    }
}

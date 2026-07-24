package com.nothingai.capture.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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

/**
 * 16kHz mono PCM16 recorder -> WAV. Started/stopped from the assistant session.
 *
 * This runs inside the system default-assistant process, so the background recording
 * [Thread] must NEVER let an exception escape uncaught -- an uncaught throw on a raw Thread
 * kills the whole assistant process. Every failure mode (bad min buffer size, an AudioRecord
 * that fails to initialize, startRecording()/read()/write() throwing mid-stream) is caught
 * inside the thread and turned into a clean abort: the WAV file is left in a valid state
 * (header patched to match however many PCM bytes actually got written, 0 if none) and
 * [stop] reports duration 0 if recording never actually started.
 */
class AudioRecorder(private val sampleRate: Int = 16000) {
    @Volatile private var recording = false
    @Volatile private var started = false
    private var thread: Thread? = null
    private var pcmBytes = 0L
    private var startMs = 0L
    private var endMs = 0L

    @Suppress("MissingPermission")
    fun start(outFile: File) {
        recording = true
        started = false
        startMs = System.currentTimeMillis()
        pcmBytes = 0
        thread = Thread {
            var record: AudioRecord? = null
            try {
                RandomAccessFile(outFile, "rw").use { raf ->
                    raf.setLength(0)
                    raf.write(Wav.header(0, sampleRate)) // placeholder, patched below (success or abort)

                    val minBuf = AudioRecord.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minBuf > 0) {
                        val r = AudioRecord(
                            MediaRecorder.AudioSource.MIC, sampleRate,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
                        )
                        record = r
                        if (r.state == AudioRecord.STATE_INITIALIZED) {
                            val buf = ByteArray(minBuf)
                            r.startRecording()
                            started = true
                            while (recording) {
                                val n = r.read(buf, 0, buf.size)
                                if (n > 0) { raf.write(buf, 0, n); pcmBytes += n }
                            }
                        } else {
                            Log.e("AudioRecorder", "AudioRecord failed to initialize, state=${r.state}; aborting recording")
                        }
                    } else {
                        Log.e("AudioRecorder", "getMinBufferSize returned invalid size=$minBuf; aborting recording")
                    }

                    // Patch header with whatever was actually recorded (0 bytes on any abort path).
                    raf.seek(0)
                    raf.write(Wav.header(pcmBytes.toInt(), sampleRate))
                }
            } catch (t: Throwable) {
                Log.e("AudioRecorder", "recording failed", t)
                // Best-effort: re-patch the header so the file on disk stays a structurally
                // valid WAV even if the failure happened mid-stream (after some PCM was written
                // but before the success-path header patch above ran).
                try {
                    RandomAccessFile(outFile, "rw").use { raf ->
                        raf.seek(0)
                        raf.write(Wav.header(pcmBytes.toInt(), sampleRate))
                    }
                } catch (t2: Throwable) {
                    Log.e("AudioRecorder", "failed to patch WAV header after error", t2)
                }
            } finally {
                if (started) {
                    try {
                        record?.stop()
                    } catch (t: Throwable) {
                        Log.e("AudioRecorder", "record.stop() failed", t)
                    }
                }
                try {
                    record?.release()
                } catch (t: Throwable) {
                    Log.e("AudioRecorder", "record.release() failed", t)
                }
            }
        }.also { it.start() }
    }

    /** @return duration in ms, or 0 if recording never actually started */
    fun stop(): Long {
        recording = false
        thread?.join()
        endMs = System.currentTimeMillis()
        return if (started) endMs - startMs else 0
    }
}

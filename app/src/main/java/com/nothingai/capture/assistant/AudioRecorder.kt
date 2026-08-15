package com.nothingai.capture.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.nothingai.capture.util.Wav
import java.io.File
import java.io.RandomAccessFile

/** Classification of an [AudioRecord.read] return value. */
internal object PcmRead {
    /**
     * [AudioRecord.read] signals failure with a negative code. Those must abort the read loop:
     * the stream never recovers (mic permission revoked mid-capture on Android 13+, an incoming
     * call seizing the input, a dead AudioRecord), so retrying just spins the recording thread
     * at 100% CPU forever. A return of 0 is benign -- no frames available yet.
     */
    fun isFatal(readResult: Int): Boolean = readResult < 0

    /** Not unit-tested: the branch labels are platform constants, so this needs a real device. */
    fun describe(readResult: Int): String = when (readResult) {
        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
        AudioRecord.ERROR -> "ERROR"
        else -> "code=$readResult"
    }
}

internal object RecordingDuration {
    /**
     * Wall-clock duration is only meaningful once some PCM actually landed on disk -- an
     * AudioRecord that never initialized would otherwise report the full "recording" time for a
     * file containing no audio. A clock adjustment mid-capture can also make the elapsed value
     * negative, which no caller expects.
     */
    fun ms(capturedBytes: Long, elapsedMs: Long): Long =
        if (capturedBytes > 0L && elapsedMs > 0L) elapsedMs else 0L
}

/**
 * 16kHz mono PCM16 recorder -> WAV. Started/stopped from the assistant session.
 *
 * This runs inside the system default-assistant process, so the background recording
 * [Thread] must NEVER let an exception escape uncaught -- an uncaught throw on a raw Thread
 * kills the whole assistant process. Every failure mode (bad min buffer size, an AudioRecord
 * that fails to initialize, startRecording()/read()/write() throwing mid-stream, a negative
 * read() error code) is caught inside the thread and turned into a clean abort: the WAV file is
 * left in a valid state (header patched to match however many PCM bytes actually got written,
 * 0 if none) and [stop] reports duration 0 if no audio was captured. [start] itself never
 * throws either -- check [isRecording] / [capturedBytes] to tell "recorded nothing" from
 * "recorded successfully".
 *
 * [start] and [stop] are safe to call from any thread and in any order: a second [start] while a
 * recording is live is rejected (the assistant can be re-triggered by an OEM double-gesture or by
 * the system redelivering onShow to a live session, and two threads writing one WAV corrupts it),
 * and [stop] is idempotent.
 */
class AudioRecorder(private val sampleRate: Int = 16000) {
    /** Guards the start/stop lifecycle. The recording thread never takes it, so joining under it is safe. */
    private val lock = Any()

    @Volatile private var recording = false
    @Volatile private var started = false
    @Volatile private var active = false
    @Volatile private var pcmBytes = 0L
    private var thread: Thread? = null
    private var startMs = 0L
    private var endMs = 0L
    private var lastDurationMs = 0L

    /** True from an accepted [start] until the [stop] that joins its thread. */
    val isRecording: Boolean get() = active

    /** PCM bytes written by the current/last recording. 0 means nothing was captured. */
    val capturedBytes: Long get() = pcmBytes

    @Suppress("MissingPermission")
    fun start(outFile: File) {
        synchronized(lock) {
            if (active) {
                Log.w("AudioRecorder", "start() ignored: a recording is already active")
                return
            }
            active = true
            recording = true
            started = false
            pcmBytes = 0
            lastDurationMs = 0
            startMs = System.currentTimeMillis()
            endMs = 0
            try {
                thread = Thread { record(outFile) }.also { it.start() }
            } catch (t: Throwable) {
                // Thread creation itself failed; stay silent-but-observable rather than throwing
                // into the assistant's session callbacks.
                Log.e("AudioRecorder", "failed to start recording thread", t)
                thread = null
                recording = false
                active = false
            }
        }
    }

    private fun record(outFile: File) {
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
                            if (n > 0) {
                                raf.write(buf, 0, n)
                                pcmBytes += n
                                if (pcmBytes >= Wav.MAX_PCM_BYTES) {
                                    // Past this the WAV size fields can no longer describe the file.
                                    Log.w("AudioRecorder", "reached max WAV payload ($pcmBytes bytes); stopping")
                                    break
                                }
                            } else if (PcmRead.isFatal(n)) {
                                Log.e("AudioRecorder", "read() failed: ${PcmRead.describe(n)}; aborting recording")
                                break
                            }
                        }
                    } else {
                        Log.e("AudioRecorder", "AudioRecord failed to initialize, state=${r.state}; aborting recording")
                    }
                } else {
                    Log.e("AudioRecorder", "getMinBufferSize returned invalid size=$minBuf; aborting recording")
                }

                // Patch header with whatever was actually recorded (0 bytes on any abort path).
                raf.seek(0)
                raf.write(Wav.header(Wav.clampPcmByteCount(pcmBytes), sampleRate))
            }
        } catch (se: SecurityException) {
            // RECORD_AUDIO is a runtime permission, so it can be absent or revoked even though the
            // manifest declares it. Handled separately from the catch-all below because it is a
            // permission state rather than a recorder fault: the capture continues with whatever
            // the screenshot yields, silently and without audio.
            Log.e("AudioRecorder", "RECORD_AUDIO not granted; continuing without audio", se)
            patchHeaderQuietly(outFile)
        } catch (t: Throwable) {
            Log.e("AudioRecorder", "recording failed", t)
            patchHeaderQuietly(outFile)
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
    }

    /**
     * Best-effort header rewrite for the failure paths, so the file on disk stays a structurally
     * valid WAV even when the failure happened mid-stream — after some PCM was written, but before
     * the success-path patch ran.
     */
    private fun patchHeaderQuietly(outFile: File) {
        try {
            RandomAccessFile(outFile, "rw").use { raf ->
                raf.seek(0)
                raf.write(Wav.header(Wav.clampPcmByteCount(pcmBytes), sampleRate))
            }
        } catch (t: Throwable) {
            Log.e("AudioRecorder", "failed to patch WAV header after error", t)
        }
    }

    /**
     * Stops the recording and joins its thread. Idempotent: a repeat call (or a call with no prior
     * [start]) returns the same value without throwing.
     *
     * @return duration in ms, or 0 if no audio was captured
     */
    fun stop(): Long {
        synchronized(lock) {
            if (!active) return lastDurationMs // never started, or already stopped
            recording = false
            val t = thread
            try {
                t?.join(JOIN_TIMEOUT_MS)
                if (t != null && t.isAlive) {
                    // Wedged in read()/write(). Abandon it rather than block the caller forever; it has
                    // already been told to stop and will patch the header and release the mic on its own.
                    Log.e("AudioRecorder", "recording thread did not exit within ${JOIN_TIMEOUT_MS}ms; abandoning it")
                }
            } catch (ie: InterruptedException) {
                Log.w("AudioRecorder", "interrupted while joining recording thread", ie)
                Thread.currentThread().interrupt()
            } catch (t2: Throwable) {
                Log.e("AudioRecorder", "join failed", t2)
            }
            thread = null
            active = false
            endMs = System.currentTimeMillis()
            lastDurationMs = RecordingDuration.ms(pcmBytes, endMs - startMs)
            return lastDurationMs
        }
    }

    private companion object {
        /** Generous vs. one buffer read; only trips when the mic stream is wedged. */
        const val JOIN_TIMEOUT_MS = 10_000L
    }
}

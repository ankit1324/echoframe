package com.nothingai.capture

import android.app.Application
import android.util.Log
import androidx.work.ExistingWorkPolicy
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureRecovery
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.stt.TranscribeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NothingApp : Application() {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        io.launch { recoverInterruptedCaptures() }
    }

    /**
     * Repairs captures orphaned by a process death. Capture runs inside a VoiceInteractionSession
     * that the OS can kill at any point, so without this sweep an interrupted capture stays
     * RECORDING or TRANSCRIBING forever with no way for the user to clear it.
     */
    private suspend fun recoverInterruptedCaptures() {
        try {
            val storage = CaptureStorage(this)
            val recovery = CaptureRecovery(
                dao = CaptureDatabase.get(this).captureDao(),
                hasPayload = storage::hasPayload,
                // KEEP: never displace an attempt WorkManager already restored for this capture.
                requeue = { id -> TranscribeWorker.enqueue(this, id, ExistingWorkPolicy.KEEP) },
            )
            val recovered = recovery.sweep()
            if (recovered > 0) Log.i(TAG, "Recovered $recovered interrupted capture(s)")
        } catch (exception: Exception) {
            Log.e(TAG, "Capture recovery sweep failed", exception)
        }
    }

    private companion object {
        const val TAG = "NothingApp"
    }
}

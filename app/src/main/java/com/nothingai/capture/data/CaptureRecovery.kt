package com.nothingai.capture.data

/** Captures younger than this may still be in flight, so recovery leaves them alone. */
const val STALE_AFTER_MS = 10 * 60 * 1000L

/** What to do with a capture left mid-flight when its process died. */
enum class RecoveryAction { LEAVE, REQUEUE, FAIL }

/**
 * Decides the fate of a single interrupted capture.
 *
 * Capture starts inside a VoiceInteractionSession, which the OS kills far more freely than a
 * foreground activity. Without this, a process death between `onShow` and `finalizeCapture` leaves
 * a row stuck at RECORDING forever, showing as a permanent "Listening…" ghost in the gallery.
 *
 * Kept pure so the policy is testable without a database, a WorkManager, or a device.
 */
fun recoveryActionFor(
    status: CaptureStatus,
    timestamp: Long,
    hasPayload: Boolean,
    now: Long,
    staleAfterMs: Long = STALE_AFTER_MS,
): RecoveryAction {
    if (status == CaptureStatus.DONE || status == CaptureStatus.FAILED) return RecoveryAction.LEAVE
    // Too young to judge: a recording may legitimately still be open, or its worker still running.
    if (now - timestamp < staleAfterMs) return RecoveryAction.LEAVE
    // Salvage anything with a screenshot or audio on disk; fail the rest rather than emit a note
    // about content that was never captured.
    return if (hasPayload) RecoveryAction.REQUEUE else RecoveryAction.FAIL
}

/**
 * Sweeps captures that were interrupted mid-flight and either re-queues or fails them.
 *
 * [hasPayload] and [requeue] are injected so this can be exercised with fakes.
 */
class CaptureRecovery(
    private val dao: CaptureDao,
    private val hasPayload: (String) -> Boolean,
    private val requeue: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Returns how many captures were acted on. */
    suspend fun sweep(): Int {
        val timeNow = now()
        var recovered = 0
        for (capture in dao.unfinished()) {
            val action = recoveryActionFor(
                status = capture.status,
                timestamp = capture.timestamp,
                hasPayload = hasPayload(capture.id),
                now = timeNow,
            )
            when (action) {
                RecoveryAction.LEAVE -> Unit
                RecoveryAction.REQUEUE -> {
                    if (capture.status != CaptureStatus.PENDING) {
                        dao.updateStatus(capture.id, CaptureStatus.PENDING)
                    }
                    requeue(capture.id)
                    recovered++
                }
                RecoveryAction.FAIL -> {
                    dao.updateStatus(capture.id, CaptureStatus.FAILED)
                    recovered++
                }
            }
        }
        return recovered
    }
}

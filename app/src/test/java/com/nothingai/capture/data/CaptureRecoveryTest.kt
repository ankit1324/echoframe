package com.nothingai.capture.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

private const val NOW = 1_000_000_000L
private fun stale(offset: Long = 0) = NOW - STALE_AFTER_MS - 1 - offset

class CaptureRecoveryTest {
    @Test
    fun leavesTerminalCapturesAlone() {
        for (status in listOf(CaptureStatus.DONE, CaptureStatus.FAILED)) {
            assertEquals(
                RecoveryAction.LEAVE,
                recoveryActionFor(status, stale(), hasPayload = true, now = NOW),
            )
        }
    }

    @Test
    fun leavesRecentCapturesAloneBecauseTheyMayStillBeRunning() {
        assertEquals(
            RecoveryAction.LEAVE,
            recoveryActionFor(CaptureStatus.RECORDING, NOW - 1_000, hasPayload = false, now = NOW),
        )
        assertEquals(
            RecoveryAction.LEAVE,
            recoveryActionFor(CaptureStatus.TRANSCRIBING, NOW - 1_000, hasPayload = true, now = NOW),
        )
    }

    @Test
    fun requeuesStaleCapturesThatHaveSomethingOnDisk() {
        for (status in listOf(CaptureStatus.RECORDING, CaptureStatus.PENDING, CaptureStatus.TRANSCRIBING)) {
            assertEquals(
                RecoveryAction.REQUEUE,
                recoveryActionFor(status, stale(), hasPayload = true, now = NOW),
            )
        }
    }

    @Test
    fun failsStaleCapturesWithNothingToAnalyze() {
        for (status in listOf(CaptureStatus.RECORDING, CaptureStatus.PENDING, CaptureStatus.TRANSCRIBING)) {
            assertEquals(
                RecoveryAction.FAIL,
                recoveryActionFor(status, stale(), hasPayload = false, now = NOW),
            )
        }
    }

    @Test
    fun sweepPromotesSalvageableRecordingToPendingAndRequeuesIt() = runBlocking {
        val dao = FakeCaptureDao(listOf(capture("salvage", CaptureStatus.RECORDING, stale())))
        val requeued = mutableListOf<String>()
        val recovered = CaptureRecovery(dao, hasPayload = { true }, requeue = { requeued += it }, now = { NOW }).sweep()

        assertEquals(1, recovered)
        assertEquals(listOf("salvage" to CaptureStatus.PENDING), dao.statusUpdates)
        assertEquals(listOf("salvage"), requeued)
    }

    @Test
    fun sweepFailsEmptyCaptureWithoutRequeueing() = runBlocking {
        val dao = FakeCaptureDao(listOf(capture("empty", CaptureStatus.RECORDING, stale())))
        val requeued = mutableListOf<String>()
        val recovered = CaptureRecovery(dao, hasPayload = { false }, requeue = { requeued += it }, now = { NOW }).sweep()

        assertEquals(1, recovered)
        assertEquals(listOf("empty" to CaptureStatus.FAILED), dao.statusUpdates)
        assertEquals(emptyList<String>(), requeued)
    }

    @Test
    fun sweepDoesNotTouchAnInFlightCapture() = runBlocking {
        val dao = FakeCaptureDao(listOf(capture("live", CaptureStatus.RECORDING, NOW - 1_000)))
        val requeued = mutableListOf<String>()
        val recovered = CaptureRecovery(dao, hasPayload = { false }, requeue = { requeued += it }, now = { NOW }).sweep()

        assertEquals(0, recovered)
        assertEquals(emptyList<Pair<String, CaptureStatus>>(), dao.statusUpdates)
        assertEquals(emptyList<String>(), requeued)
    }

    @Test
    fun sweepRequeuesAlreadyPendingCaptureWithoutRedundantStatusWrite() = runBlocking {
        val dao = FakeCaptureDao(listOf(capture("pending", CaptureStatus.PENDING, stale())))
        val requeued = mutableListOf<String>()
        CaptureRecovery(dao, hasPayload = { true }, requeue = { requeued += it }, now = { NOW }).sweep()

        assertEquals(emptyList<Pair<String, CaptureStatus>>(), dao.statusUpdates)
        assertEquals(listOf("pending"), requeued)
    }

    private fun capture(id: String, status: CaptureStatus, timestamp: Long) = Capture(
        id = id,
        timestamp = timestamp,
        hasScreenshot = false,
        durationMs = 0,
        transcript = null,
        status = status,
    )
}

/** Records the writes recovery performs; every method the sweep must not call fails loudly. */
private class FakeCaptureDao(private val rows: List<Capture>) : CaptureDao {
    val statusUpdates = mutableListOf<Pair<String, CaptureStatus>>()

    override suspend fun unfinished(): List<Capture> = rows

    override suspend fun updateStatus(id: String, status: CaptureStatus) {
        statusUpdates += id to status
    }

    override suspend fun upsert(capture: Capture) = unexpected("upsert")
    override fun observeAll(): Flow<List<Capture>> = unexpected("observeAll")
    override suspend fun get(id: String): Capture? = unexpected("get")
    override suspend fun updateTranscript(id: String, transcript: String, status: CaptureStatus) = unexpected("updateTranscript")
    override suspend fun updateTitle(id: String, title: String) = unexpected("updateTitle")
    override suspend fun updateNote(id: String, title: String, transcript: String) = unexpected("updateNote")
    override suspend fun updateTags(id: String, tags: String) = unexpected("updateTags")
    override suspend fun updateCategory(id: String, category: String) = unexpected("updateCategory")
    override suspend fun updateFavorite(id: String, favorite: Boolean) = unexpected("updateFavorite")
    override suspend fun updateSourceUrl(id: String, url: String?) = unexpected("updateSourceUrl")
    override suspend fun finalizeRecording(
        id: String,
        hasScreenshot: Boolean,
        durationMs: Long,
        status: CaptureStatus,
        sourcePackage: String?,
        sourceUrl: String?,
    ): Int = unexpected("finalizeRecording")
    override suspend fun delete(id: String) = unexpected("delete")
    override fun search(q: String): Flow<List<Capture>> = unexpected("search")

    private fun unexpected(name: String): Nothing = throw AssertionError("recovery must not call $name")
}

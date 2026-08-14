package com.nothingai.capture.assistant

import com.nothingai.capture.util.Wav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CaptureLifecycleTest {

    // --- save/discard policy -------------------------------------------------

    @Test
    fun deliberateGesturesDecideDirectly() {
        assertEquals(Outcome.SAVE, outcomeFor(DismissCause.SAVE_BUTTON, 0, hasAudio = false, hasScreenshot = false))
        for (cause in listOf(DismissCause.DISCARD_BUTTON, DismissCause.BACK_PRESSED, DismissCause.OUTSIDE_TAP)) {
            // Discards even when there is content — the user asked for it.
            assertEquals(Outcome.DISCARD, outcomeFor(cause, 30_000, hasAudio = true, hasScreenshot = true))
        }
    }

    @Test
    fun systemHideSavesAnInterruptedRecording() {
        // Screen off / incoming call / Home mid-note must not destroy the note.
        assertEquals(
            Outcome.SAVE,
            outcomeFor(DismissCause.SYSTEM, 30_000, hasAudio = true, hasScreenshot = false),
        )
        assertEquals(
            Outcome.SAVE,
            outcomeFor(DismissCause.SYSTEM, 30_000, hasAudio = false, hasScreenshot = true),
        )
    }

    @Test
    fun systemHideDiscardsAnImmediateEmptyMisfire() {
        assertEquals(
            Outcome.DISCARD,
            outcomeFor(DismissCause.SYSTEM, 200, hasAudio = false, hasScreenshot = false),
        )
    }

    @Test
    fun systemHideKeepsAnEmptyCaptureOnceTheGraceWindowPasses() {
        // Past the window the user plausibly meant to capture something, so don't silently bin it.
        assertEquals(
            Outcome.SAVE,
            outcomeFor(DismissCause.SYSTEM, MISFIRE_GRACE_MS, hasAudio = false, hasScreenshot = false),
        )
        assertEquals(
            Outcome.DISCARD,
            outcomeFor(DismissCause.SYSTEM, MISFIRE_GRACE_MS - 1, hasAudio = false, hasScreenshot = false),
        )
    }

    @Test
    fun contentPresentAlwaysBeatsTheGraceWindow() {
        assertEquals(
            Outcome.SAVE,
            outcomeFor(DismissCause.SYSTEM, 0, hasAudio = true, hasScreenshot = false),
        )
    }

    // --- "is there anything here" -------------------------------------------

    @Test
    fun aHeaderOnlyWavCountsAsNoAudio() {
        // AudioRecorder writes the 44-byte header before opening the mic, so this is the
        // mic-unavailable case, not a real recording.
        assertEquals(false, Wav.hasAudio(0))
        assertEquals(false, Wav.hasAudio(Wav.HEADER_BYTES.toLong()))
        assertEquals(true, Wav.hasAudio(Wav.HEADER_BYTES + 1L))
    }

    @Test
    fun worthKeepingNeedsEitherMedium() {
        assertEquals(false, captureWorthKeeping(hasScreenshot = false, hasAudio = false))
        assertEquals(true, captureWorthKeeping(hasScreenshot = true, hasAudio = false))
        assertEquals(true, captureWorthKeeping(hasScreenshot = false, hasAudio = true))
    }

    // --- CaptureSlot: the B1 regression ------------------------------------

    @Test
    fun mintRejectsASecondConcurrentCapture() {
        val slot = CaptureSlot()
        assertNotNull(slot.mint { ticket("a") })
        assertNull(slot.mint { ticket("b") })
    }

    @Test
    fun ensureReturnsTheLiveTicketRatherThanMintingAnother() {
        val slot = CaptureSlot()
        val first = slot.ensure { ticket("a") }
        assertSame(first, slot.ensure { ticket("b") })
    }

    @Test
    fun claimSettlesAndClearsInOneStepSoTheNextCaptureGetsAFreshId() {
        val slot = CaptureSlot()
        val first = slot.ensure { ticket("a") }
        assertSame(first, slot.claim())
        // Previously the id lingered after settling, so the next onShow reused it and stranded the mic.
        assertNull(slot.claim())
        val second = slot.ensure { ticket("b") }
        assertEquals("b", second.id)
    }

    @Test
    fun exactlyOneOfManyRacingClaimsWins() {
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        try {
            repeat(1_000) {
                val slot = CaptureSlot()
                slot.ensure { ticket("race") }
                val barrier = CyclicBarrier(threads)
                val winners = AtomicInteger(0)
                val done = CyclicBarrier(threads + 1)
                repeat(threads) {
                    pool.execute {
                        barrier.await()
                        if (slot.claim() != null) winners.incrementAndGet()
                        done.await()
                    }
                }
                done.await()
                assertEquals(1, winners.get())
                assertNull(slot.peek())
            }
        } finally {
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    private fun ticket(id: String) = CaptureTicket(
        id = id,
        startedAtMs = 0,
        audioFile = File("/tmp/$id/audio.wav"),
        screenshotFile = File("/tmp/$id/screenshot.png"),
    )
}

package com.nothingai.capture.assistant

import kotlinx.coroutines.Job
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Why a capture is ending. Only a deliberate user gesture discards; anything the system initiates
 * (screen off, incoming call, Home, memory pressure) is not a decision and therefore saves.
 */
enum class DismissCause { SAVE_BUTTON, DISCARD_BUTTON, BACK_PRESSED, OUTSIDE_TAP, SYSTEM }

enum class Outcome { SAVE, DISCARD }

/**
 * Window in which a system-initiated hide with nothing captured reads as a misfired gesture rather
 * than an interrupted note.
 */
const val MISFIRE_GRACE_MS = 1_500L

/** A capture with neither a screenshot nor audio has nothing to show and nothing to analyse. */
fun captureWorthKeeping(hasScreenshot: Boolean, hasAudio: Boolean): Boolean = hasScreenshot || hasAudio

/**
 * The entire save-vs-discard policy, kept pure so it is testable without a device.
 *
 * [hasAudio] is a decision, not a byte count, so callers can source it from the recorder in-process
 * or from the file on disk during recovery without confusing the two units.
 */
fun outcomeFor(
    cause: DismissCause,
    elapsedMs: Long,
    hasAudio: Boolean,
    hasScreenshot: Boolean,
): Outcome = when (cause) {
    DismissCause.SAVE_BUTTON -> Outcome.SAVE
    DismissCause.DISCARD_BUTTON, DismissCause.BACK_PRESSED, DismissCause.OUTSIDE_TAP -> Outcome.DISCARD
    DismissCause.SYSTEM ->
        if (!captureWorthKeeping(hasScreenshot, hasAudio) && elapsedMs < MISFIRE_GRACE_MS) {
            Outcome.DISCARD
        } else {
            Outcome.SAVE
        }
}

/**
 * One capture's immutable identity and paths. Everything a settler needs is carried by value, so it
 * can always stop and clean up exactly what it started even if a newer capture has begun.
 */
class CaptureTicket(
    val id: String,
    val startedAtMs: Long,
    val audioFile: File,
    val screenshotFile: File,
) {
    /** The in-flight screenshot write, scoped per ticket so capture N never joins capture N-1's job. */
    val screenshotSave = AtomicReference<Job?>(null)
}

/**
 * Holds at most one in-flight capture.
 *
 * [claim] settles and clears in a single atomic step. That makes the state which previously stranded
 * a live microphone — "already settled, but the id is still present, so the next onShow reuses it" —
 * unrepresentable rather than merely guarded against.
 */
class CaptureSlot {
    private val current = AtomicReference<CaptureTicket?>(null)

    /** Begins a capture, or returns null when one is already in flight. */
    fun mint(factory: () -> CaptureTicket): CaptureTicket? {
        val ticket = factory()
        return if (current.compareAndSet(null, ticket)) ticket else null
    }

    /** The in-flight capture, beginning one if there is none (the screenshot can arrive first). */
    fun ensure(factory: () -> CaptureTicket): CaptureTicket {
        while (true) {
            current.get()?.let { return it }
            mint(factory)?.let { return it }
        }
    }

    fun peek(): CaptureTicket? = current.get()

    /** Takes sole ownership. Exactly one caller wins; every later caller gets null. */
    fun claim(): CaptureTicket? = current.getAndSet(null)
}

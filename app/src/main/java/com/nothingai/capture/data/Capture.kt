package com.nothingai.capture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CaptureStatus { RECORDING, PENDING, TRANSCRIBING, DONE, FAILED }

@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val hasScreenshot: Boolean,
    val durationMs: Long,
    val transcript: String?,
    val status: CaptureStatus
)

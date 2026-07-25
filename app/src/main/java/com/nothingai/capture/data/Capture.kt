package com.nothingai.capture.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Metadata fields use defaults so existing capture creation paths remain compatible.
enum class CaptureStatus { RECORDING, PENDING, TRANSCRIBING, DONE, FAILED }

@Entity(tableName = "captures")
data class Capture(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val hasScreenshot: Boolean,
    val durationMs: Long,
    val transcript: String?,
    val status: CaptureStatus,
    val title: String? = null,
    val tags: String = "",
    val isFavorite: Boolean = false,
    val sourcePackage: String? = null,
    val sourceUrl: String? = null,
)

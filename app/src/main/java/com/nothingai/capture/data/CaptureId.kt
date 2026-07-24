package com.nothingai.capture.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CaptureId {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    fun from(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        FORMAT.withZone(zone).format(Instant.ofEpochMilli(epochMillis))
}

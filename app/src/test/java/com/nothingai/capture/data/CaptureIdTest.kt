package com.nothingai.capture.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset

class CaptureIdTest {
    @Test fun formatsEpochMillisAsSortableId() {
        // 2026-07-23T10:05:09.123Z
        val millis = 1_784_801_109_123L
        val id = CaptureId.from(millis, ZoneOffset.UTC)
        assertThat(id).isEqualTo("20260723-100509-123")
    }

    @Test fun idsAreLexicographicallyOrderedByTime() {
        val a = CaptureId.from(1_000L, ZoneOffset.UTC)
        val b = CaptureId.from(2_000L, ZoneOffset.UTC)
        assertThat(a < b).isTrue()
    }
}

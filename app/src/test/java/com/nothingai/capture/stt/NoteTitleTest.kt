package com.nothingai.capture.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTitleTest {
    @Test
    fun usesFirstSentenceAndNormalizesWhitespace() {
        assertEquals("Buy milk tomorrow", titleFromTranscript("  buy   milk tomorrow. Then call Sam."))
    }

    @Test
    fun truncatesLongTitles() {
        assertEquals("This is a very long voice note about…", titleFromTranscript("This is a very long voice note about planning the weekend with friends."))
    }
}

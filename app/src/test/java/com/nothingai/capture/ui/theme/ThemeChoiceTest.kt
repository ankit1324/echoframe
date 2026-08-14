package com.nothingai.capture.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeChoiceTest {
    @Test
    fun mapsPersistedLabelsToChoices() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromPreference("System Default"))
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.fromPreference("Light Notebook"))
        assertEquals(ThemeChoice.DARK, ThemeChoice.fromPreference("Dark Ink"))
    }

    @Test
    fun fallsBackToSystemForMissingOrUnknownValues() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromPreference(null))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromPreference(""))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromPreference("Midnight Pastel"))
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.fromPreference("dark ink"))
    }

    @Test
    fun everyLabelRoundTrips() {
        ThemeChoice.entries.forEach { choice ->
            assertEquals(choice, ThemeChoice.fromPreference(choice.label))
        }
    }

    @Test
    fun prefKeyMatchesTheKeySettingsAlreadyWrites() {
        assertEquals("theme", ThemeChoice.PREF_KEY)
    }
}

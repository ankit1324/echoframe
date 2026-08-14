package com.nothingai.capture.ui.theme

/**
 * User-selectable app theme.
 *
 * [label] is the exact string persisted in SharedPreferences under [PREF_KEY], so existing
 * saved values keep working. Pure Kotlin on purpose: unit-testable without Android.
 */
enum class ThemeChoice(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Notebook"),
    DARK("Dark Ink");

    companion object {
        const val PREF_KEY = "theme"

        fun fromPreference(value: String?): ThemeChoice = entries.firstOrNull { it.label == value } ?: SYSTEM
    }
}

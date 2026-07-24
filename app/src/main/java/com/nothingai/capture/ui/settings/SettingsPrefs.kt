package com.nothingai.capture.ui.settings

import android.content.Context
import android.content.SharedPreferences

object SettingsPrefs {
    fun get(context: Context): SharedPreferences = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
}

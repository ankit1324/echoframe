package com.nothingai.capture.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nothingai.capture.R
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import com.nothingai.capture.data.CaptureStorage
import com.nothingai.capture.ui.settings.SettingsPrefs
import com.nothingai.capture.data.CaptureId
import com.nothingai.capture.data.CaptureStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CaptureDatabase.get(app).captureDao()
    private val storage = CaptureStorage(app)
    private val query = MutableStateFlow("")

    init {
        val prefs = SettingsPrefs.get(app)
        if (!prefs.getBoolean("onboarding_done", false)) {
            prefs.edit().putBoolean("onboarding_done", true).apply()
            viewModelScope.launch {
                dao.upsert(Capture(
                    id = CaptureId.from(System.currentTimeMillis()),
                    timestamp = System.currentTimeMillis(),
                    hasScreenshot = false,
                    durationMs = 2500,
                    transcript = app.getString(R.string.welcome_note_body, app.getString(R.string.app_name)),
                    status = CaptureStatus.DONE,
                    title = app.getString(R.string.welcome_note_title, app.getString(R.string.app_name)),
                    tags = "Idea",
                    isFavorite = true
                ))
            }
        }
    }


    fun setQuery(q: String) { query.value = q }
    fun setFavorite(id: String, favorite: Boolean) = viewModelScope.launch { dao.updateFavorite(id, favorite) }
    fun setTags(id: String, tags: String) = viewModelScope.launch { dao.updateTags(id, tags) }
    fun delete(id: String) = viewModelScope.launch {
        storage.deleteCapture(id)
        dao.delete(id)
    }

    val items: StateFlow<List<Capture>> = query
        .flatMapLatest { q -> if (q.isBlank()) dao.observeAll() else dao.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

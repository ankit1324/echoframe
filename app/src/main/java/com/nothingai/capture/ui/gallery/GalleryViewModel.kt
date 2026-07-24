package com.nothingai.capture.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nothingai.capture.data.Capture
import com.nothingai.capture.data.CaptureDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CaptureDatabase.get(app).captureDao()
    private val query = MutableStateFlow("")

    fun setQuery(q: String) {
        query.value = q
    }

    val items: StateFlow<List<Capture>> = query
        .flatMapLatest { q -> if (q.isBlank()) dao.observeAll() else dao.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

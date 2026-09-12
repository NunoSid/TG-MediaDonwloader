package pt.nunosid.tgmedialibrary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import pt.nunosid.tgmedialibrary.data.TelegramVideoRepository
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramAuthState
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val engine = TelegramEngine(application)
    private val repository = TelegramVideoRepository(engine)

    val authState = engine.authState
    val connectionLabel = engine.connectionLabel

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    private val _filters = MutableStateFlow(VideoFilters())
    val filters: StateFlow<VideoFilters> = _filters.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val visibleVideos = combine(_videos, _filters) { videos, filters ->
        repository.applyFilters(videos, filters)
    }

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is TelegramAuthState.Ready && _videos.value.isEmpty()) refresh()
            }
        }
    }

    fun submitPhone(value: String) = engine.submitPhone(value)
    fun submitCode(value: String) = engine.submitCode(value)
    fun submitPassword(value: String) = engine.submitPassword(value)

    fun updateFilters(transform: (VideoFilters) -> VideoFilters) {
        _filters.value = transform(_filters.value)
    }

    fun refresh() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        runCatching { repository.resetAndLoad() }
            .onSuccess { _videos.value = it }
            .onFailure { _error.value = it.message }
        _loading.value = false
    }

    fun loadMore() = viewModelScope.launch {
        if (_loading.value) return@launch
        _loading.value = true
        runCatching { repository.loadNext() }
            .onSuccess { _videos.value = it }
            .onFailure { _error.value = it.message }
        _loading.value = false
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}

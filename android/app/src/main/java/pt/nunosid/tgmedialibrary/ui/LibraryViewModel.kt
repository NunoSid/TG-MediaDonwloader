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
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.telegram.TelegramAuthState
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val engine = TelegramEngine(application)
    private val repository = TelegramVideoRepository(engine)

    val authState = engine.authState
    val connectionLabel = engine.connectionLabel

    private val _privacyUnlocked = MutableStateFlow(false)
    val privacyUnlocked: StateFlow<Boolean> = _privacyUnlocked.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()
    private val _filters = MutableStateFlow(VideoFilters())
    val filters: StateFlow<VideoFilters> = _filters.asStateFlow()
    private val _historyScope = MutableStateFlow(HistoryScope.LAST_500)
    val historyScope: StateFlow<HistoryScope> = _historyScope.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount.asStateFlow()
    private val _indexedPages = MutableStateFlow(0)
    val indexedPages: StateFlow<Int> = _indexedPages.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val visibleVideos = combine(_videos, _filters) { videos, filters ->
        repository.applyFilters(videos, filters)
    }

    init {
        viewModelScope.launch {
            authState.collect { state ->
                if (state is TelegramAuthState.Ready && _videos.value.isEmpty() && !_loading.value) {
                    loadHistory(_historyScope.value)
                }
            }
        }
    }

    fun tryUnlock(code: String): Boolean {
        val ok = code == ENTRY_CODE
        if (ok) _privacyUnlocked.value = true
        return ok
    }

    fun lockPrivacy() {
        _privacyUnlocked.value = false
    }

    fun panicWipe() {
        _privacyUnlocked.value = false
        _videos.value = emptyList()
        _filters.value = VideoFilters()
        _loadedCount.value = 0
        _indexedPages.value = 0
        engine.panicWipeLocal()
    }

    fun configureApi(apiId: String, apiHash: String) = engine.configureApi(apiId, apiHash)
    fun resetApiCredentials() = engine.resetApiCredentials()
    fun submitPhone(value: String) = engine.submitPhone(value)
    fun submitCode(value: String) = engine.submitCode(value)
    fun submitPassword(value: String) = engine.submitPassword(value)

    fun updateFilters(transform: (VideoFilters) -> VideoFilters) {
        _filters.value = transform(_filters.value)
    }

    fun selectHistoryScope(scope: HistoryScope) {
        if (_loading.value || scope == _historyScope.value && _videos.value.isNotEmpty()) return
        _historyScope.value = scope
        loadHistory(scope)
    }

    fun refresh() = loadHistory(_historyScope.value)

    private fun loadHistory(scope: HistoryScope) = viewModelScope.launch {
        if (_loading.value) return@launch
        _loading.value = true
        _error.value = null
        _loadedCount.value = 0
        _indexedPages.value = 0
        _videos.value = emptyList()
        runCatching {
            repository.loadScope(scope) { loaded, pages ->
                _loadedCount.value = loaded
                _indexedPages.value = pages
            }
        }
            .onSuccess {
                _videos.value = it
                _loadedCount.value = it.size
            }
            .onFailure { _error.value = it.message }
        _loading.value = false
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }

    companion object {
        private const val ENTRY_CODE = "21031991"
        const val PANIC_CODE = "112"
    }
}

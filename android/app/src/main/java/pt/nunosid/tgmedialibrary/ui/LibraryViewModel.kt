package pt.nunosid.tgmedialibrary.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import pt.nunosid.tgmedialibrary.data.TelegramVideoRepository
import pt.nunosid.tgmedialibrary.data.ThumbnailMemoryCache
import pt.nunosid.tgmedialibrary.model.FilterPreset
import pt.nunosid.tgmedialibrary.model.HistoryScope
import pt.nunosid.tgmedialibrary.model.VideoFilters
import pt.nunosid.tgmedialibrary.model.VideoItem
import pt.nunosid.tgmedialibrary.model.VideoSort
import pt.nunosid.tgmedialibrary.telegram.TelegramAuthState
import pt.nunosid.tgmedialibrary.telegram.TelegramEngine
import java.util.UUID

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    val engine = TelegramEngine(application)
    private val repository = TelegramVideoRepository(engine)
    private val presetPrefs = application.getSharedPreferences("filter_presets", Context.MODE_PRIVATE)

    val authState = engine.authState
    val connectionLabel = engine.connectionLabel

    private val _privacyUnlocked = MutableStateFlow(false)
    val privacyUnlocked: StateFlow<Boolean> = _privacyUnlocked.asStateFlow()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()
    private val _filters = MutableStateFlow(VideoFilters())
    val filters: StateFlow<VideoFilters> = _filters.asStateFlow()
    private val _filterPresets = MutableStateFlow(loadPresets())
    val filterPresets: StateFlow<List<FilterPreset>> = _filterPresets.asStateFlow()
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
        ThumbnailMemoryCache.clear()
        engine.purgeTransientFilesAsync()
        _privacyUnlocked.value = false
    }

    fun purgeExternalShareCache() {
        engine.purgePrivacyCaches()
    }

    fun panicWipe() {
        ThumbnailMemoryCache.clear()
        _privacyUnlocked.value = false
        _videos.value = emptyList()
        _filters.value = VideoFilters()
        _filterPresets.value = emptyList()
        _loadedCount.value = 0
        _indexedPages.value = 0
        presetPrefs.edit().clear().commit()
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

    fun applyPreset(preset: FilterPreset) {
        _filters.value = preset.filters
    }

    fun savePreset(name: String, filters: VideoFilters = _filters.value) {
        val clean = name.trim().take(40)
        if (clean.isBlank()) return
        val current = _filterPresets.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
        val preset = if (existingIndex >= 0) {
            current[existingIndex].copy(name = clean, filters = filters)
        } else {
            FilterPreset(UUID.randomUUID().toString(), clean, filters)
        }
        if (existingIndex >= 0) current[existingIndex] = preset else current.add(preset)
        _filterPresets.value = current.sortedBy { it.name.lowercase() }
        persistPresets(_filterPresets.value)
    }

    fun deletePreset(id: String) {
        _filterPresets.value = _filterPresets.value.filterNot { it.id == id }
        persistPresets(_filterPresets.value)
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

    private fun loadPresets(): List<FilterPreset> {
        val raw = presetPrefs.getString(PRESETS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val filters = VideoFilters(
                        query = obj.optString("query", ""),
                        chatIds = obj.optChatIds(),
                        minBytes = obj.optLongNullable("minBytes"),
                        maxBytes = obj.optLongNullable("maxBytes"),
                        minDuration = obj.optIntNullable("minDuration"),
                        maxDuration = obj.optIntNullable("maxDuration"),
                        sort = runCatching { VideoSort.valueOf(obj.optString("sort", VideoSort.NEWEST.name)) }
                            .getOrDefault(VideoSort.NEWEST)
                    )
                    add(FilterPreset(obj.getString("id"), obj.getString("name"), filters))
                }
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun persistPresets(presets: List<FilterPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            val f = preset.filters
            array.put(JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("query", f.query)
                put("chatIds", JSONArray().apply { f.chatIds.sorted().forEach { put(it) } })
                putNullable("minBytes", f.minBytes)
                putNullable("maxBytes", f.maxBytes)
                putNullable("minDuration", f.minDuration)
                putNullable("maxDuration", f.maxDuration)
                put("sort", f.sort.name)
            })
        }
        presetPrefs.edit().putString(PRESETS_KEY, array.toString()).apply()
    }

    override fun onCleared() {
        ThumbnailMemoryCache.clear()
        engine.close()
        super.onCleared()
    }

    companion object {
        private const val ENTRY_CODE = "21031991"
        const val PANIC_CODE = "112"
        private const val PRESETS_KEY = "presets_json"
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optChatIds(): Set<Long> {
    val result = linkedSetOf<Long>()
    optJSONArray("chatIds")?.let { array ->
        for (i in 0 until array.length()) {
            runCatching { array.getLong(i) }.getOrNull()?.let(result::add)
        }
    }
    if (result.isNotEmpty()) return result

    // Backwards compatibility with v0.8.0 and older single-chat presets.
    optLongNullable("chatId")?.let(result::add)
    return result
}

private fun JSONObject.optLongNullable(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optIntNullable(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

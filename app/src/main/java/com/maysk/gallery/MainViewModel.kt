package com.maysk.gallery

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maysk.gallery.ai.AiIndex
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaRepository
import com.maysk.gallery.data.SettingsRepository
import com.maysk.gallery.data.UiSettings
import com.maysk.gallery.update.UpdateChecker
import com.maysk.gallery.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val repo = SettingsRepository(ctx)
    private val index = AiIndex(ctx)

    val settings: StateFlow<UiSettings> =
        repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UiSettings())

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** key снимка -> метки ИИ */
    private val _labels = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val labels: StateFlow<Map<String, List<String>>> = _labels.asStateFlow()

    /** (готово, всего) пока идёт анализ, иначе null */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var indexJob: Job? = null

    init {
        // автопроверка обновлений при запуске
        viewModelScope.launch {
            delay(1500)
            if (repo.flow.first().autoUpdate) checkUpdate(manual = false)
        }
    }

    fun reload() {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { MediaRepository.load(ctx) }
            _labels.value = index.loadCache()
            _loaded.value = true
            startIndexing()
        }
    }

    fun startIndexing() {
        if (indexJob?.isActive == true) return
        indexJob = viewModelScope.launch(Dispatchers.Default) {
            val cache = index.loadCache().toMutableMap()
            val candidates = _items.value
            val todo = candidates.filter { it.key !in cache }
            if (todo.isEmpty()) {
                _progress.value = null
                return@launch
            }
            val total = candidates.size
            var done = total - todo.size
            _progress.value = done to total
            var sinceSave = 0
            for (item in todo) {
                val labels = index.labelsFor(item)
                // null — не удалось прочитать; пометим пустым, чтобы не пробовать вечно
                cache[item.key] = labels ?: emptyList()
                done++
                sinceSave++
                if (sinceSave >= 20) {
                    sinceSave = 0
                    _labels.value = cache.toMap()
                    _progress.value = done to total
                    index.save(cache)
                }
            }
            _labels.value = cache.toMap()
            index.save(cache)
            _progress.value = null
        }
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.set(key, value) }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch { repo.toggleFavorite(item.key) }
    }

    fun checkUpdate(manual: Boolean) {
        viewModelScope.launch {
            UpdateChecker.check()
                .onSuccess { info ->
                    if (info != null) _update.value = info
                    else if (manual) _message.value = "У вас последняя версия (${BuildConfig.VERSION_NAME})"
                }
                .onFailure {
                    if (manual) _message.value = "Не удалось проверить обновления"
                }
        }
    }

    fun dismissUpdate() { _update.value = null }
    fun messageShown() { _message.value = null }

    override fun onCleared() {
        index.close()
        super.onCleared()
    }
}

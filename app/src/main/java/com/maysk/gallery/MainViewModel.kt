package com.maysk.gallery

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maysk.gallery.ai.AiIndex
import com.maysk.gallery.data.MediaItem
import com.maysk.gallery.data.MediaMeta
import com.maysk.gallery.data.MediaMetaIndex
import com.maysk.gallery.data.FaceInfo
import com.maysk.gallery.data.MediaRepository
import com.maysk.gallery.data.SelfieIndex
import com.maysk.gallery.data.SecretVault
import com.maysk.gallery.data.SettingsRepository
import com.maysk.gallery.data.UiSettings
import com.maysk.gallery.update.UpdateChecker
import com.maysk.gallery.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    private val metaIndex = MediaMetaIndex(ctx)
    val vault = SecretVault(ctx)

    private val _secret = MutableStateFlow<List<MediaItem>>(emptyList())
    val secret: StateFlow<List<MediaItem>> = _secret.asStateFlow()

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

    /** key фото -> GPS и признак селфи (заполняется в фоне и кэшируется) */
    private val _meta = MutableStateFlow<Map<String, MediaMeta>>(emptyMap())
    val meta: StateFlow<Map<String, MediaMeta>> = _meta.asStateFlow()

    private val selfieIndex = SelfieIndex(ctx)
    private val _faces = MutableStateFlow<Map<String, FaceInfo>>(emptyMap())
    val faces: StateFlow<Map<String, FaceInfo>> = _faces.asStateFlow()
    private val _selfieYes = MutableStateFlow(selfieIndex.marksYes())
    val selfieYes: StateFlow<Set<String>> = _selfieYes.asStateFlow()
    private val _selfieNo = MutableStateFlow(selfieIndex.marksNo())
    val selfieNo: StateFlow<Set<String>> = _selfieNo.asStateFlow()
    /** (проверено, всего) пока идёт поиск лиц, иначе null */
    private val _faceProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val faceProgress: StateFlow<Pair<Int, Int>?> = _faceProgress.asStateFlow()
    private var faceJob: Job? = null

    /** Ручная отметка: фото — селфи / не селфи. */
    fun markSelfie(item: MediaItem, selfie: Boolean) {
        selfieIndex.mark(item.key, selfie)
        _selfieYes.value = selfieIndex.marksYes()
        _selfieNo.value = selfieIndex.marksNo()
    }

    /** Фоновый поиск лиц по новым фото; результаты кэшируются, повторно снимки не анализируются. */
    private fun startFaceScan() {
        if (faceJob?.isActive == true) return
        faceJob = viewModelScope.launch(Dispatchers.Default) {
            val cache = (if (_faces.value.isEmpty()) selfieIndex.loadCache() else _faces.value).toMutableMap()
            _faces.value = cache.toMap()
            val todo = _items.value.filter { !it.isVideo && it.key !in cache }
            if (todo.isEmpty()) {
                _faceProgress.value = null
                return@launch
            }
            var done = 0
            for (item in todo) {
                ensureActive()
                cache[item.key] = selfieIndex.detect(item)
                done++
                if (done % 25 == 0) {
                    _faces.value = cache.toMap()
                    selfieIndex.save(cache)
                    _faceProgress.value = done to todo.size
                }
            }
            _faces.value = cache.toMap()
            selfieIndex.save(cache)
            _faceProgress.value = null
        }
    }

    private var indexJob: Job? = null
    private var metaJob: Job? = null
    @Volatile private var canReadLocation = false

    private val prefs = ctx.getSharedPreferences("meta", android.content.Context.MODE_PRIVATE)

    /** Вызывать после выдачи ACCESS_MEDIA_LOCATION: раньше система скрывала GPS, старый кэш без координат недействителен. */
    fun setLocationAccess(granted: Boolean) {
        val changed = canReadLocation != granted
        canReadLocation = granted
        if (granted && changed) metaJob?.cancel()
        if (granted && _loaded.value) startMetaScan()
    }

    fun reload() {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { MediaRepository.load(ctx) }
            _labels.value = index.loadCache()
            _loaded.value = true
            startIndexing()
            startMetaScan()
            startFaceScan()
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

    private fun startMetaScan() {
        if (metaJob?.isActive == true) return
        metaJob = viewModelScope.launch(Dispatchers.IO) {
            val withLoc = canReadLocation
            val cache = (if (_meta.value.isEmpty()) metaIndex.loadCache() else _meta.value).toMutableMap()
            val needRescan = withLoc && !prefs.getBoolean("scanned_with_location", false)
            if (needRescan) cache.entries.removeAll { !it.value.hasGeo }
            val todo = _items.value.filter { !it.isVideo && it.key !in cache }
            var n = 0
            for (item in todo) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                metaIndex.read(item, withLoc)?.let { cache[item.key] = it }
                if (++n % 150 == 0) { _meta.value = cache.toMap(); metaIndex.save(cache) }
            }
            _meta.value = cache.toMap()
            metaIndex.save(cache)
            if (withLoc) prefs.edit().putBoolean("scanned_with_location", true).apply()
        }
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repo.set(key, value) }
    }

    fun resetLook() {
        viewModelScope.launch { repo.resetLook() }
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

    fun refreshSecret() {
        viewModelScope.launch { _secret.value = withContext(Dispatchers.IO) { vault.list() } }
    }

    /** Копирует файл в сейф. Оригинал потом удаляет UI (нужно подтверждение системы). */
    suspend fun hideToVault(item: MediaItem): Boolean {
        val ok = withContext(Dispatchers.IO) { vault.hide(item) }
        refreshSecret()
        return ok
    }

    fun restoreFromVault(item: MediaItem, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { vault.restore(item) }
            refreshSecret()
            if (ok) reload()
            done(ok)
        }
    }

    fun deleteFromVault(item: MediaItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { vault.delete(item) }
            refreshSecret()
        }
    }

    fun dismissUpdate() { _update.value = null }
    fun messageShown() { _message.value = null }

    override fun onCleared() {
        index.close()
        selfieIndex.close()
        super.onCleared()
    }
}

package com.atmaca.gallery

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CollectionMode { MEDIA, TAB, ALBUM, TRASH }

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val mode: CollectionMode = CollectionMode.MEDIA,
    val albumPath: String? = null,
    val albumBucketId: Long = 0L,
    val albumBucketName: String? = null,
    val items: List<GalleryMedia> = emptyList(),
    val albums: List<GalleryAlbum> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val source = VerifiedMediaLibrary(application)
    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()
    private var inventory = emptyList<GalleryMedia>()
    private var loadJob: Job? = null
    private var refreshJob: Job? = null
    private var started = false
    private var lastRefresh = 0L
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleRefresh() }
    }

    init {
        resolver.registerContentObserver(Uri.parse("content://media"), true, observer)
    }

    fun start() {
        if (!started) { started = true; reload() }
    }

    fun refreshOnResume() {
        if (!started) start()
        else if (loadJob?.isActive != true && SystemClock.elapsedRealtime() - lastRefresh > 2000) reload()
    }

    private fun scheduleRefresh() {
        if (!started) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { delay(600); reload() }
    }

    fun openMedia() {
        _state.value = _state.value.copy(mode = CollectionMode.MEDIA, albumPath = null, albumBucketId = 0L, albumBucketName = null)
        publish()
        start()
    }

    fun switchTab(tab: GalleryTab) {
        _state.value = _state.value.copy(tab = tab, mode = CollectionMode.TAB, albumPath = null, albumBucketId = 0L, albumBucketName = null)
        publish()
    }

    fun openAlbum(album: GalleryAlbum) {
        _state.value = _state.value.copy(mode = CollectionMode.ALBUM, albumPath = album.relativePath,
            albumBucketId = album.bucketId, albumBucketName = album.bucketName ?: album.name)
        publish()
    }

    fun openAlbum(relativePath: String) {
        _state.value = _state.value.copy(mode = CollectionMode.ALBUM, albumPath = normalizeRelativePath(relativePath),
            albumBucketId = 0L, albumBucketName = null)
        publish()
    }

    fun openTrash() {
        _state.value = _state.value.copy(mode = CollectionMode.TRASH)
        publish()
    }

    fun reload() {
        loadJob?.cancel()
        inventory = emptyList()
        _state.value = _state.value.copy(items = emptyList(), albums = emptyList(), loading = true, hasMore = false, error = null)
        loadJob = viewModelScope.launch {
            try {
                val incoming = ArrayList<GalleryMedia>()
                var lastPublish = 0L
                val result = source.load { batch ->
                    incoming.addAll(batch)
                    val now = SystemClock.elapsedRealtime()
                    if (lastPublish == 0L || now - lastPublish >= 250) {
                        val snapshot = incoming.toList()
                        withContext(Dispatchers.Main) { inventory = snapshot; publish() }
                        lastPublish = now
                    }
                }
                inventory = result.items
                _state.value = _state.value.copy(loading = false, error = result.errors.takeIf { it.isNotEmpty() }?.joinToString("\n"))
                publish()
                lastRefresh = SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Medya okunamadı")
            }
        }
    }

    // The entire index loads automatically. Grid and viewer no longer control retrieval.
    fun loadNextPage() = Unit

    fun removeItemsByIds(ids: Set<Long>) {
        if (ids.isEmpty()) return
        inventory = inventory.filter { it.id !in ids }
        publish()
    }

    private fun publish() {
        val snapshot = _state.value
        val visible = inventory.filter { item ->
            if (snapshot.mode == CollectionMode.TRASH) item.isTrashed
            else !item.isTrashed && when (snapshot.mode) {
                CollectionMode.MEDIA -> true
                CollectionMode.TAB -> item.isVideo == (snapshot.tab == GalleryTab.VIDEOS)
                CollectionMode.ALBUM -> belongsToAlbum(item, snapshot.albumPath, snapshot.albumBucketId, snapshot.albumBucketName)
                CollectionMode.TRASH -> false
            }
        }.sortedWith(compareByDescending<GalleryMedia> { it.dateAdded }.thenByDescending { it.id })
        _state.value = snapshot.copy(items = visible, albums = verifiedAlbums(inventory), hasMore = false)
    }

    override fun onCleared() {
        resolver.unregisterContentObserver(observer)
        super.onCleared()
    }
}

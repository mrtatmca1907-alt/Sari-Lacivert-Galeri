package com.atmaca.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CollectionMode { MEDIA, TAB, ALBUM, TRASH }

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
    val mode: CollectionMode = CollectionMode.MEDIA,
    val albumPath: String? = null,
    val items: List<GalleryMedia> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application)
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    fun start() {
        if (_state.value.items.isEmpty() && !_state.value.loading) reload()
    }

    fun openMedia() {
        val current = _state.value
        if (current.mode == CollectionMode.MEDIA && current.items.isNotEmpty()) return
        _state.value = GalleryUiState(mode = CollectionMode.MEDIA)
        loadNextPage()
    }

    fun switchTab(tab: GalleryTab) {
        val current = _state.value
        if (current.mode == CollectionMode.TAB && current.tab == tab && current.items.isNotEmpty()) return
        _state.value = GalleryUiState(tab = tab, mode = CollectionMode.TAB)
        loadNextPage()
    }

    fun openAlbum(relativePath: String) {
        _state.value = GalleryUiState(
            tab = GalleryTab.PHOTOS,
            mode = CollectionMode.ALBUM,
            albumPath = normalizeRelativePath(relativePath)
        )
        loadNextPage()
    }

    fun openTrash() {
        _state.value = GalleryUiState(
            tab = GalleryTab.PHOTOS,
            mode = CollectionMode.TRASH
        )
        loadNextPage()
    }

    fun reload() {
        _state.value = _state.value.copy(items = emptyList(), hasMore = true, error = null)
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loading || !current.hasMore) return
        _state.value = current.copy(loading = true, error = null)

        viewModelScope.launch {
            val snapshot = _state.value
            runCatching {
                when (snapshot.mode) {
                    CollectionMode.MEDIA -> repository.loadMixedPage(
                        offset = snapshot.items.size,
                        limit = MediaStoreRepository.PAGE_SIZE
                    )
                    CollectionMode.TAB -> repository.loadPage(
                        tab = snapshot.tab,
                        offset = snapshot.items.size,
                        limit = MediaStoreRepository.PAGE_SIZE
                    )
                    CollectionMode.ALBUM -> repository.loadMixedPage(
                        offset = snapshot.items.size,
                        limit = MediaStoreRepository.PAGE_SIZE,
                        albumPath = snapshot.albumPath
                    )
                    CollectionMode.TRASH -> repository.loadMixedPage(
                        offset = snapshot.items.size,
                        limit = MediaStoreRepository.PAGE_SIZE,
                        trashedOnly = true
                    )
                }
            }.onSuccess { page ->
                val now = _state.value
                if (!sameCollection(now, snapshot)) return@onSuccess
                val existing = now.items.asSequence().map { "${it.isVideo}:${it.id}" }.toHashSet()
                val uniquePage = page.filter { existing.add("${it.isVideo}:${it.id}") }
                _state.value = now.copy(
                    items = now.items + uniquePage,
                    loading = false,
                    hasMore = page.size == MediaStoreRepository.PAGE_SIZE,
                    error = null
                )
            }.onFailure { throwable ->
                val now = _state.value
                if (!sameCollection(now, snapshot)) return@onFailure
                _state.value = now.copy(
                    loading = false,
                    error = throwable.message ?: "Medya okunamadı"
                )
            }
        }
    }

    private fun sameCollection(a: GalleryUiState, b: GalleryUiState): Boolean =
        a.mode == b.mode && a.tab == b.tab && a.albumPath == b.albumPath
}

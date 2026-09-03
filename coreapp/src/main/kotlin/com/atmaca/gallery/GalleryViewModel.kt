package com.atmaca.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val tab: GalleryTab = GalleryTab.PHOTOS,
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

    fun switchTab(tab: GalleryTab) {
        if (_state.value.tab == tab) return
        _state.value = GalleryUiState(tab = tab)
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
                repository.loadPage(
                    tab = snapshot.tab,
                    offset = snapshot.items.size,
                    limit = MediaStoreRepository.PAGE_SIZE
                )
            }.onSuccess { page ->
                val now = _state.value
                if (now.tab != snapshot.tab) return@onSuccess
                _state.value = now.copy(
                    items = now.items + page,
                    loading = false,
                    hasMore = page.size == MediaStoreRepository.PAGE_SIZE,
                    error = null
                )
            }.onFailure { throwable ->
                val now = _state.value
                if (now.tab != snapshot.tab) return@onFailure
                _state.value = now.copy(
                    loading = false,
                    error = throwable.message ?: "Medya okunamadı"
                )
            }
        }
    }
}

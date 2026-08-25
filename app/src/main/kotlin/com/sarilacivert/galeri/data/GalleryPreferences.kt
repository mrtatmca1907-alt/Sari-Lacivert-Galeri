package com.sarilacivert.galeri.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.galleryDataStore by preferencesDataStore(name = "gallery_preferences")

class GalleryPreferences(private val context: Context) {
    private object Keys {
        val favorites = stringSetPreferencesKey("favorites")
        val gridColumns = intPreferencesKey("grid_columns")
        val slideshowSeconds = intPreferencesKey("slideshow_seconds")
        val showImages = booleanPreferencesKey("show_images")
        val showVideos = booleanPreferencesKey("show_videos")
        val duplicateDistance = intPreferencesKey("duplicate_distance")
        val defaultMediaSort = stringPreferencesKey("default_media_sort")
        val defaultAlbumSort = stringPreferencesKey("default_album_sort")
    }

    val favorites: Flow<Set<String>> = context.galleryDataStore.data.map {
        it[Keys.favorites] ?: emptySet()
    }

    val gridColumns: Flow<Int> = context.galleryDataStore.data.map {
        (it[Keys.gridColumns] ?: 3).coerceIn(3, 5)
    }

    val slideshowSeconds: Flow<Int> = context.galleryDataStore.data.map {
        (it[Keys.slideshowSeconds] ?: 3).coerceIn(2, 10)
    }

    val showImages: Flow<Boolean> = context.galleryDataStore.data.map {
        it[Keys.showImages] ?: true
    }

    val showVideos: Flow<Boolean> = context.galleryDataStore.data.map {
        it[Keys.showVideos] ?: true
    }

    val duplicateDistance: Flow<Int> = context.galleryDataStore.data.map {
        (it[Keys.duplicateDistance] ?: 8).coerceIn(2, 16)
    }

    val defaultMediaSort: Flow<MediaSort> = context.galleryDataStore.data.map {
        runCatching { MediaSort.valueOf(it[Keys.defaultMediaSort] ?: MediaSort.NEWEST.name) }
            .getOrDefault(MediaSort.NEWEST)
    }

    val defaultAlbumSort: Flow<AlbumSort> = context.galleryDataStore.data.map {
        runCatching { AlbumSort.valueOf(it[Keys.defaultAlbumSort] ?: AlbumSort.NEWEST.name) }
            .getOrDefault(AlbumSort.NEWEST)
    }

    suspend fun toggleFavorite(uri: String): Boolean {
        var nowFavorite = false
        context.galleryDataStore.edit { prefs ->
            val current = (prefs[Keys.favorites] ?: emptySet()).toMutableSet()
            nowFavorite = if (uri in current) {
                current.remove(uri)
                false
            } else {
                current.add(uri)
                true
            }
            prefs[Keys.favorites] = current
        }
        return nowFavorite
    }

    suspend fun setFavorite(uri: String, favorite: Boolean) {
        context.galleryDataStore.edit { prefs ->
            val current = (prefs[Keys.favorites] ?: emptySet()).toMutableSet()
            if (favorite) current.add(uri) else current.remove(uri)
            prefs[Keys.favorites] = current
        }
    }

    suspend fun setGridColumns(value: Int) {
        context.galleryDataStore.edit { it[Keys.gridColumns] = value.coerceIn(3, 5) }
    }

    suspend fun setSlideshowSeconds(value: Int) {
        context.galleryDataStore.edit { it[Keys.slideshowSeconds] = value.coerceIn(2, 10) }
    }

    suspend fun setShowImages(value: Boolean) {
        context.galleryDataStore.edit { it[Keys.showImages] = value }
    }

    suspend fun setShowVideos(value: Boolean) {
        context.galleryDataStore.edit { it[Keys.showVideos] = value }
    }

    suspend fun setDuplicateDistance(value: Int) {
        context.galleryDataStore.edit { it[Keys.duplicateDistance] = value.coerceIn(2, 16) }
    }

    suspend fun setDefaultMediaSort(value: MediaSort) {
        context.galleryDataStore.edit { it[Keys.defaultMediaSort] = value.name }
    }

    suspend fun setDefaultAlbumSort(value: AlbumSort) {
        context.galleryDataStore.edit { it[Keys.defaultAlbumSort] = value.name }
    }
}

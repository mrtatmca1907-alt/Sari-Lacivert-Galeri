package com.sarilacivert.galeri.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateTaken: Long,
    val duration: Long,
    val width: Int,
    val height: Int,
    val albumPath: String,
    val albumName: String,
    val isVideo: Boolean,
    val isTrashed: Boolean = false
)

data class Album(
    val name: String,
    val path: String,
    val count: Int,
    val coverUri: Uri,
    val hasVideo: Boolean,
    val newestDate: Long
)

data class DuplicateGroup(
    val kind: DuplicateKind,
    val items: List<MediaItem>
)

enum class DuplicateKind {
    EXACT,
    SIMILAR
}

enum class MediaSort {
    NEWEST,
    OLDEST,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC
}

enum class AlbumSort {
    NEWEST,
    NAME,
    COUNT
}

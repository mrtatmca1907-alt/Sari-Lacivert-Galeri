package com.atmaca.gallery

enum class GalleryBackAction { CLEAR_SELECTION, CLOSE_ALBUM, EXIT }

fun galleryBackAction(selectedCount: Int, inAlbum: Boolean): GalleryBackAction = when {
    selectedCount > 0 -> GalleryBackAction.CLEAR_SELECTION
    inAlbum -> GalleryBackAction.CLOSE_ALBUM
    else -> GalleryBackAction.EXIT
}

fun toolUsesInternalAlbumPicker(tool: AtmacaToolPage): Boolean = true

fun shouldRenderOuterToolDialog(showInternalAlbumPicker: Boolean): Boolean = !showInternalAlbumPicker

fun keepToolDialogOpenForBackgroundProgress(tool: AtmacaToolPage): Boolean =
    tool == AtmacaToolPage.VIDEO_FRAMES

fun albumGridKey(album: GalleryAlbum): String = buildString {
    append(album.relativePath.trim())
    append('|')
    append(album.bucketId)
    append('|')
    append(album.bucketName.orEmpty().trim())
    append('|')
    append(album.name.trim())
}

fun dragSelectionIndexes(fromIndex: Int, toIndex: Int): List<Int> {
    if (fromIndex < 0 || toIndex < 0) return emptyList()
    return if (fromIndex <= toIndex) {
        (fromIndex..toIndex).toList()
    } else {
        (fromIndex downTo toIndex).toList()
    }
}

fun videoFrameProgressText(done: Int, total: Int): String =
    if (total <= 0) "Kareler hazırlanıyor" else "${done.coerceAtLeast(0)} / ${total.coerceAtLeast(0)} kare"

fun screenshotSourceSelectionEnabled(): Boolean = true

fun albumOpenUsesSeparateMediaCollections(): Boolean = true

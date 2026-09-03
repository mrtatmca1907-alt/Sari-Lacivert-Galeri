package com.atmaca.gallery

fun toolUsesInternalAlbumPicker(tool: AtmacaToolPage): Boolean = true

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

package com.atmaca.gallery

fun toolUsesInternalAlbumPicker(tool: AtmacaToolPage): Boolean =
    tool == AtmacaToolPage.PERSON_CROP || tool == AtmacaToolPage.PACKAGER

fun dragSelectionIndexes(fromIndex: Int, toIndex: Int): List<Int> {
    if (fromIndex < 0 || toIndex < 0) return emptyList()
    return if (fromIndex <= toIndex) {
        (fromIndex..toIndex).toList()
    } else {
        (fromIndex downTo toIndex).toList()
    }
}

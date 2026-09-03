package com.atmaca.gallery

enum class ViewerTapAction { TOGGLE_CHROME }

fun photoViewerTopActions(): List<String> = listOf("Geri", "Ad", "Döndür", "Düzenle", "Diğer")
fun photoViewerBottomActions(): List<String> = listOf("Favori", "Düzenle", "Paylaş", "Çöp", "Bilgi", "Slayt")
fun viewerDoubleTapAction(): ViewerTapAction = ViewerTapAction.TOGGLE_CHROME

class SlideshowController(
    private val count: Int,
    private val loop: Boolean
) {
    fun canAdvance(index: Int): Boolean {
        if (count <= 1) return false
        return loop || index.coerceAtLeast(0) < count - 1
    }

    fun nextIndex(index: Int): Int {
        if (count <= 0) return 0
        val safeIndex = index.coerceIn(0, count - 1)
        if (safeIndex < count - 1) return safeIndex + 1
        return if (loop) 0 else count - 1
    }
}

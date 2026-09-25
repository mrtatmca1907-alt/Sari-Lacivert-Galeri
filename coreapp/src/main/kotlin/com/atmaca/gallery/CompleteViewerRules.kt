package com.atmaca.gallery

enum class ViewerTapAction { ZOOM_RESET }

fun photoViewerTopActions(): List<String> = listOf("Geri", "Ad", "Döndür", "Düzenle", "Diğer")
fun photoViewerBottomActions(): List<String> = listOf("Favori", "Düzenle", "Paylaş", "Çöp", "Bilgi", "Slayt")
fun viewerDoubleTapAction(): ViewerTapAction = ViewerTapAction.ZOOM_RESET

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

fun slideshowPrefetchIndices(current: Int, count: Int, loop: Boolean, ahead: Int = 2): List<Int> {
    if (count <= 1 || ahead <= 0) return emptyList()
    val safeCurrent = current.coerceIn(0, count - 1)
    val result = ArrayList<Int>(ahead)
    var index = safeCurrent
    repeat(ahead) {
        val next = when {
            index < count - 1 -> index + 1
            loop -> 0
            else -> return@repeat
        }
        if (next == safeCurrent || next in result) return@repeat
        result += next
        index = next
    }
    return result
}

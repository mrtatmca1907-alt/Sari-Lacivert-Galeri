package com.sarilacivert.galeri.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

internal object DragSelectionRegistry {
    val selected = mutableStateMapOf<String, Boolean>()
    val bounds = mutableMapOf<String, Rect>()

    fun hasSelection(): Boolean = selected.isNotEmpty()

    fun isSelected(key: String): Boolean = selected[key] == true

    fun select(key: String) {
        selected[key] = true
    }

    fun toggle(key: String) {
        if (isSelected(key)) selected.remove(key) else selected[key] = true
    }

    fun selectAt(pointInWindow: Offset) {
        bounds.entries.firstOrNull { (_, rect) -> rect.contains(pointInWindow) }?.key?.let(::select)
    }

    fun clear() {
        selected.clear()
    }
}

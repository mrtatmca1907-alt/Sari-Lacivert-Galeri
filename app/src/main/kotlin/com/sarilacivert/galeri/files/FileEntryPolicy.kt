package com.sarilacivert.galeri.files

object FileEntryPolicy {
    data class Entry(val name: String, val isDirectory: Boolean)

    private val previewableExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "heic", "heif",
        "mp4", "mkv", "webm", "mov", "avi", "3gp"
    )

    fun compare(a: Entry, b: Entry): Int {
        if (a.isDirectory != b.isDirectory) {
            return if (a.isDirectory) -1 else 1
        }
        return a.name.compareTo(b.name, ignoreCase = true)
    }

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase()
    }

    fun isPreviewable(name: String): Boolean = extensionOf(name) in previewableExtensions
}

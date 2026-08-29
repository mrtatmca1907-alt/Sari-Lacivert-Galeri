package com.sarilacivert.galeri.files

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileBrowserPolicy {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v")

    fun parentPath(currentPath: String, rootPath: String): String {
        val root = rootPath.trimEnd('/')
        val current = currentPath.trimEnd('/')
        if (current == root || !current.startsWith("$root/")) return root
        val parent = current.substringBeforeLast('/', root)
        return if (parent.length < root.length || !parent.startsWith(root)) root else parent
    }

    fun isImage(name: String): Boolean = extension(name) in imageExtensions

    fun isVideo(name: String): Boolean = extension(name) in videoExtensions

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(1, units.size)
        val value = bytes / 1024.0.pow(group.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[group - 1])
    }

    private fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }
}

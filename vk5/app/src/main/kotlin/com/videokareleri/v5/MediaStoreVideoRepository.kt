package com.videokareleri.v5

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import java.util.LinkedHashMap

class MediaStoreVideoRepository(private val resolver: ContentResolver) {
    fun loadFolderCounts(): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media.RELATIVE_PATH), null, null,
            "${MediaStore.Video.Media.RELATIVE_PATH} COLLATE NOCASE ASC")?.use { c ->
            val pathCol = c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val raw = if (pathCol >= 0) c.getString(pathCol) else null
                val path = raw?.takeIf { it.isNotBlank() } ?: "Ana depolama"
                out[path] = (out[path] ?: 0) + 1
            }
        }
        return out
    }

    fun openSelectedVideos(selectedPaths: Array<String>?): Cursor? {
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RELATIVE_PATH)
        val all = selectedPaths.orEmpty()
        val paths = all.filter { it != "Ana depolama" }
        val includeRoot = all.contains("Ana depolama")
        val parts = mutableListOf<String>(); val args = mutableListOf<String>()
        paths.forEach { parts += "${MediaStore.Video.Media.RELATIVE_PATH}=?"; args += it }
        if (includeRoot) parts += "(${MediaStore.Video.Media.RELATIVE_PATH} IS NULL OR ${MediaStore.Video.Media.RELATIVE_PATH}='')"
        val selection = parts.takeIf { it.isNotEmpty() }?.joinToString(" OR ")
        return resolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection,
            args.takeIf { it.isNotEmpty() }?.toTypedArray(), "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC")
    }

    companion object {
        fun videoUri(id: Long): Uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
    }
}

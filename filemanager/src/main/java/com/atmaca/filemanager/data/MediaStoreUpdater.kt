package com.atmaca.filemanager.data

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Optional, targeted MediaStore synchronization. Never scans a directory tree: callers must pass
 * only the concrete files that changed. The file browser itself does not depend on this to refresh.
 */
class MediaStoreUpdater(private val context: Context) {
    suspend fun updateFiles(paths: Collection<String>): Set<String> {
        val files = paths.asSequence()
            .map(::File)
            .filter { it.isFile || !it.exists() }
            .map { it.absolutePath }
            .distinct()
            .toList()
        if (files.isEmpty()) return emptySet()

        return suspendCancellableCoroutine { continuation ->
            val completed = linkedSetOf<String>()
            var remaining = files.size
            MediaScannerConnection.scanFile(context, files.toTypedArray(), null) { path, _ ->
                synchronized(completed) {
                    completed += path
                    remaining--
                    if (remaining == 0 && continuation.isActive) continuation.resume(completed.toSet())
                }
            }
        }
    }
}

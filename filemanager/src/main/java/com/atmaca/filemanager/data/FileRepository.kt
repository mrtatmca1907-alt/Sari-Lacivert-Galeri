package com.atmaca.filemanager.data

import com.atmaca.filemanager.core.FileEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    data class DirectoryPage(
        val items: List<FileEntry>,
        val offset: Int,
        val nextOffset: Int,
        val hasMore: Boolean
    )

    suspend fun listDirectory(directory: File, offset: Int = 0, limit: Int = 200): DirectoryPage =
        withContext(ioDispatcher) {
            require(offset >= 0) { "offset must be >= 0" }
            require(limit in 1..1000) { "limit must be 1..1000" }

            val children = directory.listFiles()?.toList().orEmpty()
                .sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )
            val safeOffset = offset.coerceAtMost(children.size)
            val end = (safeOffset + limit).coerceAtMost(children.size)
            val page = children.subList(safeOffset, end).map { file ->
                FileEntry(
                    file = file,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    modifiedAt = file.lastModified()
                )
            }
            DirectoryPage(
                items = page,
                offset = safeOffset,
                nextOffset = end,
                hasMore = end < children.size
            )
        }

    /**
     * This deliberately returns exactly the requested folders. It never promotes them to
     * /storage/emulated/0 and never recursively enumerates storage after an operation.
     */
    suspend fun refreshDirectories(paths: Set<String>): Set<String> = withContext(ioDispatcher) {
        paths.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { File(it).absolutePath }
            .toCollection(linkedSetOf())
    }
}

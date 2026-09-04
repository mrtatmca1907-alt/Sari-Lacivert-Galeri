package com.atmaca.gallery

import java.io.File

internal const val MEDIA_TOOL_KEY_QUEUE_FILE = "queue_file"
internal const val MEDIA_TOOL_KEY_TOOL = "tool"
internal const val MEDIA_TOOL_KEY_OPTION = "option"
internal const val MEDIA_TOOL_KEY_DONE = "done"
internal const val MEDIA_TOOL_KEY_TOTAL = "total"
internal const val MEDIA_TOOL_KEY_CREATED = "created"
internal const val MEDIA_TOOL_KEY_SKIPPED = "skipped"
internal const val MEDIA_TOOL_KEY_FAILED = "failed"
internal const val MEDIA_TOOL_WORK_TAG = "atmaca_media_tools"

internal fun toolUsesBackgroundWorker(tool: AtmacaToolPage): Boolean = when (tool) {
    AtmacaToolPage.PERSON_CROP, AtmacaToolPage.PACKAGER, AtmacaToolPage.VIDEO_FRAMES -> true
}
internal fun shouldCancelMediaToolWork(explicitCancel: Boolean): Boolean = explicitCancel
internal fun mediaToolInputKeys(): Set<String> = setOf(MEDIA_TOOL_KEY_QUEUE_FILE, MEDIA_TOOL_KEY_TOOL, MEDIA_TOOL_KEY_OPTION)
internal fun writeMediaToolQueue(file: File, values: List<String>): Boolean = runCatching {
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { output -> values.asSequence().filter(String::isNotBlank).distinct().forEach(output::appendLine) }
    true
}.getOrDefault(false)
internal fun readMediaToolQueue(file: File): List<String> = file.useLines { it.filter(String::isNotBlank).distinct().toList() }

data class AlbumQueryOutcome(val albums: List<GalleryAlbum>, val imageFailed: Boolean, val videoFailed: Boolean) {
    val completelyFailed: Boolean get() = imageFailed && videoFailed
}
internal fun albumQueryOutcome(albums: List<GalleryAlbum>, imageFailed: Boolean, videoFailed: Boolean) =
    AlbumQueryOutcome(albums, imageFailed, videoFailed)

internal fun albumQuerySortFallbacks(): List<String?> = listOf(
    "date_added DESC, _id DESC",
    "date_added DESC",
    null
)

internal fun albumScanShouldContinue(received: Int, pageSize: Int): Boolean =
    pageSize > 0 && received == pageSize

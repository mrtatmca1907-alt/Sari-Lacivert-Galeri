package com.atmaca.gallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

fun toolAcceptsMime(tool: AtmacaToolPage, mimeType: String?): Boolean {
    val mime = mimeType?.lowercase()?.trim().orEmpty()
    return when (tool) {
        AtmacaToolPage.PERSON_CROP -> mime.startsWith("image/")
        AtmacaToolPage.PACKAGER -> mime.startsWith("image/") || mime.startsWith("video/")
        AtmacaToolPage.VIDEO_FRAMES -> mime.startsWith("video/")
    }
}

fun toolAcceptsDocument(tool: AtmacaToolPage, mimeType: String?, displayName: String?): Boolean {
    val declared = mimeType?.trim()?.lowercase().orEmpty()
    if (declared.isNotEmpty() && declared != "application/octet-stream" && toolAcceptsMime(tool, declared)) return true
    val ext = displayName?.substringAfterLast('.', "")?.trim()?.lowercase().orEmpty()
    val inferred = when (ext) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "dng", "svg", "cr2", "cr3", "nef", "arw", "raf", "rw2", "orf", "pef", "srw" -> "image/$ext"
        "mp4", "m4v", "mov", "mkv", "webm", "avi", "3gp", "ts", "mpeg", "mpg" -> "video/$ext"
        else -> ""
    }
    return toolAcceptsMime(tool, inferred)
}

suspend fun filterToolUris(context: Context, uris: List<Uri>, tool: AtmacaToolPage): List<Uri> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    uris.distinct().filter { uri ->
        coroutineContext.ensureActive()
        val declaredMime = runCatching { resolver.getType(uri) }.getOrNull()
        val name = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
        toolAcceptsDocument(tool, declaredMime, name)
    }
}

suspend fun collectToolUrisFromTree(
    context: Context,
    treeUri: Uri,
    tool: AtmacaToolPage,
    onScanned: (Int) -> Unit = {}
): List<Uri> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return@withContext emptyList()
    val folders = ArrayDeque<String>()
    val accepted = ArrayList<Uri>()
    folders.add(rootId)
    var scanned = 0

    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME
    )

    while (folders.isNotEmpty()) {
        coroutineContext.ensureActive()
        val parentId = folders.removeFirst()
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        }.getOrNull() ?: continue

        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    if (idIndex < 0) continue
                    val documentId = cursor.getString(idIndex) ?: continue
                    val declaredMime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    if (declaredMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders.add(documentId)
                        continue
                    }

                    scanned++
                    val documentUri = runCatching {
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    }.getOrNull()
                    if (documentUri != null) {
                        val providerMime = runCatching { resolver.getType(documentUri) }.getOrNull()
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                        val effectiveMime = when {
                            !declaredMime.isNullOrBlank() && declaredMime != "application/octet-stream" -> declaredMime
                            !providerMime.isNullOrBlank() && providerMime != "application/octet-stream" -> providerMime
                            else -> declaredMime ?: providerMime
                        }
                        if (toolAcceptsDocument(tool, effectiveMime, name)) accepted += documentUri
                    }
                    if (scanned % 100 == 0) onScanned(scanned)
                }
            }
        }
    }
    onScanned(scanned)
    accepted.distinct()
}

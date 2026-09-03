package com.atmaca.gallery

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
        DocumentsContract.Document.COLUMN_MIME_TYPE
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
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    if (idIndex < 0) continue
                    val documentId = cursor.getString(idIndex) ?: continue
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        folders.add(documentId)
                    } else {
                        scanned++
                        if (toolAcceptsMime(tool, mime)) {
                            runCatching {
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                            }.getOrNull()?.let(accepted::add)
                        }
                        if (scanned % 100 == 0) onScanned(scanned)
                    }
                }
            }
        }
    }
    onScanned(scanned)
    accepted
}

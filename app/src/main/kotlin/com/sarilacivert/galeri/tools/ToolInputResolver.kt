package com.sarilacivert.galeri.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.ArrayDeque

class ToolInputResolver(private val context: Context) {
    fun persistTreePermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun stream(input: ToolInputState, mode: ToolMode): Flow<Uri> = flow {
        if (!input.scanRequested) return@flow

        if (input.directUris.isNotEmpty()) {
            for (raw in input.directUris) {
                currentCoroutineContext().ensureActive()
                val uri = Uri.parse(raw)
                val name = queryDisplayName(uri)
                if (ToolModePolicy.accepts(mode, name, context.contentResolver.getType(uri))) emit(uri)
            }
            return@flow
        }

        val treeRaw = input.treeUri ?: return@flow
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeRaw)) ?: return@flow
        val queue = ArrayDeque<DocumentFile>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val node = queue.removeFirst()
            if (node.isDirectory) {
                for (child in runCatching { node.listFiles() }.getOrDefault(emptyArray())) {
                    currentCoroutineContext().ensureActive()
                    if (child.isDirectory) queue.addLast(child)
                    else if (child.isFile && ToolModePolicy.accepts(mode, child.name.orEmpty(), child.type)) emit(child.uri)
                }
            } else if (node.isFile && ToolModePolicy.accepts(mode, node.name.orEmpty(), node.type)) {
                emit(node.uri)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }
}

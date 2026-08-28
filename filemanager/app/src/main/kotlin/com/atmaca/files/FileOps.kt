package com.atmaca.files

import android.content.Context
import androidx.documentfile.provider.DocumentFile

object FileOps {
    fun copy(context: Context, source: DocumentFile, destDir: DocumentFile): Boolean = runCatching {
        if (source.isDirectory) {
            val newDir = destDir.createDirectory(source.name ?: "Klasor") ?: return false
            source.listFiles().all { copy(context, it, newDir) }
        } else {
            val name = source.name ?: "dosya"
            val target = destDir.createFile(source.type ?: "application/octet-stream", name) ?: return false
            val resolver = context.contentResolver
            resolver.openInputStream(source.uri).use { input ->
                resolver.openOutputStream(target.uri, "w").use { output ->
                    if (input == null || output == null) return false
                    input.copyTo(output, 1024 * 256)
                }
            }
            true
        }
    }.getOrDefault(false)

    fun move(context: Context, source: DocumentFile, destDir: DocumentFile): Boolean {
        val copied = copy(context, source, destDir)
        return if (OperationRules.canDeleteSourceAfterCopy(copied)) source.delete() else false
    }
}

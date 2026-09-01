package com.atmaca.filemanager.core

import java.io.File

/** Blocking local-file primitives. Call only from an IO dispatcher. */
class LocalFileBackend {
    fun ensureDirectory(directory: File): Boolean = directory.isDirectory || directory.mkdirs()

    fun copyVerified(source: File, target: File): Boolean {
        if (!source.exists() || target.exists()) return false
        return try {
            if (source.isDirectory) {
                if (!target.mkdirs()) return false
                val children = source.listFiles() ?: emptyArray()
                for (child in children) {
                    if (!copyVerified(child, File(target, child.name))) {
                        target.deleteRecursively()
                        return false
                    }
                }
                verifyTree(source, target)
            } else {
                source.inputStream().buffered().use { input ->
                    target.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                }
                target.exists() && target.length() == source.length()
            }
        } catch (_: Exception) {
            if (target.exists()) target.deleteRecursively()
            false
        }
    }

    fun moveVerified(source: File, target: File): Boolean {
        if (!source.exists() || target.exists()) return false
        target.parentFile?.let { if (!ensureDirectory(it)) return false }

        // Fast path: same filesystem rename. It is atomic on the common local-storage case.
        if (source.renameTo(target)) return target.exists() && !source.exists()

        // Safe fallback for provider/mount boundaries: copy -> verify -> delete source.
        if (!copyVerified(source, target)) return false
        if (!verifyTree(source, target)) {
            target.deleteRecursively()
            return false
        }
        if (!delete(source)) {
            // Destination is valid but source could not be removed: do not claim a move.
            return false
        }
        return target.exists() && !source.exists()
    }

    fun delete(source: File): Boolean = try {
        if (!source.exists()) true else source.deleteRecursively() && !source.exists()
    } catch (_: Exception) {
        false
    }

    fun verifyTree(source: File, target: File): Boolean {
        if (!source.exists() || !target.exists() || source.isDirectory != target.isDirectory) return false
        if (source.isFile) return source.length() == target.length()
        val sourceChildren = source.listFiles()?.associateBy { it.name } ?: return false
        val targetChildren = target.listFiles()?.associateBy { it.name } ?: return false
        if (sourceChildren.keys != targetChildren.keys) return false
        return sourceChildren.all { (name, child) -> verifyTree(child, targetChildren.getValue(name)) }
    }
}

package com.atmaca.filemanager.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class FileOperationEngine(
    private val backend: LocalFileBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun copy(sources: List<File>, targetDirectory: File): OperationResult = withContext(ioDispatcher) {
        val succeeded = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        if (!backend.ensureDirectory(targetDirectory)) {
            sources.forEach { failed[it.absolutePath] = "Hedef klasör oluşturulamadı" }
            return@withContext OperationResult(OperationType.COPY, succeeded, failed, RefreshScope(emptySet()))
        }

        for (source in sources.distinctBy { it.absolutePath }) {
            coroutineContext.ensureActive()
            val target = File(targetDirectory, source.name)
            if (backend.copyVerified(source, target)) succeeded += target.absolutePath
            else failed[source.absolutePath] = "Kopyalama veya doğrulama başarısız"
        }

        OperationResult(
            type = OperationType.COPY,
            succeeded = succeeded,
            failed = failed,
            refresh = RefreshScope(if (succeeded.isEmpty()) emptySet() else setOf(targetDirectory.absolutePath))
        )
    }

    suspend fun move(sources: List<File>, targetDirectory: File): OperationResult = withContext(ioDispatcher) {
        val succeeded = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        val refresh = linkedSetOf<String>()
        if (!backend.ensureDirectory(targetDirectory)) {
            sources.forEach { failed[it.absolutePath] = "Hedef klasör kullanılamıyor" }
            return@withContext OperationResult(OperationType.MOVE, succeeded, failed, RefreshScope(emptySet()))
        }

        for (source in sources.distinctBy { it.absolutePath }) {
            coroutineContext.ensureActive()
            val sourceParent = source.parentFile?.absolutePath
            val target = File(targetDirectory, source.name)
            if (backend.moveVerified(source, target)) {
                succeeded += target.absolutePath
                sourceParent?.let(refresh::add)
                refresh += targetDirectory.absolutePath
            } else {
                failed[source.absolutePath] = "Taşıma doğrulanamadı; kaynak korunuyor"
            }
        }

        OperationResult(OperationType.MOVE, succeeded, failed, RefreshScope(refresh))
    }

    suspend fun delete(sources: List<File>): OperationResult = withContext(ioDispatcher) {
        val succeeded = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        val refresh = linkedSetOf<String>()

        for (source in sources.distinctBy { it.absolutePath }) {
            coroutineContext.ensureActive()
            val originalPath = source.absolutePath
            val parent = source.parentFile?.absolutePath
            if (backend.delete(source)) {
                succeeded += originalPath
                parent?.let(refresh::add)
            } else {
                failed[originalPath] = "Silme başarısız"
            }
        }

        OperationResult(OperationType.DELETE, succeeded, failed, RefreshScope(refresh))
    }
}

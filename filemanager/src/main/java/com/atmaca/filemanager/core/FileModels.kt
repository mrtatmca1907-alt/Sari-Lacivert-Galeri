package com.atmaca.filemanager.core

import java.io.File

data class FileEntry(
    val file: File,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long
)

enum class OperationType { COPY, MOVE, DELETE }

data class RefreshScope(
    val directories: Set<String>
)

data class OperationResult(
    val type: OperationType,
    val succeeded: List<String>,
    val failed: Map<String, String>,
    val refresh: RefreshScope
)

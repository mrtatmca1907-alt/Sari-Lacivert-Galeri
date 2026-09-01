package com.atmaca.filemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atmaca.filemanager.core.FileEntry
import com.atmaca.filemanager.core.FileOperationEngine
import com.atmaca.filemanager.core.LocalFileBackend
import com.atmaca.filemanager.core.OperationResult
import com.atmaca.filemanager.data.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections

class OperationGate {
    private val active = Collections.synchronizedSet(mutableSetOf<String>())

    fun tryStart(paths: Set<String>): Boolean = synchronized(active) {
        if (paths.any(active::contains)) return false
        active.addAll(paths)
        true
    }

    fun finish(paths: Set<String>) = synchronized(active) { active.removeAll(paths) }
}

class TargetedRefreshAccumulator {
    private val pending = linkedSetOf<String>()

    @Synchronized
    fun add(paths: Set<String>) {
        pending += paths.filter { it.isNotBlank() }
    }

    @Synchronized
    fun drain(): Set<String> = pending.toSet().also { pending.clear() }
}

data class BrowserState(
    val currentDirectory: File = File("/storage/emulated/0"),
    val entries: List<FileEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val activeOperationCount: Int = 0,
    val message: String? = null
)

class FileManagerViewModel(
    private val repository: FileRepository = FileRepository(),
    private val engine: FileOperationEngine = FileOperationEngine(LocalFileBackend()),
    private val gate: OperationGate = OperationGate(),
    private val refreshAccumulator: TargetedRefreshAccumulator = TargetedRefreshAccumulator()
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        loadDirectory(_state.value.currentDirectory)
    }

    fun loadDirectory(directory: File) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(currentDirectory = directory, isLoading = true, message = null)
            runCatching { repository.listDirectory(directory, offset = 0, limit = 1000) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(entries = page.items, selected = emptySet(), isLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isLoading = false, message = error.message ?: "Klasör okunamadı")
                }
        }
    }

    fun open(entry: FileEntry) {
        if (entry.isDirectory) loadDirectory(entry.file)
    }

    fun goUp() {
        _state.value.currentDirectory.parentFile?.let(::loadDirectory)
    }

    fun toggleSelection(path: String) {
        val next = _state.value.selected.toMutableSet()
        if (!next.add(path)) next.remove(path)
        _state.value = _state.value.copy(selected = next)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    fun deleteSelected() {
        val files = selectedFiles()
        runOperation(files) { engine.delete(it) }
    }

    fun copySelected(targetDirectory: File) {
        val files = selectedFiles()
        runOperation(files) { engine.copy(it, targetDirectory) }
    }

    fun moveSelected(targetDirectory: File) {
        val files = selectedFiles()
        runOperation(files) { engine.move(it, targetDirectory) }
    }

    private fun selectedFiles(): List<File> = _state.value.selected.map(::File)

    private fun runOperation(
        files: List<File>,
        block: suspend (List<File>) -> OperationResult
    ) {
        if (files.isEmpty()) return
        val paths = files.mapTo(linkedSetOf()) { it.absolutePath }
        if (!gate.tryStart(paths)) {
            _state.value = _state.value.copy(message = "Bu dosya için zaten işlem çalışıyor")
            return
        }

        _state.value = _state.value.copy(activeOperationCount = _state.value.activeOperationCount + 1)
        viewModelScope.launch {
            try {
                val result = block(files)
                refreshAccumulator.add(result.refresh.directories)
                _state.value = _state.value.copy(
                    selected = emptySet(),
                    message = when {
                        result.failed.isEmpty() -> "İşlem tamamlandı"
                        result.succeeded.isEmpty() -> "İşlem başarısız"
                        else -> "Bazı dosyalar işlenemedi"
                    }
                )
                scheduleTargetedRefresh()
            } finally {
                gate.finish(paths)
                _state.value = _state.value.copy(
                    activeOperationCount = (_state.value.activeOperationCount - 1).coerceAtLeast(0)
                )
            }
        }
    }

    private fun scheduleTargetedRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(120)
            val directories = repository.refreshDirectories(refreshAccumulator.drain())
            if (_state.value.currentDirectory.absolutePath in directories) {
                loadDirectory(_state.value.currentDirectory)
            }
        }
    }
}

package com.sarilacivert.galeri.tools

data class ToolInputState(
    val directUris: List<String> = emptyList(),
    val treeUri: String? = null,
    val scanRequested: Boolean = false
)

internal object ToolInputPolicy {
    fun folderSelected(uri: String): ToolInputState = ToolInputState(
        treeUri = uri,
        scanRequested = false
    )

    fun filesSelected(uris: List<String>): ToolInputState = ToolInputState(
        directUris = uris.filter { it.isNotBlank() }.distinct(),
        scanRequested = false
    )

    fun start(state: ToolInputState): ToolInputState = state.copy(scanRequested = true)
}

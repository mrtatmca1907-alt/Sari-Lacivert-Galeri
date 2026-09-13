package com.atmaca.hizlisil

data class DeleteStats(
    val deletedFiles: Int = 0,
    val deletedDirs: Int = 0,
    val errors: Int = 0
)

data class NodeInfo(val id: String, val isDirectory: Boolean)

interface TreeOps<H> {
    fun childrenOf(directory: H): Sequence<NodeInfo>
    fun childHandle(parent: H, child: NodeInfo): H
    fun delete(handle: H): Boolean
}

class DeleteCore<H>(private val ops: TreeOps<H>) {
    fun deleteContents(root: H, onProgress: ((DeleteStats) -> Unit)? = null): DeleteStats {
        var stats = DeleteStats()

        fun walk(directory: H) {
            try {
                for (child in ops.childrenOf(directory)) {
                    val handle = ops.childHandle(directory, child)
                    if (child.isDirectory) {
                        walk(handle)
                        stats = if (safeDelete(handle)) {
                            stats.copy(deletedDirs = stats.deletedDirs + 1)
                        } else {
                            stats.copy(errors = stats.errors + 1)
                        }
                    } else {
                        stats = if (safeDelete(handle)) {
                            stats.copy(deletedFiles = stats.deletedFiles + 1)
                        } else {
                            stats.copy(errors = stats.errors + 1)
                        }
                    }
                    onProgress?.invoke(stats)
                }
            } catch (_: Throwable) {
                stats = stats.copy(errors = stats.errors + 1)
                onProgress?.invoke(stats)
            }
        }

        walk(root)
        return stats
    }

    fun deleteSubtree(root: H, onProgress: ((DeleteStats) -> Unit)? = null): DeleteStats {
        var stats = deleteContents(root, onProgress)
        stats = if (safeDelete(root)) {
            stats.copy(deletedDirs = stats.deletedDirs + 1)
        } else {
            stats.copy(errors = stats.errors + 1)
        }
        onProgress?.invoke(stats)
        return stats
    }

    private fun safeDelete(handle: H): Boolean = try {
        ops.delete(handle)
    } catch (_: Throwable) {
        false
    }
}

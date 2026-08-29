package com.sarilacivert.galeri.data

data class BatchProgress(
    val total: Int,
    val processed: Int,
    val succeeded: Int,
    val failed: Int
) {
    init {
        require(total >= 0)
        require(succeeded >= 0)
        require(failed >= 0)
    }

    val isComplete: Boolean get() = processed >= total

    companion object {
        operator fun invoke(total: Int, processed: Int, succeeded: Int, failed: Int): BatchProgress {
            val safeTotal = total.coerceAtLeast(0)
            val safeProcessed = processed.coerceIn(0, safeTotal)
            val safeSucceeded = succeeded.coerceIn(0, safeProcessed)
            val safeFailed = failed.coerceIn(0, safeProcessed - safeSucceeded)
            return BatchProgress(
                total = safeTotal,
                processed = safeProcessed,
                succeeded = safeSucceeded,
                failed = safeFailed
            )
        }
    }
}

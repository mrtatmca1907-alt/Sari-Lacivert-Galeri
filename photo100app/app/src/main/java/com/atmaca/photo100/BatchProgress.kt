package com.atmaca.photo100

object BatchProgress {
    fun nextCursor(ids: List<Long>, currentCursor: Long = Long.MAX_VALUE): Long =
        ids.minOrNull() ?: currentCursor
}

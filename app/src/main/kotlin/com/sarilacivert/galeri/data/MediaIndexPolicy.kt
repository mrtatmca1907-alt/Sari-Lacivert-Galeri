package com.sarilacivert.galeri.data

object MediaIndexPolicy {
    fun shouldReuseCache(nowMs: Long, builtAtMs: Long, ttlMs: Long): Boolean =
        builtAtMs > 0L && nowMs >= builtAtMs && nowMs - builtAtMs <= ttlMs

    fun normalizedTimestamp(dateTaken: Long, dateAdded: Long): Long =
        maxOf(dateTaken, dateAdded)
}

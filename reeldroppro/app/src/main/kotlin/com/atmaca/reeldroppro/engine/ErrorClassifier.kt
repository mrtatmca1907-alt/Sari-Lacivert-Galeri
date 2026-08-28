package com.atmaca.reeldroppro.engine

object ErrorClassifier {
    fun classify(stderr: String, throwable: Throwable?): DownloadError {
        val combined = listOf(stderr, throwable?.message.orEmpty()).joinToString("\n").trim()
        val text = combined.lowercase()
        val message = stderr.trim().ifEmpty { throwable?.message.orEmpty().ifEmpty { "Bilinmeyen indirme hatası" } }

        return when {
            text.contains("private") || text.contains("login required") || text.contains("sign in") || text.contains("authentication") || text.contains("cookies are needed") ->
                DownloadError(DownloadError.Kind.AUTH_REQUIRED, message, false)
            text.contains("429") || text.contains("too many requests") || text.contains("rate limit") || text.contains("temporarily blocked") ->
                DownloadError(DownloadError.Kind.RATE_LIMITED, message, true)
            text.contains("no space left") || text.contains("enospc") || text.contains("disk full") || text.contains("storage full") ->
                DownloadError(DownloadError.Kind.STORAGE_FULL, message, false)
            text.contains("removed") || text.contains("unavailable") || text.contains("not found") || text.contains("does not exist") ->
                DownloadError(DownloadError.Kind.REMOVED, message, false)
            text.contains("unsupported url") || text.contains("unsupported site") || text.contains("not supported") ->
                DownloadError(DownloadError.Kind.UNSUPPORTED, message, false)
            text.contains("timed out") || text.contains("timeout") || text.contains("network is unreachable") || text.contains("unable to download webpage") || text.contains("connection reset") || text.contains("connection refused") || text.contains("temporary failure in name resolution") ->
                DownloadError(DownloadError.Kind.NETWORK, message, true)
            stderr.isNotBlank() -> DownloadError(DownloadError.Kind.EXTRACTOR, message, false)
            throwable != null -> DownloadError(DownloadError.Kind.UNKNOWN, message, true)
            else -> DownloadError(DownloadError.Kind.UNKNOWN, message, false)
        }
    }
}

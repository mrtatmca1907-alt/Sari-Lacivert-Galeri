package com.atmaca.reeldroppro.engine

data class DownloadError(
    val kind: Kind,
    val message: String,
    val retryable: Boolean
) {
    enum class Kind {
        AUTH_REQUIRED,
        REMOVED,
        RATE_LIMITED,
        NETWORK,
        STORAGE_FULL,
        UNSUPPORTED,
        EXTRACTOR,
        UNKNOWN
    }
}

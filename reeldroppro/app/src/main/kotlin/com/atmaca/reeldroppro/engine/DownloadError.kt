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

    companion object {
        val NETWORK: Kind = Kind.NETWORK
        val RATE_LIMIT: Kind = Kind.RATE_LIMITED
        val PRIVATE: Kind = Kind.AUTH_REQUIRED
        val REMOVED: Kind = Kind.REMOVED
        val UNSUPPORTED: Kind = Kind.UNSUPPORTED
    }
}

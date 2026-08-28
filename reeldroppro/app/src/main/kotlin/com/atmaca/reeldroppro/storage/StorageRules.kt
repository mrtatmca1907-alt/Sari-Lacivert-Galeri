package com.atmaca.reeldroppro.storage

import java.security.MessageDigest
import java.util.Locale

enum class MediaBucket(val folder: String) {
    PHOTO("Fotoğraflar"),
    VIDEO("Videolar")
}

object OutputPathPolicy {
    fun relativePath(platform: String, source: String, bucket: MediaBucket): String {
        val category = when (platform.trim().uppercase(Locale.ROOT)) {
            "INSTAGRAM_PROFILE", "INSTAGRAM", "PROFILE", "PROFIL" -> "Profil"
            "INSTAGRAM_HASHTAG", "HASHTAG" -> "Hashtag"
            "FACEBOOK" -> "Facebook"
            else -> sanitize(platform)
        }
        val cleanSource = sanitize(source.removePrefix("#")).lowercase(Locale.ROOT)
        return "Download/ReelDrop Pro/$category/$cleanSource/${bucket.folder}"
    }

    private fun sanitize(raw: String): String = raw
        .trim()
        .replace(Regex("[\\s/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')
        .ifBlank { "unknown" }
}

object CompletedKeyPolicy {
    fun key(platform: String, source: String, mediaKey: String): String {
        val normalized = listOf(
            platform.trim().lowercase(Locale.ROOT),
            source.trim().lowercase(Locale.ROOT),
            mediaKey.trim()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

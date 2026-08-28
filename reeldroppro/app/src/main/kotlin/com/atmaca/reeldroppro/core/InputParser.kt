package com.atmaca.reeldroppro.core

import com.atmaca.reeldroppro.model.ParsedInput
import com.atmaca.reeldroppro.model.Platform
import java.net.URI

object InputParser {
    fun parse(mode: Platform, raw: String): List<ParsedInput> = when (mode) {
        Platform.INSTAGRAM_PROFILE -> parseInstagramProfiles(raw)
        Platform.INSTAGRAM_HASHTAG -> parseHashtags(raw)
        Platform.FACEBOOK -> parseFacebook(raw)
    }

    private fun parseInstagramProfiles(raw: String): List<ParsedInput> = tokens(raw)
        .mapNotNull { token ->
            val username = when {
                token.startsWith("@") -> cleanKey(token.drop(1))
                token.startsWith("http://") || token.startsWith("https://") -> instagramUsernameFromUrl(token)
                else -> cleanKey(token)
            } ?: return@mapNotNull null
            ParsedInput(Platform.INSTAGRAM_PROFILE, "https://www.instagram.com/$username/", username)
        }
        .distinctBy { it.sourceKey.lowercase() }

    private fun parseHashtags(raw: String): List<ParsedInput> = tokens(raw)
        .mapNotNull { token ->
            val tag = cleanKey(token.removePrefix("#")) ?: return@mapNotNull null
            ParsedInput(Platform.INSTAGRAM_HASHTAG, "https://www.instagram.com/explore/tags/$tag/", tag)
        }
        .distinctBy { it.sourceKey.lowercase() }

    private fun parseFacebook(raw: String): List<ParsedInput> = tokens(raw)
        .mapNotNull { token ->
            if (!isFacebookUrl(token)) return@mapNotNull null
            ParsedInput(Platform.FACEBOOK, token, token)
        }
        .distinctBy { it.value }

    private fun tokens(raw: String): List<String> = raw
        .split(Regex("[\\s,;]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private fun instagramUsernameFromUrl(value: String): String? = try {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
        if (host != "instagram.com") return null
        val first = uri.path.orEmpty().trim('/').substringBefore('/')
        if (first.isBlank() || first in setOf("p", "reel", "reels", "stories", "explore")) null else cleanKey(first)
    } catch (_: Exception) { null }

    private fun isFacebookUrl(value: String): Boolean = try {
        val uri = URI(value)
        val schemeOk = uri.scheme == "http" || uri.scheme == "https"
        val host = uri.host?.lowercase().orEmpty().removePrefix("www.").removePrefix("m.")
        schemeOk && (host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch")
    } catch (_: Exception) { false }

    private fun cleanKey(value: String): String? {
        val cleaned = value.trim().trim('/').takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
        return cleaned?.lowercase()
    }
}

package com.facebookdrop

import java.net.URI

object UrlUtils {
    fun parseUrls(text: String): List<String> = text.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() && isSupported(it) }.distinct()
    fun isSupported(url: String): Boolean {
        return try {
            val u = URI(url)
            val schemeOk = u.scheme == "http" || u.scheme == "https"
            val host = u.host?.lowercase().orEmpty().removePrefix("www.").removePrefix("m.")
            schemeOk && (host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch")
        } catch (_: Exception) { false }
    }
}

package com.atmaca.reeldroppro.cookie

object CookieModePolicy {
    fun looksUsable(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val netscape = lower.contains("netscape http cookie file") || lower.contains("#httponly_")
        val instagram = lower.contains("instagram.com")
        val session = lower.contains("\tsessionid\t") || lower.contains("sessionid=")
        return instagram && session && (netscape || text.contains('\t'))
    }
}

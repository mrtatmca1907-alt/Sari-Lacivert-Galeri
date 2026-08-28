package com.videokareleri.v5

object NameUtils {
    fun sanitizeBaseName(value: String?): String {
        var s = value.orEmpty().trim()
        val dot = s.lastIndexOf('.')
        if (dot > 0) s = s.substring(0, dot)
        s = s.replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[. ]+|[. ]+$"), "")
            .trim()
        return s.ifBlank { "video" }
    }
    fun frameName(base: String?, number: Int) = "${sanitizeBaseName(base)} $number.jpg"
    fun frameCount(durationMs: Long): Int = if (durationMs <= 0L) 0 else ((durationMs + 999L) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

package com.atmaca.canlidublaj

import java.util.Locale

/** Maps detected BCP-47 language tags to user-facing language names. */
object LanguageDetector {
    fun normalize(tag: String): String {
        return Locale.forLanguageTag(tag).language.ifBlank { "und" }
    }
    fun displayName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return if (locale.language.isBlank()) "Bilinmeyen dil"
        else locale.getDisplayLanguage(Locale.forLanguageTag("tr"))
    }
    fun isTurkish(tag: String): Boolean = normalize(tag) == "tr"
}

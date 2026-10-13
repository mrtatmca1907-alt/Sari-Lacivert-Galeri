package com.atmaca.canlidublaj

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Identifies the language of recognized speech text, downloads the required model,
 * and translates the text into Turkish. Does not transcribe raw audio.
 */
class AutoTranslator {
    private val detector = LanguageIdentification.getClient()
    private var translator: Translator? = null
    private var source: String? = null

    fun translate(text: String, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        if (text.isBlank()) return
        detector.identifyLanguage(text).addOnSuccessListener { detected ->
            val language = TranslateLanguage.fromLanguageTag(detected)
            if (language == null) {
                onError(IllegalArgumentException("Unsupported language: $detected"))
            } else if (language == TranslateLanguage.TURKISH) {
                onSuccess(text)
            } else {
                if (source != language) {
                    translator?.close()
                    translator = Translation.getClient(
                        TranslatorOptions.Builder()
                            .setSourceLanguage(language)
                            .setTargetLanguage(TranslateLanguage.TURKISH)
                            .build()
                    )
                    source = language
                }
                val client = translator ?: return@addOnSuccessListener
                client.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        client.translate(text)
                            .addOnSuccessListener(onSuccess)
                            .addOnFailureListener(onError)
                    }
                    .addOnFailureListener(onError)
            }
        }.addOnFailureListener(onError)
    }

    fun close() {
        translator?.close()
        detector.close()
    }
}

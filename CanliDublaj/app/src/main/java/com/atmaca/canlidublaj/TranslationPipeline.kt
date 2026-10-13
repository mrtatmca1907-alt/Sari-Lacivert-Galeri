package com.atmaca.canlidublaj

/**
 * Receives recognized text from a speech recognition engine and forwards translated
 * Turkish text to the speech synthesizer. Speech recognition and translation engines
 * must be provided before this can process captured audio.
 */
class TranslationPipeline(private val speech: TurkishSpeech) {
    fun onTranslatedText(turkishText: String): Boolean {
        return speech.speak(turkishText.trim())
    }
}

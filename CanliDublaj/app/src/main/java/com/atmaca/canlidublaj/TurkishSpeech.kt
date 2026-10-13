package com.atmaca.canlidublaj

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks translated Turkish text once a transcription/translation engine supplies it. */
class TurkishSpeech(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = tts.setLanguage(Locale.forLanguageTag("tr-TR")) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    fun speak(text: String): Boolean {
        if (!ready || text.isBlank()) return false
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "atmaca-translation") == TextToSpeech.SUCCESS
    }

    fun stop() { tts.stop() }
    fun release() { tts.shutdown() }
}

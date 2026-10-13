package com.atmaca.canlidublaj

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper

/** Microphone-based speech translation fallback; cannot read Facebook's internal audio. */
class MicrophoneDubbing(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private val speech = TurkishSpeech(context)
    private val translator = AutoTranslator()
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus("Konuşma tanıma bu cihazda bulunamadı")
            return
        }
        active = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onStatus("Dinleniyor") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (active) handler.postDelayed({ listen() }, 800)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        onStatus("Algılandı: $text")
                        translator.translate(text, { turkish ->
                            onStatus("Türkçe: $turkish")
                            speech.speak(turkish)
                        }, { error -> onStatus("Çeviri hatası: ${error.message}") })
                    }
                    if (active) handler.postDelayed({ listen() }, 1200)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        listen()
    }

    private fun listen() {
        if (!active) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        active = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        speech.stop()
    }

    fun release() {
        stop()
        translator.close()
        speech.release()
    }
}

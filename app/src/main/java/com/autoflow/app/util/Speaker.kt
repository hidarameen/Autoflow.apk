package com.autoflow.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Lazily-initialised text to speech. The engine holds one instance for the process because
 * TextToSpeech takes a second or two to bind and a rule should not pay that every run.
 */
object Speaker {

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    private val pending = mutableListOf<String>()

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return

        val current = engine
        if (current != null && ready) {
            current.speak(text, TextToSpeech.QUEUE_ADD, null, "autoflow")
            return
        }

        synchronized(this) {
            pending += text
            if (engine != null) return
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) return@TextToSpeech
                engine?.language = Locale.getDefault()
                synchronized(this) {
                    pending.forEach { engine?.speak(it, TextToSpeech.QUEUE_ADD, null, "autoflow") }
                    pending.clear()
                }
            }
        }
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
        ready = false
    }
}

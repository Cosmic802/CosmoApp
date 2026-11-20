package com.cosmo.butler

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class CosmoVoiceEngine(
    private val context: Context,
    private val onCommandDetected: (String) -> Unit,
    private val onStatusUpdate: (String) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                // Эрэгтэй, тайван J.A.R.V.I.S. өнгөтэй хоолой хайж тохируулах
                val maleVoice = tts?.voices?.firstOrNull {
                    it.locale.language == "en" && (it.name.contains("male", true) || it.name.contains("gba", true) || it.name.contains("iom", true))
                }
                if (maleVoice != null) tts?.voice = maleVoice
                tts?.setPitch(0.88f)
                tts?.setSpeechRate(0.96f)
            }
        }
    }

    fun speak(text: String) {
        onStatusUpdate("COSMO: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "COSMO_UTTERANCE")
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        mainHandler.post { initAndListen() }
    }

    fun stopListening() {
        isListening = false
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun initAndListen() {
        if (!isListening) return
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onStatusUpdate("VOICE: Listening...") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // Алдаа гарсан ч батерей идэхгүйгээр 1.5 сек дараа дахин сонсоно
                    mainHandler.postDelayed({ if (isListening) initAndListen() }, 1500)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val heard = matches?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                    if (heard.contains("cosmo")) {
                        processVoiceCommand(heard)
                    }
                    mainHandler.postDelayed({ if (isListening) initAndListen() }, 500)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceCommand(heard: String) {
        onStatusUpdate("HEARD: \"$heard\"")
        when {
            heard.contains("study") -> {
                speak("Right away, sir. Engaging Study Mode and silencing distractions.")
                onCommandDetected("STUDY")
            }
            heard.contains("game") -> {
                speak("Optimizing system resources and unlocking 120 Hertz. Game Mode active.")
                onCommandDetected("GAME")
            }
            heard.contains("sleep") -> {
                speak("Engaging evening protocol, sir. Applying red optical shield.")
                onCommandDetected("SLEEP")
            }
            heard.contains("normal") -> {
                speak("Restoring standard system parameters, sir.")
                onCommandDetected("NORMAL")
            }
            heard.contains("flashlight") || heard.contains("light") -> {
                speak("Toggling illumination, sir.")
                onCommandDetected("FLASHLIGHT")
            }
            heard.contains("report") || heard.contains("status") -> {
                speak("Compiling your daily telemetry now, sir.")
                onCommandDetected("REPORT")
            }
            else -> {
                speak("At your service, sir. Awaiting your directive.")
            }
        }
    }

    fun shutdown() {
        stopListening()
        tts?.shutdown()
    }
}

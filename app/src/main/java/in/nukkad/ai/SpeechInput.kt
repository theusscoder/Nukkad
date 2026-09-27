package `in`.nukkad.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

class SpeechInput(private val context: Context) {
    private var active: SpeechRecognizer? = null
    fun cancel() { val old = active; active = null; old?.cancel(); old?.destroy() }
    fun finish() { active?.stopListening() }
    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    fun start(languageTag: String, onResult: (String) -> Unit, onError: (String) -> Unit, onPartial: (String) -> Unit = {}) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Microphone permission is required")
            return
        }
        if (!available()) { onError("No speech recognition service is installed"); return }
        cancel()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        active = recognizer
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val value = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (active !== recognizer) return
                active = null
                recognizer.destroy()
                if (value.isNullOrBlank()) onError("No speech was recognized") else onResult(value)
            }
            override fun onError(error: Int) {
                if (active !== recognizer) return
                active = null
                recognizer.destroy()
                val message = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Speech service network error. Turn on internet, check Google Speech Services, then try again."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service timed out. Check internet and try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Speak closer to the phone and try again."
                    SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error. Check that another app is not using the microphone."
                    else -> "Speech recognition failed (code $error). Check the phone speech service and try again."
                }
                onError(message)
            }
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                if (active !== recognizer) return
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf(String::isNotBlank)?.let(onPartial)
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }
}





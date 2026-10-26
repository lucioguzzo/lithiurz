package com.lucio.guzzobot.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * L'orecchio del telefono.
 *
 * Android sa già trascrivere il parlato, spesso senza rete e sempre senza
 * crediti. Usarlo invece di mandare l'audio a un servizio di trascrizione
 * rende la conversazione parlata gratuita quanto la voce che risponde — e
 * molto più rapida, perché non c'è niente da caricare.
 *
 * Resta la registrazione con trascrizione via API, per le lingue che il
 * telefono non copre e per gli audio da allegare.
 */
class DeviceListener(private val context: Context) {

    /** Quello che si sente mentre si parla, prima del risultato definitivo. */
    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening

    /** Il testo riconosciuto, a frase finita. */
    private val _heard = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val heard: SharedFlow<String> = _heard

    /** Perché non ha capito: va detto, altrimenti sembra che l'app si sia persa. */
    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val failed: SharedFlow<String> = _failed

    /** Qualcuno ha cominciato a parlare: è il momento di valutare se zittirsi. */
    private val _onset = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val onset: SharedFlow<Unit> = _onset

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Comincia ad ascoltare. Va chiamato dal thread principale. */
    fun start(language: String? = null) {
        if (!isAvailable) {
            _failed.tryEmit("Questo telefono non ha il riconoscimento vocale")
            return
        }
        stop()
        val tag = language?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Se il telefono sa farlo da solo, si evita di mandare la voce
            // fuori: è più veloce e non esce niente dal dispositivo.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener)
            startListening(intent)
        }
        _partial.value = ""
        _listening.value = true
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        _listening.value = false
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            _onset.tryEmit(Unit)
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _listening.value = false
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let { _partial.value = it }
        }

        override fun onResults(results: Bundle?) {
            _listening.value = false
            val text = first(results).orEmpty().trim()
            _partial.value = ""
            if (text.isEmpty()) {
                _failed.tryEmit("Non ho sentito niente")
            } else {
                _heard.tryEmit(text)
            }
        }

        override fun onError(error: Int) {
            _listening.value = false
            _partial.value = ""
            _failed.tryEmit(message(error))
        }

        private fun first(bundle: Bundle?): String? = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
    }

    /** I codici del riconoscitore tradotti in qualcosa che si capisce. */
    private fun message(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Non ho capito: riprova"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Non ho sentito niente"
        SpeechRecognizer.ERROR_AUDIO -> "Problema col microfono"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Manca il permesso del microfono"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Il riconoscimento vocale non è disponibile offline su questo telefono"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Il riconoscimento è occupato: riprova"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> "Questa lingua non è installata per il riconoscimento vocale"
        else -> "Riconoscimento vocale non riuscito"
    }
}

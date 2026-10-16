package com.lucio.brief

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Trascrive un file audio con il riconoscitore vocale del sistema, lo
 * stesso che usano le app di trascrizione del telefono: è gratuito,
 * funziona senza rete una volta scaricata la lingua ed è molto più rapido
 * di whisper perché ottimizzato per il chip del dispositivo.
 *
 * Richiede Android 13 (API 33): è da lì che `SpeechRecognizer` accetta un
 * file come sorgente invece del solo microfono, tramite EXTRA_AUDIO_SOURCE
 * e la sessione segmentata, che continua a produrre risultati fino alla
 * fine dell'audio invece di fermarsi alla prima pausa.
 *
 * Tutto gira sul thread principale perché SpeechRecognizer lo pretende.
 */
class SystemSpeechPlugin(private val context: Context) : MethodChannel.MethodCallHandler {

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isAvailable" -> result.success(isAvailable())
            "unavailableReason" -> result.success(unavailableReason())
            "transcribeFile" -> {
                val path = call.argument<String>("path")
                val language = call.argument<String>("language") ?: "it-IT"
                if (path == null) {
                    result.error("ARGOMENTI", "Percorso del file mancante.", null)
                    return
                }
                transcribeFile(path, language, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun unavailableReason(): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
            "Serve Android 13 o successivo: prima di allora il riconoscitore " +
                "di sistema legge solo dal microfono, non da un file."
        !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            "Il riconoscitore vocale offline non è installato su questo " +
                "telefono. Di solito si attiva installando o aggiornando i " +
                "servizi vocali di Google e scaricando la lingua italiana."
        else -> null
    }

    private fun transcribeFile(path: String, language: String, result: MethodChannel.Result) {
        if (!isAvailable()) {
            result.error("NON_DISPONIBILE", unavailableReason(), null)
            return
        }
        val file = File(path)
        if (!file.exists() || file.length() <= WAV_HEADER_BYTES) {
            result.error("FILE", "Audio da trascrivere non leggibile.", null)
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                startRecognition(file, language, result)
            } catch (error: Throwable) {
                Log.e(TAG, "Avvio del riconoscimento non riuscito", error)
                result.error("AVVIO", error.message ?: "Errore sconosciuto.", null)
            }
        }
    }

    private fun startRecognition(file: File, language: String, result: MethodChannel.Result) {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val pipe = ParcelFileDescriptor.createPipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        // Una sola risposta: il riconoscitore può chiamare più callback.
        val answered = AtomicBoolean(false)
        val transcript = StringBuilder()

        fun finish(text: String?, errorCode: String?, errorMessage: String?) {
            if (!answered.compareAndSet(false, true)) return
            try {
                recognizer.destroy()
            } catch (ignored: Throwable) {
                Log.w(TAG, "Chiusura del riconoscitore non riuscita", ignored)
            }
            try {
                readSide.close()
            } catch (ignored: Throwable) {
                // Già chiuso.
            }
            if (errorCode != null) {
                result.error(errorCode, errorMessage, null)
            } else {
                result.success(text.orEmpty())
            }
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) = appendAndMaybeFinish(results, final = true)

            override fun onPartialResults(partialResults: Bundle?) {
                // In sessione segmentata i pezzi definitivi arrivano qui
                // man mano che l'audio viene consumato.
                appendAndMaybeFinish(partialResults, final = false)
            }

            override fun onSegmentResults(segmentResults: Bundle) =
                appendAndMaybeFinish(segmentResults, final = false)

            override fun onEndOfSegmentedSession() {
                finish(transcript.toString().trim(), null, null)
            }

            override fun onError(error: Int) {
                // A fine audio il riconoscitore segnala "nessun parlato" o
                // "timeout": se abbiamo già del testo non è un errore.
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (recoverable) {
                    finish(transcript.toString().trim(), null, null)
                } else {
                    finish(null, "RICONOSCIMENTO", describeError(error))
                }
            }

            private fun appendAndMaybeFinish(bundle: Bundle?, final: Boolean) {
                val best = bundle
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (!best.isNullOrEmpty()) {
                    if (transcript.isNotEmpty()) transcript.append(' ')
                    transcript.append(best)
                }
                if (final && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    finish(transcript.toString().trim(), null, null)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Sessione segmentata: continua fino alla fine del file invece
            // di chiudersi alla prima pausa lunga.
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        }

        recognizer.startListening(intent)
        feedPcm(file, writeSide)
    }

    /**
     * Versa il PCM del WAV nella pipe, su un thread a parte, saltando
     * l'intestazione di 44 byte: al riconoscitore va il crudo.
     */
    private fun feedPcm(file: File, writeSide: ParcelFileDescriptor) {
        Thread({
            try {
                FileOutputStream(writeSide.fileDescriptor).use { out ->
                    file.inputStream().use { input ->
                        input.skip(WAV_HEADER_BYTES)
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                        }
                        out.flush()
                    }
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Invio dell'audio al riconoscitore interrotto", error)
            } finally {
                try {
                    writeSide.close()
                } catch (ignored: Throwable) {
                    // Già chiusa: il riconoscitore vedrà comunque la fine.
                }
            }
        }, "brief-pcm-feed").start()
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Errore nella lettura dell'audio."
        SpeechRecognizer.ERROR_CLIENT -> "Il riconoscitore ha rifiutato la richiesta."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permesso negato al riconoscitore."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Il riconoscitore ha cercato la rete: la lingua offline non è installata."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Riconoscitore occupato, riprova fra poco."
        SpeechRecognizer.ERROR_SERVER -> "Errore del servizio di riconoscimento."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Lingua non supportata offline."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Lingua non scaricata: scaricala dalle impostazioni vocali del telefono."
        else -> "Riconoscimento non riuscito (codice $error)."
    }

    companion object {
        const val CHANNEL = "brief/system_speech"
        private const val TAG = "BRIEF"
        private const val SAMPLE_RATE = 16000
        private const val WAV_HEADER_BYTES = 44L
    }
}

package com.lucio.guzzobot.data.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Locale

/** Registra note vocali in AAC/m4a, il formato che i servizi di trascrizione accettano. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    fun start(file: File) {
        stopQuietly()
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        instance.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = instance
        target = file
        startedAt = System.currentTimeMillis()
    }

    /** Chiude la registrazione e restituisce il file, null se è troppo breve. */
    fun stop(): File? {
        val instance = recorder ?: return null
        val file = target
        recorder = null
        target = null
        runCatching {
            instance.stop()
        }.onFailure {
            instance.reset()
            instance.release()
            file?.delete()
            return null
        }
        instance.release()
        val tooShort = System.currentTimeMillis() - startedAt < 700
        if (file == null || tooShort || file.length() < 1024) {
            file?.delete()
            return null
        }
        return file
    }

    fun cancel() {
        val file = target
        stopQuietly()
        file?.delete()
    }

    private fun stopQuietly() {
        recorder?.let { instance ->
            runCatching { instance.stop() }
            runCatching { instance.release() }
        }
        recorder = null
        target = null
    }
}

/** Un solo audio alla volta, così il pulsante play riflette sempre ciò che si sente. */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)
    val playing: StateFlow<String?> = _playing

    fun toggle(path: String) {
        if (_playing.value == path) {
            stop()
            return
        }
        stop()
        runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.onSuccess {
            player = it
            _playing.value = path
        }
    }

    fun stop() {
        player?.let { instance ->
            runCatching { instance.stop() }
            runCatching { instance.release() }
        }
        player = null
        _playing.value = null
    }
}

/**
 * Sintesi vocale del telefono: la usiamo quando non è configurato un modello
 * di voce, così "ascolta la risposta" funziona anche offline e senza crediti.
 */
class DeviceSpeaker(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private var ready = false
    private val engine = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    val isAvailable: Boolean get() = ready

    fun speak(text: String, locale: Locale = Locale.getDefault()) {
        if (!ready || text.isBlank()) return
        engine.language = locale
        engine.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                @Deprecated("Richiesto dall'interfaccia")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }
            },
        )
        engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "guzzobot")
    }

    fun stop() {
        runCatching { engine.stop() }
        _speaking.value = false
    }
}

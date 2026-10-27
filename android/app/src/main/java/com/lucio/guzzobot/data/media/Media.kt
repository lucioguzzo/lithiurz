package com.lucio.guzzobot.data.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
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
 * La voce del telefono.
 *
 * Il motore di sintesi di Android non costa niente, non consuma crediti e
 * funziona anche senza rete: per una conversazione parlata è la scelta giusta,
 * non il ripiego. Qui viene usato in coda, una frase alla volta, perché la
 * risposta possa essere letta mentre arriva invece che dopo.
 */
class DeviceSpeaker(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /**
     * Quello che sta leggendo in questo momento.
     *
     * Serve a riconoscere la propria eco: se il microfono sente le stesse
     * parole che l'altoparlante sta dicendo, non è l'utente che parla.
     */
    private val _saying = MutableStateFlow("")
    val saying: StateFlow<String> = _saying

    /**
     * L'ultima cosa detta, che a differenza di [saying] non si azzera a fine
     * lettura: il riconoscimento può consegnare l'eco un attimo dopo che la
     * voce ha smesso, e va riconosciuto lo stesso.
     */
    @Volatile
    var lastSaid: String = ""
        private set

    /** Finito di parlare davvero: serve a sapere quando tornare in ascolto. */
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val finished: SharedFlow<Unit> = _finished

    private var ready = false

    /**
     * L'inizializzazione del motore è asincrona: chi legge l'elenco delle
     * voci subito lo trova vuoto. Questo dice quando ha senso rileggerlo.
     */
    private val _ready = MutableStateFlow(false)
    val readyState: StateFlow<Boolean> = _ready
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val listener = object : android.speech.tts.UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speaking.value = true
        }

        override fun onDone(utteranceId: String?) = settle()

        @Deprecated("Richiesto dall'interfaccia")
        override fun onError(utteranceId: String?) = settle()

        override fun onError(utteranceId: String?, errorCode: Int) = settle()
    }

    private val engine: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        _ready.value = ready
        if (ready) engine.setOnUtteranceProgressListener(listener)
    }

    /** Una frase è finita: se era l'ultima della coda, il turno è chiuso. */
    private fun settle() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            _speaking.value = false
            _saying.value = ""
            scope.launch { _finished.emit(Unit) }
        }
    }

    val isAvailable: Boolean get() = ready

    /** Il motore di sintesi in uso, per dire all'utente chi sta parlando. */
    fun engineName(): String? = runCatching {
        val id = engine.defaultEngine ?: return@runCatching null
        engine.engines.orEmpty().firstOrNull { it.name == id }?.label ?: id
    }.getOrNull()

    /** Vero se il motore di Google, quello con le voci neurali, è installato. */
    fun hasGoogleEngine(): Boolean = runCatching {
        engine.engines.orEmpty().any { it.name == VoiceSetup.GOOGLE_TTS }
    }.getOrDefault(false)

    /** Vero se è anche quello attivo. */
    fun usesGoogleEngine(): Boolean =
        runCatching { engine.defaultEngine == VoiceSetup.GOOGLE_TTS }.getOrDefault(false)

    /**
     * Le voci disponibili per una lingua, descritte in modo leggibile.
     *
     * Il motore le chiama «it-it-x-kda-local»: un nome così non aiuta a
     * scegliere. Qui diventano lingua, qualità e se funzionano offline — le
     * tre cose che contano davvero — con le migliori in cima.
     */
    fun voices(language: String?): List<VoiceOption> = runCatching {
        engine.voices.orEmpty()
            .filter { voice ->
                language.isNullOrBlank() ||
                    voice.locale.language.equals(localeOf(language).language, ignoreCase = true)
            }
            .filterNot { it.features.orEmpty().contains("notInstalled") }
            .map { voice ->
                VoiceOption(
                    id = voice.name,
                    locale = voice.locale.displayName.replaceFirstChar(Char::uppercase),
                    quality = voice.quality,
                    offline = !voice.isNetworkConnectionRequired,
                )
            }
            // Prima le migliori, poi quelle che funzionano senza rete.
            .sortedWith(compareByDescending<VoiceOption> { it.quality }.thenByDescending { it.offline })
    }.getOrDefault(emptyList())

    /** Le lingue che il motore sa pronunciare. */
    fun languages(): List<String> = runCatching {
        engine.availableLanguages.orEmpty().map { it.toLanguageTag() }.sorted()
    }.getOrDefault(emptyList())

    /**
     * Legge una frase. Con [queue] la accoda a quelle già in lettura, invece
     * di interromperle: è così che si legge una risposta mentre arriva.
     */
    fun speak(
        text: String,
        language: String? = null,
        voice: String? = null,
        rate: Float = 1f,
        pitch: Float = 1f,
        queue: Boolean = false,
    ) {
        if (!ready) return
        val spoken = text.trim().take(3_500)
        if (spoken.isEmpty()) return
        runCatching {
            engine.language = localeOf(language)
            voice?.takeIf { it.isNotBlank() }?.let { wanted ->
                engine.voices.orEmpty().firstOrNull { it.name == wanted }?.let(engine::setVoice)
            }
            engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
            engine.setPitch(pitch.coerceIn(0.5f, 2f))
        }
        if (!queue) {
            pending.set(0)
            _saying.value = spoken
        } else {
            // In coda si accumula: l'eco può arrivare da qualunque frase
            // ancora in lettura, non solo dall'ultima.
            _saying.value = (_saying.value + " " + spoken).trim().takeLast(600)
        }
        lastSaid = _saying.value
        pending.incrementAndGet()
        engine.speak(
            spoken,
            if (queue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH,
            null,
            "guzzobot-" + System.nanoTime(),
        )
    }

    fun stop() {
        runCatching { engine.stop() }
        pending.set(0)
        _speaking.value = false
        _saying.value = ""
    }

    fun shutdown() {
        runCatching { engine.shutdown() }
        scope.cancel()
    }

    private fun localeOf(tag: String?): Locale =
        tag?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.getDefault()
}


package com.lucio.guzzobot.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lucio.guzzobot.AppContainer
import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.files.TextExtractor
import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.MessageEntity
import com.lucio.guzzobot.data.media.AudioPlayer
import com.lucio.guzzobot.data.media.AudioRecorder
import com.lucio.guzzobot.data.media.DeviceSpeaker
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.data.model.AttachmentKind
import com.lucio.guzzobot.data.model.ModelCapabilities
import com.lucio.guzzobot.data.model.ModelRef
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.model.ThemeMode
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.prefs.SettingsStore
import com.lucio.guzzobot.data.remote.ApiMessage
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.remote.Content
import com.lucio.guzzobot.data.remote.StreamEvent
import com.lucio.guzzobot.data.repo.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StreamingState(
    val conversationId: Long,
    val model: String,
    val content: String = "",
    val reasoning: String = "",
)

/** Quali modalità sono disponibili con la configurazione attuale. */
data class Abilities(
    val nativeVision: Boolean = false,
    val canDescribeImages: Boolean = false,
    val canTranscribe: Boolean = false,
    val canGenerateImages: Boolean = false,
    val canSpeakWithProvider: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val settingsStore: SettingsStore,
    private val secretStore: SecretStore,
    private val chatApi: ChatApi,
    private val attachments: AttachmentStore,
    private val recorder: AudioRecorder,
    val audioPlayer: AudioPlayer,
    private val deviceSpeaker: DeviceSpeaker,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val conversations: StateFlow<List<ConversationEntity>> =
        repository.conversations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _conversationId = MutableStateFlow<Long?>(null)
    val conversationId: StateFlow<Long?> = _conversationId

    val messages: StateFlow<List<MessageEntity>> = _conversationId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.messages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input

    /** Allegati scelti e non ancora inviati. */
    private val _pending = MutableStateFlow<List<Attachment>>(emptyList())
    val pending: StateFlow<List<Attachment>> = _pending

    /** Descrizione del lavoro in corso fuori dallo streaming (trascrizione, vision, immagini). */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val speaking: StateFlow<Boolean> = deviceSpeaker.speaking

    val ready: StateFlow<Boolean> = combine(settings, secretStore.changes) { current, _ ->
        current.activeProvider?.let { isReady(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val abilities: StateFlow<Abilities> = combine(settings, secretStore.changes) { current, _ ->
        Abilities(
            nativeVision = current.forceNativeVision || ModelCapabilities.seesImages(current.activeModel),
            canDescribeImages = usable(current, current.visionRef),
            canTranscribe = usable(current, current.transcribeRef),
            canGenerateImages = usable(current, current.imageRef),
            canSpeakWithProvider = usable(current, current.speechRef),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Abilities())

    private var streamJob: Job? = null

    // --- conversazioni -----------------------------------------------------

    fun newChat() {
        if (_streaming.value != null) stop()
        _conversationId.value = null
        _input.value = ""
        clearPending()
    }

    fun openConversation(id: Long) {
        if (_streaming.value != null && _streaming.value?.conversationId != id) stop()
        _conversationId.value = id
    }

    fun deleteConversation(id: Long) = viewModelScope.launch {
        if (_streaming.value?.conversationId == id) stop()
        repository.history(id).forEach { message ->
            message.attachments.forEach(attachments::delete)
        }
        repository.delete(id)
        if (_conversationId.value == id) _conversationId.value = null
    }

    fun renameConversation(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repository.rename(id, title)
    }

    fun deleteAllConversations() = viewModelScope.launch {
        stop()
        conversations.value.forEach { conversation ->
            repository.history(conversation.id).forEach { message ->
                message.attachments.forEach(attachments::delete)
            }
        }
        repository.deleteAll()
        _conversationId.value = null
    }

    // --- allegati ----------------------------------------------------------

    fun attach(uri: Uri, kind: AttachmentKind) = viewModelScope.launch {
        _busy.value = when (kind) {
            AttachmentKind.DOCUMENT -> "Leggo il documento…"
            AttachmentKind.AUDIO -> "Preparo l'audio…"
            else -> "Preparo l'immagine…"
        }
        try {
            val attachment = attachments.importUri(uri, kind)
            if (kind == AttachmentKind.DOCUMENT &&
                !attachment.hasText &&
                attachment.pageImages.isEmpty()
            ) {
                attachments.delete(attachment)
                notice(
                    "Da \"${attachment.name}\" non riesco a estrarre testo. " +
                        "Formati supportati: PDF, DOCX, testo, CSV, codice.",
                )
                return@launch
            }
            _pending.update { it + attachment }
            if (kind == AttachmentKind.AUDIO) transcribeAttachment(attachment)
        } catch (error: Throwable) {
            notice(error.message ?: "Non riesco ad allegare il file")
        } finally {
            _busy.value = null
        }
    }

    fun attachPhotoFile(file: File) = viewModelScope.launch {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@launch
        }
        val attachment = attachments.importFile(
            file = file,
            kind = AttachmentKind.IMAGE,
            name = file.name,
            mime = "image/jpeg",
        )
        _pending.update { it + attachment }
    }

    fun removeAttachment(attachment: Attachment) {
        _pending.update { list -> list.filterNot { it.id == attachment.id } }
        attachments.delete(attachment)
    }

    private fun clearPending() {
        _pending.value = emptyList()
    }

    fun cameraTarget(): Pair<File, Uri> = attachments.cameraTarget()

    // --- registrazione e trascrizione --------------------------------------

    fun startRecording() {
        runCatching { recorder.start(attachments.recordingTarget()) }
            .onSuccess { _recording.value = true }
            .onFailure { notice("Non riesco ad accedere al microfono") }
    }

    fun stopRecording() {
        if (!_recording.value) return
        val file = recorder.stop()
        _recording.value = false
        if (file == null) {
            notice("Registrazione troppo breve")
            return
        }
        viewModelScope.launch {
            val attachment = attachments.importFile(
                file = file,
                kind = AttachmentKind.AUDIO,
                name = "Nota vocale",
                mime = "audio/mp4",
            )
            _pending.update { it + attachment }
            transcribeAttachment(attachment)
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        _recording.value = false
    }

    private suspend fun transcribeAttachment(attachment: Attachment) {
        val current = settings.value
        val ref = current.transcribeRef
        if (ref == null || !usable(current, ref)) {
            notice("Per trascrivere l'audio scegli un modello di trascrizione nelle impostazioni")
            return
        }
        val provider = current.providers.firstOrNull { it.id == ref.providerId } ?: return
        _busy.value = "Trascrivo l'audio con ${ref.model}…"
        try {
            val text = chatApi.transcribe(
                baseUrl = provider.baseUrl,
                apiKey = secretStore.apiKey(provider.id),
                model = ref.model,
                file = File(attachment.path),
                mime = attachment.mime,
            )
            if (text.isBlank()) {
                notice("La trascrizione è vuota")
                return
            }
            _pending.update { list ->
                list.map {
                    if (it.id == attachment.id) {
                        it.copy(text = text, note = "trascritto con ${ref.model}")
                    } else {
                        it
                    }
                }
            }
        } catch (error: Throwable) {
            notice(error.message ?: "Trascrizione non riuscita")
        } finally {
            _busy.value = null
        }
    }

    // --- invio -------------------------------------------------------------

    fun setInput(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        val files = _pending.value
        if ((text.isEmpty() && files.isEmpty()) || _streaming.value != null) return
        val current = settings.value
        val provider = current.activeProvider
        if (provider == null) {
            notice("Nessun provider configurato")
            return
        }
        if (!isReady(provider)) {
            notice("Aggiungi la chiave API di ${provider.name} nelle impostazioni")
            return
        }
        viewModelScope.launch {
            val prepared = prepareAttachments(files, current) ?: return@launch
            _input.value = ""
            clearPending()
            val body = text.ifEmpty { defaultPrompt(prepared) }
            val id = _conversationId.value
                ?: repository.createConversation(provider.id, current.activeModel, titleFrom(body))
                    .also { _conversationId.value = it }
            val wasEmpty = repository.isEmpty(id)
            repository.addMessage(id, Roles.USER, body, attachments = prepared)
            if (wasEmpty) repository.rename(id, titleFrom(body))
            runStream(id, provider, current)
        }
    }

    /**
     * Fa in modo che ogni allegato arrivi al modello in una forma che può usare:
     * se il modello in chat non vede le immagini, le fa descrivere (o trascrivere,
     * per i PDF scansionati) dal modello vision configurato e tiene il testo.
     * Restituisce null se l'invio va annullato.
     */
    private suspend fun prepareAttachments(
        files: List<Attachment>,
        current: AppSettings,
    ): List<Attachment>? {
        if (files.isEmpty()) return files
        val native = current.forceNativeVision || ModelCapabilities.seesImages(current.activeModel)
        val needsReading = files.filter { file ->
            !file.hasText && (file.isImage || file.pageImages.isNotEmpty())
        }
        if (native || needsReading.isEmpty()) return files

        val ref = current.visionRef
        if (ref == null || !usable(current, ref)) {
            notice(
                "${current.activeModel} non legge le immagini. Scegli un modello vision " +
                    "nelle impostazioni: lo userò per descriverle.",
            )
            return null
        }
        val provider = current.providers.firstOrNull { it.id == ref.providerId } ?: return null
        val apiKey = secretStore.apiKey(provider.id)
        val updated = files.toMutableList()
        try {
            needsReading.forEach { file ->
                _busy.value = "Leggo ${file.name} con ${ref.model}…"
                val description = describe(provider, apiKey, ref.model, file)
                if (description.isNotBlank()) {
                    val index = updated.indexOfFirst { it.id == file.id }
                    if (index >= 0) {
                        updated[index] = updated[index].copy(
                            text = description,
                            note = "letto da ${ref.model}",
                        )
                    }
                }
            }
        } catch (error: Throwable) {
            notice(error.message ?: "Il modello vision non ha risposto")
            return null
        } finally {
            _busy.value = null
        }
        return updated
    }

    private suspend fun describe(
        provider: Provider,
        apiKey: String,
        model: String,
        attachment: Attachment,
    ): String {
        val images = if (attachment.pageImages.isNotEmpty()) {
            attachment.pageImages
        } else {
            listOf(attachment.path)
        }
        val isDocument = attachment.pageImages.isNotEmpty()
        val instruction = if (isDocument) {
            "Trascrivi fedelmente tutto il testo di questa pagina, mantenendo la struttura. " +
                "Se ci sono tabelle, rendile in markdown."
        } else {
            "Descrivi questa immagine in modo dettagliato e fattuale: soggetti, testo " +
                "leggibile, numeri, contesto. Non aggiungere commenti tuoi."
        }
        val pieces = images.mapIndexedNotNull { index, path ->
            val dataUrl = attachments.imageDataUrl(path) ?: return@mapIndexedNotNull null
            val answer = chatApi.complete(
                baseUrl = provider.baseUrl,
                apiKey = apiKey,
                model = model,
                messages = listOf(
                    ApiMessage(Roles.USER, Content.parts(instruction, listOf(dataUrl))),
                ),
            )
            if (isDocument) "— pagina ${index + 1} —\n$answer" else answer
        }
        return pieces.joinToString("\n\n").trim()
    }

    private fun defaultPrompt(files: List<Attachment>): String = when {
        files.any { it.isAudio } -> "Ascolta e rispondi a questa nota vocale."
        files.any { it.kind == AttachmentKind.DOCUMENT } -> "Analizza questo documento."
        files.any { it.isImage } -> "Cosa vedi in questa immagine?"
        else -> "Analizza l'allegato."
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    fun regenerate() {
        val id = _conversationId.value ?: return
        if (_streaming.value != null) return
        val current = settings.value
        val provider = current.activeProvider ?: return
        if (!isReady(provider)) {
            notice("Aggiungi la chiave API di ${provider.name} nelle impostazioni")
            return
        }
        viewModelScope.launch {
            repository.deleteTrailingAssistant(id)
            runStream(id, provider, current)
        }
    }

    /** Riporta il messaggio nel composer e taglia la conversazione da quel punto. */
    fun editMessage(message: MessageEntity) {
        if (_streaming.value != null) stop()
        viewModelScope.launch {
            repository.deleteFrom(message.conversationId, message.id)
            _input.value = message.content
            _pending.value = message.attachments.filterNot { it.kind == AttachmentKind.GENERATED_IMAGE }
        }
    }

    private fun runStream(conversationId: Long, provider: Provider, current: AppSettings) {
        val model = current.activeModel
        val apiKey = secretStore.apiKey(provider.id)
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val history = buildHistory(conversationId, current)
            _streaming.value = StreamingState(conversationId = conversationId, model = model)
            val content = StringBuilder()
            val reasoning = StringBuilder()
            try {
                chatApi.streamChat(
                    baseUrl = provider.baseUrl,
                    apiKey = apiKey,
                    model = model,
                    messages = history,
                    temperature = current.temperature,
                    maxTokens = current.maxTokens,
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Content -> {
                            content.append(event.text)
                            val snapshot = content.toString()
                            _streaming.update { it?.copy(content = snapshot) }
                        }
                        is StreamEvent.Reasoning -> {
                            reasoning.append(event.text)
                            val snapshot = reasoning.toString()
                            _streaming.update { it?.copy(reasoning = snapshot) }
                        }
                    }
                }
                persist(conversationId, content.toString(), reasoning.toString(), model)
            } catch (cancel: CancellationException) {
                withContext(NonCancellable) {
                    persist(conversationId, content.toString(), reasoning.toString(), model)
                }
                throw cancel
            } catch (error: Throwable) {
                persist(conversationId, content.toString(), reasoning.toString(), model)
                repository.addMessage(
                    conversationId = conversationId,
                    role = Roles.ASSISTANT,
                    content = error.message ?: "Errore sconosciuto",
                    model = model,
                    isError = true,
                )
            } finally {
                _streaming.value = null
                streamJob = null
            }
        }
    }

    private suspend fun persist(conversationId: Long, content: String, reasoning: String, model: String) {
        if (content.isBlank() && reasoning.isBlank()) return
        repository.addMessage(
            conversationId = conversationId,
            role = Roles.ASSISTANT,
            content = content,
            reasoning = reasoning,
            model = model,
        )
    }

    /**
     * Traduce la conversazione salvata nel formato dell'API. Le immagini vanno
     * come parti `image_url` solo se il modello le vede; per non far esplodere la
     * richiesta ne teniamo al massimo [MAX_IMAGES], le più recenti.
     */
    private suspend fun buildHistory(conversationId: Long, current: AppSettings): List<ApiMessage> {
        val native = current.forceNativeVision || ModelCapabilities.seesImages(current.activeModel)
        val stored = repository.history(conversationId)
            .filterNot { it.isError }
            .filter { it.content.isNotBlank() || it.attachments.isNotEmpty() }
            .takeLast(current.historyLimit.coerceAtLeast(2))

        var imageBudget = if (native) MAX_IMAGES else 0
        val reversed = stored.reversed().map { message ->
            val paths = if (message.role == Roles.USER) imagePaths(message) else emptyList()
            val used = paths.take(imageBudget)
            imageBudget -= used.size
            message to used
        }.reversed()

        val api = reversed.map { (message, paths) ->
            val blocks = mutableListOf<String>()
            if (message.content.isNotBlank()) blocks += message.content
            message.attachments.forEach { attachment ->
                val sent = attachment.isImage && attachment.path in paths
                when {
                    attachment.hasText && !sent ->
                        blocks += "[${label(attachment)}: ${attachment.name}]\n${attachment.text}"

                    attachment.kind == AttachmentKind.GENERATED_IMAGE ->
                        blocks += "[immagine generata: ${attachment.name}]"

                    attachment.isAudio && !attachment.hasText ->
                        blocks += "[audio allegato: ${attachment.name}, non trascritto]"

                    !sent && attachment.isImage ->
                        blocks += "[immagine allegata: ${attachment.name}, non descritta]"
                }
            }
            val text = blocks.joinToString("\n\n").ifBlank { "(allegato)" }
            val urls = paths.mapNotNull { attachments.imageDataUrl(it) }
            ApiMessage(
                role = message.role,
                content = if (urls.isEmpty()) Content.text(text) else Content.parts(text, urls),
            )
        }
        val system = current.systemPrompt.trim()
        return if (system.isEmpty()) {
            api
        } else {
            listOf(ApiMessage(Roles.SYSTEM, Content.text(system))) + api
        }
    }

    private fun imagePaths(message: MessageEntity): List<String> =
        message.attachments.flatMap { attachment ->
            when {
                attachment.kind == AttachmentKind.IMAGE -> listOf(attachment.path)
                attachment.pageImages.isNotEmpty() && !attachment.hasText -> attachment.pageImages
                else -> emptyList()
            }
        }

    private fun label(attachment: Attachment): String = when (attachment.kind) {
        AttachmentKind.DOCUMENT -> "documento"
        AttachmentKind.AUDIO -> "trascrizione audio"
        AttachmentKind.IMAGE -> "descrizione immagine"
        AttachmentKind.GENERATED_IMAGE -> "immagine generata"
        AttachmentKind.GENERATED_AUDIO -> "audio generato"
    }

    // --- generazione -------------------------------------------------------

    /** Genera un'immagine dal testo nel composer e la salva nella conversazione. */
    fun generateImage() {
        val prompt = _input.value.trim()
        if (prompt.isEmpty()) {
            notice("Scrivi che immagine vuoi, poi premi di nuovo")
            return
        }
        val current = settings.value
        val ref = current.imageRef
        if (ref == null || !usable(current, ref)) {
            notice("Scegli un modello per le immagini nelle impostazioni")
            return
        }
        val provider = current.providers.firstOrNull { it.id == ref.providerId } ?: return
        viewModelScope.launch {
            _busy.value = "Genero l'immagine con ${ref.model}…"
            try {
                val id = _conversationId.value
                    ?: repository.createConversation(
                        current.activeProviderId,
                        current.activeModel,
                        titleFrom(prompt),
                    ).also { _conversationId.value = it }
                val bytes = chatApi.generateImage(
                    baseUrl = provider.baseUrl,
                    apiKey = secretStore.apiKey(provider.id),
                    model = ref.model,
                    prompt = prompt,
                    size = current.imageSize,
                )
                _input.value = ""
                repository.addMessage(id, Roles.USER, prompt)
                val saved = bytes.map { attachments.saveGeneratedImage(it, prompt) }
                repository.addMessage(
                    conversationId = id,
                    role = Roles.ASSISTANT,
                    content = "",
                    model = ref.model,
                    attachments = saved,
                )
            } catch (error: Throwable) {
                notice(error.message ?: "Generazione non riuscita")
            } finally {
                _busy.value = null
            }
        }
    }

    /** Legge una risposta ad alta voce: col modello di voce se c'è, altrimenti col telefono. */
    fun speak(message: MessageEntity) {
        if (message.content.isBlank()) return
        val current = settings.value
        val ref = current.speechRef
        val provider = ref?.let { reference ->
            current.providers.firstOrNull { it.id == reference.providerId }
        }
        if (ref == null || provider == null || !usable(current, ref)) {
            if (!deviceSpeaker.isAvailable) {
                notice("Nessuna voce disponibile sul telefono: configura un modello di voce")
                return
            }
            deviceSpeaker.speak(message.content)
            return
        }
        viewModelScope.launch {
            _busy.value = "Genero l'audio con ${ref.model}…"
            try {
                val bytes = chatApi.speech(
                    baseUrl = provider.baseUrl,
                    apiKey = secretStore.apiKey(provider.id),
                    model = ref.model,
                    voice = current.speechVoice,
                    text = message.content,
                )
                val saved = attachments.saveGeneratedAudio(bytes, "Lettura della risposta")
                audioPlayer.toggle(saved.path)
            } catch (error: Throwable) {
                notice(error.message ?: "Sintesi vocale non riuscita")
            } finally {
                _busy.value = null
            }
        }
    }

    fun stopSpeaking() {
        deviceSpeaker.stop()
        audioPlayer.stop()
    }

    fun togglePlayback(attachment: Attachment) = audioPlayer.toggle(attachment.path)

    fun shareUri(attachment: Attachment): Uri = attachments.shareUri(attachment)

    // --- modelli e impostazioni -------------------------------------------

    fun selectModel(providerId: String, model: String) = viewModelScope.launch {
        settingsStore.setActiveModel(providerId, model)
        _conversationId.value?.let { id ->
            if (repository.isEmpty(id)) repository.setModel(id, providerId, model)
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(mode) }

    fun setSystemPrompt(prompt: String) = viewModelScope.launch { settingsStore.setSystemPrompt(prompt) }

    fun setTemperature(value: Float) = viewModelScope.launch { settingsStore.setTemperature(value) }

    fun setMaxTokens(value: Int) = viewModelScope.launch { settingsStore.setMaxTokens(value) }

    fun setHistoryLimit(value: Int) = viewModelScope.launch { settingsStore.setHistoryLimit(value) }

    fun setShowReasoning(value: Boolean) = viewModelScope.launch { settingsStore.setShowReasoning(value) }

    fun setVisionRef(ref: ModelRef?) = viewModelScope.launch { settingsStore.setVisionRef(ref) }

    fun setTranscribeRef(ref: ModelRef?) = viewModelScope.launch { settingsStore.setTranscribeRef(ref) }

    fun setImageRef(ref: ModelRef?) = viewModelScope.launch { settingsStore.setImageRef(ref) }

    fun setSpeechRef(ref: ModelRef?) = viewModelScope.launch { settingsStore.setSpeechRef(ref) }

    fun setSpeechVoice(voice: String) = viewModelScope.launch { settingsStore.setSpeechVoice(voice) }

    fun setImageSize(size: String) = viewModelScope.launch { settingsStore.setImageSize(size) }

    fun setForceNativeVision(value: Boolean) = viewModelScope.launch {
        settingsStore.setForceNativeVision(value)
    }

    fun apiKey(providerId: String): String = secretStore.apiKey(providerId)

    fun setApiKey(providerId: String, key: String) = secretStore.setApiKey(providerId, key)

    fun saveProvider(provider: Provider) = viewModelScope.launch {
        val existing = settings.value.providers
        val updated = if (existing.any { it.id == provider.id }) {
            existing.map { if (it.id == provider.id) provider else it }
        } else {
            existing + provider
        }
        settingsStore.saveProviders(updated)
    }

    fun deleteProvider(providerId: String) = viewModelScope.launch {
        val remaining = settings.value.providers.filterNot { it.id == providerId }
        if (remaining.isEmpty()) {
            notice("Serve almeno un provider")
            return@launch
        }
        settingsStore.saveProviders(remaining)
        secretStore.setApiKey(providerId, "")
        if (settings.value.activeProviderId == providerId) {
            val fallback = remaining.first()
            settingsStore.setActiveModel(fallback.id, fallback.models.firstOrNull().orEmpty())
        }
    }

    /** Chiede al provider l'elenco dei modelli; [onResult] riceve una lista vuota se fallisce. */
    fun fetchModels(provider: Provider, onResult: (List<String>) -> Unit = {}) = viewModelScope.launch {
        runCatching { chatApi.listModels(provider.baseUrl, secretStore.apiKey(provider.id)) }
            .onSuccess { models ->
                if (models.isEmpty()) {
                    notice("Il provider non ha restituito modelli")
                } else {
                    saveProvider(provider.copy(models = models))
                    notice("${models.size} modelli caricati")
                }
                onResult(models)
            }
            .onFailure {
                notice(it.message ?: "Impossibile leggere l'elenco dei modelli")
                onResult(emptyList())
            }
    }

    fun conversationAsText(conversationId: Long, onReady: (String) -> Unit) = viewModelScope.launch {
        val conversation = repository.conversation(conversationId)
        val body = repository.history(conversationId).joinToString("\n\n") { message ->
            val who = if (message.role == Roles.USER) "Io" else "Assistente"
            val files = message.attachments
                .joinToString("") { "\n[allegato: ${it.name}]" }
            "$who:\n${message.content}$files"
        }
        onReady(listOfNotNull(conversation?.title, body).joinToString("\n\n"))
    }

    fun supportsDocument(mime: String, name: String): Boolean = TextExtractor.isSupported(mime, name)

    private fun usable(current: AppSettings, ref: ModelRef?): Boolean {
        val provider = current.providers.firstOrNull { it.id == ref?.providerId } ?: return false
        return ref?.model?.isNotBlank() == true && isReady(provider)
    }

    private fun isReady(provider: Provider): Boolean =
        secretStore.hasKey(provider.id) || !provider.baseUrl.startsWith("https://", ignoreCase = true)

    private fun notice(message: String) {
        _notices.tryEmit(message)
    }

    private fun titleFrom(text: String): String {
        val single = text.replace(Regex("\\s+"), " ").trim()
        return if (single.length <= 48) single else single.take(48).trimEnd() + "…"
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
        deviceSpeaker.stop()
    }

    companion object {
        /** Quante immagini al massimo tenere nel contesto inviato. */
        private const val MAX_IMAGES = 4

        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ChatViewModel(
                    repository = container.repository,
                    settingsStore = container.settingsStore,
                    secretStore = container.secretStore,
                    chatApi = container.chatApi,
                    attachments = container.attachmentStore,
                    recorder = container.audioRecorder,
                    audioPlayer = container.audioPlayer,
                    deviceSpeaker = container.deviceSpeaker,
                )
            }
        }
    }
}

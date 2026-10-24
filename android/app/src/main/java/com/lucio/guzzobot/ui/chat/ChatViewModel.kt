package com.lucio.guzzobot.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lucio.guzzobot.AppContainer
import com.lucio.guzzobot.data.files.AttachmentStore
import com.lucio.guzzobot.data.agent.AgentEngine
import com.lucio.guzzobot.data.agent.AgentLimits
import com.lucio.guzzobot.data.agent.AgentRun
import com.lucio.guzzobot.data.agent.ToolHub
import com.lucio.guzzobot.data.mcp.McpClient
import com.lucio.guzzobot.data.mcp.McpServer
import com.lucio.guzzobot.data.files.TextExtractor
import com.lucio.guzzobot.data.local.ConversationEntity
import com.lucio.guzzobot.data.local.MessageEntity
import com.lucio.guzzobot.data.local.ArtifactDao
import com.lucio.guzzobot.data.local.ArtifactEntity
import com.lucio.guzzobot.data.local.ProjectDao
import com.lucio.guzzobot.data.local.ProjectEntity
import com.lucio.guzzobot.data.local.ScheduledTaskEntity
import com.lucio.guzzobot.data.local.TaskDao
import com.lucio.guzzobot.data.media.AudioPlayer
import com.lucio.guzzobot.data.media.AudioRecorder
import com.lucio.guzzobot.data.media.DeviceSpeaker
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.data.model.AttachmentKind
import com.lucio.guzzobot.data.model.ModelCapabilities
import com.lucio.guzzobot.data.model.ModelRef
import com.lucio.guzzobot.data.model.ModelRefresher
import com.lucio.guzzobot.data.model.VisionRouting
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.KeyDetector
import com.lucio.guzzobot.data.model.ProviderCatalog
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.model.ThemeMode
import com.lucio.guzzobot.data.prefs.SecretStore
import com.lucio.guzzobot.data.prefs.SettingsStore
import com.lucio.guzzobot.data.remote.ApiMessage
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.remote.Content
import com.lucio.guzzobot.data.remote.ApiToolCall
import com.lucio.guzzobot.data.remote.ApiToolFunction
import com.lucio.guzzobot.data.remote.StreamEvent
import com.lucio.guzzobot.data.remote.ToolCall
import com.lucio.guzzobot.data.model.WorkspaceConfig
import com.lucio.guzzobot.data.model.WorkspaceMode
import com.lucio.guzzobot.data.repo.ChatRepository
import com.lucio.guzzobot.data.workspace.ArtifactReader
import com.lucio.guzzobot.data.workspace.Workflow
import com.lucio.guzzobot.data.workspace.Workspace
import com.lucio.guzzobot.data.workspace.WorkspaceBundle
import com.lucio.guzzobot.data.workspace.WorkspaceEntry
import com.lucio.guzzobot.data.workspace.WorkspaceFactory
import com.lucio.guzzobot.data.workspace.WorkspaceTools
import com.lucio.guzzobot.service.WorkspaceService
import com.lucio.guzzobot.work.TaskScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

data class StreamingState(
    val conversationId: Long,
    val model: String,
    val content: String = "",
    val reasoning: String = "",
)

/** Lo stato della schermata Code: dove siamo e cosa stiamo guardando. */
data class CodeState(
    val label: String = "",
    val path: String = "",
    val entries: List<WorkspaceEntry> = emptyList(),
    val file: CodeFile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val github: Boolean = false,
)

data class CodeFile(val path: String, val content: String)

/** Dove porta un artefatto quando lo si tocca. */
sealed interface ArtifactTarget {
    data class Web(val url: String) : ArtifactTarget
    data class Local(val uri: Uri, val mime: String) : ArtifactTarget
    data object Missing : ArtifactTarget
}

/** L'avanzamento dell'agente, mostrato sopra il composer. */
data class AgentProgress(
    val goal: String,
    val steps: Int,
    val maxSteps: Int,
    val minutes: Long,
    val note: String = "",
)

/** Una modifica che aspetta il sì dell'utente. */
data class ApprovalRequest(
    val title: String,
    val preview: String,
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
    private val context: Context,
    private val json: Json,
    private val agentEngine: AgentEngine,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val modelRefresher: ModelRefresher,
    private val artifactDao: ArtifactDao,
    private val mcpClient: McpClient,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * Se mostrare il primo avvio: resta null finché le impostazioni non sono
     * state lette da disco, altrimenti a ogni lancio comparirebbe per un
     * istante anche a chi l'ha già fatto.
     */
    val onboardingNeeded: StateFlow<Boolean?> = settingsStore.settings
        .map { current ->
            // Chi aggiorna avendo già configurato una chiave non va portato a
            // configurarne un'altra: per lui il primo avvio è già passato.
            !current.onboardingDone &&
                current.providers.none { secretStore.apiKey(it.id).isNotBlank() }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    private val _agentProgress = MutableStateFlow<AgentProgress?>(null)
    val agentProgress: StateFlow<AgentProgress?> = _agentProgress

    /** L'interruttore "modalità agente" accanto al composer. */
    private val _agentMode = MutableStateFlow(false)
    val agentMode: StateFlow<Boolean> = _agentMode

    private val _approval = MutableStateFlow<ApprovalRequest?>(null)
    val approval: StateFlow<ApprovalRequest?> = _approval

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    val speaking: StateFlow<Boolean> = deviceSpeaker.speaking

    val ready: StateFlow<Boolean> = combine(settings, secretStore.changes) { current, _ ->
        current.activeProvider?.let { isReady(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val abilities: StateFlow<Abilities> = combine(settings, secretStore.changes) { current, _ ->
        Abilities(
            nativeVision = current.forceNativeVision || ModelCapabilities.seesImages(current.activeModel),
            canDescribeImages = usable(current, current.visionRef) ||
                VisionRouting.pick(current.providers, current.activeProviderId) { isReady(it) } != null,
            canTranscribe = usable(current, current.transcribeRef),
            canGenerateImages = usable(current, current.imageRef),
            canSpeakWithProvider = usable(current, current.speechRef),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Abilities())

    val artifacts: StateFlow<List<ArtifactEntity>> =
        artifactDao.observeArtifacts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val projects: StateFlow<List<ProjectEntity>> =
        projectDao.observeProjects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tasks: StateFlow<List<ScheduledTaskEntity>> =
        taskDao.observeTasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _code = MutableStateFlow(CodeState())
    val code: StateFlow<CodeState> = _code

    private var streamJob: Job? = null
    private var agentJob: Job? = null
    private var approvalGate: CompletableDeferred<Boolean>? = null

    /** "Consenti sempre" vale per la conversazione in corso, non per sempre. */
    private var sessionAutonomy = false

    // --- conversazioni -----------------------------------------------------

    fun newChat() {
        if (_streaming.value != null) stop()
        _conversationId.value = null
        _input.value = ""
        sessionAutonomy = false
        clearPending()
    }

    /**
      * Torna all'elenco senza toccare la bozza nel composer: serve al tasto
      * indietro, che non deve far perdere quello che si stava scrivendo.
      */
    fun closeConversation() {
        _conversationId.value = null
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

    fun togglePin(conversation: ConversationEntity) = viewModelScope.launch {
        repository.setPinned(conversation.id, !conversation.pinned)
    }

    fun assignProject(conversationId: Long, projectId: Long?) = viewModelScope.launch {
        repository.setProject(conversationId, projectId)
    }

    fun createProject(name: String, assign: Long? = null) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = projectDao.upsert(
            ProjectEntity(name = name.trim().take(60), createdAt = System.currentTimeMillis()),
        )
        assign?.let { repository.setProject(it, id) }
    }

    fun deleteProject(id: Long) = viewModelScope.launch {
        projectDao.detachConversations(id)
        projectDao.delete(id)
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
            AttachmentKind.VIDEO -> "Estraggo fotogrammi e audio dal video…"
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
            val hasSpeech = kind == AttachmentKind.AUDIO ||
                (kind == AttachmentKind.VIDEO && attachment.audioPath != null)
            if (hasSpeech) transcribeAttachment(attachment)
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
        // Di un video si trascrive la traccia audio estratta, non il filmato.
        val source = File(attachment.audioPath ?: attachment.path)
        if (!source.exists()) return
        if (source.length() > MAX_TRANSCRIPTION_BYTES) {
            notice("L'audio è troppo lungo per la trascrizione: il limite è 24 MB")
            return
        }
        _busy.value = "Trascrivo l'audio con ${ref.model}…"
        try {
            val text = chatApi.transcribe(
                baseUrl = provider.baseUrl,
                apiKey = secretStore.apiKey(provider.id),
                model = ref.model,
                file = source,
                mime = if (attachment.audioPath != null) "audio/mp4" else attachment.mime,
            )
            if (text.isBlank()) {
                notice("La trascrizione è vuota")
                return
            }
            _pending.update { list ->
                list.map { item ->
                    if (item.id == attachment.id) {
                        item.copy(
                            text = text,
                            note = listOfNotNull(item.note, "trascritto con ${ref.model}")
                                .joinToString(" · "),
                        )
                    } else {
                        item
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
        if (_agentMode.value && files.isEmpty()) {
            startAgent(text)
            return
        }
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
            !file.hasVisionText && file.visualPaths.isNotEmpty()
        }
        if (native || needsReading.isEmpty()) return files

        // Il modello scelto a mano, oppure uno capace fra quelli configurati.
        val chosen = current.visionRef?.takeIf { usable(current, it) }
        val ref = chosen ?: VisionRouting.pick(
            providers = current.providers,
            preferProviderId = current.activeProviderId,
        ) { provider -> isReady(provider) }
        if (ref == null) {
            // Un video con l'audio già trascritto resta utile anche senza vision.
            if (needsReading.all { it.hasText }) return files
            notice(
                "${current.activeModel} non legge le immagini e non ne trovo uno che le " +
                    "veda fra i provider configurati: aggiungine uno nelle impostazioni.",
            )
            return null
        }
        if (chosen == null) {
            notice("${current.activeModel} non vede le immagini: le legge ${ref.model}")
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
                            visionText = description,
                            note = listOfNotNull(updated[index].note, "letto da ${ref.model}")
                                .joinToString(" · "),
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
        val images = attachment.visualPaths
        if (images.isEmpty()) return ""

        // Un video va letto in un colpo solo: i fotogrammi hanno senso in sequenza.
        if (attachment.isVideo) {
            val urls = images.mapNotNull { attachments.imageDataUrl(it) }
            if (urls.isEmpty()) return ""
            val note = attachment.note?.let { " ($it)" }.orEmpty()
            val prompt = "Questi sono ${urls.size} fotogrammi in ordine cronologico di un " +
                "video" + note + ". Descrivi cosa accade: soggetti, azioni, cambi di scena, " +
                "testo leggibile. Resta fattuale."
            return chatApi.complete(
                baseUrl = provider.baseUrl,
                apiKey = apiKey,
                model = model,
                messages = listOf(ApiMessage(Roles.USER, Content.parts(prompt, urls))),
            )
        }

        val isDocument = attachment.kind == AttachmentKind.DOCUMENT
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
        files.any { it.isVideo } -> "Cosa succede in questo video?"
        files.any { it.isAudio } -> "Ascolta e rispondi a questa nota vocale."
        files.any { it.kind == AttachmentKind.DOCUMENT } -> "Analizza questo documento."
        files.any { it.isImage } -> "Cosa vedi in questa immagine?"
        else -> "Analizza l'allegato."
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        agentJob?.cancel()
        agentJob = null
        approvalGate?.complete(false)
    }

    fun setAgentMode(value: Boolean) {
        _agentMode.value = value
    }

    /**
     * Avvia una corsa dell'agente sull'obiettivo scritto nel composer: va avanti
     * da solo finché non conclude, si blocca o esaurisce il budget.
     */
    fun startAgent(goal: String) {
        if (goal.isBlank() || _agentProgress.value != null || _streaming.value != null) return
        val current = settings.value
        val provider = current.activeProvider
        if (provider == null || !isReady(provider)) {
            notice("Configura un provider con la sua chiave per usare l'agente")
            return
        }
        val tools = hub(current)
        if (!tools.hasTools) {
            notice("L'agente ha bisogno di un'area di lavoro o di un connettore")
            return
        }
        _input.value = ""
        agentJob = viewModelScope.launch {
            if (current.backgroundWork) {
                WorkspaceService.start(
                    context,
                    context.getString(com.lucio.guzzobot.R.string.service_working),
                )
            }
            val id = _conversationId.value
                ?: repository.createConversation(provider.id, current.activeModel, titleFrom(goal))
                    .also { _conversationId.value = it }
            val wasEmpty = repository.isEmpty(id)
            repository.addMessage(id, Roles.USER, goal)
            if (wasEmpty) repository.rename(id, titleFrom(goal))
            try {
                agentEngine.run(
                    conversationId = id,
                    goal = goal,
                    provider = provider,
                    apiKey = secretStore.apiKey(provider.id),
                    model = current.activeModel,
                    settings = current,
                    hub = tools,
                    limits = AgentLimits(
                        maxSteps = current.agentMaxSteps,
                        maxRounds = (current.agentMaxSteps * 2 / 3).coerceAtLeast(6),
                        maxMinutes = current.agentMaxMinutes,
                    ),
                    approve = { call ->
                        if (current.autonomousTools || sessionAutonomy) true else requestApproval(call)
                    },
                    onProgress = { run -> _agentProgress.value = snapshot(run) },
                    onChunk = { content, reasoning ->
                        _streaming.value = StreamingState(
                            conversationId = id,
                            model = current.activeModel,
                            content = content,
                            reasoning = reasoning,
                        )
                    },
                    onTool = { description -> _busy.value = "$description…" },
                )
            } finally {
                _streaming.value = null
                _busy.value = null
                _agentProgress.value = null
                agentJob = null
                WorkspaceService.stop(context)
            }
        }
    }

    private suspend fun saveArtifact(
        conversationId: Long?,
        draft: com.lucio.guzzobot.data.workspace.ArtifactDraft,
    ) {
        artifactDao.insert(
            ArtifactEntity(
                conversationId = conversationId,
                kind = draft.kind,
                name = draft.name,
                location = draft.location,
                detail = draft.detail,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun deleteArtifact(id: Long) = viewModelScope.launch { artifactDao.delete(id) }

    private fun snapshot(run: AgentRun) = AgentProgress(
        goal = run.goal,
        steps = run.steps,
        maxSteps = run.limits.maxSteps,
        minutes = run.elapsedMinutes,
    )

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
            _pending.value = message.attachments.filterNot {
                it.kind == AttachmentKind.GENERATED_IMAGE || it.kind == AttachmentKind.GENERATED_AUDIO
            }
        }
    }

    /**
     * Un turno: il modello risponde, oppure chiede degli strumenti. In quel caso
     * li eseguiamo — chiedendo conferma quando toccano i file — e gli
     * rimandiamo i risultati, finché non arriva una risposta vera.
     */
    private fun runStream(conversationId: Long, provider: Provider, current: AppSettings) {
        val model = current.activeModel
        val apiKey = secretStore.apiKey(provider.id)
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            if (current.backgroundWork) {
                WorkspaceService.start(context, context.getString(com.lucio.guzzobot.R.string.service_working))
            }
            try {
                var round = 0
                while (round++ <= MAX_TOOL_ROUNDS) {
                    val history = buildHistory(conversationId, current)
                    _streaming.value = StreamingState(conversationId = conversationId, model = model)
                    val content = StringBuilder()
                    val reasoning = StringBuilder()
                    var requested: List<ToolCall> = emptyList()
                    try {
                        chatApi.streamChat(
                            baseUrl = provider.baseUrl,
                            apiKey = apiKey,
                            model = model,
                            messages = history,
                            temperature = current.temperature,
                            maxTokens = current.maxTokens,
                            tools = hub(current).definitions(agent = false),
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
                                is StreamEvent.ToolRequests -> requested = event.calls
                            }
                        }
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
                        break
                    } finally {
                        _streaming.value = null
                    }

                    if (requested.isEmpty()) {
                        persist(conversationId, content.toString(), reasoning.toString(), model)
                        break
                    }
                    if (round > MAX_TOOL_ROUNDS) {
                        repository.addMessage(
                            conversationId = conversationId,
                            role = Roles.ASSISTANT,
                            content = "Mi sono fermato dopo $MAX_TOOL_ROUNDS giri di strumenti.",
                            model = model,
                            isError = true,
                        )
                        break
                    }

                    repository.addMessage(
                        conversationId = conversationId,
                        role = Roles.ASSISTANT,
                        content = content.toString(),
                        reasoning = reasoning.toString(),
                        model = model,
                        toolCalls = encodeToolCalls(requested),
                    )
                    requested.forEach { call ->
                        val outcome = executeTool(call, current)
                        repository.addMessage(
                            conversationId = conversationId,
                            role = Roles.TOOL,
                            content = outcome.first,
                            isError = outcome.second,
                            toolCallId = call.id,
                            toolName = WorkspaceTools.describe(call),
                        )
                        if (!outcome.second) {
                            ArtifactReader.from(
                                toolName = call.name,
                                arguments = call.arguments,
                                result = outcome.first,
                                repoUrl = hub(current).github?.blobBaseUrl,
                            ).forEach { draft -> saveArtifact(conversationId, draft) }
                        }
                    }
                }
            } finally {
                _streaming.value = null
                _busy.value = null
                streamJob = null
                WorkspaceService.stop(context)
            }
        }
    }

    /** Esegue uno strumento, passando dal permesso quando tocca i file. */
    private suspend fun executeTool(call: ToolCall, current: AppSettings): Pair<String, Boolean> {
        val tools = hub(current)
        if (!tools.hasTools) return "Nessuno strumento disponibile." to true
        if (tools.modifies(call) && !current.autonomousTools && !sessionAutonomy) {
            val allowed = requestApproval(call)
            if (!allowed) return "L'utente ha rifiutato l'operazione." to true
        }
        _busy.value = tools.describe(call) + "…"
        return try {
            tools.execute(call, null) to false
        } catch (error: Throwable) {
            (error.message ?: "Operazione non riuscita") to true
        } finally {
            _busy.value = null
        }
    }

    private suspend fun requestApproval(call: ToolCall): Boolean {
        val gate = CompletableDeferred<Boolean>()
        approvalGate = gate
        val tools = hub(settings.value)
        _approval.value = ApprovalRequest(
            title = tools.describe(call),
            preview = tools.preview(call),
        )
        return try {
            gate.await()
        } finally {
            _approval.value = null
            approvalGate = null
        }
    }

    fun resolveApproval(allow: Boolean, alwaysInThisChat: Boolean = false) {
        if (allow && alwaysInThisChat) sessionAutonomy = true
        approvalGate?.complete(allow)
    }

    private fun bundle(current: AppSettings): WorkspaceBundle? =
        WorkspaceFactory.create(context, current, secretStore, json)

    /** Area di lavoro e connettori insieme: è quello che il modello può chiamare. */
    private fun hub(current: AppSettings) = ToolHub(
        bundle = bundle(current),
        servers = current.mcpServers,
        mcpClient = mcpClient,
        secrets = secretStore,
        json = json,
    )

    // --- connettori MCP ----------------------------------------------------

    fun saveConnector(server: McpServer) = viewModelScope.launch {
        val current = settings.value.mcpServers
        val updated = if (current.any { it.id == server.id }) {
            current.map { if (it.id == server.id) server else it }
        } else {
            current + server
        }
        settingsStore.saveMcpServers(updated)
    }

    fun deleteConnector(id: String) = viewModelScope.launch {
        settingsStore.saveMcpServers(settings.value.mcpServers.filterNot { it.id == id })
        secretStore.setApiKey(McpServer.secretKey(id), "")
    }

    fun setConnectorToken(id: String, token: String) =
        secretStore.setApiKey(McpServer.secretKey(id), token)

    fun connectorToken(id: String): String = secretStore.apiKey(McpServer.secretKey(id))

    /** Interroga il connettore e salva l'elenco dei suoi strumenti. */
    fun testConnector(server: McpServer) = viewModelScope.launch {
        _busy.value = "Provo ${server.name}…"
        val token = secretStore.apiKey(McpServer.secretKey(server.id))
        val result = runCatching { mcpClient.listTools(server, token) }
        _busy.value = null
        result.onSuccess { tools ->
            saveConnector(
                server.copy(
                    tools = tools,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastError = null,
                ),
            )
            notice(
                if (tools.isEmpty()) {
                    "${server.name} risponde ma non espone strumenti"
                } else {
                    "${server.name}: ${tools.size} strumenti disponibili"
                },
            )
        }.onFailure { error ->
            saveConnector(
                server.copy(
                    lastCheckedAt = System.currentTimeMillis(),
                    lastError = error.message ?: "Non risponde",
                ),
            )
            notice(error.message ?: "Il connettore non risponde")
        }
    }

    private fun workspace(current: AppSettings): Workspace? = bundle(current)?.workspace

    private fun encodeToolCalls(calls: List<ToolCall>): String = json.encodeToString(
        ListSerializer(ApiToolCall.serializer()),
        calls.map { call ->
            ApiToolCall(
                id = call.id,
                function = ApiToolFunction(name = call.name, arguments = call.arguments),
            )
        },
    )

    private fun decodeToolCalls(raw: String): List<ApiToolCall> = runCatching {
        json.decodeFromString(ListSerializer(ApiToolCall.serializer()), raw)
    }.getOrDefault(emptyList())

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
            .filterNot { it.isError && it.role != Roles.TOOL }
            .filter {
                it.content.isNotBlank() || it.attachments.isNotEmpty() || it.toolCalls != null
            }
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
                val sentNatively = attachment.visualPaths.any { it in paths }
                if (attachment.hasText) {
                    blocks += "[${label(attachment)}: ${attachment.name}]\n${attachment.text}"
                }
                if (attachment.hasVisionText) {
                    blocks += "[${visionLabel(attachment)}: ${attachment.name}]\n" +
                        attachment.visionText
                }
                when {
                    attachment.kind == AttachmentKind.GENERATED_IMAGE ->
                        blocks += "[immagine generata: ${attachment.name}]"

                    attachment.isAudio && !attachment.hasText ->
                        blocks += "[audio allegato: ${attachment.name}, non trascritto]"

                    !sentNatively && !attachment.hasVisionText &&
                        attachment.visualPaths.isNotEmpty() ->
                        blocks += "[allegato visivo: ${attachment.name}, non descritto]"
                }
            }
            val text = blocks.joinToString("\n\n").ifBlank { "(allegato)" }
            val urls = paths.mapNotNull { attachments.imageDataUrl(it) }
            when {
                message.role == Roles.TOOL -> ApiMessage(
                    role = Roles.TOOL,
                    content = Content.text(message.content),
                    toolCallId = message.toolCallId,
                )

                message.toolCalls != null -> ApiMessage(
                    role = Roles.ASSISTANT,
                    content = message.content.takeIf { it.isNotBlank() }?.let(Content::text),
                    toolCalls = decodeToolCalls(message.toolCalls),
                )

                else -> ApiMessage(
                    role = message.role,
                    content = if (urls.isEmpty()) Content.text(text) else Content.parts(text, urls),
                )
            }
        }
        // Un messaggio "tool" senza la richiesta che lo ha generato fa fallire la
        // chiamata: se il taglio della cronologia l'ha lasciato orfano, via.
        val trimmed = api.dropWhile { it.role == Roles.TOOL }

        val system = buildString {
            append(current.systemPrompt.trim())
            workspace(current)?.let { workspace ->
                if (isNotEmpty()) append("\n\n")
                append(
                    WorkspaceTools.systemPrompt(
                        label = workspace.label,
                        autonomous = current.autonomousTools || sessionAutonomy,
                    ),
                )
            }
        }
        return if (system.isEmpty()) {
            trimmed
        } else {
            listOf(ApiMessage(Roles.SYSTEM, Content.text(system))) + trimmed
        }
    }

    private fun imagePaths(message: MessageEntity): List<String> =
        message.attachments
            .filterNot { it.hasVisionText }
            .flatMap { it.visualPaths }

    private fun label(attachment: Attachment): String = when (attachment.kind) {
        AttachmentKind.DOCUMENT -> "documento"
        AttachmentKind.AUDIO -> "trascrizione audio"
        AttachmentKind.VIDEO -> "trascrizione dell'audio del video"
        AttachmentKind.IMAGE -> "immagine"
        AttachmentKind.GENERATED_IMAGE -> "immagine generata"
        AttachmentKind.GENERATED_AUDIO -> "audio generato"
    }

    private fun visionLabel(attachment: Attachment): String = when (attachment.kind) {
        AttachmentKind.VIDEO -> "descrizione del video"
        AttachmentKind.DOCUMENT -> "testo letto dalle pagine"
        else -> "descrizione immagine"
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
                saved.forEach { image ->
                    saveArtifact(
                        conversationId = id,
                        draft = com.lucio.guzzobot.data.workspace.ArtifactDraft(
                            kind = ArtifactReader.IMAGE,
                            name = prompt.take(60),
                            location = image.path,
                            detail = ref.model,
                        ),
                    )
                }
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
                saveArtifact(
                    conversationId = _conversationId.value,
                    draft = com.lucio.guzzobot.data.workspace.ArtifactDraft(
                        kind = ArtifactReader.AUDIO,
                        name = "Lettura della risposta",
                        location = saved.path,
                        detail = ref.model,
                    ),
                )
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

    fun setWorkspace(config: WorkspaceConfig) = viewModelScope.launch {
        settingsStore.setWorkspace(config)
    }

    fun setAutonomousTools(value: Boolean) = viewModelScope.launch {
        settingsStore.setAutonomousTools(value)
    }

    fun setBackgroundWork(value: Boolean) = viewModelScope.launch {
        settingsStore.setBackgroundWork(value)
    }

    fun setAgentBudget(steps: Int, minutes: Int) = viewModelScope.launch {
        settingsStore.setAgentBudget(steps, minutes)
    }

    // --- Code: sfogliare l'area di lavoro ---------------------------------

    fun browse(path: String = "") {
        val current = settings.value
        val active = bundle(current)
        if (active == null) {
            _code.value = CodeState(error = "Nessuna area di lavoro configurata")
            return
        }
        viewModelScope.launch {
            _code.value = _code.value.copy(
                loading = true,
                error = null,
                file = null,
                label = active.workspace.label,
                github = active.github != null,
            )
            val result = runCatching { active.workspace.list(path) }
            _code.value = result.fold(
                onSuccess = { entries ->
                    _code.value.copy(path = path, entries = entries, loading = false)
                },
                onFailure = { error ->
                    _code.value.copy(loading = false, error = error.message)
                },
            )
        }
    }

    fun openCodeFile(path: String) {
        val active = bundle(settings.value) ?: return
        viewModelScope.launch {
            _code.value = _code.value.copy(loading = true, error = null)
            val result = runCatching { active.workspace.read(path) }
            _code.value = result.fold(
                onSuccess = { text ->
                    _code.value.copy(file = CodeFile(path, text), loading = false)
                },
                onFailure = { error ->
                    _code.value.copy(loading = false, error = error.message)
                },
            )
        }
    }

    fun closeCodeFile() {
        _code.value = _code.value.copy(file = null)
    }

    /** Risale di una cartella; dalla radice non si va oltre. */
    fun codeUp() {
        val path = _code.value.path
        if (path.isEmpty()) return
        browse(path.substringBeforeLast('/', ""))
    }

    fun workflows(onResult: (List<Workflow>) -> Unit) = viewModelScope.launch {
        val actions = bundle(settings.value)?.actions ?: return@launch onResult(emptyList())
        runCatching { actions.workflows() }
            .onSuccess(onResult)
            .onFailure {
                notice(it.message ?: "Non riesco a leggere i workflow")
                onResult(emptyList())
            }
    }

    fun runWorkflow(workflow: Workflow) = viewModelScope.launch {
        val active = bundle(settings.value) ?: return@launch
        val actions = active.actions ?: return@launch
        val branch = active.github?.branch ?: return@launch
        _busy.value = "Avvio ${workflow.name}…"
        runCatching { actions.dispatch(workflow.path.substringAfterLast('/'), branch) }
            .onSuccess { notice("${workflow.name} avviato su $branch") }
            .onFailure { notice(it.message ?: "Avvio non riuscito") }
        _busy.value = null
    }

    fun ciStatus() = viewModelScope.launch {
        val active = bundle(settings.value) ?: return@launch
        val actions = active.actions ?: return@launch
        val branch = active.github?.branch ?: return@launch
        _busy.value = "Controllo la CI…"
        runCatching { actions.awaitRun(branch, afterId = 0, waitSeconds = 0) }
            .onSuccess { run ->
                notice(
                    run?.let { "${it.name}: ${it.conclusion ?: it.status}" }
                        ?: "Nessuna corsa su $branch",
                )
            }
            .onFailure { notice(it.message ?: "Non riesco a leggere la CI") }
        _busy.value = null
    }

    /** Dalla schermata Code: manda l'agente a lavorare su un file preciso. */
    fun askAgentAbout(path: String, request: String) {
        val goal = buildString {
            append(request.trim())
            if (path.isNotBlank()) append("\n\nFile di partenza: $path")
        }
        startAgent(goal)
    }

    // --- privacy -----------------------------------------------------------

    /** Gli indirizzi che l'app contatta davvero, con la ragione. */
    fun endpoints(): List<Pair<String, String>> {
        val current = settings.value
        val list = mutableListOf<Pair<String, String>>()
        current.activeProvider?.let { list += it.baseUrl to "modello in chat (${it.name})" }
        listOfNotNull(
            current.visionRef?.let { it to "lettura delle immagini" },
            current.transcribeRef?.let { it to "trascrizione audio" },
            current.imageRef?.let { it to "generazione immagini" },
            current.speechRef?.let { it to "sintesi vocale" },
        ).forEach { (ref, reason) ->
            current.providers.firstOrNull { it.id == ref.providerId }?.let { provider ->
                list += provider.baseUrl to "$reason (${ref.model})"
            }
        }
        when (current.workspace.mode) {
            com.lucio.guzzobot.data.model.WorkspaceMode.GITHUB ->
                list += "https://api.github.com" to "area di lavoro ${current.workspace.label}"
            com.lucio.guzzobot.data.model.WorkspaceMode.LOCAL ->
                list += current.workspace.label to "cartella sul telefono, non esce da qui"
            com.lucio.guzzobot.data.model.WorkspaceMode.NONE -> Unit
        }
        current.mcpServers.filter { it.enabled }.forEach { server ->
            list += server.url to "connettore ${server.name}"
        }
        return list.distinctBy { it.first }
    }

    /** Quanto spazio occupano gli allegati salvati. */
    fun attachmentsBytes(): Long = runCatching {
        java.io.File(context.filesDir, "attachments").walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    /** Dimentica tutte le chiavi: provider, GitHub e connettori. */
    fun forgetAllKeys() {
        val current = settings.value
        current.providers.forEach { secretStore.setApiKey(it.id, "") }
        secretStore.setApiKey(WorkspaceConfig.GITHUB_SECRET, "")
        current.mcpServers.forEach { secretStore.setApiKey(McpServer.secretKey(it.id), "") }
        notice("Chiavi cancellate dal dispositivo")
    }

    /** Restituisce il permesso sulla cartella e dimentica l'area di lavoro. */
    fun revokeWorkspaceFolder() = viewModelScope.launch {
        val current = settings.value.workspace
        current.localTreeUri?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        settingsStore.setWorkspace(
            current.copy(
                mode = com.lucio.guzzobot.data.model.WorkspaceMode.NONE,
                localTreeUri = null,
                localLabel = null,
            ),
        )
        notice("Permesso sulla cartella restituito")
    }

    // --- artefatti ---------------------------------------------------------

    fun openArtifact(artifact: ArtifactEntity, onResult: (ArtifactTarget) -> Unit) =
        viewModelScope.launch {
            val target = when {
                artifact.location.startsWith("http") -> ArtifactTarget.Web(artifact.location)

                artifact.location.startsWith("ws:") -> {
                    val relative = artifact.location.removePrefix("ws:")
                    val uri = attachments.workspaceFileUri(
                        settings.value.workspace.localTreeUri,
                        relative,
                    )
                    if (uri == null) ArtifactTarget.Missing else ArtifactTarget.Local(uri, "*/*")
                }

                else -> {
                    val file = File(artifact.location)
                    if (!file.exists()) {
                        ArtifactTarget.Missing
                    } else {
                        ArtifactTarget.Local(
                            attachments.uriForFile(file),
                            if (artifact.kind == ArtifactReader.AUDIO) "audio/*" else "image/*",
                        )
                    }
                }
            }
            onResult(target)
        }

    // --- compiti pianificati ----------------------------------------------

    fun saveTask(task: ScheduledTaskEntity) = viewModelScope.launch {
        val id = taskDao.upsert(task)
        val stored = taskDao.task(if (task.id == 0L) id else task.id) ?: return@launch
        TaskScheduler.sync(context, stored)
    }

    fun deleteTask(id: Long) = viewModelScope.launch {
        TaskScheduler.cancel(context, id)
        taskDao.delete(id)
    }

    fun runTaskNow(id: Long) {
        TaskScheduler.runNow(context, id)
        notice("Compito avviato: riceverai una notifica alla fine")
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
        // Se era un predefinito cancellato e lo si rimette, non è più cancellato.
        if (provider.id in settings.value.removedProviders) {
            settingsStore.setProviderRemoved(provider.id, false)
        }
    }

    fun deleteProvider(providerId: String) = viewModelScope.launch {
        val remaining = settings.value.providers.filterNot { it.id == providerId }
        if (remaining.isEmpty()) {
            notice("Serve almeno un provider")
            return@launch
        }
        settingsStore.saveProviders(remaining)
        if (settings.value.providers.firstOrNull { it.id == providerId }?.builtIn == true) {
            settingsStore.setProviderRemoved(providerId, true)
        }
        secretStore.setApiKey(providerId, "")
        if (settings.value.activeProviderId == providerId) {
            val fallback = remaining.first()
            settingsStore.setActiveModel(fallback.id, fallback.models.firstOrNull().orEmpty())
        }
    }

    /**
     * Riallinea gli elenchi con quello che i provider servono oggi: i nomi dei
     * modelli cambiano, e un nome morto in mano non serve a nessuno.
     */
    // --- modelli gratuiti --------------------------------------------------

    /** I provider con un piano gratuito, quelli pronti all'uso per primi. */
    fun freeProviders(): List<Provider> = ProviderCatalog.free(settings.value.providers)
        .sortedByDescending { apiKey(it.id).isNotBlank() }

    /** Vero se il modello in chat è coperto da un piano gratuito. */
    fun isFreeModel(providerId: String, model: String): Boolean =
        ProviderCatalog.isFree(settings.value.providers, providerId, model)

    /**
     * Nessuna chiave da nessuna parte: l'app non può ancora parlare con
     * niente. Ollama non conta: c'è di serie su ogni installazione, e
     * contarlo come «configurato» nasconderebbe l'avviso a tutti.
     */
    fun hasNoKeys(): Boolean =
        settings.value.providers.none { apiKey(it.id).isNotBlank() }

    fun acceptFreeDisclaimer() = viewModelScope.launch { settingsStore.acceptFreeDisclaimer() }

    fun completeOnboarding() = viewModelScope.launch { settingsStore.completeOnboarding() }

    /** Una chiave trovata negli appunti, se calza questo provider. */
    fun keyFromClipboard(providerId: String, clipboard: String?): String? {
        val provider = settings.value.providers.firstOrNull { it.id == providerId }
            ?: return null
        return KeyDetector.forProvider(clipboard, provider)
    }

    /**
     * Prova la chiave prima di salvarla, chiedendo al provider l'elenco dei
     * suoi modelli: se risponde, la chiave è buona; se no, si riporta il
     * motivo vero invece di un generico "errore".
     */
    suspend fun verifyKey(providerId: String, key: String): Result<Int> {
        val provider = settings.value.providers.firstOrNull { it.id == providerId }
            ?: return Result.failure(IllegalStateException("Provider sconosciuto"))
        return runCatching { chatApi.listModels(provider.baseUrl, key.trim()).size }
    }

    /**
     * Salva la chiave di un provider gratuito, sceglie subito un suo modello
     * gratuito e si fa dire da /models quali esistono davvero: i nomi scritti
     * nell'app sono soltanto un punto di partenza.
     */
    fun activateFreeProvider(providerId: String, key: String) = viewModelScope.launch {
        val provider = settings.value.providers.firstOrNull { it.id == providerId }
            ?: return@launch
        if (key.isNotBlank()) secretStore.setApiKey(providerId, key.trim())
        val model = provider.models.firstOrNull { provider.freeTier?.covers(it) == true }
            ?: provider.models.firstOrNull()
        if (model != null) settingsStore.setActiveModel(providerId, model)
        settingsStore.acceptFreeDisclaimer()
        if (secretStore.apiKey(providerId).isNotBlank()) refreshModels(providerId)
        notice("${provider.name}: pronto")
    }

    fun refreshModels(providerId: String? = null) = viewModelScope.launch {
        _busy.value = "Aggiorno l'elenco dei modelli…"
        val report = runCatching { modelRefresher.refresh(settings.value, providerId) }.getOrNull()
        _busy.value = null
        when {
            report == null -> notice("Aggiornamento dei modelli non riuscito")
            report.activeModelChanged != null -> notice(
                "${report.activeModelChanged!!.first} non c'è più: passo a " +
                    report.activeModelChanged!!.second,
            )
            report.updated.isNotEmpty() -> notice("Modelli aggiornati: ${report.updated.joinToString()}")
            report.failed.isNotEmpty() -> notice("Non ho potuto aggiornare: ${report.failed.joinToString()}")
            else -> notice("Gli elenchi erano già aggiornati")
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
        /** Oltre questo numero di giri di strumenti ci fermiamo da soli. */
        private const val MAX_TOOL_ROUNDS = 8

        /** Quante immagini al massimo tenere nel contesto inviato. */
        private const val MAX_IMAGES = 8

        /** I servizi di trascrizione rifiutano file più grandi di così. */
        private const val MAX_TRANSCRIPTION_BYTES = 24_000_000L

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
                    context = container.appContext,
                    json = container.json,
                    agentEngine = container.agentEngine,
                    taskDao = container.taskDao,
                    projectDao = container.projectDao,
                    modelRefresher = container.modelRefresher,
                    artifactDao = container.artifactDao,
                    mcpClient = container.mcpClient,
                )
            }
        }
    }
}

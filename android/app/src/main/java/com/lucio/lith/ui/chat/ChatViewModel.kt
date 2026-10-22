package com.lucio.lith.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lucio.lith.AppContainer
import com.lucio.lith.data.local.ConversationEntity
import com.lucio.lith.data.local.MessageEntity
import com.lucio.lith.data.model.AppSettings
import com.lucio.lith.data.model.Provider
import com.lucio.lith.data.model.Roles
import com.lucio.lith.data.model.ThemeMode
import com.lucio.lith.data.prefs.SecretStore
import com.lucio.lith.data.prefs.SettingsStore
import com.lucio.lith.data.remote.ApiMessage
import com.lucio.lith.data.remote.ChatApi
import com.lucio.lith.data.remote.StreamEvent
import com.lucio.lith.data.repo.ChatRepository
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

data class StreamingState(
    val conversationId: Long,
    val model: String,
    val content: String = "",
    val reasoning: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val settingsStore: SettingsStore,
    private val secretStore: SecretStore,
    private val chatApi: ChatApi,
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

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    /** true quando il provider attivo è pronto all'uso (chiave presente o non necessaria). */
    val ready: StateFlow<Boolean> = combine(settings, secretStore.changes) { current, _ ->
        current.activeProvider?.let { isReady(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var streamJob: Job? = null

    // --- conversazioni -----------------------------------------------------

    fun newChat() {
        if (_streaming.value != null) stop()
        _conversationId.value = null
        _input.value = ""
    }

    fun openConversation(id: Long) {
        if (_streaming.value != null && _streaming.value?.conversationId != id) stop()
        _conversationId.value = id
    }

    fun deleteConversation(id: Long) = viewModelScope.launch {
        if (_streaming.value?.conversationId == id) stop()
        repository.delete(id)
        if (_conversationId.value == id) _conversationId.value = null
    }

    fun renameConversation(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repository.rename(id, title)
    }

    fun deleteAllConversations() = viewModelScope.launch {
        stop()
        repository.deleteAll()
        _conversationId.value = null
    }

    // --- composer ----------------------------------------------------------

    fun setInput(text: String) {
        _input.value = text
    }

    fun send() {
        val text = _input.value.trim()
        if (text.isEmpty() || _streaming.value != null) return
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
        _input.value = ""
        viewModelScope.launch {
            val id = _conversationId.value
                ?: repository.createConversation(provider.id, current.activeModel, titleFrom(text))
                    .also { _conversationId.value = it }
            val wasEmpty = repository.isEmpty(id)
            repository.addMessage(id, Roles.USER, text)
            if (wasEmpty) repository.rename(id, titleFrom(text))
            runStream(id, provider, current)
        }
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

    private suspend fun buildHistory(conversationId: Long, current: AppSettings): List<ApiMessage> {
        val stored = repository.history(conversationId)
            .filterNot { it.isError }
            .filter { it.content.isNotBlank() }
            .takeLast(current.historyLimit.coerceAtLeast(2))
            .map { ApiMessage(role = it.role, content = it.content) }
        val system = current.systemPrompt.trim()
        return if (system.isEmpty()) stored else listOf(ApiMessage(Roles.SYSTEM, system)) + stored
    }

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
            "$who:\n${message.content}"
        }
        onReady(listOfNotNull(conversation?.title, body).joinToString("\n\n"))
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

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ChatViewModel(
                    repository = container.repository,
                    settingsStore = container.settingsStore,
                    secretStore = container.secretStore,
                    chatApi = container.chatApi,
                )
            }
        }
    }
}

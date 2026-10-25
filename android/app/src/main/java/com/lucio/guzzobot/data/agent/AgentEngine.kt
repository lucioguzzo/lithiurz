package com.lucio.guzzobot.data.agent

import com.lucio.guzzobot.data.local.ArtifactDao
import com.lucio.guzzobot.data.local.ArtifactEntity
import com.lucio.guzzobot.data.local.MessageEntity
import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.Roles
import com.lucio.guzzobot.data.remote.ApiMessage
import com.lucio.guzzobot.data.remote.ApiToolCall
import com.lucio.guzzobot.data.remote.ApiToolFunction
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.remote.Content
import com.lucio.guzzobot.data.remote.StreamEvent
import com.lucio.guzzobot.data.remote.ToolCall
import com.lucio.guzzobot.data.repo.ChatRepository
import com.lucio.guzzobot.data.workspace.ArtifactReader
import com.lucio.guzzobot.data.workspace.GitHubWorkspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Il ciclo dell'agente: parte da un obiettivo, chiama strumenti, legge i
 * risultati e va avanti da solo finché non dichiara di aver finito, si blocca,
 * esaurisce il budget o lo si interrompe.
 *
 * Vive fuori dall'interfaccia perché lo usano sia la chat sia i compiti
 * pianificati, che girano senza nessuno a guardare.
 */
class AgentEngine(
    private val repository: ChatRepository,
    private val chatApi: ChatApi,
    private val json: Json,
    private val artifacts: ArtifactDao,
) {

    suspend fun run(
        conversationId: Long,
        goal: String,
        provider: Provider,
        apiKey: String,
        model: String,
        settings: AppSettings,
        hub: ToolHub,
        limits: AgentLimits = AgentLimits(),
        /** Il permesso per scritture ed eliminazioni; in background decide la configurazione. */
        approve: suspend (ToolCall) -> Boolean,
        onProgress: (AgentRun) -> Unit = {},
        onChunk: (content: String, reasoning: String) -> Unit = { _, _ -> },
        onTool: (String) -> Unit = {},
    ): AgentRun {
        val run = AgentRun(goal = goal, limits = limits)
        val definitions = hub.definitions(agent = true)
        onProgress(run)

        try {
            while (run.beginRound()) {
                val history = history(
                    conversationId = conversationId,
                    settings = settings,
                    workspaceLabel = hub.workspace?.label ?: "connettori",
                    github = hub.github != null,
                    run = run,
                )
                val content = StringBuilder()
                val reasoning = StringBuilder()
                var requested: List<ToolCall> = emptyList()

                try {
                    chatApi.streamChat(
                        baseUrl = provider.baseUrl,
                        apiKey = apiKey,
                        model = model,
                        messages = history,
                        temperature = settings.temperature,
                        maxTokens = settings.maxTokens,
                        tools = definitions,
                    ).collect { event ->
                        when (event) {
                            // Senza strumenti l'agente non può fare niente:
                            // continuare vorrebbe dire bruciare il budget per
                            // arrivare allo stesso punto, ma senza dire perché.
                            StreamEvent.ToolsDropped -> run.blocked(
                                "Il modello scelto non sa usare gli strumenti: " +
                                    "scegline un altro per l'agente",
                            )
                            is StreamEvent.Content -> {
                                content.append(event.text)
                                onChunk(content.toString(), reasoning.toString())
                            }
                            is StreamEvent.Reasoning -> {
                                reasoning.append(event.text)
                                onChunk(content.toString(), reasoning.toString())
                            }
                            is StreamEvent.ToolRequests -> requested = event.calls
                        }
                    }
                } catch (cancel: CancellationException) {
                    withContext(NonCancellable) {
                        persist(conversationId, content.toString(), reasoning.toString(), model)
                        run.stop()
                    }
                    throw cancel
                } catch (error: Throwable) {
                    repository.addMessage(
                        conversationId = conversationId,
                        role = Roles.ASSISTANT,
                        content = error.message ?: "Errore sconosciuto",
                        model = model,
                        isError = true,
                    )
                    run.onToolResult(failed = true)
                    onProgress(run)
                    if (!run.running) break
                    continue
                }

                if (requested.isEmpty()) {
                    // Ha parlato senza agire: se non ha concluso, lo si richiama.
                    persist(conversationId, content.toString(), reasoning.toString(), model)
                    if (!run.running) break
                    if (!run.nudge()) break
                    repository.addMessage(
                        conversationId = conversationId,
                        role = Roles.SYSTEM,
                        content = AgentPrompts.NUDGE,
                    )
                    onProgress(run)
                    continue
                }

                repository.addMessage(
                    conversationId = conversationId,
                    role = Roles.ASSISTANT,
                    content = content.toString(),
                    reasoning = reasoning.toString(),
                    model = model,
                    toolCalls = encode(requested),
                )

                for (call in requested) {
                    val description = hub.describe(call)
                    onTool(description)
                    val allowed = !hub.modifies(call) || approve(call)
                    val result = if (!allowed) {
                        "Operazione non autorizzata." to true
                    } else {
                        try {
                            hub.execute(call, run) to false
                        } catch (error: Throwable) {
                            (error.message ?: "Operazione non riuscita") to true
                        }
                    }
                    repository.addMessage(
                        conversationId = conversationId,
                        role = Roles.TOOL,
                        content = result.first,
                        isError = result.second,
                        toolCallId = call.id,
                        toolName = description,
                    )
                    if (!result.second) {
                        record(conversationId, call.name, call.arguments, result.first, hub.github)
                    }
                    run.onToolResult(failed = result.second)
                    onProgress(run)
                    if (!run.running) break
                }
            }
        } finally {
            withContext(NonCancellable) {
                val note = run.closingNote()
                if (note.isNotBlank()) {
                    repository.addMessage(
                        conversationId = conversationId,
                        role = Roles.ASSISTANT,
                        content = note,
                        model = model,
                        isError = run.outcome == AgentOutcome.FAILING,
                    )
                }
                onProgress(run)
            }
        }
        return run
    }

    /** Ciò che l'agente produce finisce anche fra gli artefatti, per ritrovarlo. */
    private suspend fun record(
        conversationId: Long,
        tool: String,
        arguments: String,
        result: String,
        github: GitHubWorkspace?,
    ) {
        ArtifactReader.from(tool, arguments, result, github?.blobBaseUrl).forEach { draft ->
            artifacts.insert(
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
    }

    private suspend fun persist(
        conversationId: Long,
        content: String,
        reasoning: String,
        model: String,
    ) {
        if (content.isBlank() && reasoning.isBlank()) return
        repository.addMessage(
            conversationId = conversationId,
            role = Roles.ASSISTANT,
            content = content,
            reasoning = reasoning,
            model = model,
        )
    }

    private suspend fun history(
        conversationId: Long,
        settings: AppSettings,
        workspaceLabel: String,
        github: Boolean,
        run: AgentRun,
    ): List<ApiMessage> {
        val stored = repository.history(conversationId)
            .filter { it.content.isNotBlank() || it.toolCalls != null }
            .takeLast(MAX_MESSAGES)
        val recent = stored.size - KEEP_FULL
        val mapped = stored.mapIndexed { index, message ->
            when {
                message.role == Roles.TOOL -> ApiMessage(
                    role = Roles.TOOL,
                    // I log vecchi non servono più per intero: si tiene l'essenziale.
                    content = Content.text(
                        if (index < recent) trim(message.content) else message.content,
                    ),
                    toolCallId = message.toolCallId,
                )

                message.toolCalls != null -> ApiMessage(
                    role = Roles.ASSISTANT,
                    content = message.content.takeIf { it.isNotBlank() }?.let(Content::text),
                    toolCalls = decode(message.toolCalls),
                )

                else -> ApiMessage(message.role, Content.text(message.content))
            }
        }
        val system = AgentPrompts.system(
            goal = run.goal,
            workspace = workspaceLabel,
            github = github,
            autonomous = settings.autonomousTools,
            limits = run.limits,
            base = settings.systemPrompt,
        )
        return listOf(ApiMessage(Roles.SYSTEM, Content.text(system))) +
            mapped.dropWhile { it.role == Roles.TOOL }
    }

    private fun trim(text: String): String =
        if (text.length <= TRIMMED) text else text.take(TRIMMED) + "\n…[esito abbreviato]"

    private fun encode(calls: List<ToolCall>): String = json.encodeToString(
        ListSerializer(ApiToolCall.serializer()),
        calls.map { ApiToolCall(id = it.id, function = ApiToolFunction(it.name, it.arguments)) },
    )

    private fun decode(raw: String): List<ApiToolCall> = runCatching {
        json.decodeFromString(ListSerializer(ApiToolCall.serializer()), raw)
    }.getOrDefault(emptyList())

    private companion object {
        /** Quanti messaggi tenere nella richiesta: oltre, la finestra si satura. */
        const val MAX_MESSAGES = 60

        /** Gli ultimi passi restano per intero, i precedenti si abbreviano. */
        const val KEEP_FULL = 8
        const val TRIMMED = 400
    }
}

object AgentPrompts {

    const val NUDGE = "Prosegui. Se il compito è concluso chiama agent_finish con un " +
        "riepilogo; se ti manca qualcosa chiama agent_blocked."

    fun system(
        goal: String,
        workspace: String,
        github: Boolean,
        autonomous: Boolean,
        limits: AgentLimits,
        base: String,
    ): String = buildString {
        if (base.isNotBlank()) append(base.trim()).append("\n\n")
        append("Lavori come agente autonomo su questo obiettivo:\n\"")
        append(goal.trim())
        append("\"\n\n")
        append("Area di lavoro: $workspace. ")
        append("Procedi da solo: esplora, decidi, agisci, controlla il risultato e correggi. ")
        append("Non chiedere conferme di cortesia e non descrivere quello che faresti: fallo. ")
        append("Un passo alla volta, con chiamate agli strumenti; spiega in una riga cosa stai ")
        append("facendo e perché.\n")
        if (github) {
            append("Non hai shell né compilatore: il codice si verifica con la CI. ")
            append("Prima di modificare crea un branch con git_branch, scrivi, avvia il ")
            append("workflow con ci_run e leggi l'esito con ci_status; se è rosso correggi e ")
            append("riprova. Apri una pull request solo a verifica superata.\n")
        }
        append(
            if (autonomous) {
                "Puoi scrivere ed eliminare senza chiedere.\n"
            } else {
                "Scritture, eliminazioni e pull request passano dall'approvazione " +
                    "dell'utente: se rifiuta, adatta il piano.\n"
            },
        )
        append("Hai al massimo ${limits.maxSteps} passi e ${limits.maxMinutes} minuti. ")
        append("Quando il risultato è raggiunto e verificato chiama agent_finish; ")
        append("se ti manca un'informazione o un permesso chiama agent_blocked.")
    }
}

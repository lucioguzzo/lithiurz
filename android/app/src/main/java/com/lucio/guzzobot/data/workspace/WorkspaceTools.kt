package com.lucio.guzzobot.data.workspace

import com.lucio.guzzobot.data.agent.AgentRun
import com.lucio.guzzobot.data.remote.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json

/**
 * Gli strumenti che il modello può chiamare sull'area di lavoro. Le definizioni
 * viaggiano nella richiesta; l'esecuzione resta qui, nell'app, dove possiamo
 * fermarla e chiedere conferma.
 */
object WorkspaceTools {

    const val LIST = "workspace_list"
    const val READ = "workspace_read"
    const val WRITE = "workspace_write"
    const val DELETE = "workspace_delete"
    const val SEARCH = "workspace_search"
    const val BRANCH = "git_branch"
    const val PULL_REQUEST = "pr_open"
    const val CI_LIST = "ci_list"
    const val CI_RUN = "ci_run"
    const val CI_STATUS = "ci_status"
    const val FINISH = "agent_finish"
    const val BLOCKED = "agent_blocked"

    /**
     * Le operazioni che cambiano qualcosa fuori dall'app: sono quelle che
     * passano dal permesso. Creare un branch o far girare la CI no: si
     * disfano da sé e bloccherebbero l'agente a ogni passo.
     */
    fun modifies(name: String): Boolean = name == WRITE || name == DELETE || name == PULL_REQUEST

    fun definitions(github: Boolean = false, agent: Boolean = false): JsonArray = buildJsonArray {
        add(
            tool(
                name = LIST,
                description = "Elenca file e cartelle di un percorso dell'area di lavoro. " +
                    "Usa \"\" per la radice.",
            ) {
                put("path", parameter("string", "Percorso relativo alla radice, \"\" per la radice"))
            },
        )
        add(
            tool(
                name = READ,
                description = "Legge il contenuto testuale di un file dell'area di lavoro.",
                required = listOf("path"),
            ) {
                put("path", parameter("string", "Percorso del file relativo alla radice"))
            },
        )
        add(
            tool(
                name = WRITE,
                description = "Crea o sostituisce uno o più file. Il contenuto va passato " +
                    "per intero: sostituisce quello esistente. Su GitHub i file passati " +
                    "insieme diventano un solo commit.",
                required = listOf("files"),
            ) {
                put(
                    "files",
                    buildJsonObject {
                        put("type", "array")
                        put("description", "I file da scrivere")
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("path", parameter("string", "Percorso del file"))
                                        put("content", parameter("string", "Contenuto completo"))
                                    },
                                )
                                put("required", buildJsonArray { add(str("path")); add(str("content")) })
                            },
                        )
                    },
                )
                put("message", parameter("string", "Messaggio di commit, se l'area è un repository"))
            },
        )
        add(
            tool(
                name = DELETE,
                description = "Elimina un file dall'area di lavoro.",
                required = listOf("path"),
            ) {
                put("path", parameter("string", "Percorso del file da eliminare"))
                put("message", parameter("string", "Messaggio di commit, se l'area è un repository"))
            },
        )
        add(
            tool(
                name = SEARCH,
                description = "Cerca nei nomi dei file e, se richiesto, nel loro contenuto.",
                required = listOf("query"),
            ) {
                put("query", parameter("string", "Testo da cercare"))
                put("path", parameter("string", "Sottocartella in cui cercare, \"\" per tutto"))
                put("content", parameter("boolean", "true per cercare anche dentro ai file"))
            },
        )
        if (github) {
            add(
                tool(
                    name = BRANCH,
                    description = "Crea un branch e ci sposta il lavoro successivo. " +
                        "Usalo prima di modificare il codice, così il ramo principale resta intatto.",
                    required = listOf("name"),
                ) {
                    put("name", parameter("string", "Nome del nuovo branch"))
                    put("from", parameter("string", "Branch di partenza; vuoto per quello corrente"))
                },
            )
            add(
                tool(
                    name = PULL_REQUEST,
                    description = "Apre una pull request dal branch corrente.",
                    required = listOf("title"),
                ) {
                    put("title", parameter("string", "Titolo della pull request"))
                    put("body", parameter("string", "Descrizione: cosa cambia e perché"))
                    put("base", parameter("string", "Branch di destinazione, di norma main"))
                },
            )
            add(
                tool(
                    name = CI_LIST,
                    description = "Elenca i workflow di GitHub Actions del repository.",
                ) {},
            )
            add(
                tool(
                    name = CI_RUN,
                    description = "Avvia un workflow sul branch corrente. Richiede che il " +
                        "workflow preveda workflow_dispatch.",
                    required = listOf("workflow"),
                ) {
                    put("workflow", parameter("string", "Nome o file del workflow"))
                },
            )
            add(
                tool(
                    name = CI_STATUS,
                    description = "Aspetta l'esito dell'ultima corsa sul branch corrente e " +
                        "restituisce conclusione e righe utili dei log. È così che verifichi " +
                        "il tuo lavoro: qui non esistono shell né compilatore.",
                ) {
                    put("wait", parameter("integer", "Secondi di attesa, al massimo 600"))
                },
            )
        }
        if (agent) {
            add(
                tool(
                    name = FINISH,
                    description = "Dichiara concluso il compito. Chiamalo solo quando il " +
                        "risultato è raggiunto e verificato.",
                    required = listOf("summary"),
                ) {
                    put("summary", parameter("string", "Cosa hai fatto, in poche righe"))
                },
            )
            add(
                tool(
                    name = BLOCKED,
                    description = "Fermati e chiedi: usalo quando ti manca un'informazione o " +
                        "un permesso e non puoi proseguire da solo.",
                    required = listOf("reason"),
                ) {
                    put("reason", parameter("string", "Cosa ti serve per andare avanti"))
                },
            )
        }
    }

    /** Riga che descrive la chiamata all'utente, nel riquadro di attività. */
    fun describe(call: ToolCall): String {
        val arguments = parse(call.arguments)
        val path = arguments.text("path")
        return when (call.name) {
            LIST -> "Elenca ${path.ifBlank { "la radice" }}"
            READ -> "Legge $path"
            WRITE -> {
                val files = arguments["files"]?.jsonArray.orEmpty()
                    .mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
                if (files.size == 1) "Scrive ${files.first()}" else "Scrive ${files.size} file"
            }
            DELETE -> "Elimina $path"
            SEARCH -> "Cerca \"${arguments.text("query")}\""
            BRANCH -> "Crea il branch ${arguments.text("name")}"
            PULL_REQUEST -> "Apre la pull request \"${arguments.text("title")}\""
            CI_LIST -> "Elenca i workflow"
            CI_RUN -> "Avvia ${arguments.text("workflow")}"
            CI_STATUS -> "Controlla l'esito della CI"
            FINISH -> "Conclude il compito"
            BLOCKED -> "Chiede indicazioni"
            else -> call.name
        }
    }

    /** Anteprima mostrata nella richiesta di conferma. */
    fun preview(call: ToolCall): String {
        val arguments = parse(call.arguments)
        return when (call.name) {
            WRITE -> arguments["files"]?.jsonArray.orEmpty().joinToString("\n\n") { item ->
                val file = item.jsonObject
                val content = file["content"]?.jsonPrimitive?.content.orEmpty()
                val head = content.lineSequence().take(14).joinToString("\n")
                val more = content.lines().size - 14
                "▸ ${file["path"]?.jsonPrimitive?.content}\n$head" +
                    if (more > 0) "\n… altre $more righe" else ""
            }
            DELETE -> "Il file ${arguments.text("path")} verrà eliminato."
            PULL_REQUEST -> "${arguments.text("title")}\n\n${arguments.text("body").take(600)}"
            else -> ""
        }
    }

    suspend fun execute(
        workspace: Workspace,
        call: ToolCall,
        github: GitHubWorkspace? = null,
        actions: GitHubActions? = null,
        run: AgentRun? = null,
    ): String {
        val arguments = parse(call.arguments)
        return when (call.name) {
            BRANCH -> github?.createBranch(
                name = arguments.text("name"),
                from = arguments.text("from").ifBlank { null },
            ) ?: throw WorkspaceException("I branch esistono solo su un'area di lavoro GitHub")

            PULL_REQUEST -> github?.openPullRequest(
                title = arguments.text("title"),
                body = arguments.text("body"),
                base = arguments.text("base").ifBlank { "main" },
            ) ?: throw WorkspaceException("Le pull request esistono solo su GitHub")

            CI_LIST -> actions?.workflows()
                ?.joinToString("\n") { "${it.name} (${it.path})" }
                ?.ifBlank { "Nessun workflow nel repository" }
                ?: throw WorkspaceException("GitHub Actions richiede un'area di lavoro GitHub")

            CI_RUN -> {
                val branch = github?.branch
                    ?: throw WorkspaceException("GitHub Actions richiede un'area di lavoro GitHub")
                val service = actions ?: throw WorkspaceException("Actions non disponibile")
                val last = service.lastRunId(branch)
                service.dispatch(arguments.text("workflow"), branch)
                "Workflow avviato su $branch. Usa ${CI_STATUS} per aspettarne l'esito. " +
                    "(ultima corsa precedente: $last)"
            }

            CI_STATUS -> {
                val branch = github?.branch
                    ?: throw WorkspaceException("GitHub Actions richiede un'area di lavoro GitHub")
                val service = actions ?: throw WorkspaceException("Actions non disponibile")
                val wait = arguments["wait"]?.jsonPrimitive?.content?.toIntOrNull() ?: 240
                val outcome = service.awaitRun(branch, afterId = 0, waitSeconds = wait)
                    ?: return "Nessuna corsa trovata su $branch"
                service.report(outcome)
            }

            FINISH -> {
                run?.finish(arguments.text("summary"))
                "Compito dichiarato concluso."
            }

            BLOCKED -> {
                run?.blocked(arguments.text("reason"))
                "Richiesta di indicazioni registrata."
            }

            LIST -> {
                val entries = workspace.list(arguments.text("path"))
                if (entries.isEmpty()) {
                    "Cartella vuota"
                } else {
                    entries.joinToString("\n") { entry ->
                        if (entry.directory) {
                            "[cartella] ${entry.path}"
                        } else {
                            "[file] ${entry.path} (${entry.size} byte)"
                        }
                    }
                }
            }

            READ -> workspace.read(arguments.text("path"))

            WRITE -> {
                val files = arguments["files"]?.jsonArray.orEmpty().map { item ->
                    val file = item.jsonObject
                    WriteRequest(
                        path = file["path"]?.jsonPrimitive?.content.orEmpty(),
                        content = file["content"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }
                if (files.isEmpty()) throw WorkspaceException("Nessun file da scrivere")
                workspace.write(files, arguments.text("message"))
            }

            DELETE -> workspace.delete(arguments.text("path"), arguments.text("message"))

            SEARCH -> {
                val hits = workspace.search(
                    query = arguments.text("query"),
                    path = arguments.text("path"),
                    inContent = arguments["content"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                if (hits.isEmpty()) {
                    "Nessun riscontro"
                } else {
                    hits.joinToString("\n") { hit ->
                        if (hit.line != null) "${hit.path}:${hit.line}: ${hit.text}" else hit.path
                    }
                }
            }

            else -> throw WorkspaceException("Strumento sconosciuto: ${call.name}")
        }
    }

    /** Istruzioni aggiunte al prompt di sistema quando c'è un'area di lavoro. */
    fun systemPrompt(label: String, autonomous: Boolean): String = buildString {
        append("Hai a disposizione un'area di lavoro: $label. ")
        append("Usa gli strumenti workspace_* per esplorarla e modificarla: i percorsi sono ")
        append("relativi alla radice, senza \"..\" e senza barra iniziale. ")
        append("Prima di modificare un file leggilo, e scrivi sempre il contenuto completo. ")
        append("Non hai shell né compilatore: per verificare il codice usa la CI di GitHub. ")
        append(
            if (autonomous) {
                "Puoi scrivere ed eliminare senza chiedere, ma spiega ogni modifica che fai."
            } else {
                "Le scritture e le eliminazioni devono essere approvate dall'utente: " +
                    "se rifiuta, fermati e chiedi come procedere."
            },
        )
    }

    private fun parse(raw: String): JsonObject = runCatching {
        Json.parseToJsonElement(raw.ifBlank { "{}" }).jsonObject
    }.getOrElse { JsonObject(emptyMap()) }

    private fun JsonObject.text(key: String): String =
        this[key]?.jsonPrimitive?.content.orEmpty()

    private fun str(value: String) = kotlinx.serialization.json.JsonPrimitive(value)

    private fun parameter(type: String, description: String) = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun tool(
        name: String,
        description: String,
        required: List<String> = emptyList(),
        properties: JsonObjectBuilderScope,
    ) = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", name)
                put("description", description)
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject(properties))
                        put("required", buildJsonArray { required.forEach { add(str(it)) } })
                    },
                )
            },
        )
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit

/** Quello che un'operazione ha prodotto, da ritrovare nella sezione Artefatti. */
data class ArtifactDraft(
    val kind: String,
    val name: String,
    val location: String,
    val detail: String? = null,
)

/**
 * Ricava dagli strumenti ciò che vale la pena conservare: i file scritti e le
 * pull request aperte. Letture e ricerche non producono niente.
 */
object ArtifactReader {

    const val FILE = "file"
    const val PULL_REQUEST = "pull_request"
    const val IMAGE = "image"
    const val AUDIO = "audio"

    fun from(toolName: String, arguments: String, result: String, repoUrl: String?): List<ArtifactDraft> =
        when (toolName) {
            WorkspaceTools.WRITE -> paths(arguments).map { path ->
                ArtifactDraft(
                    kind = FILE,
                    name = path.substringAfterLast('/'),
                    location = repoUrl?.let { "$it/$path" } ?: "ws:$path",
                    detail = result.lineSequence().firstOrNull()?.take(120),
                )
            }

            WorkspaceTools.PULL_REQUEST -> {
                val url = Regex("https://\\S+").find(result)?.value
                if (url == null) {
                    emptyList()
                } else {
                    listOf(
                        ArtifactDraft(
                            kind = PULL_REQUEST,
                            name = result.substringBefore(':').trim().ifBlank { "Pull request" },
                            location = url,
                            detail = null,
                        ),
                    )
                }
            }

            else -> emptyList()
        }

    private fun paths(arguments: String): List<String> = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(arguments.ifBlank { "{}" })
            .jsonObject["files"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["path"]?.jsonPrimitive?.content }
            .filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}

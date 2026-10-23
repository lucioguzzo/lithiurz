package com.lucio.guzzobot.data.workspace

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

    /** Le operazioni che cambiano qualcosa: sono quelle che passano dal permesso. */
    fun modifies(name: String): Boolean = name == WRITE || name == DELETE

    fun definitions(): JsonArray = buildJsonArray {
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
            else -> ""
        }
    }

    suspend fun execute(workspace: Workspace, call: ToolCall): String {
        val arguments = parse(call.arguments)
        return when (call.name) {
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

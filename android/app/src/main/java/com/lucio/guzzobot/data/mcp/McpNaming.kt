package com.lucio.guzzobot.data.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Gli strumenti dei connettori viaggiano insieme a quelli dell'app, quindi i
 * nomi vanno resi univoci e accettabili per l'API: lettere, cifre e underscore.
 */
object McpNaming {

    private const val PREFIX = "mcp_"
    private const val SEPARATOR = "__"

    fun sanitize(value: String): String =
        value.map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .trim('_')
            .take(40)
            .ifBlank { "x" }

    fun toolId(serverId: String, tool: String): String =
        PREFIX + sanitize(serverId) + SEPARATOR + sanitize(tool)

    fun isMcp(name: String): Boolean = name.startsWith(PREFIX)

    /** Dal nome composto torna al connettore e allo strumento veri. */
    fun resolve(name: String, servers: List<McpServer>): Pair<McpServer, McpTool>? {
        if (!isMcp(name)) return null
        val body = name.removePrefix(PREFIX)
        val serverPart = body.substringBefore(SEPARATOR)
        val toolPart = body.substringAfter(SEPARATOR, "")
        val server = servers.firstOrNull { sanitize(it.id) == serverPart } ?: return null
        val tool = server.tools.firstOrNull { sanitize(it.name) == toolPart } ?: return null
        return server to tool
    }

    fun definitions(servers: List<McpServer>): JsonArray = buildJsonArray {
        servers.filter { it.enabled }.forEach { server ->
            server.tools.forEach { tool ->
                add(
                    buildJsonObject {
                        put("type", "function")
                        put(
                            "function",
                            buildJsonObject {
                                put("name", toolId(server.id, tool.name))
                                put(
                                    "description",
                                    listOf(server.name, tool.description)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                        .take(900),
                                )
                                put(
                                    "parameters",
                                    tool.schema ?: buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject { })
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

package com.lucio.guzzobot.data.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Un connettore: un server MCP remoto, raggiunto via HTTP.
 *
 * I server MCP "stdio", quelli che si lanciano con npx o uvx, qui non esistono:
 * un'app Android non può avviare processi di altri programmi. Restano quelli
 * remoti, che è anche l'unica forma sensata da un telefono.
 */
@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val url: String,
    /** Intestazione di autenticazione, se il server la richiede. */
    val authHeader: String = "Authorization",
    val enabled: Boolean = true,
    /** Strumenti visti l'ultima volta che il server ha risposto. */
    val tools: List<McpTool> = emptyList(),
    val lastCheckedAt: Long = 0,
    val lastError: String? = null,
) {
    companion object {
        /** Chiave con cui il token del connettore viene custodito, cifrato. */
        fun secretKey(id: String) = "mcp_$id"
    }
}

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val schema: JsonObject? = null,
    /** Dichiarato dal server: se legge soltanto, non serve chiedere il permesso. */
    val readOnly: Boolean = false,
)

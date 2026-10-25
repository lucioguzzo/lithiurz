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
    /** Presente se il connettore si autentica con OAuth invece che con un token. */
    val oauth: OAuthConfig? = null,
) {
    val usesOAuth: Boolean get() = oauth != null

    companion object {
        /**
         * Chiave con cui il token del connettore viene custodito, cifrato.
         * Vale sia per un token personale sia per un token OAuth: il client
         * manda comunque `Authorization: Bearer`, e questo evita due strade.
         */
        fun secretKey(id: String) = "mcp_$id"

        /** Il token di rinnovo, custodito a parte. */
        fun refreshKey(id: String) = "mcp_${id}_refresh"

        /** Il segreto del client, se il server di autorizzazione ne ha dato uno. */
        fun clientSecretKey(id: String) = "mcp_${id}_client"
    }
}

/**
 * Quello che resta da sapere dopo essersi autorizzati: dove chiedere un token
 * nuovo e con quale identità. I token veri non stanno qui — stanno cifrati nel
 * portachiavi — perché queste impostazioni finiscono in chiaro in DataStore.
 */
@Serializable
data class OAuthConfig(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String? = null,
    val clientId: String,
    /** L'identificativo con cui il token è legato a questo server. */
    val resource: String,
    val scopes: List<String> = emptyList(),
    /** Quando il token scade, se il server l'ha detto. */
    val expiresAt: Long = 0,
) {
    fun authServer() = OAuthDiscovery.AuthServer(
        issuer = tokenEndpoint,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        registrationEndpoint = registrationEndpoint,
    )

    /** Si rinnova in anticipo: un token che scade a metà richiesta è un errore. */
    fun expiringSoon(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt > 0 && now > expiresAt - 60_000
}

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val schema: JsonObject? = null,
    /** Dichiarato dal server: se legge soltanto, non serve chiedere il permesso. */
    val readOnly: Boolean = false,
)

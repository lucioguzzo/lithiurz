package com.lucio.guzzobot.data.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Il giro di autorizzazione di un connettore, dalla scoperta al token.
 *
 * La sequenza non è negoziabile e viene dalla specifica MCP: il server
 * risponde 401 dicendo dove sono i suoi metadati, i metadati dicono chi
 * autorizza, il server di autorizzazione dichiara i suoi endpoint, e solo
 * allora si manda l'utente nel browser. Ogni passaggio può mancare, e quando
 * manca va detto quale: «non si è collegato» non aiuta nessuno.
 */
class McpOAuth(private val json: Json) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Discovery(
        val resource: String,
        val authServer: OAuthDiscovery.AuthServer,
        val scopes: List<String>,
    )

    /**
     * Chiede al server MCP chi lo autorizza.
     *
     * Si parte dall'indirizzo che il 401 ha indicato; se non ne ha indicato
     * nessuno si prova comunque ai posti previsti, perché alcuni server
     * l'intestazione non la mandano.
     */
    suspend fun discover(serverUrl: String, wwwAuthenticate: String?): Discovery =
        withContext(Dispatchers.IO) {
            val candidates = buildList {
                OAuthDiscovery.resourceMetadataFrom(wwwAuthenticate)?.let(::add)
                addAll(OAuthDiscovery.resourceMetadataUrls(serverUrl))
            }.distinct()

            val resource = candidates.firstNotNullOfOrNull { url ->
                fetch(url)?.let(OAuthDiscovery::parseProtectedResource)
            } ?: throw McpException(
                "Il connettore non dichiara chi lo autorizza: nessun metadato " +
                    "trovato a ${candidates.firstOrNull().orEmpty()}",
            )

            val issuer = resource.authorizationServers.first()
            val authServer = OAuthDiscovery.authServerMetadataUrls(issuer)
                .firstNotNullOfOrNull { url -> fetch(url)?.let(OAuthDiscovery::parseAuthServer) }
                ?: throw McpException("Il server di autorizzazione $issuer non espone i suoi metadati")

            if (!authServer.supportsPkce) {
                throw McpException("$issuer non supporta PKCE: non è sicuro collegarsi")
            }

            Discovery(
                // L'identificativo lo dice il server; se tace, vale il suo indirizzo.
                resource = resource.resource.ifBlank { OAuthDiscovery.canonicalResource(serverUrl) },
                authServer = authServer,
                scopes = resource.scopesSupported.ifEmpty { authServer.scopesSupported },
            )
        }

    /**
     * Si presenta al server di autorizzazione per ottenere un'identità.
     *
     * Senza registrazione dinamica un'app come questa non può collegarsi da
     * sola: non ha un client_id da nessuna parte e non può averlo.
     */
    suspend fun register(
        authServer: OAuthDiscovery.AuthServer,
        redirectUri: String,
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val endpoint = authServer.registrationEndpoint ?: throw McpException(
            "Questo servizio non permette la registrazione automatica dei client: " +
                "non si può collegare da un'app senza credenziali fornite a mano",
        )
        val body = buildJsonObject {
            put("client_name", "GUZZOBOT")
            put("redirect_uris", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(redirectUri)) })
            put("grant_types", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("authorization_code"))
                add(kotlinx.serialization.json.JsonPrimitive("refresh_token"))
            })
            put("response_types", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("code")) })
            // Pubblico: un'app su un telefono non può custodire un segreto.
            put("token_endpoint_auth_method", "none")
            put("application_type", "native")
        }
        val response = post(endpoint, json.encodeToString(JsonObject.serializer(), body))
        OAuthDiscovery.parseRegistration(response)
            ?: throw McpException("La registrazione non ha restituito un client_id")
    }

    /** Scambia il codice tornato dal browser con i token veri. */
    suspend fun exchange(
        authServer: OAuthDiscovery.AuthServer,
        clientId: String,
        clientSecret: String?,
        code: String,
        verifier: String,
        redirectUri: String,
        resource: String,
    ): OAuthDiscovery.Tokens = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .add("resource", resource)
            .apply { clientSecret?.let { add("client_secret", it) } }
            .build()
        tokens(authServer.tokenEndpoint, form)
    }

    /** Rinnova il token scaduto senza disturbare l'utente. */
    suspend fun refresh(
        authServer: OAuthDiscovery.AuthServer,
        clientId: String,
        clientSecret: String?,
        refreshToken: String,
        resource: String,
    ): OAuthDiscovery.Tokens = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .add("resource", resource)
            .apply { clientSecret?.let { add("client_secret", it) } }
            .build()
        tokens(authServer.tokenEndpoint, form)
    }

    private fun tokens(endpoint: String, form: FormBody): OAuthDiscovery.Tokens {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            if (!response.isSuccessful) {
                val error = parsed?.get("error")?.let { it.toString().trim('"') }
                throw McpException(error ?: "Il server ha rifiutato lo scambio (HTTP ${response.code})")
            }
            return parsed?.let(OAuthDiscovery::parseTokens)
                ?: throw McpException("Risposta senza token")
        }
    }

    private fun fetch(url: String): JsonObject? = runCatching {
        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
        }
    }.getOrNull()

    private fun post(url: String, body: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            if (!response.isSuccessful && parsed == null) {
                throw McpException("HTTP ${response.code}${raw.take(160).let { if (it.isBlank()) "" else ": $it" }}")
            }
            return parsed ?: JsonObject(emptyMap())
        }
    }

    companion object {
        /** Dove il browser riporta il codice: dichiarato nel manifest. */
        const val REDIRECT_URI = "guzzobot://oauth"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

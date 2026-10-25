package com.lucio.guzzobot.data.mcp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder

/**
 * La scoperta dell'autorizzazione, secondo la specifica MCP.
 *
 * Il percorso è lungo e tutto fatto di passaggi che possono andare storti, per
 * questo qui c'è solo logica pura: comporre indirizzi, leggere metadati,
 * costruire la richiesta di autorizzazione. La rete sta altrove, e questo si
 * può verificare senza un server.
 *
 * Il senso della sequenza: il server MCP risponde 401 dicendo dove sono i suoi
 * metadati; i metadati dicono quale server di autorizzazione usare; quello
 * dichiara i suoi endpoint; e solo a quel punto si può mandare l'utente ad
 * autorizzare.
 */
object OAuthDiscovery {

    /** Quello che il server MCP dichiara di sé. */
    data class ProtectedResource(
        val resource: String,
        val authorizationServers: List<String>,
        val scopesSupported: List<String> = emptyList(),
    )

    /** Quello che il server di autorizzazione dichiara di sé. */
    data class AuthServer(
        val issuer: String,
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val registrationEndpoint: String? = null,
        val scopesSupported: List<String> = emptyList(),
        val codeChallengeMethods: List<String> = emptyList(),
    ) {
        /** Senza S256 dichiarato non si va avanti: PKCE è obbligatorio. */
        val supportsPkce: Boolean
            get() = codeChallengeMethods.isEmpty() || codeChallengeMethods.contains("S256")
    }

    data class Tokens(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresInSeconds: Int? = null,
        val scope: String? = null,
    )

    /**
     * Dall'intestazione del 401 si ricava dove stanno i metadati.
     *
     * Esempio: `Bearer resource_metadata="https://x/.well-known/..."`. Il
     * valore può essere fra virgolette o no, e possono esserci altri parametri
     * attorno.
     */
    fun resourceMetadataFrom(header: String?): String? {
        val value = header ?: return null
        val match = Regex("""resource_metadata\s*=\s*"?([^",\s]+)"?""", RegexOption.IGNORE_CASE)
            .find(value) ?: return null
        return match.groupValues[1].takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    /**
     * Gli indirizzi da provare per i metadati del server MCP, in ordine.
     *
     * Con un percorso (`https://host/mcp`) la specifica lo inserisce dopo il
     * well-known, non prima; ma non tutti i server lo fanno, quindi si prova
     * anche la forma semplice invece di arrendersi al primo 404.
     */
    fun resourceMetadataUrls(serverUrl: String): List<String> =
        wellKnownUrls(serverUrl, "oauth-protected-resource")

    /** Gli indirizzi da provare per i metadati del server di autorizzazione. */
    fun authServerMetadataUrls(issuer: String): List<String> =
        wellKnownUrls(issuer, "oauth-authorization-server") +
            wellKnownUrls(issuer, "openid-configuration")

    private fun wellKnownUrls(url: String, document: String): List<String> {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return emptyList()
        val origin = buildString {
            append(uri.scheme?.lowercase() ?: "https")
            append("://")
            append(uri.host?.lowercase().orEmpty())
            if (uri.port != -1) append(":${uri.port}")
        }
        val path = uri.path.orEmpty().trim('/')
        return buildList {
            if (path.isNotEmpty()) {
                add("$origin/.well-known/$document/$path")
                add("$origin/$path/.well-known/$document")
            }
            add("$origin/.well-known/$document")
        }.distinct()
    }

    /**
     * L'identificativo con cui il token viene legato a questo server.
     *
     * Va mandato in tutte le richieste e deve essere la forma canonica:
     * schema e host minuscoli, niente frammento, niente barra finale.
     */
    fun canonicalResource(serverUrl: String): String {
        val uri = runCatching { URI(serverUrl.trim()) }.getOrNull()
            ?: return serverUrl.trim().trimEnd('/')
        return buildString {
            append(uri.scheme?.lowercase() ?: "https")
            append("://")
            append(uri.host?.lowercase().orEmpty())
            if (uri.port != -1) append(":${uri.port}")
            val path = uri.path.orEmpty().trimEnd('/')
            if (path.isNotEmpty() && path != "/") append(path)
        }
    }

    fun parseProtectedResource(body: JsonObject): ProtectedResource? {
        val servers = body["authorization_servers"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
            .orEmpty()
        if (servers.isEmpty()) return null
        return ProtectedResource(
            resource = body["resource"]?.jsonPrimitive?.content.orEmpty(),
            authorizationServers = servers,
            scopesSupported = strings(body, "scopes_supported"),
        )
    }

    fun parseAuthServer(body: JsonObject): AuthServer? {
        val authorize = body["authorization_endpoint"]?.jsonPrimitive?.content
        val token = body["token_endpoint"]?.jsonPrimitive?.content
        if (authorize.isNullOrBlank() || token.isNullOrBlank()) return null
        return AuthServer(
            issuer = body["issuer"]?.jsonPrimitive?.content.orEmpty(),
            authorizationEndpoint = authorize,
            tokenEndpoint = token,
            registrationEndpoint = body["registration_endpoint"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() },
            scopesSupported = strings(body, "scopes_supported"),
            codeChallengeMethods = strings(body, "code_challenge_methods_supported"),
        )
    }

    fun parseTokens(body: JsonObject): Tokens? {
        val access = body["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return null
        return Tokens(
            accessToken = access,
            refreshToken = body["refresh_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            expiresInSeconds = body["expires_in"]?.jsonPrimitive?.intOrNull,
            scope = body["scope"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
        )
    }

    /** Il client appena registrato: l'id, e il segreto solo se il server lo dà. */
    fun parseRegistration(body: JsonObject): Pair<String, String?>? {
        val id = body["client_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return null
        return id to body["client_secret"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    /** L'indirizzo a cui mandare l'utente, nel browser. */
    fun authorizeUrl(
        server: AuthServer,
        clientId: String,
        redirectUri: String,
        state: String,
        codeChallenge: String,
        resource: String,
        scopes: List<String> = emptyList(),
    ): String {
        val params = buildList {
            add("response_type" to "code")
            add("client_id" to clientId)
            add("redirect_uri" to redirectUri)
            add("state" to state)
            add("code_challenge" to codeChallenge)
            add("code_challenge_method" to "S256")
            // Va mandato comunque, anche se il server non lo supporta: lega il
            // token a questo server e basta.
            add("resource" to resource)
            if (scopes.isNotEmpty()) add("scope" to scopes.joinToString(" "))
        }
        val separator = if (server.authorizationEndpoint.contains("?")) "&" else "?"
        return server.authorizationEndpoint + separator + params.joinToString("&") { (key, value) ->
            "$key=" + URLEncoder.encode(value, "UTF-8")
        }
    }

    /** Il codice e lo stato che tornano dal browser. */
    fun parseCallback(uri: String): Callback {
        val query = uri.substringAfter('?', "").substringBefore('#')
        val values = query.split("&").mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.isBlank()) return@mapNotNull null
            key to decode(part.substringAfter('=', ""))
        }.toMap()
        return Callback(
            code = values["code"],
            state = values["state"],
            error = values["error"],
            errorDescription = values["error_description"],
        )
    }

    data class Callback(
        val code: String?,
        val state: String?,
        val error: String?,
        val errorDescription: String?,
    ) {
        /** Lo stato va verificato: senza, un codice estraneo passerebbe per nostro. */
        fun codeFor(expectedState: String): String? =
            code?.takeIf { error == null && state == expectedState }

        val message: String?
            get() = when {
                error == null -> null
                errorDescription.isNullOrBlank() -> error
                else -> "$error: $errorDescription"
            }
    }

    private fun strings(body: JsonObject, key: String): List<String> =
        body[key]?.let { element ->
            runCatching {
                element.jsonArray.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
            }.getOrNull()
        }.orEmpty()

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}

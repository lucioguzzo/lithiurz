package com.lucio.guzzobot.data.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class McpException(message: String) : Exception(message)

/**
 * Client MCP sul trasporto HTTP: una richiesta JSON-RPC per volta, con la
 * risposta che può arrivare come JSON o come flusso di eventi. Ogni operazione
 * apre la sua sessione, perché da un telefono una connessione tenuta aperta
 * non sopravvive a niente.
 */
class McpClient(private val json: Json) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class Session(val id: String?, val serverName: String)

    suspend fun handshake(server: McpServer, token: String): Session = withContext(Dispatchers.IO) {
        val (body, sessionId) = exchange(
            server = server,
            token = token,
            sessionId = null,
            payload = request(
                id = 1,
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", PROTOCOL)
                    put("capabilities", buildJsonObject { })
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "GUZZOBOT")
                            put("version", "1")
                        },
                    )
                },
            ),
        )
        val info = body["result"]?.jsonObject?.get("serverInfo")?.jsonObject
        // Il protocollo vuole la notifica di avvenuta inizializzazione.
        runCatching {
            exchange(
                server = server,
                token = token,
                sessionId = sessionId,
                payload = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("method", "notifications/initialized")
                },
                expectResult = false,
            )
        }
        Session(
            id = sessionId,
            serverName = info?.get("name")?.jsonPrimitive?.content ?: server.name,
        )
    }

    suspend fun listTools(server: McpServer, token: String): List<McpTool> =
        withContext(Dispatchers.IO) {
            val session = handshake(server, token)
            val (body, _) = exchange(
                server = server,
                token = token,
                sessionId = session.id,
                payload = request(2, "tools/list", buildJsonObject { }),
            )
            body["result"]?.jsonObject?.get("tools")?.jsonArray.orEmpty().map { element ->
                val tool = element.jsonObject
                McpTool(
                    name = tool["name"]?.jsonPrimitive?.content.orEmpty(),
                    description = tool["description"]?.jsonPrimitive?.content.orEmpty(),
                    schema = tool["inputSchema"]?.jsonObject,
                    readOnly = tool["annotations"]?.jsonObject
                        ?.get("readOnlyHint")?.jsonPrimitive?.booleanOrNull ?: false,
                )
            }.filter { it.name.isNotBlank() }
        }

    suspend fun callTool(
        server: McpServer,
        token: String,
        tool: String,
        arguments: JsonObject,
    ): String = withContext(Dispatchers.IO) {
        val session = handshake(server, token)
        val (body, _) = exchange(
            server = server,
            token = token,
            sessionId = session.id,
            payload = request(
                id = 3,
                method = "tools/call",
                params = buildJsonObject {
                    put("name", tool)
                    put("arguments", arguments)
                },
            ),
        )
        val result = body["result"]?.jsonObject ?: throw McpException("Risposta senza risultato")
        val failed = result["isError"]?.jsonPrimitive?.booleanOrNull ?: false
        val text = result["content"]?.jsonArray.orEmpty().mapNotNull { item ->
            val part = item.jsonObject
            when (part["type"]?.jsonPrimitive?.content) {
                "text" -> part["text"]?.jsonPrimitive?.content
                "resource" -> part["resource"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                else -> null
            }
        }.joinToString("\n").ifBlank { result.toString().take(2_000) }
        if (failed) throw McpException(text.take(1_000)) else text
    }

    private fun request(id: Int, method: String, params: JsonObject) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }

    private fun exchange(
        server: McpServer,
        token: String,
        sessionId: String?,
        payload: JsonObject,
        expectResult: Boolean = true,
    ): Pair<JsonObject, String?> {
        val builder = Request.Builder()
            .url(server.url.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL)
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
        if (token.isNotBlank()) {
            val value = if (server.authHeader.equals("Authorization", ignoreCase = true) &&
                !token.startsWith("Bearer ", ignoreCase = true)
            ) {
                "Bearer $token"
            } else {
                token
            }
            builder.header(server.authHeader.ifBlank { "Authorization" }, value)
        }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }

        client.newCall(builder.build()).execute().use { response ->
            val returnedSession = response.header("Mcp-Session-Id") ?: sessionId
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw McpException(
                    when (response.code) {
                        401, 403 -> "Il connettore ha rifiutato le credenziali"
                        404 -> "Indirizzo del connettore non valido"
                        else -> "HTTP ${response.code}${raw.take(200).let { if (it.isBlank()) "" else ": $it" }}"
                    },
                )
            }
            if (!expectResult) return JsonObject(emptyMap()) to returnedSession
            val body = parse(raw) ?: throw McpException("Risposta non interpretabile")
            body["error"]?.jsonObject?.let { error ->
                throw McpException(
                    error["message"]?.jsonPrimitive?.content ?: "Errore del connettore",
                )
            }
            return body to returnedSession
        }
    }

    /** La risposta può essere JSON diretto o un flusso di eventi: qui si normalizza. */
    internal fun parse(raw: String): JsonObject? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return runCatching { json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
        }
        return trimmed.lineSequence()
            .filter { it.startsWith("data:") }
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotEmpty() && it != "[DONE]" }
            .mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .firstOrNull { it.containsKey("result") || it.containsKey("error") }
    }

    private companion object {
        const val PROTOCOL = "2025-06-18"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

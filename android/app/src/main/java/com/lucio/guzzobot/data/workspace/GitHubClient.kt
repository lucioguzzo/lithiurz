package com.lucio.guzzobot.data.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Le chiamate all'API di GitHub, condivise fra i file e le Actions. */
class GitHubClient(
    val owner: String,
    val repo: String,
    private val token: String,
    val json: Json,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun get(path: String): String = request("GET", path, null)

    fun post(path: String, body: JsonObject?): String = request("POST", path, body)

    fun patch(path: String, body: JsonObject): String = request("PATCH", path, body)

    fun delete(path: String, body: JsonObject): String = request("DELETE", path, body)

    /** Testo grezzo: i log delle Actions arrivano così, dietro un redirect. */
    fun raw(path: String): String {
        val call = Request.Builder()
            .url(url(path))
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", API_VERSION)
            .get()
            .build()
        client.newCall(call).execute().use { response ->
            if (!response.isSuccessful) {
                throw WorkspaceException("GitHub HTTP ${response.code} su $path")
            }
            return response.body?.string().orEmpty()
        }
    }

    fun request(method: String, path: String, body: JsonObject?): String {
        val payload = body?.let { json.encodeToString(JsonObject.serializer(), it) }
            ?.toRequestBody(JSON_MEDIA)
        val builder = Request.Builder()
            .url(url(path))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload ?: EMPTY)
            "PATCH" -> builder.patch(payload ?: EMPTY)
            "DELETE" -> if (payload != null) builder.delete(payload) else builder.delete()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw WorkspaceException(explain(response.code, text, path))
            return text
        }
    }

    private fun url(path: String) =
        if (path.startsWith("http")) path else "$API/repos/$owner/$repo/$path"

    private fun explain(code: Int, body: String, path: String): String {
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401, 403 -> "GitHub ha rifiutato il token (${detail ?: "permessi insufficienti"})"
            404 -> "Non trovato su $owner/$repo: $path"
            409 -> "Conflitto su GitHub: il branch è cambiato, riprova"
            422 -> "GitHub ha rifiutato la richiesta${detail?.let { ": $it" }.orEmpty()}"
            else -> "GitHub HTTP $code${detail?.let { ": $it" }.orEmpty()}"
        }
    }

    private companion object {
        const val API = "https://api.github.com"
        const val API_VERSION = "2022-11-28"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMPTY = "".toRequestBody(null)
    }
}

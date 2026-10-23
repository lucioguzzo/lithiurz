package com.lucio.guzzobot.data.workspace

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
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

/**
 * Un repository GitHub come area di lavoro, via API REST con un token
 * fine-grained. Le scritture multiple diventano un solo commit (blob, albero,
 * commit, spostamento del ref) invece di un commit per file.
 */
class GitHubWorkspace(
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val token: String,
    private val json: Json,
) : Workspace {

    override val label: String = "$owner/$repo ($branch)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun list(path: String): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val body = get("contents/${encode(clean)}?ref=$branch")
        val element = json.parseToJsonElement(body)
        if (element !is JsonArray) throw WorkspaceException("Non è una cartella: \"$path\"")
        element.map { item ->
            val entry = item.jsonObject
            WorkspaceEntry(
                path = entry.string("path"),
                directory = entry.string("type") == "dir",
                size = entry["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
            )
        }.sortedWith(compareByDescending<WorkspaceEntry> { it.directory }.thenBy { it.path })
    }

    override suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        if (looksBinary(clean)) throw WorkspaceException("Non è un file di testo: \"$path\"")
        val body = get("contents/${encode(clean)}?ref=$branch")
        val element = json.parseToJsonElement(body)
        if (element is JsonArray) throw WorkspaceException("È una cartella: \"$path\"")
        val entry = element.jsonObject
        val text = when {
            entry["content"] != null && entry.string("encoding") == "base64" ->
                decode(entry.string("content"))
            // Oltre 1 MB la Contents API non restituisce il contenuto: serve il blob.
            else -> {
                val sha = entry.string("sha")
                val blob = get("git/blobs/$sha")
                decode(json.parseToJsonElement(blob).jsonObject.string("content"))
            }
        }
        if (text.length > MAX_READ_CHARS) {
            text.take(MAX_READ_CHARS) + "\n…[troncato: il file ha ${text.length} caratteri]"
        } else {
            text
        }
    }

    override suspend fun write(files: List<WriteRequest>, message: String): String =
        withContext(Dispatchers.IO) {
            val headSha = json.parseToJsonElement(get("git/ref/heads/$branch"))
                .jsonObject["object"]!!.jsonObject.string("sha")
            val baseTree = json.parseToJsonElement(get("git/commits/$headSha"))
                .jsonObject["tree"]!!.jsonObject.string("sha")

            val entries = files.map { file ->
                val clean = normalizePath(file.path)
                if (clean.isEmpty()) throw WorkspaceException("Percorso vuoto")
                val blob = post(
                    "git/blobs",
                    buildJsonObject {
                        put("content", file.content)
                        put("encoding", "utf-8")
                    },
                )
                clean to json.parseToJsonElement(blob).jsonObject.string("sha")
            }

            val tree = post(
                "git/trees",
                buildJsonObject {
                    put("base_tree", baseTree)
                    put(
                        "tree",
                        buildJsonArray {
                            entries.forEach { (path, sha) ->
                                add(
                                    buildJsonObject {
                                        put("path", path)
                                        put("mode", "100644")
                                        put("type", "blob")
                                        put("sha", sha)
                                    },
                                )
                            }
                        },
                    )
                },
            )
            val treeSha = json.parseToJsonElement(tree).jsonObject.string("sha")

            val commit = post(
                "git/commits",
                buildJsonObject {
                    put("message", message.ifBlank { "GUZZOBOT: aggiorna ${entries.size} file" })
                    put("tree", treeSha)
                    put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(headSha)) })
                },
            )
            val commitSha = json.parseToJsonElement(commit).jsonObject.string("sha")

            patch("git/refs/heads/$branch", buildJsonObject { put("sha", commitSha) })
            "Commit ${commitSha.take(7)} su $branch: ${entries.joinToString { it.first }}"
        }

    override suspend fun delete(path: String, message: String): String = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val entry = json.parseToJsonElement(get("contents/${encode(clean)}?ref=$branch")).jsonObject
        val sha = entry.string("sha")
        val body = buildJsonObject {
            put("message", message.ifBlank { "GUZZOBOT: elimina $clean" })
            put("sha", sha)
            put("branch", branch)
        }
        request("DELETE", "contents/${encode(clean)}", body)
        "Eliminato $clean su $branch"
    }

    override suspend fun search(
        query: String,
        path: String,
        inContent: Boolean,
    ): List<WorkspaceHit> = withContext(Dispatchers.IO) {
        val prefix = normalizePath(path)
        val tree = json.parseToJsonElement(get("git/trees/$branch?recursive=1")).jsonObject
        val files = tree["tree"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it.string("type") == "blob" }
            .map { it.string("path") }
            .filter { prefix.isEmpty() || it.startsWith("$prefix/") || it == prefix }

        val byName = files.filter { it.contains(query, ignoreCase = true) }
            .take(MAX_HITS)
            .map { WorkspaceHit(it) }
        if (!inContent) return@withContext byName

        val hits = byName.toMutableList()
        files.asSequence()
            .filterNot { looksBinary(it) }
            .take(MAX_GREP_FILES)
            .forEach { file ->
                if (hits.size >= MAX_HITS) return@forEach
                val text = runCatching { read(file) }.getOrNull() ?: return@forEach
                text.lineSequence().forEachIndexed { index, line ->
                    if (hits.size < MAX_HITS && line.contains(query, ignoreCase = true)) {
                        hits += WorkspaceHit(file, index + 1, line.trim().take(200))
                    }
                }
            }
        hits
    }

    // --- chiamate ----------------------------------------------------------

    private fun get(path: String): String = request("GET", path, null)

    private fun post(path: String, body: JsonObject): String = request("POST", path, body)

    private fun patch(path: String, body: JsonObject): String = request("PATCH", path, body)

    private fun request(method: String, path: String, body: JsonObject?): String {
        val payload = body?.let { json.encodeToString(JsonObject.serializer(), it) }
            ?.toRequestBody(JSON_MEDIA)
        val builder = Request.Builder()
            .url("$API/repos/$owner/$repo/$path")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(payload ?: EMPTY)
            "PATCH" -> builder.patch(payload ?: EMPTY)
            "DELETE" -> if (payload != null) builder.delete(payload) else builder.delete()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
                }.getOrNull()
                throw WorkspaceException(
                    when (response.code) {
                        401, 403 -> "GitHub ha rifiutato il token (${detail ?: "permessi insufficienti"})"
                        404 -> "Non trovato su $owner/$repo: $path"
                        409 -> "Conflitto su GitHub: il branch è cambiato, riprova"
                        else -> "GitHub HTTP ${response.code}${detail?.let { ": $it" }.orEmpty()}"
                    },
                )
            }
            return text
        }
    }

    private fun encode(path: String): String =
        path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun decode(base64: String): String =
        Base64.decode(base64.replace("\n", ""), Base64.DEFAULT).decodeToString()

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw WorkspaceException("Risposta GitHub senza \"$key\"")

    private companion object {
        const val API = "https://api.github.com"
        const val MAX_HITS = 80
        const val MAX_GREP_FILES = 60
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMPTY = "".toRequestBody(null)
    }
}

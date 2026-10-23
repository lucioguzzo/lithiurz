package com.lucio.guzzobot.data.workspace

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Un repository GitHub come area di lavoro. Le scritture multiple diventano un
 * solo commit (blob, albero, commit, spostamento del ref) invece di un commit
 * per file, e il branch di lavoro può cambiare durante una sessione: l'agente
 * ne crea uno nuovo prima di toccare il codice.
 */
class GitHubWorkspace(
    private val client: GitHubClient,
    initialBranch: String,
) : Workspace {

    var branch: String = initialBranch
        private set

    override val label: String get() = "${client.owner}/${client.repo} ($branch)"

    private val json = client.json

    // --- branch e pull request --------------------------------------------

    /** Crea il branch (se manca) e ci sposta il lavoro successivo. */
    suspend fun createBranch(name: String, from: String?): String = withContext(Dispatchers.IO) {
        val clean = name.trim().trim('/')
        if (clean.isEmpty()) throw WorkspaceException("Nome del branch vuoto")
        val source = from?.trim()?.ifBlank { null } ?: branch
        val exists = runCatching { client.get("git/ref/heads/$clean") }.isSuccess
        if (!exists) {
            val sha = json.parseToJsonElement(client.get("git/ref/heads/$source"))
                .jsonObject["object"]!!.jsonObject.string("sha")
            client.post(
                "git/refs",
                buildJsonObject {
                    put("ref", "refs/heads/$clean")
                    put("sha", sha)
                },
            )
        }
        branch = clean
        if (exists) "Branch $clean già esistente: ci lavoro sopra" else "Creato il branch $clean da $source"
    }

    fun useBranch(name: String) {
        branch = name.trim().trim('/')
    }

    suspend fun openPullRequest(
        title: String,
        body: String,
        base: String,
    ): String = withContext(Dispatchers.IO) {
        val response = client.post(
            "pulls",
            buildJsonObject {
                put("title", title.ifBlank { "Modifiche da GUZZOBOT" })
                put("body", body)
                put("head", branch)
                put("base", base)
            },
        )
        val pull = json.parseToJsonElement(response).jsonObject
        "Pull request #${pull.string("number")} aperta: ${pull.string("html_url")}"
    }

    // --- file --------------------------------------------------------------

    override suspend fun list(path: String): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val element = json.parseToJsonElement(client.get("contents/${encode(clean)}?ref=$branch"))
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
        val element = json.parseToJsonElement(client.get("contents/${encode(clean)}?ref=$branch"))
        if (element is JsonArray) throw WorkspaceException("È una cartella: \"$path\"")
        val entry = element.jsonObject
        val text = when {
            entry["content"] != null && entry["encoding"]?.jsonPrimitive?.content == "base64" ->
                decode(entry.string("content"))
            // Oltre 1 MB la Contents API non restituisce il contenuto: serve il blob.
            else -> decode(
                json.parseToJsonElement(client.get("git/blobs/${entry.string("sha")}"))
                    .jsonObject.string("content"),
            )
        }
        if (text.length > MAX_READ_CHARS) {
            text.take(MAX_READ_CHARS) + "\n…[troncato: il file ha ${text.length} caratteri]"
        } else {
            text
        }
    }

    override suspend fun write(files: List<WriteRequest>, message: String): String =
        withContext(Dispatchers.IO) {
            val headSha = json.parseToJsonElement(client.get("git/ref/heads/$branch"))
                .jsonObject["object"]!!.jsonObject.string("sha")
            val baseTree = json.parseToJsonElement(client.get("git/commits/$headSha"))
                .jsonObject["tree"]!!.jsonObject.string("sha")

            val entries = files.map { file ->
                val clean = normalizePath(file.path)
                if (clean.isEmpty()) throw WorkspaceException("Percorso vuoto")
                val blob = client.post(
                    "git/blobs",
                    buildJsonObject {
                        put("content", file.content)
                        put("encoding", "utf-8")
                    },
                )
                clean to json.parseToJsonElement(blob).jsonObject.string("sha")
            }

            val tree = client.post(
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

            val commit = client.post(
                "git/commits",
                buildJsonObject {
                    put("message", message.ifBlank { "GUZZOBOT: aggiorna ${entries.size} file" })
                    put("tree", treeSha)
                    put("parents", buildJsonArray { add(JsonPrimitive(headSha)) })
                },
            )
            val commitSha = json.parseToJsonElement(commit).jsonObject.string("sha")
            client.patch("git/refs/heads/$branch", buildJsonObject { put("sha", commitSha) })
            "Commit ${commitSha.take(7)} su $branch: ${entries.joinToString { it.first }}"
        }

    override suspend fun delete(path: String, message: String): String = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val entry = json.parseToJsonElement(client.get("contents/${encode(clean)}?ref=$branch"))
            .jsonObject
        client.delete(
            "contents/${encode(clean)}",
            buildJsonObject {
                put("message", message.ifBlank { "GUZZOBOT: elimina $clean" })
                put("sha", entry.string("sha"))
                put("branch", branch)
            },
        )
        "Eliminato $clean su $branch"
    }

    override suspend fun search(
        query: String,
        path: String,
        inContent: Boolean,
    ): List<WorkspaceHit> = withContext(Dispatchers.IO) {
        val prefix = normalizePath(path)
        val tree = json.parseToJsonElement(client.get("git/trees/$branch?recursive=1")).jsonObject
        val files = tree["tree"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "blob" }
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

    private fun encode(path: String): String = path.split('/').joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun decode(base64: String): String =
        Base64.decode(base64.replace("\n", ""), Base64.DEFAULT).decodeToString()

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.content ?: throw WorkspaceException("Risposta GitHub senza \"$key\"")

    private companion object {
        const val MAX_HITS = 80
        const val MAX_GREP_FILES = 60
    }
}

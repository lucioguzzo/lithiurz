package com.lucio.guzzobot.data.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class Workflow(val id: Long, val name: String, val path: String)

data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String?,
    val url: String,
) {
    val finished: Boolean get() = status == "completed"
    val succeeded: Boolean get() = conclusion == "success"
}

/**
 * GitHub Actions come banco di prova: il telefono non ha né compilatore né
 * shell, quindi l'agente committa e lascia che sia il cloud a compilare ed
 * eseguire i test, poi legge l'esito e i log per correggersi.
 */
class GitHubActions(private val client: GitHubClient) {

    suspend fun workflows(): List<Workflow> = withContext(Dispatchers.IO) {
        parseWorkflows(client.get("actions/workflows?per_page=50"), client.json)
    }

    /** L'identificativo dell'ultima corsa, per riconoscere quella nuova. */
    suspend fun lastRunId(branch: String): Long = withContext(Dispatchers.IO) {
        parseRuns(client.get("actions/runs?branch=$branch&per_page=1"), client.json)
            .firstOrNull()?.id ?: 0L
    }

    suspend fun dispatch(workflow: String, branch: String) = withContext(Dispatchers.IO) {
        val target = resolve(workflow)
        client.post(
            "actions/workflows/$target/dispatches",
            buildJsonObject { put("ref", branch) },
        )
    }

    /** Aspetta la fine della corsa invece di far spendere al modello un giro per volta. */
    suspend fun awaitRun(
        branch: String,
        afterId: Long,
        waitSeconds: Int,
    ): WorkflowRun? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + waitSeconds.coerceIn(0, 600) * 1000L
        var seen: WorkflowRun? = null
        while (true) {
            val runs = parseRuns(client.get("actions/runs?branch=$branch&per_page=5"), client.json)
            val candidate = runs.firstOrNull { it.id > afterId } ?: runs.firstOrNull()
            if (candidate != null) {
                seen = candidate
                if (candidate.finished) return@withContext candidate
            }
            if (System.currentTimeMillis() >= deadline) return@withContext seen
            delay(6_000)
        }
        @Suppress("UNREACHABLE_CODE")
        seen
    }

    /** Esito leggibile: quali lavori sono falliti e le righe utili dei log. */
    suspend fun report(run: WorkflowRun): String = withContext(Dispatchers.IO) {
        val head = buildString {
            append("Corsa \"${run.name}\" #${run.id}: ")
            append(if (run.finished) run.conclusion ?: "senza esito" else "ancora in corso (${run.status})")
            append("\n${run.url}")
        }
        if (run.succeeded || !run.finished) return@withContext head

        val jobs = client.get("actions/runs/${run.id}/jobs")
        val failing = client.json.parseToJsonElement(jobs).jsonObject["jobs"]?.jsonArray.orEmpty()
            .map { it.jsonObject }
            .filter { it["conclusion"]?.jsonPrimitive?.content !in setOf("success", "skipped") }
        if (failing.isEmpty()) return@withContext head

        val details = failing.take(3).joinToString("\n\n") { job ->
            val id = job["id"]?.jsonPrimitive?.content
            val name = job["name"]?.jsonPrimitive?.content ?: "lavoro"
            val log = id?.let { runCatching { client.raw("actions/jobs/$it/logs") }.getOrNull() }
            "▸ $name: ${job["conclusion"]?.jsonPrimitive?.content}\n" +
                (log?.let { interestingLines(it) } ?: "(log non disponibile)")
        }
        "$head\n\n$details"
    }

    private suspend fun resolve(workflow: String): String {
        if (workflow.toLongOrNull() != null || workflow.endsWith(".yml") ||
            workflow.endsWith(".yaml")
        ) {
            return workflow.substringAfterLast('/')
        }
        val match = workflows().firstOrNull { it.name.equals(workflow, ignoreCase = true) }
            ?: workflows().firstOrNull { it.name.contains(workflow, ignoreCase = true) }
            ?: throw WorkspaceException("Nessun workflow chiamato \"$workflow\"")
        return match.path.substringAfterLast('/')
    }

    companion object {
        fun parseWorkflows(raw: String, json: Json): List<Workflow> =
            json.parseToJsonElement(raw).jsonObject["workflows"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .map { item ->
                    Workflow(
                        id = item["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        name = item["name"]?.jsonPrimitive?.content.orEmpty(),
                        path = item["path"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }

        fun parseRuns(raw: String, json: Json): List<WorkflowRun> =
            json.parseToJsonElement(raw).jsonObject["workflow_runs"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .map { item ->
                    WorkflowRun(
                        id = item["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        name = item["name"]?.jsonPrimitive?.content.orEmpty(),
                        status = item["status"]?.jsonPrimitive?.content.orEmpty(),
                        conclusion = item["conclusion"]?.jsonPrimitive?.contentOrNull(),
                        url = item["html_url"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }

        /**
         * Un log di CI è lungo migliaia di righe: al modello servono quelle che
         * parlano dell'errore, più la coda, dove di solito sta il riepilogo.
         */
        fun interestingLines(raw: String, maxChars: Int = 4_000): String {
            val lines = raw.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val markers = listOf(
                "error", "errore", "failed", "failure", "exception", "FAILED",
                "e: ", "> Task", "AssertionError", "not ok",
            )
            val flagged = lines.withIndex()
                .filter { (_, line) -> markers.any { line.contains(it, ignoreCase = true) } }
                .map { it.index }
            val keep = sortedSetOf<Int>()
            flagged.forEach { index ->
                for (i in (index - 1)..(index + 2)) if (i in lines.indices) keep += i
            }
            for (i in (lines.size - 12).coerceAtLeast(0) until lines.size) keep += i
            val text = keep.sorted().joinToString("\n") { lines[it] }
            return if (text.length <= maxChars) text else "…\n" + text.takeLast(maxChars)
        }

        private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
            content.takeIf { it != "null" && it.isNotBlank() }
    }
}

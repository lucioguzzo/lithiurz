package com.lucio.guzzobot

import com.lucio.guzzobot.data.workspace.GitHubActions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubActionsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `legge l'elenco dei workflow`() {
        val raw = """
            {"total_count":2,"workflows":[
              {"id":11,"name":"Build GUZZOBOT APK","path":".github/workflows/build.yml","state":"active"},
              {"id":12,"name":"Test","path":".github/workflows/test.yml","state":"active"}]}
        """.trimIndent()
        val workflows = GitHubActions.parseWorkflows(raw, json)
        assertEquals(2, workflows.size)
        assertEquals(11L, workflows.first().id)
        assertEquals(".github/workflows/build.yml", workflows.first().path)
    }

    @Test
    fun `riconosce una corsa finita male`() {
        val raw = """
            {"workflow_runs":[
              {"id":99,"name":"Test","status":"completed","conclusion":"failure",
               "html_url":"https://github.com/a/b/actions/runs/99"}]}
        """.trimIndent()
        val run = GitHubActions.parseRuns(raw, json).single()
        assertEquals(99L, run.id)
        assertTrue(run.finished)
        assertFalse(run.succeeded)
        assertEquals("https://github.com/a/b/actions/runs/99", run.url)
    }

    @Test
    fun `una corsa ancora in coda non risulta finita`() {
        val raw = """{"workflow_runs":[{"id":100,"name":"Test","status":"queued","conclusion":null,"html_url":"u"}]}"""
        val run = GitHubActions.parseRuns(raw, json).single()
        assertFalse(run.finished)
        assertEquals(null, run.conclusion)
    }

    @Test
    fun `dal log tiene le righe dell'errore e la coda`() {
        val log = buildString {
            repeat(400) { appendLine("2026-10-23T10:00:0${it % 10}Z normale riga $it") }
            appendLine("2026-10-23T10:05:00Z e: AppTest.kt:12 Unresolved reference 'pippo'")
            repeat(50) { appendLine("altra riga $it") }
            appendLine("FAILURE: Build failed with an exception.")
            appendLine("ultima riga del log")
        }
        val summary = GitHubActions.interestingLines(log)
        assertTrue(summary.contains("Unresolved reference"))
        assertTrue(summary.contains("FAILURE: Build failed"))
        assertTrue(summary.contains("ultima riga del log"))
        assertFalse(summary.contains("normale riga 5\n"))
        assertTrue(summary.length <= 4_100)
    }
}

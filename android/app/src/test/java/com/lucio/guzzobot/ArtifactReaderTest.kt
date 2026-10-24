package com.lucio.guzzobot

import com.lucio.guzzobot.data.workspace.ArtifactReader
import com.lucio.guzzobot.data.workspace.WorkspaceTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dagli strumenti si ricava cosa conservare fra gli artefatti, e cosa no. */
class ArtifactReaderTest {

    private val writeArgs =
        """{"files":[{"path":"src/App.kt","content":"..."},{"path":"README.md","content":"..."}]}"""

    @Test
    fun `i file scritti su GitHub puntano al repository`() {
        val drafts = ArtifactReader.from(
            toolName = WorkspaceTools.WRITE,
            arguments = writeArgs,
            result = "Commit a1b2c3d su main: src/App.kt, README.md",
            repoUrl = "https://github.com/lucio/guzzobot/blob/main",
        )
        assertEquals(2, drafts.size)
        assertEquals("App.kt", drafts.first().name)
        assertEquals(
            "https://github.com/lucio/guzzobot/blob/main/src/App.kt",
            drafts.first().location,
        )
        assertTrue(drafts.first().detail!!.contains("a1b2c3d"))
    }

    @Test
    fun `i file di una cartella locale restano relativi all'area di lavoro`() {
        val drafts = ArtifactReader.from(
            toolName = WorkspaceTools.WRITE,
            arguments = writeArgs,
            result = "Scritti 2 file",
            repoUrl = null,
        )
        assertEquals(listOf("ws:src/App.kt", "ws:README.md"), drafts.map { it.location })
        assertTrue(drafts.all { it.kind == ArtifactReader.FILE })
    }

    @Test
    fun `una pull request diventa un artefatto col suo indirizzo`() {
        val drafts = ArtifactReader.from(
            toolName = WorkspaceTools.PULL_REQUEST,
            arguments = """{"title":"Sistema i test"}""",
            result = "Pull request #42 aperta: https://github.com/lucio/guzzobot/pull/42",
            repoUrl = null,
        )
        val draft = drafts.single()
        assertEquals(ArtifactReader.PULL_REQUEST, draft.kind)
        assertEquals("https://github.com/lucio/guzzobot/pull/42", draft.location)
    }

    @Test
    fun `leggere e cercare non producono artefatti`() {
        assertTrue(
            ArtifactReader.from(WorkspaceTools.READ, """{"path":"a.kt"}""", "contenuto", null).isEmpty(),
        )
        assertTrue(ArtifactReader.from(WorkspaceTools.SEARCH, "{}", "niente", null).isEmpty())
        assertTrue(ArtifactReader.from(WorkspaceTools.WRITE, "{non json", "ok", null).isEmpty())
    }
}

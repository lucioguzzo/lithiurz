package com.lucio.guzzobot

import com.lucio.guzzobot.data.remote.ToolCall
import com.lucio.guzzobot.data.workspace.Workspace
import com.lucio.guzzobot.data.workspace.WorkspaceEntry
import com.lucio.guzzobot.data.workspace.WorkspaceException
import com.lucio.guzzobot.data.workspace.WorkspaceHit
import com.lucio.guzzobot.data.workspace.WorkspaceTools
import com.lucio.guzzobot.data.workspace.WriteRequest
import com.lucio.guzzobot.data.workspace.normalizePath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceToolsTest {

    /** Un'area di lavoro finta, in memoria. */
    private class FakeWorkspace : Workspace {
        override val label = "finta"
        val files = linkedMapOf("note.txt" to "ciao")
        var deleted: String? = null
        var lastMessage: String? = null

        override suspend fun list(path: String) =
            files.keys.map { WorkspaceEntry(it, false, files.getValue(it).length.toLong()) }

        override suspend fun read(path: String) =
            files[normalizePath(path)] ?: throw WorkspaceException("Non esiste")

        override suspend fun write(files: List<WriteRequest>, message: String): String {
            files.forEach { this.files[normalizePath(it.path)] = it.content }
            lastMessage = message
            return "scritti ${files.size}"
        }

        override suspend fun delete(path: String, message: String): String {
            deleted = normalizePath(path)
            files.remove(deleted)
            return "eliminato"
        }

        override suspend fun search(query: String, path: String, inContent: Boolean) =
            files.filterKeys { it.contains(query) }.keys.map { WorkspaceHit(it) }
    }

    private fun call(name: String, arguments: String) = ToolCall("id", name, arguments)

    @Test
    fun `i percorsi non possono uscire dall'area`() {
        assertEquals("src/App.kt", normalizePath("/src/App.kt"))
        assertEquals("src/App.kt", normalizePath("src//App.kt"))
        assertEquals("", normalizePath("/"))
        listOf("../segreti", "src/../../fuori", "..").forEach { path ->
            val error = runCatching { normalizePath(path) }.exceptionOrNull()
            assertTrue(path, error is WorkspaceException)
        }
    }

    @Test
    fun `solo scrittura ed eliminazione chiedono il permesso`() {
        assertTrue(WorkspaceTools.modifies(WorkspaceTools.WRITE))
        assertTrue(WorkspaceTools.modifies(WorkspaceTools.DELETE))
        assertFalse(WorkspaceTools.modifies(WorkspaceTools.READ))
        assertFalse(WorkspaceTools.modifies(WorkspaceTools.LIST))
        assertFalse(WorkspaceTools.modifies(WorkspaceTools.SEARCH))
    }

    @Test
    fun `scrive piu file in una volta sola`() = runTest {
        val workspace = FakeWorkspace()
        val result = WorkspaceTools.execute(
            workspace,
            call(
                WorkspaceTools.WRITE,
                """{"files":[{"path":"a.txt","content":"uno"},{"path":"b.txt","content":"due"}],"message":"commit di prova"}""",
            ),
        )
        assertEquals("uno", workspace.files["a.txt"])
        assertEquals("due", workspace.files["b.txt"])
        assertEquals("commit di prova", workspace.lastMessage)
        assertTrue(result.contains("2"))
    }

    @Test
    fun `legge ed elimina`() = runTest {
        val workspace = FakeWorkspace()
        assertEquals(
            "ciao",
            WorkspaceTools.execute(workspace, call(WorkspaceTools.READ, """{"path":"note.txt"}""")),
        )
        WorkspaceTools.execute(workspace, call(WorkspaceTools.DELETE, """{"path":"note.txt"}"""))
        assertEquals("note.txt", workspace.deleted)
    }

    @Test
    fun `le descrizioni finiscono nella richiesta di conferma`() {
        val write = call(WorkspaceTools.WRITE, """{"files":[{"path":"App.kt","content":"riga1\nriga2"}]}""")
        assertEquals("Scrive App.kt", WorkspaceTools.describe(write))
        assertTrue(WorkspaceTools.preview(write).contains("riga1"))
        assertEquals("Elimina vecchio.txt", WorkspaceTools.describe(call(WorkspaceTools.DELETE, """{"path":"vecchio.txt"}""")))
    }

    @Test
    fun `le definizioni elencano i cinque strumenti`() {
        val names = WorkspaceTools.definitions().map {
            it.toString().substringAfter("\"name\":\"").substringBefore("\"")
        }
        assertEquals(
            listOf("workspace_list", "workspace_read", "workspace_write", "workspace_delete", "workspace_search"),
            names,
        )
    }

    @Test
    fun `argomenti malformati non fanno esplodere nulla`() = runTest {
        val workspace = FakeWorkspace()
        val error = runCatching {
            WorkspaceTools.execute(workspace, call(WorkspaceTools.WRITE, "{non json"))
        }.exceptionOrNull()
        assertTrue(error is WorkspaceException)
    }
}

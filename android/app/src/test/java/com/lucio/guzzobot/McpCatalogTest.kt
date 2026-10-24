package com.lucio.guzzobot

import com.lucio.guzzobot.data.mcp.McpAuth
import com.lucio.guzzobot.data.mcp.McpCatalog
import com.lucio.guzzobot.data.mcp.McpNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpCatalogTest {

    @Test
    fun `ogni voce ha un indirizzo sicuro e un id unico`() {
        val entries = McpCatalog.entries
        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            assertTrue(entry.name, entry.url.startsWith("https://"))
            assertTrue(entry.name, entry.summary.isNotBlank())
        }
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
    }

    @Test
    fun `nessuna voce pronta usa il vecchio trasporto a soli eventi`() {
        // Il client dell'app parla streamable HTTP: un endpoint /sse non
        // funzionerebbe, e spacciarlo per pronto sarebbe una bugia.
        McpCatalog.ready().forEach { entry ->
            assertFalse(entry.name, entry.url.trimEnd('/').endsWith("/sse"))
        }
    }

    @Test
    fun `chi chiede un token dice anche dove prenderlo`() {
        McpCatalog.ready().filter { it.auth == McpAuth.TOKEN }.forEach { entry ->
            assertNotNull(entry.name, entry.tokenUrl)
            assertTrue(entry.name, entry.tokenUrl!!.startsWith("https://"))
        }
    }

    @Test
    fun `i connettori aperti non chiedono niente`() {
        McpCatalog.ready().filter { it.auth == McpAuth.NONE }.forEach { entry ->
            assertEquals(entry.name, null, entry.tokenUrl)
        }
    }

    @Test
    fun `i connettori OAuth restano fuori da quelli pronti`() {
        val ready = McpCatalog.ready().map { it.id }
        val pending = McpCatalog.pending().map { it.id }
        assertTrue(pending.isNotEmpty())
        assertTrue(ready.none { it in pending })
        assertEquals(McpCatalog.entries.size, ready.size + pending.size)
        McpCatalog.pending().forEach { assertEquals(McpAuth.OAUTH, it.auth) }
    }

    @Test
    fun `una voce del catalogo diventa un connettore valido`() {
        val entry = McpCatalog.ready().first()
        val server = McpCatalog.toServer(entry)
        assertEquals(entry.url, server.url)
        assertTrue(server.enabled)
        // Il nome dello strumento va composto senza caratteri che l'API rifiuta.
        val toolId = McpNaming.toolId(server.id, "prova")
        assertTrue(toolId.all { it.isLetterOrDigit() || it == '_' || it == '-' })
        assertEquals(server.id, McpServerIdFrom(toolId))
    }

    private fun McpServerIdFrom(toolId: String): String =
        toolId.removePrefix("mcp_").substringBefore("__")

    @Test
    fun `le categorie coprono tutte le voci`() {
        val grouped = McpCatalog.byCategory().values.flatten()
        assertEquals(McpCatalog.entries.size, grouped.size)
    }
}

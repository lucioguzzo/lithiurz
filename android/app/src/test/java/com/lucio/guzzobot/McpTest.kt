package com.lucio.guzzobot

import com.lucio.guzzobot.data.mcp.McpClient
import com.lucio.guzzobot.data.mcp.McpNaming
import com.lucio.guzzobot.data.mcp.McpServer
import com.lucio.guzzobot.data.mcp.McpTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val server = McpServer(
        id = "calendario-2",
        name = "Calendario",
        url = "https://example.test/mcp",
        tools = listOf(
            McpTool(name = "list_events", description = "Elenca gli eventi", readOnly = true),
            McpTool(name = "create/event", description = "Crea un evento"),
        ),
    )

    @Test
    fun `il nome composto torna al connettore e allo strumento veri`() {
        val name = McpNaming.toolId(server.id, "create/event")
        assertTrue(McpNaming.isMcp(name))
        val resolved = McpNaming.resolve(name, listOf(server))
        assertEquals(server.id, resolved?.first?.id)
        assertEquals("create/event", resolved?.second?.name)
    }

    @Test
    fun `i caratteri che l'API non accetta vengono sostituiti`() {
        val name = McpNaming.toolId("id con spazi", "a.b:c")
        assertTrue(name.all { it.isLetterOrDigit() || it == '_' || it == '-' })
    }

    @Test
    fun `uno strumento sconosciuto non viene inventato`() {
        assertNull(McpNaming.resolve("mcp_calendario-2__sparito", listOf(server)))
        assertNull(McpNaming.resolve("mcp_altro__list_events", listOf(server)))
        assertNull(McpNaming.resolve("fs_read", listOf(server)))
        assertFalse(McpNaming.isMcp("fs_read"))
    }

    @Test
    fun `i connettori spenti non finiscono fra gli strumenti`() {
        val definitions = McpNaming.definitions(
            listOf(server, server.copy(id = "spento", enabled = false)),
        )
        assertEquals(2, definitions.size)
        val names = definitions.map {
            it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        }
        assertEquals(
            listOf(
                McpNaming.toolId(server.id, "list_events"),
                McpNaming.toolId(server.id, "create/event"),
            ),
            names,
        )
    }

    @Test
    fun `uno strumento senza schema riceve comunque dei parametri validi`() {
        val definitions = McpNaming.definitions(listOf(server))
        val parameters = definitions.first()
            .jsonObject["function"]!!.jsonObject["parameters"]!!.jsonObject
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun `la risposta JSON diretta viene letta`() {
        val body = McpClient(json).parse("""{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}""")
        assertTrue(body?.containsKey("result") == true)
    }

    @Test
    fun `la risposta a eventi viene normalizzata`() {
        val raw = """
            event: message
            data: {"jsonrpc":"2.0","id":1,"result":{"serverInfo":{"name":"Prova"}}}

            data: [DONE]
        """.trimIndent()
        val body = McpClient(json).parse(raw)
        assertEquals(
            "Prova",
            body?.get("result")?.jsonObject?.get("serverInfo")
                ?.jsonObject?.get("name")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `fra gli eventi si cerca quello che porta il risultato`() {
        val raw = """
            data: {"jsonrpc":"2.0","method":"notifications/message","params":{}}
            data: {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"ignoto"}}
        """.trimIndent()
        val body = McpClient(json).parse(raw)
        assertEquals(
            "ignoto",
            body?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `una risposta illeggibile non diventa un risultato vuoto`() {
        assertNull(McpClient(json).parse(""))
        assertNull(McpClient(json).parse("<html>errore del proxy</html>"))
    }
}

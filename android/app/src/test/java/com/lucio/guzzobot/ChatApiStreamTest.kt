package com.lucio.guzzobot

import com.lucio.guzzobot.data.remote.ApiMessage
import com.lucio.guzzobot.data.remote.ChatApi
import com.lucio.guzzobot.data.remote.Content
import com.lucio.guzzobot.data.remote.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lo streaming consegna le chiamate agli strumenti a pezzi: il nome in un
 * frammento, gli argomenti spalmati sui successivi. Qui si verifica che
 * vengano ricomposte, e che il testo normale continui ad arrivare.
 */
class ChatApiStreamTest {

    private lateinit var server: MockWebServer
    private val api = ChatApi(Json { ignoreUnknownKeys = true; explicitNulls = false })

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun sse(vararg chunks: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n")

    private suspend fun collect(): List<StreamEvent> = api.streamChat(
        baseUrl = server.url("/v1").toString(),
        apiKey = "k",
        model = "m",
        messages = listOf(ApiMessage("user", Content.text("ciao"))),
        temperature = null,
        maxTokens = null,
    ).toList()

    @Test
    fun `ricompone una chiamata spezzata in piu frammenti`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"workspace_read"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"pa"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\":\"src/App.kt\"}"}}]}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            ),
        )
        val events = collect()
        val requests = events.filterIsInstance<StreamEvent.ToolRequests>().single()
        assertEquals(1, requests.calls.size)
        assertEquals("call_1", requests.calls.first().id)
        assertEquals("workspace_read", requests.calls.first().name)
        assertEquals("""{"path":"src/App.kt"}""", requests.calls.first().arguments)
    }

    @Test
    fun `la firma agganciata alla chiamata viene conservata`() = runTest {
        // Gemini 3 aggancia qui la «firma del pensiero» e al giro dopo la
        // pretende indietro identica, altrimenti rifiuta tutto con un 400.
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"image_create"},"extra_content":{"google":{"thought_signature":"AbCdEf=="}}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]}}]}""",
                """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            ),
        )
        val call = collect().filterIsInstance<StreamEvent.ToolRequests>().single().calls.single()
        assertEquals("image_create", call.name)
        val signature = call.extra
            ?.get("google")?.let { it as kotlinx.serialization.json.JsonObject }
            ?.get("thought_signature")
            ?.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertEquals("AbCdEf==", signature)
    }

    @Test
    fun `una chiamata senza firma resta valida`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"workspace_list","arguments":"{}"}}]}}]}""",
            ),
        )
        val call = collect().filterIsInstance<StreamEvent.ToolRequests>().single().calls.single()
        assertEquals(null, call.extra)
    }

    @Test
    fun `tiene distinte due chiamate nello stesso turno`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"a","function":{"name":"workspace_list","arguments":"{}"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"b","function":{"name":"workspace_read","arguments":"{\"path\":\"a.txt\"}"}}]}}]}""",
            ),
        )
        val calls = collect().filterIsInstance<StreamEvent.ToolRequests>().single().calls
        assertEquals(listOf("workspace_list", "workspace_read"), calls.map { it.name })
        assertEquals(listOf("a", "b"), calls.map { it.id })
    }

    @Test
    fun `testo e ragionamento arrivano come prima`() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"delta":{"reasoning_content":"penso"}}]}""",
                """{"choices":[{"delta":{"content":"Ciao "}}]}""",
                """{"choices":[{"delta":{"content":"Lucio"}}]}""",
            ),
        )
        val events = collect()
        assertEquals(
            "Ciao Lucio",
            events.filterIsInstance<StreamEvent.Content>().joinToString("") { it.text },
        )
        assertEquals(
            "penso",
            events.filterIsInstance<StreamEvent.Reasoning>().joinToString("") { it.text },
        )
        assertTrue(events.none { it is StreamEvent.ToolRequests })
    }

    @Test
    fun `un errore del provider diventa un messaggio leggibile`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        val failure = runCatching { collect() }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("Invalid API key"))
        assertTrue(failure?.message.orEmpty().contains("Chiave API non valida"))
    }
}

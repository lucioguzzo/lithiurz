package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.ModelCapabilities
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.VisionRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Se il modello in chat non vede le immagini, l'app ne cerca uno che le veda
 * fra quelli già configurati, preferendo lo stesso provider.
 */
class VisionRoutingTest {

    private val deepseek = Provider(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        models = listOf("deepseek-v4-pro", "deepseek-flash"),
    )
    private val groq = Provider(
        id = "groq",
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        models = listOf("llama-3.3-70b-versatile", "whisper-large-v3-turbo"),
    )
    private val openrouter = Provider(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        models = listOf("qwen/qwen2.5-vl-72b-instruct"),
    )

    @Test
    fun `solo la flash di DeepSeek vede le immagini`() {
        assertTrue(ModelCapabilities.seesImages("deepseek-flash"))
        assertFalse(ModelCapabilities.seesImages("deepseek-v4-pro"))
        assertFalse(ModelCapabilities.seesImages("whisper-large-v3-turbo"))
    }

    @Test
    fun `preferisce un modello dello stesso provider`() {
        val ref = VisionRouting.pick(
            providers = listOf(openrouter, deepseek, groq),
            preferProviderId = "deepseek",
        ) { true }
        assertEquals("deepseek", ref?.providerId)
        assertEquals("deepseek-flash", ref?.model)
    }

    @Test
    fun `ripiega su un altro provider quando serve`() {
        val ref = VisionRouting.pick(
            providers = listOf(groq, openrouter),
            preferProviderId = "groq",
        ) { true }
        assertEquals("openrouter", ref?.providerId)
    }

    @Test
    fun `ignora i provider senza chiave`() {
        val ref = VisionRouting.pick(
            providers = listOf(deepseek, openrouter),
            preferProviderId = "deepseek",
        ) { it.id == "openrouter" }
        assertEquals("openrouter", ref?.providerId)
    }

    @Test
    fun `senza nessun modello capace non inventa niente`() {
        assertNull(VisionRouting.pick(listOf(groq), "groq") { true })
        assertNull(VisionRouting.pick(listOf(deepseek), "deepseek") { false })
    }
}

package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.KeyDetector
import com.lucio.guzzobot.data.model.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDetectorTest {

    private val providers = Providers.defaults
    private val google = providers.first { it.id == "google" }
    private val groq = providers.first { it.id == "groq" }

    private val googleKey = "AIza" + "B".repeat(35)

    @Test
    fun `una chiave Google viene riconosciuta negli appunti`() {
        assertEquals(googleKey, KeyDetector.forProvider(googleKey, google))
        assertEquals(listOf("google"), KeyDetector.candidates(googleKey, providers).map { it.id })
    }

    @Test
    fun `la chiave viene estratta anche se copiata con dell'altro attorno`() {
        val messy = "La tua chiave API:\n  $googleKey  \nnon condividerla"
        assertEquals(googleKey, KeyDetector.forProvider(messy, google))
    }

    @Test
    fun `un testo qualunque non viene preso per una chiave`() {
        assertNull(KeyDetector.forProvider("ciao come stai", google))
        assertFalse(KeyDetector.looksLikeKey("ciao come stai"))
        assertFalse(KeyDetector.looksLikeKey(""))
        assertFalse(KeyDetector.looksLikeKey(null))
        // Una chiave troppo corta non è una chiave.
        assertFalse(KeyDetector.looksLikeKey("AIzaTroppoCorta"))
    }

    @Test
    fun `la chiave di un provider non viene offerta a un altro`() {
        assertNull(KeyDetector.forProvider(googleKey, groq))
        val groqKey = "gsk_" + "c".repeat(44)
        assertNull(KeyDetector.forProvider(groqKey, google))
        assertEquals(groqKey, KeyDetector.forProvider(groqKey, groq))
    }

    @Test
    fun `fra forme che si somigliano vince la piu' specifica`() {
        val openRouter = "sk-or-v1-" + "d".repeat(64)
        val ids = KeyDetector.candidates(openRouter, providers).map { it.id }
        assertEquals(listOf("openrouter"), ids)
    }

    @Test
    fun `una chiave ambigua lascia scegliere all'utente`() {
        // sk- senza prefisso riconoscibile: OpenAI, DeepSeek e Moonshot.
        val ambiguous = "sk-" + "e".repeat(40)
        val ids = KeyDetector.candidates(ambiguous, providers).map { it.id }
        assertTrue(ids.size > 1)
        assertTrue(ids.contains("openai"))
        assertTrue(KeyDetector.looksLikeKey(ambiguous))
    }

    @Test
    fun `un provider senza forma dichiarata non pretende di riconoscere niente`() {
        val mistral = providers.first { it.id == "mistral" }
        assertNull(mistral.keyPattern)
        assertNull(KeyDetector.forProvider("qualunque cosa", mistral))
    }
}

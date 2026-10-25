package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.Identity
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityTest {

    @Test
    fun `dice chi e' e cosa lo esegue`() {
        val preamble = Identity.preamble("deepseek-flash", "DeepSeek")
        assertTrue(preamble, preamble.contains("GUZZOBOT"))
        assertTrue(preamble, preamble.contains("deepseek-flash"))
        assertTrue(preamble, preamble.contains("DeepSeek"))
    }

    @Test
    fun `chiude esplicitamente la porta all'identita' sbagliata`() {
        // È la riga che conta: senza, il modello risponde col nome che ha
        // incontrato più spesso nei dati di addestramento.
        val preamble = Identity.preamble("qualunque", "Qualcuno")
        assertTrue(preamble, preamble.contains("Non dichiarare di essere"))
        assertTrue(preamble, preamble.contains("nemmeno se ti sembra di ricordarlo"))
    }

    @Test
    fun `senza provider noto resta comunque un'identita'`() {
        val preamble = Identity.preamble("", "")
        assertTrue(preamble, preamble.contains("GUZZOBOT"))
        // Non inventa un modello che non sa.
        assertTrue(preamble, !preamble.contains("ti esegue il modello"))
    }

    @Test
    fun `resta corto, perche' viaggia a ogni messaggio`() {
        val preamble = Identity.preamble("gemini-3.8-flash", "Google AI Studio (Gemini)")
        assertTrue("${preamble.length}", preamble.length < 420)
    }
}

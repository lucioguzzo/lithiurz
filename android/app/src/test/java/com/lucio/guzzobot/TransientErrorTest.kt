package com.lucio.guzzobot

import com.lucio.guzzobot.data.remote.ApiErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransientErrorTest {

    @Test
    fun `il sovraccarico dichiarato dal provider si riprova`() {
        // Il messaggio vero visto sullo schermo.
        assertTrue(
            ApiErrors.transient(
                503,
                "This model is currently experiencing high demand. Spikes in demand " +
                    "are usually temporary. Please try again later.",
            ),
        )
        assertTrue(ApiErrors.transient(500, "Internal error"))
        assertTrue(ApiErrors.transient(502, null))
        assertTrue(ApiErrors.transient(504, null))
    }

    @Test
    fun `la quota finita non si riprova, perche' non cambierebbe niente`() {
        assertFalse(
            ApiErrors.transient(
                429,
                "You exceeded your current quota, please check your plan and billing details.",
            ),
        )
        assertFalse(ApiErrors.transient(503, "Quota exceeded for this project"))
    }

    @Test
    fun `un limite al minuto invece si riprova`() {
        assertTrue(ApiErrors.transient(429, "Too many requests, slow down"))
        assertTrue(ApiErrors.transient(429, null))
    }

    @Test
    fun `gli errori definitivi non vengono ritentati`() {
        assertFalse(ApiErrors.transient(400, "Invalid argument"))
        assertFalse(ApiErrors.transient(401, "Invalid API key"))
        assertFalse(ApiErrors.transient(403, "Forbidden"))
        assertFalse(ApiErrors.transient(404, "Model not found"))
        assertFalse(ApiErrors.transient(0, null))
    }

    @Test
    fun `dopo i tentativi il messaggio dice cosa aspettare, non il JSON`() {
        assertTrue(ApiErrors.exhausted(503).contains("sovraccarico"))
        assertTrue(ApiErrors.exhausted(429).contains("limitando"))
        // Nessuna parentesi graffa: non è JSON da leggere.
        assertFalse(ApiErrors.exhausted(503).contains("{"))
    }

    @Test
    fun `un rifiuto degli strumenti non viene scambiato per sovraccarico`() {
        val message = "Function calling is not enabled for models/antigravity-preview-05-2026"
        assertFalse(ApiErrors.transient(400, message))
        assertTrue(ApiErrors.toolsUnsupported(message))
    }
}

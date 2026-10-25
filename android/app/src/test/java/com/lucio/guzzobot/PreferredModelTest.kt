package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.ModelCatalog
import com.lucio.guzzobot.data.remote.ApiErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferredModelTest {

    // L'elenco vero che ha causato il guasto: arriva ordinato alfabeticamente.
    private val fromGoogle = listOf(
        "antigravity-preview-05-2026",
        "gemini-2.5-flash-image",
        "gemini-3.6-flash",
        "gemini-3.8-flash",
        "gemini-embedding-001",
    ).sorted()

    private val curated = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-flash-image")

    @Test
    fun `non vince il primo in ordine alfabetico`() {
        // Prima di questa correzione usciva «antigravity-preview», che non sa
        // usare gli strumenti.
        assertEquals("gemini-3.8-flash", ModelCatalog.preferredChat(fromGoogle, curated))
    }

    @Test
    fun `senza elenco dell'app si scarta comunque l'anteprima`() {
        assertEquals("gemini-3.6-flash", ModelCatalog.preferredChat(fromGoogle))
    }

    @Test
    fun `i modelli di servizio non diventano modelli di chat`() {
        val chosen = ModelCatalog.preferredChat(fromGoogle, curated)
        assertFalse(chosen, ModelCatalog.isAccessory(chosen!!))
    }

    @Test
    fun `se ci sono solo anteprime si usa quella`() {
        val only = listOf("qualcosa-preview", "altro-preview")
        assertEquals("altro-preview", ModelCatalog.preferredChat(only.sorted()))
    }

    @Test
    fun `un elenco vuoto non produce un modello inventato`() {
        assertNull(ModelCatalog.preferredChat(emptyList()))
        assertNull(ModelCatalog.preferredChat(emptyList(), curated))
    }

    @Test
    fun `l'anteprima viene riconosciuta per com'e' scritta`() {
        assertTrue(ModelCatalog.isPreview("antigravity-preview-05-2026"))
        assertTrue(ModelCatalog.isPreview("gemini-3-pro-image-preview"))
        assertTrue(ModelCatalog.isPreview("qualcosa-exp-1234"))
        assertFalse(ModelCatalog.isPreview("gemini-3.8-flash"))
        assertFalse(ModelCatalog.isPreview("gpt-4o"))
    }

    @Test
    fun `il rifiuto degli strumenti viene riconosciuto`() {
        assertTrue(
            ApiErrors.toolsUnsupported(
                "Function calling is not enabled for models/antigravity-preview-05-2026",
            ),
        )
        assertTrue(ApiErrors.toolsUnsupported("This model does not support tools."))
        assertTrue(ApiErrors.toolsUnsupported("Unsupported parameter: 'tools'"))
    }

    @Test
    fun `un errore qualunque non viene scambiato per quello`() {
        assertFalse(ApiErrors.toolsUnsupported(null))
        assertFalse(ApiErrors.toolsUnsupported(""))
        assertFalse(ApiErrors.toolsUnsupported("You exceeded your current quota"))
        assertFalse(ApiErrors.toolsUnsupported("Invalid API key"))
        // Parla di strumenti ma è un altro problema: non va silenziato.
        assertFalse(ApiErrors.toolsUnsupported("The tool returned an invalid result"))
    }
}

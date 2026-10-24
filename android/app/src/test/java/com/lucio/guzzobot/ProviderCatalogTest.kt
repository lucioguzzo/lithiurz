package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.FreeNote
import com.lucio.guzzobot.data.model.FreeTier
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.data.model.ProviderCatalog
import com.lucio.guzzobot.data.model.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {

    private val defaults = listOf(
        Provider(id = "a", name = "A", baseUrl = "https://a.test/v1", builtIn = true),
        Provider(
            id = "b",
            name = "B nuovo nome",
            baseUrl = "https://b.test/v1",
            keyUrl = "https://b.test/keys",
            builtIn = true,
            freeTier = FreeTier(
                signupUrl = "https://b.test/keys",
                freeMarker = ":free",
                notes = listOf(FreeNote.NO_CARD),
            ),
        ),
    )

    @Test
    fun `al primo avvio valgono i predefiniti`() {
        assertEquals(defaults, ProviderCatalog.merge(stored = emptyList(), defaults = defaults))
    }

    @Test
    fun `un provider predefinito nuovo compare a chi ha gia l'app`() {
        val stored = listOf(defaults[0].copy(models = listOf("a-1")))
        val merged = ProviderCatalog.merge(stored, defaults)
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals(listOf("a-1"), merged.first().models)
    }

    @Test
    fun `le scelte dell'utente sopravvivono all'aggiornamento`() {
        val stored = listOf(
            defaults[1].copy(
                name = "B",
                baseUrl = "https://b.test/altro",
                models = listOf("scelto-a-mano"),
                keyUrl = null,
                freeTier = null,
            ),
        )
        val merged = ProviderCatalog.merge(stored, defaults)
        val b = merged.first { it.id == "b" }
        // L'indirizzo corretto a mano resta; i metadati dell'app si riallineano.
        assertEquals("https://b.test/altro", b.baseUrl)
        assertEquals(listOf("scelto-a-mano"), b.models)
        assertEquals("B nuovo nome", b.name)
        assertEquals("https://b.test/keys", b.keyUrl)
        assertNotNull(b.freeTier)
    }

    @Test
    fun `un provider aggiunto a mano resta, in coda`() {
        val mine = Provider(id = "mio", name = "Mio", baseUrl = "https://mio.test/v1")
        val merged = ProviderCatalog.merge(listOf(defaults[0], mine), defaults)
        assertEquals(listOf("a", "b", "mio"), merged.map { it.id })
        assertFalse(merged.last().builtIn)
    }

    @Test
    fun `un predefinito cancellato non viene resuscitato`() {
        val merged = ProviderCatalog.merge(
            stored = listOf(defaults[0]),
            defaults = defaults,
            removed = setOf("b"),
        )
        assertEquals(listOf("a"), merged.map { it.id })
        assertEquals(
            listOf("a"),
            ProviderCatalog.merge(emptyList(), defaults, removed = setOf("b")).map { it.id },
        )
    }

    @Test
    fun `il marcatore distingue i modelli gratuiti dagli altri`() {
        assertTrue(ProviderCatalog.isFree(defaults, "b", "qualcosa/modello:free"))
        assertFalse(ProviderCatalog.isFree(defaults, "b", "qualcosa/modello"))
        // Senza marcatore è gratis tutto il provider; senza piano, niente.
        assertFalse(ProviderCatalog.isFree(defaults, "a", "a-1"))
        assertTrue(
            ProviderCatalog.isFree(
                listOf(defaults[1].copy(freeTier = FreeTier(signupUrl = "x"))),
                "b",
                "qualunque",
            ),
        )
    }

    @Test
    fun `i provider gratuiti veri sono quelli dichiarati`() {
        assertEquals(listOf("b"), ProviderCatalog.free(defaults).map { it.id })
    }

    @Test
    fun `i predefiniti dell'app restano coerenti`() {
        val free = ProviderCatalog.free(Providers.defaults)
        // Chi ha un piano gratuito deve dire dove si prende la chiave.
        free.forEach { provider ->
            assertTrue(provider.name, provider.freeTier!!.signupUrl.startsWith("https://"))
        }
        // Gemini è l'unico gratuito che vede le immagini: se cambia, il
        // disclaimer va riscritto.
        val vision = free.filter { it.freeTier!!.notes.contains(FreeNote.SEES_IMAGES) }
        assertEquals(listOf("google"), vision.map { it.id })
        // Nessun doppione fra gli id dei predefiniti.
        assertEquals(
            Providers.defaults.size,
            Providers.defaults.map { it.id }.distinct().size,
        )
        assertNull(Providers.defaults.firstOrNull { it.id == "github" })
    }
}

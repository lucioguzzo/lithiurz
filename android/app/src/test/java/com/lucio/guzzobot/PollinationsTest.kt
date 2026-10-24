package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.ImageApi
import com.lucio.guzzobot.data.model.Providers
import com.lucio.guzzobot.data.remote.Pollinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollinationsTest {

    @Test
    fun `il prompt finisce nel percorso, codificato`() {
        val url = Pollinations.url("un gatto / in cucina?")
        assertTrue(url, url.startsWith("https://image.pollinations.ai/prompt/"))
        // Barra e punto interrogativo nel prompt cambierebbero la richiesta.
        val path = url.removePrefix("https://image.pollinations.ai/prompt/").substringBefore("?")
        assertFalse(path, path.contains("/"))
        assertFalse(path, path.contains("?"))
        assertTrue(path, path.contains("%2F"))
        // Gli spazi come %20, non come segni più.
        assertFalse(path, path.contains("+"))
        assertTrue(path, path.contains("%20"))
    }

    @Test
    fun `una misura troppo grande viene riportata nei limiti, in proporzione`() {
        assertEquals(768 to 768, Pollinations.dimensions("1024x1024"))
        // 1536x768 sta in 768x384: le proporzioni restano.
        assertEquals(768 to 384, Pollinations.dimensions("1536x768"))
        assertEquals(384 to 768, Pollinations.dimensions("768x1536"))
    }

    @Test
    fun `una misura ammessa resta com'e'`() {
        assertEquals(512 to 512, Pollinations.dimensions("512x512"))
        assertEquals(640 to 480, Pollinations.dimensions("640x480"))
    }

    @Test
    fun `una misura assente o scritta male non blocca la richiesta`() {
        assertEquals(768 to 768, Pollinations.dimensions(null))
        assertEquals(768 to 768, Pollinations.dimensions(""))
        assertEquals(768 to 768, Pollinations.dimensions("grande"))
        assertEquals(512 to 512, Pollinations.dimensions("512"))
    }

    @Test
    fun `i parametri richiesti sono nella query`() {
        val url = Pollinations.url("prova", size = "512x512", seed = 7, safe = true)
        assertTrue(url, url.contains("width=512"))
        assertTrue(url, url.contains("height=512"))
        assertTrue(url, url.contains("seed=7"))
        assertTrue(url, url.contains("safe=true"))
        // Senza seed il parametro non viene inventato.
        assertFalse(Pollinations.url("prova").contains("seed="))
    }

    @Test
    fun `il rifiuto per troppa fretta dice quanto aspettare`() {
        assertTrue(Pollinations.waitMessage("30").contains("30 secondi"))
        // Senza intestazione si dice comunque la regola.
        assertTrue(Pollinations.waitMessage(null).contains("45 secondi"))
        assertTrue(Pollinations.waitMessage("non un numero").contains("45 secondi"))
    }

    @Test
    fun `il provider e' configurato come servizio senza chiave`() {
        val provider = Providers.defaults.first { it.id == "pollinations" }
        assertEquals(ImageApi.POLLINATIONS, provider.imageApi)
        assertTrue(provider.imageOnly)
        // Non chiede credenziali: l'app non deve considerarlo da configurare.
        assertFalse(provider.requiresKey)
        // Ollama gira in locale e nemmeno lui chiede chiavi.
        assertFalse(Providers.defaults.first { it.id == "ollama" }.requiresKey)
        // Tutti gli altri sì.
        assertTrue(Providers.defaults.first { it.id == "google" }.requiresKey)
    }
}

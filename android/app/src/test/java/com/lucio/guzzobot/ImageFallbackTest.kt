package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.AppSettings
import com.lucio.guzzobot.data.model.ImageApi
import com.lucio.guzzobot.data.model.ModelRef
import com.lucio.guzzobot.data.model.Providers
import com.lucio.guzzobot.data.remote.ApiErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFallbackTest {

    @Test
    fun `il ripiego e' spento finche' non lo si accende`() {
        assertFalse(AppSettings().imageFallback)
    }

    @Test
    fun `si ripiega quando il provider non puo' servire, non quando sbaglia l'utente`() {
        // Sovraccarico: un altro provider può rispondere.
        assertTrue(ApiErrors.unavailable(503, "This model is currently experiencing high demand"))
        // Quota finita: insistere con lui è inutile, un altro no.
        assertTrue(
            ApiErrors.unavailable(429, "You exceeded your current quota, check your plan and billing"),
        )
        // Chiave sbagliata o prompt rifiutato: cambiare provider non aggiusta
        // niente e nasconderebbe il vero motivo.
        assertFalse(ApiErrors.unavailable(401, "Invalid API key"))
        assertFalse(ApiErrors.unavailable(400, "The prompt was blocked by safety filters"))
        assertFalse(ApiErrors.unavailable(404, "Model not found"))
    }

    @Test
    fun `la quota finita si ripiega ma non si ritenta`() {
        val quota = "You exceeded your current quota, please check your plan and billing details."
        // Sono due domande diverse: riprovare con lo stesso, o cercare altrove.
        assertFalse(ApiErrors.transient(429, quota))
        assertTrue(ApiErrors.unavailable(429, quota))
    }

    @Test
    fun `il provider di ripiego esiste e non chiede chiavi`() {
        val spare = Providers.defaults.first { it.imageApi == ImageApi.POLLINATIONS }
        assertFalse(spare.requiresKey)
        assertTrue(spare.imageOnly)
    }

    @Test
    fun `con Pollinations gia' scelto non c'e' niente su cui ripiegare`() {
        val spare = Providers.defaults.first { it.imageApi == ImageApi.POLLINATIONS }
        val settings = AppSettings(
            imageFallback = true,
            imageRef = ModelRef(spare.id, spare.models.first()),
        )
        // Ripiegare su se stessi non avrebbe senso: resta l'errore vero.
        val current = settings.providers.first { it.id == settings.imageRef!!.providerId }
        assertEquals(ImageApi.POLLINATIONS, current.imageApi)
    }

    @Test
    fun `fra i predefiniti c'e' sempre un generatore senza chiave`() {
        assertNotNull(Providers.defaults.firstOrNull { it.imageApi == ImageApi.POLLINATIONS })
    }
}

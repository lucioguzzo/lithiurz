package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.FreeNote
import com.lucio.guzzobot.data.model.ImageApi
import com.lucio.guzzobot.data.model.ModelCatalog
import com.lucio.guzzobot.data.model.Providers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePaidTest {

    private val google = Providers.defaults.first { it.id == "google" }

    @Test
    fun `i modelli immagine di Google sono dichiarati a pagamento`() {
        // La pagina dei prezzi di Google li dà tutti fuori dal piano gratuito.
        assertTrue(google.imagesNeedPayment)
        assertTrue(google.freeTier!!.notes.contains(FreeNote.IMAGES_PAID))
        // E non va promesso il contrario.
        assertFalse(google.freeTier!!.notes.contains(FreeNote.CREATES_IMAGES))
    }

    @Test
    fun `il modello immagini dismesso non viene piu' offerto`() {
        // gemini-2.5-flash-image è stato spento il 2 ottobre 2026.
        assertFalse(google.models.any { it.contains("gemini-2.5-flash-image") })
        // Ma un modello immagini di Google resta, per chi paga.
        assertTrue(ModelCatalog.imageModels(google.models).isNotEmpty())
    }

    @Test
    fun `l'unico generatore senza chiave ne' fatturazione e' dichiarato tale`() {
        val spare = Providers.defaults.first { it.imageApi == ImageApi.POLLINATIONS }
        assertFalse(spare.requiresKey)
        assertFalse(spare.imagesNeedPayment)
        assertTrue(spare.freeTier!!.notes.contains(FreeNote.CREATES_IMAGES))
    }

    @Test
    fun `nessun altro provider gratuito promette immagini che non fa`() {
        Providers.defaults
            .filter { it.freeTier?.notes?.contains(FreeNote.CREATES_IMAGES) == true }
            .forEach { provider ->
                // Chi dichiara di generare immagini non deve chiedere soldi
                // per farlo: altrimenti è una promessa che non mantiene.
                assertFalse(provider.name, provider.imagesNeedPayment)
            }
    }

    @Test
    fun `c'e' sempre un ripiego senza chiave fra i predefiniti`() {
        assertNotNull(Providers.defaults.firstOrNull { it.imageApi == ImageApi.POLLINATIONS })
    }
}

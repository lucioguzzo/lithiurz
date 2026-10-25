package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.FreeNote
import com.lucio.guzzobot.data.model.FreeTier
import com.lucio.guzzobot.data.model.ProviderCatalog
import com.lucio.guzzobot.data.model.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le promesse della sezione «gratuiti», verificate contro i listini dei
 * fornitori. Un numero inventato qui diventa un errore in faccia all'utente.
 */
class FreeClaimsTest {

    private val free = ProviderCatalog.free(Providers.defaults)

    @Test
    fun `Cerebras non sta fra i gratuiti, perche' chiede un metodo di pagamento`() {
        val cerebras = Providers.defaults.first { it.id == "cerebras" }
        assertNull(cerebras.freeTier)
        assertTrue(free.none { it.id == "cerebras" })
    }

    @Test
    fun `di Z_ai sono gratuiti i modelli dichiarati tali, non tutti i flash`() {
        val zai = Providers.defaults.first { it.id == "zai" }.freeTier!!
        // Il listino Z.ai dà GLM-5.3-Flash a pagamento: il nome inganna.
        assertFalse(zai.covers("glm-5.3-flash"))
        assertFalse(zai.covers("glm-5.4"))
        assertTrue(zai.covers("glm-4.7-flash"))
        assertTrue(zai.covers("glm-4.5-flash"))
        assertTrue(zai.covers("glm-4.6v-flash"))
    }

    @Test
    fun `i modelli gratuiti di Z_ai sono anche selezionabili`() {
        val zai = Providers.defaults.first { it.id == "zai" }
        zai.freeTier!!.freeModels.forEach { model ->
            assertTrue(model, zai.models.any { it.contains(model, ignoreCase = true) })
        }
    }

    @Test
    fun `un elenco esplicito ha la precedenza sul marcatore`() {
        val tier = FreeTier(
            signupUrl = "https://x",
            freeMarker = "flash",
            freeModels = listOf("a-flash"),
        )
        assertTrue(tier.covers("a-flash"))
        assertFalse(tier.covers("b-flash"))
        // Senza né elenco né marcatore è gratis tutto il provider.
        assertTrue(FreeTier(signupUrl = "https://x").covers("qualunque"))
    }

    @Test
    fun `nessun limite dichiarato contiene un numero che non sappiamo`() {
        // Dove il fornitore non pubblica i limiti, si rimanda alla sua console
        // invece di scrivere una cifra presa da un blog.
        val vague = listOf("groq", "mistral")
        vague.forEach { id ->
            val limits = Providers.defaults.first { it.id == id }.freeTier!!.limits
            assertFalse(limits, limits.contains("al minuto"))
            assertFalse(limits, limits.contains("miliardo"))
        }
    }

    @Test
    fun `chi promette di non chiedere la carta non deve chiedere nemmeno una prova`() {
        free.filter { it.freeTier!!.notes.contains(FreeNote.NO_CARD) }.forEach { provider ->
            val limits = provider.freeTier!!.limits.lowercase()
            assertFalse(provider.name, limits.contains("prova"))
            assertFalse(provider.name, limits.contains("credito"))
        }
    }

    @Test
    fun `ogni provider gratuito dice dove si prende la chiave e cosa dà`() {
        assertTrue(free.isNotEmpty())
        free.forEach { provider ->
            val tier = provider.freeTier!!
            assertTrue(provider.name, tier.signupUrl.startsWith("https://"))
            assertTrue(provider.name, tier.limits.isNotBlank())
            assertTrue(provider.name, tier.notes.isNotEmpty())
        }
    }

    @Test
    fun `i gratuiti rimasti sono quelli verificati`() {
        assertEquals(
            listOf("openrouter", "groq", "mistral", "zai", "google", "pollinations", "ollama"),
            free.map { it.id },
        )
    }
}

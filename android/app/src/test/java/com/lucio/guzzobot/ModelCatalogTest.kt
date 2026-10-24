package com.lucio.guzzobot

import com.lucio.guzzobot.data.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `un elenco corto del provider sostituisce quello scritto nell'app`() {
        val merged = ModelCatalog.merge(
            curated = listOf("glm-5.4", "glm-5.1"),
            fetched = listOf("glm-5.3", "glm-5.3-air", "glm-4.6v"),
        )
        assertEquals(listOf("glm-5.3", "glm-5.3-air", "glm-4.6v"), merged)
    }

    @Test
    fun `un catalogo enorme non invade il selettore`() {
        val fetched = (1..400).map { "vendor/model-$it" } + "deepseek/deepseek-chat"
        val merged = ModelCatalog.merge(
            curated = listOf("deepseek/deepseek-chat", "qualcosa/sparito"),
            fetched = fetched,
        )
        assertEquals(listOf("deepseek/deepseek-chat"), merged)
    }

    @Test
    fun `se il provider non risponde si tiene quello che c'era`() {
        val curated = listOf("kimi-k3.1")
        assertEquals(curated, ModelCatalog.merge(curated, emptyList()))
    }

    @Test
    fun `un catalogo enorme senza nessun modello noto ripiega sui primi`() {
        val fetched = (1..100).map { "vendor/model-$it" }
        val merged = ModelCatalog.merge(listOf("niente-di-simile"), fetched, limit = 10)
        assertEquals(10, merged.size)
    }

    @Test
    fun `il modello sparito viene sostituito dal parente piu prossimo`() {
        assertEquals(
            "glm-5.3",
            ModelCatalog.replacementFor("glm-5.4", listOf("glm-4.6", "glm-5.3", "kimi-k3")),
        )
        assertEquals(
            "kimi-k3",
            ModelCatalog.replacementFor("kimi-k3.1", listOf("kimi-k2.6", "kimi-k3", "glm-5.3")),
        )
    }

    @Test
    fun `un modello ancora presente non viene toccato`() {
        assertEquals(
            "deepseek-flash",
            ModelCatalog.replacementFor("deepseek-flash", listOf("deepseek-flash", "deepseek-v4-pro")),
        )
    }

    @Test
    fun `senza parenti non si inventa un sostituto`() {
        assertNull(ModelCatalog.replacementFor("glm-5.4", listOf("gpt-4o", "o3-mini")))
        assertNull(ModelCatalog.replacementFor("", listOf("gpt-4o")))
        assertTrue(ModelCatalog.replacementFor("glm-5.4", emptyList()) == null)
    }

    @Test
    fun `i modelli che non conversano restano fuori dal selettore della chat`() {
        val all = listOf(
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-embedding-001",
            "gemini-3.8-flash-tts",
            "gemini-3.5-transcribe",
            "gemini-2.5-flash-image",
        )
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash"),
            ModelCatalog.chatModels(all),
        )
    }

    @Test
    fun `ma restano nell'elenco del provider, perche' servono ai ruoli`() {
        // Nasconderli nel selettore è una cosa; cancellarli lascerebbe senza
        // modello la generazione di immagini e la trascrizione.
        val fetched = listOf("gemini-3.8-flash", "gemini-2.5-flash-image", "gemini-3.5-transcribe")
        val merged = ModelCatalog.merge(curated = listOf("gemini-3.8-flash"), fetched = fetched)
        assertEquals(fetched, merged)
        assertEquals(listOf("gemini-2.5-flash-image"), ModelCatalog.imageModels(merged))
        assertEquals(listOf("gemini-3.5-transcribe"), ModelCatalog.transcribeModels(merged))
    }

    @Test
    fun `un provider di soli modelli di servizio non resta senza elenco`() {
        val only = listOf("whisper-1", "tts-1")
        assertEquals(only, ModelCatalog.chatModels(only))
    }

    @Test
    fun `i modelli di servizio restano disponibili ai ruoli`() {
        val curated = listOf("gpt-4o", "whisper-1", "tts-1", "gpt-image-1")
        val merged = ModelCatalog.merge(
            curated = curated,
            fetched = curated + listOf("text-embedding-3-small"),
        )
        assertTrue(merged.containsAll(curated))
        assertEquals(listOf("gpt-image-1"), ModelCatalog.imageModels(merged))
        assertEquals(listOf("whisper-1"), ModelCatalog.transcribeModels(merged))
        // Nella chat resta solo quello con cui si può parlare.
        assertEquals(listOf("gpt-4o"), ModelCatalog.chatModels(merged))
    }
}

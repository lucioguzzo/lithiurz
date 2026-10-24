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
}

package com.lucio.guzzobot

import com.lucio.guzzobot.data.agent.ImageTools
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageToolsTest {

    @Test
    fun `lo strumento si dichiara come l'API si aspetta`() {
        val definition = ImageTools.definition()
        assertEquals("function", definition["type"]?.jsonPrimitive?.content)
        val function = definition["function"]!!.jsonObject
        assertEquals(ImageTools.CREATE, function["name"]?.jsonPrimitive?.content)
        val parameters = function["parameters"]!!.jsonObject
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
        assertTrue(parameters["properties"]!!.jsonObject.containsKey("prompt"))
        assertEquals(
            listOf("prompt"),
            parameters["required"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `il nome non collide con gli strumenti dei connettori`() {
        // Quelli dei connettori portano tutti il prefisso mcp_.
        assertTrue(ImageTools.CREATE.all { it.isLetterOrDigit() || it == '_' })
        assertTrue(!ImageTools.CREATE.startsWith("mcp_"))
    }

    @Test
    fun `la descrizione dice cosa sta per succedere, senza dilungarsi`() {
        val lungo = "una ".repeat(200)
        val descritto = ImageTools.describe(lungo)
        assertTrue(descritto.startsWith("Creo un'immagine:"))
        assertTrue(descritto.length < 120)
    }
}

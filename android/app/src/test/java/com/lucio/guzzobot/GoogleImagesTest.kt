package com.lucio.guzzobot

import com.lucio.guzzobot.data.remote.GoogleImages
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleImagesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val bytes = "R".repeat(200)
    private val picture = GoogleImages.Picture(base64 = bytes, mimeType = "image/png")

    @Test
    fun `dalla base compatibile si ricava quella nativa`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            GoogleImages.nativeBase("https://generativelanguage.googleapis.com/v1beta/openai/"),
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            GoogleImages.nativeBase("https://generativelanguage.googleapis.com/v1beta/openai"),
        )
        // Una base già nativa resta com'è.
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            GoogleImages.nativeBase("https://generativelanguage.googleapis.com/v1beta/"),
        )
    }

    @Test
    fun `la richiesta porta il testo e le immagini nella stessa lista`() {
        val body = GoogleImages.interactionsBody("m", "falla di notte", listOf(picture), "image/jpeg")
        val input = body["input"]!!.jsonArray
        assertEquals("text", input[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("falla di notte", input[0].jsonObject["text"]?.jsonPrimitive?.content)
        assertEquals("image", input[1].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals("image/png", input[1].jsonObject["mime_type"]?.jsonPrimitive?.content)
        assertEquals(bytes, input[1].jsonObject["data"]?.jsonPrimitive?.content)
        assertEquals("m", body["model"]?.jsonPrimitive?.content)
    }

    @Test
    fun `il ripiego usa la forma a contenuti e parti`() {
        val body = GoogleImages.generateContentBody("prova", listOf(picture))
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("prova", parts[0].jsonObject["text"]?.jsonPrimitive?.content)
        val inline = parts[1].jsonObject["inline_data"]!!.jsonObject
        assertEquals("image/png", inline["mime_type"]?.jsonPrimitive?.content)
        assertEquals(bytes, inline["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun `l'immagine si ritrova nella forma a passi`() {
        val raw = """
            {"steps":[{"type":"model_output","content":[
              {"type":"text","text":"ecco"},
              {"type":"image","mime_type":"image/jpeg","data":"$bytes"}]}]}
        """.trimIndent()
        assertEquals(listOf(bytes), GoogleImages.extractImages(json.parseToJsonElement(raw)))
    }

    @Test
    fun `l'immagine si ritrova anche nella forma a candidati`() {
        val raw = """
            {"candidates":[{"content":{"parts":[
              {"text":"fatto"},
              {"inline_data":{"mime_type":"image/png","data":"$bytes"}}]}}]}
        """.trimIndent()
        assertEquals(listOf(bytes), GoogleImages.extractImages(json.parseToJsonElement(raw)))
    }

    @Test
    fun `e nella forma abbreviata, senza duplicare`() {
        val raw = """
            {"output_image":{"mime_type":"image/jpeg","data":"$bytes"},
             "steps":[{"content":[{"type":"image","mimeType":"image/jpeg","data":"$bytes"}]}]}
        """.trimIndent()
        // La stessa immagine compare due volte nell'involucro: una sola volta qui.
        assertEquals(listOf(bytes), GoogleImages.extractImages(json.parseToJsonElement(raw)))
    }

    @Test
    fun `un blocco che non e' un'immagine non viene scambiato per tale`() {
        val raw = """
            {"steps":[{"type":"model_output","content":[
              {"type":"text","data":"${"t".repeat(200)}"},
              {"type":"thought","mime_type":"text/plain","data":"${"p".repeat(200)}"}]}]}
        """.trimIndent()
        assertTrue(GoogleImages.extractImages(json.parseToJsonElement(raw)).isEmpty())
    }

    @Test
    fun `una risposta vuota o di errore non produce immagini`() {
        assertTrue(GoogleImages.extractImages(json.parseToJsonElement("{}")).isEmpty())
        val error = """{"error":{"code":400,"message":"non va","status":"INVALID_ARGUMENT"}}"""
        assertTrue(GoogleImages.extractImages(json.parseToJsonElement(error)).isEmpty())
        // Un "data" troppo corto per essere un'immagine viene ignorato.
        val tiny = """{"content":[{"type":"image","data":"abc"}]}"""
        assertTrue(GoogleImages.extractImages(json.parseToJsonElement(tiny)).isEmpty())
    }
}

package com.lucio.guzzobot

import com.lucio.guzzobot.data.files.TextExtractor
import com.lucio.guzzobot.data.model.ModelCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextExtractorTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `riconosce i formati supportati`() {
        assertTrue(TextExtractor.isSupported("application/pdf", "contratto.pdf"))
        assertTrue(TextExtractor.isSupported("application/octet-stream", "note.md"))
        assertTrue(TextExtractor.isSupported("text/csv", "dati.csv"))
        assertTrue(TextExtractor.isSupported("application/octet-stream", "App.kt"))
        assertFalse(TextExtractor.isSupported("application/zip", "archivio.zip"))
        assertFalse(TextExtractor.isSupported("video/mp4", "clip.mp4"))
    }

    @Test
    fun `legge un file di testo`() {
        val file = folder.newFile("appunti.txt").apply { writeText("  ciao mondo  ") }
        assertEquals("ciao mondo", TextExtractor.extract(file, "text/plain", file.name))
    }

    @Test
    fun `estrae il testo da un docx`() {
        val file = folder.newFile("lettera.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(
                """
                <w:document><w:body>
                <w:p><w:r><w:t>Primo paragrafo</w:t></w:r></w:p>
                <w:p><w:r><w:t>Secondo &amp; ultimo</w:t></w:r></w:p>
                </w:body></w:document>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        val text = TextExtractor.extract(
            file,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            file.name,
        )
        assertTrue(text.contains("Primo paragrafo"))
        assertTrue(text.contains("Secondo & ultimo"))
    }

    @Test
    fun `tronca i documenti troppo lunghi`() {
        val file = folder.newFile("lungo.txt").apply { writeText("a".repeat(TextExtractor.MAX_CHARS + 500)) }
        val text = TextExtractor.extract(file, "text/plain", file.name)
        assertTrue(text.length < TextExtractor.MAX_CHARS + 100)
        assertTrue(text.endsWith("[documento troncato]"))
    }

    @Test
    fun `distingue i modelli che vedono le immagini`() {
        assertTrue(ModelCapabilities.seesImages("gpt-4o"))
        assertTrue(ModelCapabilities.seesImages("qwen/qwen2.5-vl-72b-instruct"))
        assertTrue(ModelCapabilities.seesImages("mistralai/pixtral-large-2411"))
        assertFalse(ModelCapabilities.seesImages("deepseek-chat"))
        assertFalse(ModelCapabilities.seesImages("deepseek-reasoner"))
        assertFalse(ModelCapabilities.seesImages("llama-3.3-70b-versatile"))
    }
}

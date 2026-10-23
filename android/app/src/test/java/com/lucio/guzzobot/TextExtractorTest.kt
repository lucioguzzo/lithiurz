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

    @Test
    fun `legge le celle di un xlsx`() {
        val file = folder.newFile("vendite.xlsx")
        zip(
            file,
            "xl/sharedStrings.xml" to
                "<sst><si><t>Nome</t></si><si><t>Totale</t></si><si><t>Mario</t></si></sst>",
            "xl/workbook.xml" to
                "<workbook><sheets><sheet name=\"Vendite\" sheetId=\"1\"/></sheets></workbook>",
            "xl/worksheets/sheet1.xml" to """
                <worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>1500</v></c></row>
                </sheetData></worksheet>
            """.trimIndent(),
        )
        val text = TextExtractor.extract(
            file,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            file.name,
        )
        assertTrue(text.contains("— Vendite —"))
        assertTrue(text.contains("Nome\tTotale"))
        assertTrue(text.contains("Mario\t1500"))
    }

    @Test
    fun `legge le slide e le note di un pptx`() {
        val file = folder.newFile("presentazione.pptx")
        zip(
            file,
            "ppt/slides/slide1.xml" to "<p:sld><a:t>Titolo</a:t><a:t>Punto uno</a:t></p:sld>",
            "ppt/notesSlides/notesSlide1.xml" to "<p:notes><a:t>Ricorda i numeri</a:t></p:notes>",
            "ppt/slides/slide2.xml" to "<p:sld><a:t>Seconda slide</a:t></p:sld>",
        )
        val text = TextExtractor.extract(
            file,
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            file.name,
        )
        assertTrue(text.contains("— slide 1 —"))
        assertTrue(text.contains("Punto uno"))
        assertTrue(text.contains("[note del relatore]"))
        assertTrue(text.contains("Ricorda i numeri"))
        assertTrue(text.indexOf("— slide 1 —") < text.indexOf("— slide 2 —"))
    }

    @Test
    fun `riconosce i formati Office`() {
        assertTrue(TextExtractor.isSupported("application/octet-stream", "bilancio.xlsx"))
        assertTrue(TextExtractor.isSupported("application/octet-stream", "pitch.pptx"))
    }

    private fun zip(target: java.io.File, vararg entries: Pair<String, String>) {
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
    }
}

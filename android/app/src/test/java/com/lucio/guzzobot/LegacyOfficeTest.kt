package com.lucio.guzzobot

import com.lucio.guzzobot.data.files.TextExtractor
import com.lucio.guzzobot.data.files.office.CompoundFile
import com.lucio.guzzobot.data.files.office.LegacyOffice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * I file di prova sono veri .doc/.xls/.ppt prodotti dai filtri "MS Office 97"
 * di LibreOffice, non costruiti a mano: così il parser viene messo alla prova
 * sul formato reale.
 */
class LegacyOfficeTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun fixture(name: String): File {
        val bytes = javaClass.getResourceAsStream("/$name")?.readBytes()
            ?: error("manca la risorsa $name")
        return folder.newFile(name).apply { writeBytes(bytes) }
    }

    @Test
    fun `apre il contenitore OLE2 e trova i flussi`() {
        val container = CompoundFile.open(fixture("foglio.xls"))!!
        assertTrue("Workbook" in container.streamNames)
        assertTrue((container.stream("Workbook")?.size ?: 0) > 500)
    }

    @Test
    fun `legge il testo di un doc`() {
        val text = LegacyOffice.extract(fixture("documento.doc"))
        assertTrue(text, text.contains("Relazione annuale"))
        assertTrue(text, text.contains("1.500 euro"))
        assertTrue(text, text.contains("perché è così"))
    }

    @Test
    fun `legge celle e fogli di un xls`() {
        val text = LegacyOffice.extract(fixture("foglio.xls"))
        assertTrue(text, text.contains("— Vendite —"))
        assertTrue(text, text.contains("Nome\tTotale\tSconto"))
        assertTrue(text, text.contains("Mario Rossi\t1500"))
        assertTrue(text, text.contains("Anna Bianchi\t2750.5"))
        assertTrue(text, text.contains("— Note —"))
        assertTrue(text, text.contains("chiudere il trimestre"))
    }

    @Test
    fun `legge le slide di un ppt con le note, senza i master`() {
        val text = LegacyOffice.extract(fixture("presentazione.ppt"))
        assertTrue(text, text.contains("— slide 1 —"))
        assertTrue(text, text.contains("Piano del trimestre"))
        assertTrue(text, text.contains("Primo punto"))
        assertTrue(text, text.contains("Secondo punto"))
        assertTrue(text, text.contains("[note del relatore]"))
        assertTrue(text, text.contains("Ricorda i numeri di marzo"))
        assertTrue(text, text.contains("— slide 2 —"))
        assertTrue(text, text.contains("Conclusioni"))
        // I segnaposto dei master non sono contenuto della presentazione.
        assertFalse(text, text.contains("Click to edit"))
        assertFalse(text, text.lines().any { it.trim() == "*" })
        assertTrue(text, text.indexOf("Piano del trimestre") < text.indexOf("Conclusioni"))
    }

    @Test
    fun `i vecchi formati passano da TextExtractor`() {
        assertTrue(TextExtractor.isSupported("application/msword", "lettera.doc"))
        assertTrue(TextExtractor.isSupported("application/octet-stream", "conti.xls"))
        assertTrue(TextExtractor.isSupported("application/vnd.ms-powerpoint", "pitch.ppt"))
        val file = fixture("documento.doc")
        assertTrue(TextExtractor.extract(file, "application/msword", file.name).isNotBlank())
    }

    @Test
    fun `un file che non e OLE2 non viene aperto`() {
        val fake = folder.newFile("finto.doc").apply { writeText("solo testo") }
        assertEquals("", LegacyOffice.extract(fake))
    }
}

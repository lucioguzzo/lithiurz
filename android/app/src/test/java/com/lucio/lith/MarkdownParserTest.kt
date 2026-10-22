package com.lucio.lith

import com.lucio.lith.ui.markdown.MdBlock
import com.lucio.lith.ui.markdown.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `titoli e paragrafi`() {
        val blocks = parseMarkdown("# Titolo\n\nUn paragrafo\nsu due righe.")
        assertEquals(2, blocks.size)
        assertEquals(MdBlock.Heading(1, "Titolo"), blocks[0])
        assertEquals(MdBlock.Paragraph("Un paragrafo\nsu due righe."), blocks[1])
    }

    @Test
    fun `blocco di codice con linguaggio`() {
        val blocks = parseMarkdown("```kotlin\nval x = 1\n```")
        assertEquals(listOf(MdBlock.Code("kotlin", "val x = 1")), blocks)
    }

    @Test
    fun `blocco di codice ancora aperto durante lo streaming`() {
        val blocks = parseMarkdown("Ecco:\n```python\nprint(1)")
        assertEquals(MdBlock.Paragraph("Ecco:"), blocks[0])
        assertEquals(MdBlock.Code("python", "print(1)"), blocks[1])
    }

    @Test
    fun `elenchi puntati e numerati`() {
        val blocks = parseMarkdown("- uno\n- due\n\n1. primo\n2. secondo")
        val bullets = blocks[0] as MdBlock.Listing
        assertEquals(listOf("uno", "due"), bullets.items.map { it.text })
        assertTrue(bullets.items.all { it.marker == null })
        val ordered = blocks[1] as MdBlock.Listing
        assertEquals(listOf("1.", "2."), ordered.items.map { it.marker })
    }

    @Test
    fun `citazione e riga orizzontale`() {
        val blocks = parseMarkdown("> nota\n> su due righe\n\n---")
        assertEquals(MdBlock.Quote("nota\nsu due righe"), blocks[0])
        assertEquals(MdBlock.Rule, blocks[1])
    }

    @Test
    fun `i cancelletti dentro al codice non diventano titoli`() {
        val blocks = parseMarkdown("```sh\n# commento\n```")
        assertEquals(listOf(MdBlock.Code("sh", "# commento")), blocks)
    }
}

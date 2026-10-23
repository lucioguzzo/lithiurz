package com.lucio.guzzobot

import com.lucio.guzzobot.data.files.office.WordDocExtractor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Word non salva il testo in ordine: la sequenza vera sta nella piece table del
 * flusso Table. Qui il documento ha due pezzi scritti al contrario, uno a un
 * byte e uno UTF-16, così leggere il flusso dall'inizio darebbe l'ordine
 * sbagliato e la codifica sbagliata.
 */
class WordPieceTableTest {

    private val textStart = 0x200
    private val secondPiece = 0x200 // "MONDO", un byte per carattere
    private val firstPiece = 0x210 // "CIAO ", UTF-16LE

    @Test
    fun `i pezzi vengono rimessi in ordine`() {
        val document = buildDocument()
        val table = buildTable()
        assertEquals("CIAO MONDO", WordDocExtractor.extract(document, null, table))
    }

    @Test
    fun `senza tabella si ripiega sul testo grezzo`() {
        val fallback = WordDocExtractor.extract(buildDocument(), null, null)
        // Il ripiego legge in ordine di flusso: comincia dal pezzo sbagliato.
        assertEquals(true, fallback.startsWith("MONDO"))
    }

    private fun buildDocument(): ByteArray {
        val data = ByteArray(0x240)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(0, 0xA5EC.toShort()) // wIdent
        buffer.putShort(0x0A, (1 shl 9).toShort()) // fWhichTblStm: usa 1Table
        buffer.putInt(0x18, textStart) // fcMin
        buffer.putInt(0x1C, textStart + 5) // fcMac
        buffer.putInt(0x01A2, 0) // fcClx: il CLX è all'inizio del flusso Table
        buffer.putInt(0x01A6, 33) // lcbClx
        "MONDO".forEachIndexed { index, char -> data[secondPiece + index] = char.code.toByte() }
        "CIAO ".forEachIndexed { index, char ->
            data[firstPiece + index * 2] = char.code.toByte()
            data[firstPiece + index * 2 + 1] = 0
        }
        return data
    }

    private fun buildTable(): ByteArray {
        val data = ByteArray(64)
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        data[0] = 2 // clxt: tabella dei pezzi
        buffer.putInt(1, 28) // dimensione del PlcPcd: 3 CP + 2 descrittori
        buffer.putInt(5, 0) // CP del primo pezzo
        buffer.putInt(9, 5)
        buffer.putInt(13, 10)
        // Primo pezzo: UTF-16, nessun bit di compressione.
        buffer.putInt(17 + 2, firstPiece)
        // Secondo pezzo: un byte per carattere, bit 0x40000000 e offset doppio.
        buffer.putInt(25 + 2, 0x40000000 or (secondPiece * 2))
        return data
    }
}

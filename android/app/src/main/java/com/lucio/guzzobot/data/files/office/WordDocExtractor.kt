package com.lucio.guzzobot.data.files.office

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Testo di un .doc (Word 97-2003). Il flusso WordDocument contiene i caratteri,
 * ma l'ordine vero sta nella "piece table" del flusso Table: Word salva in modo
 * incrementale, quindi leggere il flusso dall'inizio alla fine non basta.
 */
object WordDocExtractor {

    private const val IDENT = 0xA5EC
    private const val FC_CLX = 0x01A2
    private const val LCB_CLX = 0x01A6

    fun extract(wordDocument: ByteArray, table0: ByteArray?, table1: ByteArray?): String {
        if (wordDocument.size < 0x0200) return ""
        val fib = ByteBuffer.wrap(wordDocument).order(ByteOrder.LITTLE_ENDIAN)
        if ((fib.getShort(0).toInt() and 0xFFFF) != IDENT) return ""
        val useTable1 = ((fib.getShort(0x0A).toInt() shr 9) and 1) == 1
        val table = (if (useTable1) table1 else table0) ?: table1 ?: table0

        val pieces = table?.let { fromPieceTable(wordDocument, it, fib) }?.takeIf { it.isNotBlank() }
        if (pieces != null) return pieces

        // Ripiego: il testo "principale" senza piece table, da fcMin a fcMac.
        val from = fib.getInt(0x18).coerceAtLeast(0)
        val to = fib.getInt(0x1C).coerceAtMost(wordDocument.size)
        if (to <= from) return ""
        return clean(wordDocument.decodeCp1252(from, to - from))
    }

    private fun fromPieceTable(
        document: ByteArray,
        table: ByteArray,
        fib: ByteBuffer,
    ): String = runCatching {
        val clxStart = fib.getInt(FC_CLX)
        val clxLength = fib.getInt(LCB_CLX)
        if (clxStart < 0 || clxLength <= 0 || clxStart + clxLength > table.size) return@runCatching ""
        val buffer = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)

        // Il CLX è una sequenza di blocchi di proprietà (clxt 1) chiusa dalla
        // tabella dei pezzi (clxt 2).
        var position = clxStart
        val limit = clxStart + clxLength
        var pieceTable = -1
        var pieceLength = 0
        while (position < limit) {
            when (table[position].toInt() and 0xFF) {
                1 -> {
                    val size = buffer.getShort(position + 1).toInt() and 0xFFFF
                    position += 3 + size
                }

                2 -> {
                    pieceLength = buffer.getInt(position + 1)
                    pieceTable = position + 5
                    position = limit
                }

                else -> position = limit
            }
        }
        if (pieceTable < 0 || pieceLength <= 4) return@runCatching ""

        val count = (pieceLength - 4) / 12
        if (count <= 0) return@runCatching ""
        val descriptors = pieceTable + 4 * (count + 1)
        val text = StringBuilder()
        for (index in 0 until count) {
            val start = buffer.getInt(pieceTable + index * 4)
            val end = buffer.getInt(pieceTable + (index + 1) * 4)
            val chars = end - start
            if (chars <= 0) continue
            val fc = buffer.getInt(descriptors + index * 8 + 2)
            val compressed = (fc and 0x40000000) != 0
            val offset = if (compressed) (fc and 0x3FFFFFFF) / 2 else (fc and 0x3FFFFFFF)
            if (offset < 0 || offset >= document.size) continue
            text.append(
                if (compressed) {
                    document.decodeCp1252(offset, chars)
                } else {
                    val length = (chars * 2).coerceAtMost(document.size - offset)
                    String(document, offset, length, Charsets.UTF_16LE)
                },
            )
        }
        clean(text.toString())
    }.getOrDefault("")

    /**
     * Word usa caratteri di controllo per fine cella, codici di campo e simili:
     * qui diventano testo leggibile o sparscono.
     */
    private fun clean(raw: String): String {
        val out = StringBuilder(raw.length)
        var skippingField = false
        raw.forEach { char ->
            when (char.code) {
                0x13 -> skippingField = true // inizio istruzione di campo
                0x14, 0x15 -> skippingField = false // risultato del campo / fine
                else -> if (!skippingField) {
                    when (char.code) {
                        0x0D, 0x0B, 0x0C -> out.append('\n')
                        0x07 -> out.append('\t') // fine cella di tabella
                        0x1E -> out.append('-')
                        0x09, 0x0A -> out.append(char)
                        0x01, 0x02, 0x05, 0x08, 0x1F, 0x00 -> Unit
                        else -> if (char.code >= 0x20) out.append(char)
                    }
                }
            }
        }
        return out.toString()
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}

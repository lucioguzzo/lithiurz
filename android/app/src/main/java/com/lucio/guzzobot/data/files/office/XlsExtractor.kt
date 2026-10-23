package com.lucio.guzzobot.data.files.office

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Testo di un .xls (Excel 97-2003, formato BIFF8). Il flusso Workbook è una
 * sequenza di record [tipo, lunghezza, dati]: i nomi dei fogli stanno nei
 * BOUNDSHEET, le stringhe in una tabella condivisa (SST) e le celle nei record
 * LABELSST, LABEL, RK, NUMBER e FORMULA.
 */
object XlsExtractor {

    private const val BOF = 0x0809
    private const val EOF_RECORD = 0x000A
    private const val BOUNDSHEET = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABELSST = 0x00FD
    private const val LABEL = 0x0204
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val NUMBER = 0x0203
    private const val BOOLERR = 0x0205
    private const val FORMULA = 0x0006
    private const val STRING_RECORD = 0x0207
    private const val SHEET_TYPE_WORKSHEET = 0x0010

    private data class Record(val type: Int, val data: ByteArray)

    fun extract(workbook: ByteArray): String {
        val records = readRecords(workbook)
        if (records.isEmpty()) return ""

        val sheetNames = records.filter { it.type == BOUNDSHEET }.map { boundSheetName(it.data) }
        val shared = sharedStrings(records)

        val sheets = mutableListOf<Pair<String, MutableMap<Int, MutableMap<Int, String>>>>()
        var current: MutableMap<Int, MutableMap<Int, String>>? = null
        var sheetIndex = -1
        var pendingString: Pair<Int, Int>? = null

        records.forEach { record ->
            val data = record.data
            when (record.type) {
                BOF -> {
                    val kind = if (data.size >= 4) u16(data, 2) else 0
                    if (kind == SHEET_TYPE_WORKSHEET) {
                        sheetIndex++
                        current = mutableMapOf()
                        val name = sheetNames.getOrNull(sheetIndex) ?: "Foglio ${sheetIndex + 1}"
                        sheets += name to current!!
                    }
                }

                EOF_RECORD -> current = null

                LABELSST -> if (data.size >= 10) {
                    val index = u32(data, 6).toInt()
                    put(current, data, shared.getOrNull(index).orEmpty())
                }

                LABEL -> if (data.size >= 9) {
                    put(current, data, unicodeString(data, 6))
                }

                RK -> if (data.size >= 10) {
                    put(current, data, number(rkValue(u32(data, 6))))
                }

                MULRK -> if (data.size >= 6) {
                    val row = u16(data, 0)
                    val firstColumn = u16(data, 2)
                    var offset = 4
                    var column = firstColumn
                    while (offset + 6 <= data.size - 2) {
                        put(current, row, column, number(rkValue(u32(data, offset + 2))))
                        offset += 6
                        column++
                    }
                }

                NUMBER -> if (data.size >= 14) {
                    put(current, data, number(Double.fromBits(u64(data, 6))))
                }

                BOOLERR -> if (data.size >= 8) {
                    val value = data[6].toInt() and 0xFF
                    val isError = (data[7].toInt() and 0xFF) == 1
                    put(current, data, if (isError) "#ERR" else if (value != 0) "VERO" else "FALSO")
                }

                FORMULA -> if (data.size >= 14) {
                    // Il valore in cache è un double, oppure un marcatore: in quel
                    // caso la stringa arriva col record successivo.
                    val isSpecial = (data[12].toInt() and 0xFF) == 0xFF &&
                        (data[13].toInt() and 0xFF) == 0xFF
                    if (!isSpecial) {
                        put(current, data, number(Double.fromBits(u64(data, 6))))
                    } else {
                        when (data[6].toInt() and 0xFF) {
                            0 -> pendingString = u16(data, 0) to u16(data, 2)
                            1 -> put(
                                current,
                                data,
                                if ((data[8].toInt() and 0xFF) != 0) "VERO" else "FALSO",
                            )
                            2 -> put(current, data, "#ERR")
                        }
                    }
                }

                STRING_RECORD -> {
                    val target = pendingString
                    if (target != null && data.size >= 3) {
                        put(current, target.first, target.second, unicodeString(data, 0))
                        pendingString = null
                    }
                }
            }
        }

        return sheets
            .filter { (_, rows) -> rows.isNotEmpty() }
            .joinToString("\n\n") { (name, rows) ->
                val body = rows.toSortedMap().values.joinToString("\n") { cells ->
                    cells.toSortedMap().values.joinToString("\t").trimEnd('\t')
                }
                "— $name —\n$body"
            }
            .trim()
    }

    private fun put(
        sheet: MutableMap<Int, MutableMap<Int, String>>?,
        data: ByteArray,
        value: String,
    ) = put(sheet, u16(data, 0), u16(data, 2), value)

    private fun put(
        sheet: MutableMap<Int, MutableMap<Int, String>>?,
        row: Int,
        column: Int,
        value: String,
    ) {
        if (sheet == null || value.isBlank()) return
        sheet.getOrPut(row) { mutableMapOf() }[column] = value
    }

    private fun readRecords(data: ByteArray): List<Record> {
        val records = mutableListOf<Record>()
        var position = 0
        while (position + 4 <= data.size) {
            val type = u16(data, position)
            val length = u16(data, position + 2)
            val from = position + 4
            if (from + length > data.size) break
            records += Record(type, data.copyOfRange(from, from + length))
            position = from + length
            if (type == 0 && length == 0) break
        }
        return records
    }

    private fun boundSheetName(data: ByteArray): String {
        if (data.size < 8) return ""
        val chars = data[6].toInt() and 0xFF
        val wide = (data[7].toInt() and 1) == 1
        return if (wide) {
            String(data, 8, (chars * 2).coerceAtMost(data.size - 8), Charsets.UTF_16LE)
        } else {
            data.decodeCp1252(8, chars.coerceAtMost(data.size - 8))
        }
    }

    /** XLUnicodeString: lunghezza su 2 byte, un byte di opzioni, poi i caratteri. */
    private fun unicodeString(data: ByteArray, offset: Int): String {
        if (offset + 3 > data.size) return ""
        val chars = u16(data, offset)
        val wide = (data[offset + 2].toInt() and 1) == 1
        val start = offset + 3
        return if (wide) {
            String(data, start, (chars * 2).coerceAtMost(data.size - start), Charsets.UTF_16LE)
        } else {
            data.decodeCp1252(start, chars.coerceAtMost(data.size - start))
        }
    }

    /**
     * La tabella delle stringhe può continuare nei record CONTINUE, e una
     * stringa spezzata riparte con un byte di opzioni: va letta a blocchi.
     */
    private fun sharedStrings(records: List<Record>): List<String> {
        val start = records.indexOfFirst { it.type == SST }
        if (start < 0) return emptyList()
        val blocks = mutableListOf(records[start].data)
        var index = start + 1
        while (index < records.size && records[index].type == CONTINUE) {
            blocks += records[index].data
            index++
        }
        val reader = BlockReader(blocks)
        if (!reader.skip(8)) return emptyList()
        val unique = u32(blocks[0], 4).toInt().coerceIn(0, 1_000_000)
        val strings = ArrayList<String>(minOf(unique, 10_000))
        repeat(unique) {
            if (!reader.hasMore()) return@repeat
            val chars = reader.u16()
            val flags = reader.u8()
            val rich = (flags and 0x08) != 0
            val extended = (flags and 0x04) != 0
            val runs = if (rich) reader.u16() else 0
            val extendedSize = if (extended) reader.u32().toInt() else 0
            strings += reader.chars(chars, compressed = (flags and 0x01) == 0)
            if (runs > 0) reader.skip(runs * 4)
            if (extendedSize > 0) reader.skip(extendedSize)
        }
        return strings
    }

    /** Un numero RK sta in 4 byte: intero o double troncato, eventualmente /100. */
    private fun rkValue(raw: Long): Double {
        val bits = raw.toInt()
        val value = if ((bits and 0x02) != 0) {
            (bits shr 2).toDouble()
        } else {
            Double.fromBits((raw and 0xFFFFFFFCL) shl 32)
        }
        return if ((bits and 0x01) != 0) value / 100.0 else value
    }

    private fun number(value: Double): String = when {
        value.isNaN() || value.isInfinite() -> ""
        value == Math.rint(value) && abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        if (offset + 2 > data.size) {
            0
        } else {
            (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
        }

    private fun u32(data: ByteArray, offset: Int): Long =
        if (offset + 4 > data.size) {
            0L
        } else {
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt(offset).toLong() and 0xFFFFFFFFL
        }

    private fun u64(data: ByteArray, offset: Int): Long =
        if (offset + 8 > data.size) {
            0L
        } else {
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(offset)
        }

    /** Lettore su più record consecutivi, consapevole dei confini. */
    private class BlockReader(private val blocks: List<ByteArray>) {
        private var block = 0
        private var offset = 0

        fun hasMore(): Boolean {
            align()
            return block < blocks.size
        }

        private fun align() {
            while (block < blocks.size && offset >= blocks[block].size) {
                block++
                offset = 0
            }
        }

        private fun hasData(): Boolean = block < blocks.size && offset < blocks[block].size

        fun u8(): Int {
            align()
            if (block >= blocks.size) return 0
            return blocks[block][offset++].toInt() and 0xFF
        }

        fun u16(): Int = u8() or (u8() shl 8)

        fun u32(): Long = (u16().toLong()) or (u16().toLong() shl 16)

        fun skip(count: Int): Boolean {
            var remaining = count
            while (remaining > 0) {
                align()
                if (block >= blocks.size) return false
                val available = blocks[block].size - offset
                val step = minOf(available, remaining)
                offset += step
                remaining -= step
            }
            return true
        }

        /**
         * Legge [count] caratteri: se la stringa prosegue nel record successivo,
         * quel record inizia con un nuovo byte di opzioni.
         */
        fun chars(count: Int, compressed: Boolean): String {
            var wide = !compressed
            val out = StringBuilder(count.coerceAtMost(4096))
            var remaining = count
            while (remaining > 0) {
                if (!hasData()) {
                    align()
                    if (block >= blocks.size) break
                    wide = (u8() and 0x01) == 1
                }
                out.append(
                    if (wide) {
                        val low = u8()
                        val high = u8()
                        ((high shl 8) or low).toChar()
                    } else {
                        val value = u8()
                        ByteArray(1) { value.toByte() }.decodeCp1252()[0]
                    },
                )
                remaining--
            }
            return out.toString()
        }
    }
}

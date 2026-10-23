package com.lucio.guzzobot.data.files.office

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lettore minimo del Compound File Binary Format (OLE2): il contenitore dei
 * vecchi formati Office .doc, .xls e .ppt, che non sono zip ma un piccolo file
 * system con settori, tabella di allocazione e directory.
 * Serve solo a tirare fuori i flussi che ci interessano.
 */
class CompoundFile private constructor(
    private val data: ByteArray,
    private val sectorSize: Int,
    private val miniCutoff: Int,
    private val fat: IntArray,
    private val miniFat: IntArray,
    private val entries: List<Entry>,
    private val miniStream: ByteArray,
) {

    data class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    val streamNames: List<String> get() = entries.filter { it.type == TYPE_STREAM }.map { it.name }

    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == TYPE_STREAM && it.name == name } ?: return null
        val size = entry.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (size <= 0) return ByteArray(0)
        return if (entry.size < miniCutoff) {
            readChain(miniStream, miniFat, entry.start, MINI_SECTOR, size)
        } else {
            readChain(data, fat, entry.start, sectorSize, size, headerOffset = true)
        }
    }

    private fun readChain(
        source: ByteArray,
        table: IntArray,
        start: Int,
        unit: Int,
        size: Int,
        headerOffset: Boolean = false,
    ): ByteArray {
        val out = ByteArray(size)
        var written = 0
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < table.size && written < size && guard++ < MAX_SECTORS) {
            val offset = if (headerOffset) (sector + 1) * unit else sector * unit
            if (offset < 0 || offset >= source.size) break
            val length = minOf(unit, size - written, source.size - offset)
            System.arraycopy(source, offset, out, written, length)
            written += length
            sector = table[sector]
        }
        return if (written == size) out else out.copyOf(written)
    }

    companion object {
        private const val TYPE_STREAM = 2
        private const val TYPE_ROOT = 5
        private const val MINI_SECTOR = 64
        private const val END_OF_CHAIN = -2 // 0xFFFFFFFE
        private const val MAX_SECTORS = 1 shl 22

        private val SIGNATURE = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
        )

        fun looksLikeCompoundFile(file: File): Boolean = runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(8)
                input.read(head) == 8 && head.contentEquals(SIGNATURE)
            }
        }.getOrDefault(false)

        fun open(file: File): CompoundFile? = runCatching { parse(file.readBytes()) }.getOrNull()

        fun parse(data: ByteArray): CompoundFile? {
            if (data.size < 512) return null
            if (!data.copyOfRange(0, 8).contentEquals(SIGNATURE)) return null
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val sectorSize = 1 shl buffer.getShort(0x1E).toInt()
            if (sectorSize < 128 || sectorSize > 1 shl 16) return null
            val miniCutoff = buffer.getInt(0x38).takeIf { it > 0 } ?: 4096
            val fatSectorCount = buffer.getInt(0x2C)
            val firstDirSector = buffer.getInt(0x30)
            val firstMiniFat = buffer.getInt(0x3C)
            val miniFatCount = buffer.getInt(0x40)
            val firstDifat = buffer.getInt(0x44)
            val difatCount = buffer.getInt(0x48)

            // 1. Elenco dei settori che contengono la tabella di allocazione.
            val fatSectors = ArrayList<Int>(fatSectorCount.coerceAtMost(1 shl 20))
            for (index in 0 until 109) {
                if (fatSectors.size >= fatSectorCount) break
                val sector = buffer.getInt(0x4C + index * 4)
                if (sector < 0) break
                fatSectors += sector
            }
            var difat = firstDifat
            var difatGuard = 0
            val perDifat = sectorSize / 4 - 1
            while (difat >= 0 && fatSectors.size < fatSectorCount && difatGuard++ < difatCount + 8) {
                val base = (difat + 1) * sectorSize
                if (base + sectorSize > data.size) break
                for (index in 0 until perDifat) {
                    if (fatSectors.size >= fatSectorCount) break
                    val sector = buffer.getInt(base + index * 4)
                    if (sector < 0) continue
                    fatSectors += sector
                }
                difat = buffer.getInt(base + perDifat * 4)
            }

            // 2. La tabella stessa, un intero per settore del file.
            val fat = IntArray(fatSectors.size * (sectorSize / 4)) { END_OF_CHAIN }
            fatSectors.forEachIndexed { sectorIndex, sector ->
                val base = (sector + 1) * sectorSize
                if (base + sectorSize <= data.size) {
                    for (index in 0 until sectorSize / 4) {
                        fat[sectorIndex * (sectorSize / 4) + index] = buffer.getInt(base + index * 4)
                    }
                }
            }

            // 3. La mini tabella, per i flussi più piccoli del limite.
            val miniFat = IntArray(miniFatCount * (sectorSize / 4)) { END_OF_CHAIN }
            var miniSector = firstMiniFat
            var miniIndex = 0
            var miniGuard = 0
            while (miniSector >= 0 && miniIndex < miniFatCount && miniGuard++ < miniFatCount + 8) {
                val base = (miniSector + 1) * sectorSize
                if (base + sectorSize > data.size) break
                for (index in 0 until sectorSize / 4) {
                    miniFat[miniIndex * (sectorSize / 4) + index] = buffer.getInt(base + index * 4)
                }
                miniIndex++
                miniSector = if (miniSector < fat.size) fat[miniSector] else END_OF_CHAIN
            }

            // 4. La directory: nomi, tipo e posizione di ogni flusso.
            val directory = ArrayList<Byte>()
            var dirSector = firstDirSector
            var dirGuard = 0
            while (dirSector >= 0 && dirGuard++ < MAX_SECTORS) {
                val base = (dirSector + 1) * sectorSize
                if (base + sectorSize > data.size) break
                for (index in 0 until sectorSize) directory += data[base + index]
                dirSector = if (dirSector < fat.size) fat[dirSector] else END_OF_CHAIN
            }
            val dirBytes = directory.toByteArray()
            val dirBuffer = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN)
            val entries = ArrayList<Entry>()
            var root: Entry? = null
            for (offset in 0 until dirBytes.size - 127 step 128) {
                val nameLength = dirBuffer.getShort(offset + 0x40).toInt()
                val type = dirBytes[offset + 0x42].toInt()
                if (type != TYPE_STREAM && type != TYPE_ROOT) continue
                val chars = ((nameLength - 2).coerceAtLeast(0)).coerceAtMost(62)
                val name = String(dirBytes, offset, chars, Charsets.UTF_16LE)
                val entry = Entry(
                    name = name,
                    type = type,
                    start = dirBuffer.getInt(offset + 0x74),
                    size = dirBuffer.getLong(offset + 0x78),
                )
                if (type == TYPE_ROOT) root = entry else entries += entry
            }

            // 5. Il mini stream vive dentro al flusso della radice.
            val empty = CompoundFile(data, sectorSize, miniCutoff, fat, miniFat, emptyList(), ByteArray(0))
            val miniStream = root?.let { entry ->
                val size = entry.size.coerceAtMost(32 * 1024 * 1024).toInt()
                empty.readChain(data, fat, entry.start, sectorSize, size, headerOffset = true)
            } ?: ByteArray(0)

            return CompoundFile(data, sectorSize, miniCutoff, fat, miniFat, entries, miniStream)
        }
    }
}

/** Decodifica una stringa CP1252, la codifica dei vecchi file Office a un byte. */
internal fun ByteArray.decodeCp1252(from: Int = 0, length: Int = size - from): String {
    val end = (from + length).coerceAtMost(size)
    if (from >= end) return ""
    val out = StringBuilder(end - from)
    for (index in from until end) {
        val value = this[index].toInt() and 0xFF
        out.append(
            if (value in 0x80..0x9F) CP1252_HIGH[value - 0x80] else value.toChar(),
        )
    }
    return out.toString()
}

/** I 32 caratteri in cui CP1252 si discosta da Latin-1. */
private val CP1252_HIGH = charArrayOf(
    '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
    'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
    '\u0090', '‘', '’', '“', '”', '•', '–', '—',
    '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
)

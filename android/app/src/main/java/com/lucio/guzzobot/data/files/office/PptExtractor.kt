package com.lucio.guzzobot.data.files.office

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Testo di un .ppt (PowerPoint 97-2003). Il flusso è un albero di record: le
 * slide stanno in container propri, separati dai master (che contengono solo i
 * segnaposto "Click to edit…" e non vanno letti) e dalle note, legate alla loro
 * slide da un identificatore.
 */
object PptExtractor {

    private const val DOCUMENT = 0x03E8
    private const val SLIDE = 0x03EE
    private const val NOTES = 0x03F0
    private const val NOTES_ATOM = 0x03F1
    private const val SLIDE_PERSIST_ATOM = 0x03F3
    private const val SLIDE_LIST_WITH_TEXT = 0x0FF0
    private const val TEXT_CHARS_ATOM = 0x0FA0
    private const val TEXT_BYTES_ATOM = 0x0FA8

    private data class Record(
        val type: Int,
        val instance: Int,
        val container: Boolean,
        val from: Int,
        val to: Int,
    )

    fun extract(stream: ByteArray): String {
        val top = records(stream, 0, stream.size)
        val slides = top.filter { it.type == SLIDE }
        if (slides.isEmpty()) {
            // Nessun container slide: meglio tutto il testo che niente.
            return clean(collectText(stream, 0, stream.size))
        }

        val slideIds = slideIds(stream, top)
        val notes = HashMap<Int, String>()
        top.filter { it.type == NOTES }.forEach { record ->
            val id = notesSlideId(stream, record) ?: return@forEach
            val index = slideIds.indexOf(id)
            if (index >= 0) notes[index] = collectText(stream, record.from, record.to)
        }

        return slides.mapIndexed { index, slide ->
            buildString {
                append("— slide ${index + 1} —\n")
                append(clean(collectText(stream, slide.from, slide.to)))
                notes[index]?.let(::clean)?.takeIf { it.isNotBlank() }?.let { text ->
                    append("\n[note del relatore]\n")
                    append(text)
                }
            }
        }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    /** L'ordine delle slide e il loro identificatore stanno nel container Document. */
    private fun slideIds(stream: ByteArray, top: List<Record>): List<Int> {
        val document = top.firstOrNull { it.type == DOCUMENT } ?: return emptyList()
        val buffer = ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN)
        return records(stream, document.from, document.to)
            .filter { it.type == SLIDE_LIST_WITH_TEXT && it.instance == 0 }
            .flatMap { list -> records(stream, list.from, list.to) }
            .filter { it.type == SLIDE_PERSIST_ATOM && it.to - it.from >= 16 }
            .map { buffer.getInt(it.from + 12) }
    }

    private fun notesSlideId(stream: ByteArray, notes: Record): Int? {
        val atom = records(stream, notes.from, notes.to)
            .firstOrNull { it.type == NOTES_ATOM && it.to - it.from >= 4 } ?: return null
        return ByteBuffer.wrap(stream).order(ByteOrder.LITTLE_ENDIAN).getInt(atom.from)
    }

    private fun records(data: ByteArray, from: Int, to: Int): List<Record> {
        val out = mutableListOf<Record>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var position = from
        while (position + 8 <= to) {
            val info = buffer.getShort(position).toInt() and 0xFFFF
            val type = buffer.getShort(position + 2).toInt() and 0xFFFF
            val length = buffer.getInt(position + 4)
            if (length < 0) break
            val body = position + 8
            val end = (body + length).coerceAtMost(to)
            out += Record(
                type = type,
                instance = (info shr 4) and 0x0FFF,
                container = (info and 0x000F) == 0x000F,
                from = body,
                to = end,
            )
            position = body + length
        }
        return out
    }

    private fun collectText(data: ByteArray, from: Int, to: Int): String {
        val pieces = mutableListOf<String>()
        fun walk(start: Int, end: Int, depth: Int) {
            if (depth > 16) return
            records(data, start, end).forEach { record ->
                when {
                    record.container -> walk(record.from, record.to, depth + 1)
                    record.type == TEXT_CHARS_ATOM -> pieces += String(
                        data,
                        record.from,
                        (record.to - record.from) and 0x7FFFFFFE,
                        Charsets.UTF_16LE,
                    )

                    record.type == TEXT_BYTES_ATOM ->
                        pieces += data.decodeCp1252(record.from, record.to - record.from)
                }
            }
        }
        walk(from, to, 0)
        return pieces.joinToString("\n")
    }

    /** I segnaposto vuoti arrivano come righe con un solo asterisco: via. */
    private fun clean(raw: String): String = raw
        .replace('\r', '\n')
        .replace('\u000B', '\n')
        .lines()
        .map { it.trim() }
        .filterNot { it.isEmpty() || it == "*" }
        .joinToString("\n")
        .trim()
}

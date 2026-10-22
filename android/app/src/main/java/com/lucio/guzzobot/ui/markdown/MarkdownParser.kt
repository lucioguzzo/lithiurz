package com.lucio.guzzobot.ui.markdown

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Listing(val items: List<MdListItem>) : MdBlock
    data object Rule : MdBlock
}

data class MdListItem(
    val text: String,
    val indent: Int,
    val marker: String?,
)

private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val ORDERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])\\1{2,}\\s*$")
private val FENCE = Regex("^\\s*(`{3,}|~{3,})\\s*([A-Za-z0-9+#._-]*)\\s*$")

/**
 * Parser markdown minimale pensato per le risposte di un modello: titoli, blocchi
 * di codice, elenchi, citazioni, righe orizzontali e paragrafi. Tollerante: un
 * blocco di codice ancora aperto (streaming in corso) viene chiuso da solo.
 */
fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val listItems = mutableListOf<MdListItem>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    fun flushList() {
        if (listItems.isNotEmpty()) {
            blocks += MdBlock.Listing(listItems.toList())
            listItems.clear()
        }
    }

    fun flushAll() {
        flushParagraph()
        flushList()
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            flushAll()
            val marker = fence.groupValues[1]
            val language = fence.groupValues[2].takeIf { it.isNotBlank() }
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimEnd().startsWith(marker)) {
                code.appendLine(lines[index])
                index++
            }
            if (index < lines.size) index++ // consuma la chiusura
            blocks += MdBlock.Code(language, code.toString().trimEnd('\n'))
            continue
        }
        when {
            line.isBlank() -> flushAll()

            RULE.matches(line) -> {
                flushAll()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushAll()
                val match = HEADING.matchEntire(line)!!
                blocks += MdBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
            }

            line.trimStart().startsWith("> ") || line.trimStart() == ">" -> {
                flushAll()
                val quote = StringBuilder()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote.appendLine(lines[index].trimStart().removePrefix(">").trim())
                    index++
                }
                blocks += MdBlock.Quote(quote.toString().trim())
                continue
            }

            BULLET.matches(line) -> {
                flushParagraph()
                val match = BULLET.matchEntire(line)!!
                listItems += MdListItem(
                    text = match.groupValues[2].trim(),
                    indent = match.groupValues[1].length / 2,
                    marker = null,
                )
            }

            ORDERED.matches(line) -> {
                flushParagraph()
                val match = ORDERED.matchEntire(line)!!
                listItems += MdListItem(
                    text = match.groupValues[3].trim(),
                    indent = match.groupValues[1].length / 2,
                    marker = match.groupValues[2] + ".",
                )
            }

            else -> {
                flushList()
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line.trim())
            }
        }
        index++
    }
    flushAll()
    return blocks
}

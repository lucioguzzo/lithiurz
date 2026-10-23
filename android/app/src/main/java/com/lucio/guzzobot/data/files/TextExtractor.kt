package com.lucio.guzzobot.data.files

import com.lucio.guzzobot.data.files.office.LegacyOffice
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipFile

/**
 * Estrae il testo dai documenti allegati, sul telefono: niente di tutto questo
 * passa da un servizio esterno finché l'utente non manda il messaggio.
 * I formati Office sono zip di XML, quindi si leggono senza librerie.
 */
object TextExtractor {

    /** Oltre questa soglia il testo viene troncato: nessun modello ha un contesto infinito. */
    const val MAX_CHARS = 120_000

    private val textualExtensions = setOf(
        "txt", "md", "markdown", "csv", "tsv", "log", "json", "xml", "yaml", "yml",
        "html", "htm", "css", "js", "ts", "tsx", "jsx", "kt", "kts", "java", "py",
        "rb", "go", "rs", "c", "h", "cpp", "hpp", "cs", "swift", "php", "sh", "bat",
        "sql", "ini", "conf", "toml", "properties", "gradle", "srt", "vtt", "tex",
    )

    fun looksTextual(mime: String, name: String): Boolean =
        mime.startsWith("text/") ||
            mime in setOf("application/json", "application/xml", "application/x-yaml") ||
            mime.endsWith("+json") || mime.endsWith("+xml") ||
            name.substringAfterLast('.', "").lowercase() in textualExtensions

    fun isPdf(mime: String, name: String): Boolean =
        mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)

    fun isDocx(mime: String, name: String): Boolean =
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            name.endsWith(".docx", ignoreCase = true)

    fun isXlsx(mime: String, name: String): Boolean =
        mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            name.endsWith(".xlsx", ignoreCase = true)

    fun isPptx(mime: String, name: String): Boolean =
        mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
            name.endsWith(".pptx", ignoreCase = true)

    /** I formati Office pre-2007 (.doc, .xls, .ppt): contenitori binari OLE2. */
    fun isLegacyOffice(mime: String, name: String): Boolean = LegacyOffice.looksLegacy(mime, name)

    fun isSupported(mime: String, name: String): Boolean =
        looksTextual(mime, name) || isPdf(mime, name) || isDocx(mime, name) ||
            isXlsx(mime, name) || isPptx(mime, name) || isLegacyOffice(mime, name)

    /** Restituisce il testo trovato, stringa vuota se il documento non ne contiene. */
    fun extract(file: File, mime: String, name: String): String = when {
        isPdf(mime, name) -> fromPdf(file)
        isLegacyOffice(mime, name) -> LegacyOffice.extract(file)
        isDocx(mime, name) -> fromDocx(file)
        isXlsx(mime, name) -> fromXlsx(file)
        isPptx(mime, name) -> fromPptx(file)
        looksTextual(mime, name) -> file.readText().trim()
        else -> ""
    }.let { if (it.length > MAX_CHARS) it.take(MAX_CHARS) + "\n…[documento troncato]" else it }

    private fun fromPdf(file: File): String = runCatching {
        PDDocument.load(file).use { document ->
            PDFTextStripper().getText(document).trim()
        }
    }.getOrDefault("")

    /** Un .docx è uno zip: il testo sta in word/document.xml. */
    private fun fromDocx(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use ""
            stripOfficeXml(zip.getInputStream(entry).readBytes().decodeToString())
        }
    }.getOrDefault("")

    /**
     * Un .xlsx tiene i testi in una tabella condivisa (xl/sharedStrings.xml) e le
     * celle nei fogli: ricostruiamo ogni foglio riga per riga, valori separati da tab.
     */
    private fun fromXlsx(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                sharedStrings(zip.getInputStream(entry).readBytes().decodeToString())
            } ?: emptyList()
            val names = sheetNames(zip)
            val sheets = zip.entries().toList()
                .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                .sortedBy { numberIn(it.name) }
            sheets.joinToString("\n\n") { entry ->
                val index = numberIn(entry.name)
                val title = names.getOrNull(index - 1) ?: "Foglio $index"
                val xml = zip.getInputStream(entry).readBytes().decodeToString()
                "— $title —\n" + sheetRows(xml, shared)
            }.trim()
        }
    }.getOrDefault("")

    /** Un .pptx: il testo di ogni slide sta nei tag <a:t>, le note a parte. */
    private fun fromPptx(file: File): String = runCatching {
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val slides = entries
                .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { numberIn(it.name) }
            slides.joinToString("\n\n") { entry ->
                val index = numberIn(entry.name)
                val body = runs(zip.getInputStream(entry).readBytes().decodeToString())
                val notesEntry = entries.firstOrNull {
                    it.name == "ppt/notesSlides/notesSlide$index.xml"
                }
                val notes = notesEntry
                    ?.let { runs(zip.getInputStream(it).readBytes().decodeToString()) }
                    ?.takeIf { it.isNotBlank() }
                buildString {
                    append("— slide $index —\n")
                    append(body)
                    if (notes != null) append("\n[note del relatore]\n").append(notes)
                }
            }.trim()
        }
    }.getOrDefault("")

    private fun numberIn(name: String): Int =
        Regex("(\\d+)\\.xml$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun sheetNames(zip: ZipFile): List<String> = runCatching {
        val entry = zip.getEntry("xl/workbook.xml") ?: return@runCatching emptyList()
        val xml = zip.getInputStream(entry).readBytes().decodeToString()
        Regex("<sheet[^>]*\\sname=\"([^\"]*)\"").findAll(xml)
            .map { unescape(it.groupValues[1]) }
            .toList()
    }.getOrDefault(emptyList())

    private fun sharedStrings(xml: String): List<String> =
        Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { item -> runs(item.groupValues[1], separator = "") }
            .toList()

    /** Tutti i <t>/<a:t> di un frammento, nell'ordine in cui appaiono. */
    private fun runs(xml: String, separator: String = "\n"): String =
        Regex("<(?:a:)?t(?:\\s[^>]*)?>(.*?)</(?:a:)?t>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { unescape(it.groupValues[1]) }
            .filter { it.isNotBlank() }
            .joinToString(separator)
            .trim()

    private fun sheetRows(xml: String, shared: List<String>): String =
        Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { row ->
                Regex("<c\\b([^>]*)>(.*?)</c>|<c\\b([^>]*)/>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(row.groupValues[1])
                    .map { cell -> cellValue(cell.groupValues[1], cell.groupValues[2], shared) }
                    .joinToString("\t")
                    .trimEnd('\t')
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun cellValue(attributes: String, body: String, shared: List<String>): String {
        val type = Regex("\\st=\"([^\"]*)\"").find(attributes)?.groupValues?.get(1)
        return when (type) {
            "s" -> {
                val index = Regex("<v>(.*?)</v>").find(body)?.groupValues?.get(1)?.toIntOrNull()
                index?.let { shared.getOrNull(it) }.orEmpty()
            }

            "inlineStr" -> runs(body, separator = " ")
            "str" -> Regex("<v>(.*?)</v>").find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty()
            else -> Regex("<v>(.*?)</v>").find(body)?.groupValues?.get(1).orEmpty()
        }
    }

    private fun stripOfficeXml(xml: String): String = xml
        .replace(Regex("<w:p[ >][^>]*>|<w:p/>|<w:p>"), "\n")
        .replace("</w:p>", "\n")
        .replace(Regex("<w:tab[^>]*/>"), "\t")
        .replace(Regex("<w:br[^>]*/>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .let(::unescape)
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#10;", "\n")
        .replace("&amp;", "&")
}

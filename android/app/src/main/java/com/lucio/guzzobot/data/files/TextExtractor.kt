package com.lucio.guzzobot.data.files

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Estrae il testo dai documenti allegati, sul telefono: niente di tutto questo
 * passa da un servizio esterno finché l'utente non manda il messaggio.
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

    fun isSupported(mime: String, name: String): Boolean =
        looksTextual(mime, name) || isPdf(mime, name) || isDocx(mime, name)

    /** Restituisce il testo trovato, stringa vuota se il documento non ne contiene. */
    fun extract(file: File, mime: String, name: String): String = when {
        isPdf(mime, name) -> fromPdf(file)
        isDocx(mime, name) -> fromDocx(file)
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
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return@runCatching stripOfficeXml(zip.readBytes().decodeToString())
                }
                entry = zip.nextEntry
            }
            ""
        }
    }.getOrDefault("")

    private fun stripOfficeXml(xml: String): String = xml
        .replace(Regex("<w:p[ >][^>]*>|<w:p/>|<w:p>"), "\n")
        .replace("</w:p>", "\n")
        .replace(Regex("<w:tab[^>]*/>"), "\t")
        .replace(Regex("<w:br[^>]*/>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

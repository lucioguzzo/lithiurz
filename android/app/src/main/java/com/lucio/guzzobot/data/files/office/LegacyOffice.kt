package com.lucio.guzzobot.data.files.office

import java.io.File

/** Smista un vecchio file Office al parser giusto, in base ai flussi che contiene. */
object LegacyOffice {

    private val extensions = setOf("doc", "xls", "ppt", "dot", "xlt", "pps", "pot")

    private val mimes = setOf(
        "application/msword",
        "application/vnd.ms-word",
        "application/vnd.ms-excel",
        "application/excel",
        "application/vnd.ms-powerpoint",
        "application/mspowerpoint",
    )

    fun looksLegacy(mime: String, name: String): Boolean =
        mime in mimes || name.substringAfterLast('.', "").lowercase() in extensions

    fun extract(file: File): String {
        val container = CompoundFile.open(file) ?: return ""
        val names = container.streamNames
        return when {
            "WordDocument" in names -> WordDocExtractor.extract(
                wordDocument = container.stream("WordDocument") ?: return "",
                table0 = container.stream("0Table"),
                table1 = container.stream("1Table"),
            )

            "Workbook" in names -> XlsExtractor.extract(container.stream("Workbook") ?: return "")
            "Book" in names -> XlsExtractor.extract(container.stream("Book") ?: return "")
            "PowerPoint Document" in names ->
                PptExtractor.extract(container.stream("PowerPoint Document") ?: return "")

            else -> ""
        }
    }
}

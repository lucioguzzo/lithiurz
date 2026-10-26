package com.lucio.guzzobot.data.crash

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Il resoconto di un crash, in testo semplice.
 *
 * Il dialogo di sistema dice solo «l'app si è interrotta», che non basta a
 * correggere niente. Questo invece contiene la riga esatta: viene scritto
 * mentre l'app muore e offerto da condividere al lancio successivo.
 */
object CrashReport {

    /** Oltre questa misura la coda della traccia non aggiunge niente. */
    const val MAX_LENGTH = 24_000

    fun compose(
        error: Throwable,
        version: String,
        device: String,
        android: String,
        moment: String,
        thread: String,
    ): String = buildString {
        appendLine("GUZZOBOT $version")
        appendLine("Dispositivo: $device")
        appendLine("Android: $android")
        appendLine("Quando: $moment")
        appendLine("Thread: $thread")
        appendLine()
        append(stack(error))
    }.take(MAX_LENGTH)

    fun stack(error: Throwable): String {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    /**
     * La riga che spiega il crash: l'ultima causa, perché è lì che sta il
     * motivo vero, non nell'involucro che l'ha rilanciata.
     */
    fun headline(report: String): String {
        val lines = report.lines()
        val cause = lines.lastOrNull { it.startsWith("Caused by: ") }?.removePrefix("Caused by: ")
        val first = lines.firstOrNull { line ->
            line.isNotBlank() && !line.startsWith("GUZZOBOT ") && ':' in line &&
                !HEADER.any { line.startsWith(it) }
        }
        return (cause ?: first ?: "").trim().take(300)
    }

    private val HEADER = listOf("Dispositivo:", "Android:", "Quando:", "Thread:")
}

/** Il file dove resta l'ultimo crash finché l'utente non l'ha visto. */
class CrashStore(private val file: File) {

    /** Scrittura sincrona: chi la chiama sta per morire. */
    fun save(report: String) {
        file.parentFile?.mkdirs()
        file.writeText(report)
    }

    fun read(): String? = file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear() {
        file.delete()
    }
}

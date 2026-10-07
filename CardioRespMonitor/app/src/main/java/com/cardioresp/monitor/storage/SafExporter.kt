package com.cardioresp.monitor.storage

import android.content.ContentResolver
import android.net.Uri
import com.cardioresp.monitor.domain.model.RecordingSession
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export via Storage Access Framework: l'utente sceglie la destinazione con
 * ACTION_CREATE_DOCUMENT (Downloads, Drive, scheda SD...). Nessun permesso di storage
 * necessario su Android 10+; scriviamo sull'Uri concesso tramite ContentResolver.
 */
class SafExporter(private val resolver: ContentResolver) {

    fun copyFile(source: File, destination: Uri) {
        val out = resolver.openOutputStream(destination, "w") ?: throw IOException("Impossibile aprire $destination")
        out.use { o -> source.inputStream().use { it.copyTo(o, 64 * 1024) } }
    }

    fun zipSession(session: RecordingSession, destination: Uri) {
        val out = resolver.openOutputStream(destination, "w") ?: throw IOException("Impossibile aprire $destination")
        ZipOutputStream(out.buffered()).use { zip ->
            listOf(session.samplesCsv, session.rawEventsCsv, session.metadataJson)
                .filter { it.exists() }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("${session.id}/${f.name}"))
                    f.inputStream().use { it.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
        }
    }
}

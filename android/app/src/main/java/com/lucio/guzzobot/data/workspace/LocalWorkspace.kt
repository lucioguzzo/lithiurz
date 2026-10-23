package com.lucio.guzzobot.data.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Una cartella del telefono, concessa dall'utente col selettore di sistema
 * (Storage Access Framework). Il permesso è persistente: resta valido dopo il
 * riavvio, e l'app non vede nulla fuori da quell'albero.
 */
class LocalWorkspace(
    private val context: Context,
    treeUri: String,
    override val label: String,
) : Workspace {

    private val root: DocumentFile = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        ?: throw WorkspaceException("La cartella scelta non è più raggiungibile")

    override suspend fun list(path: String): List<WorkspaceEntry> = withContext(Dispatchers.IO) {
        val target = resolve(path) ?: throw WorkspaceException("Non esiste: \"$path\"")
        if (!target.isDirectory) throw WorkspaceException("Non è una cartella: \"$path\"")
        target.listFiles()
            .map { file ->
                WorkspaceEntry(
                    path = join(path, file.name ?: ""),
                    directory = file.isDirectory,
                    size = file.length(),
                )
            }
            .sortedWith(compareByDescending<WorkspaceEntry> { it.directory }.thenBy { it.path })
    }

    override suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val file = resolve(clean) ?: throw WorkspaceException("Non esiste: \"$path\"")
        if (file.isDirectory) throw WorkspaceException("È una cartella: \"$path\"")
        if (looksBinary(file.name.orEmpty())) {
            throw WorkspaceException("Non è un file di testo: \"$path\"")
        }
        val text = context.contentResolver.openInputStream(file.uri)?.use { input ->
            input.readBytes().decodeToString()
        } ?: throw WorkspaceException("Non riesco a leggere \"$path\"")
        if (text.length > MAX_READ_CHARS) {
            text.take(MAX_READ_CHARS) + "\n…[troncato: il file ha ${text.length} caratteri]"
        } else {
            text
        }
    }

    override suspend fun write(files: List<WriteRequest>, message: String): String =
        withContext(Dispatchers.IO) {
            files.forEach { request ->
                val clean = normalizePath(request.path)
                if (clean.isEmpty()) throw WorkspaceException("Percorso vuoto")
                val name = clean.substringAfterLast('/')
                val parent = createDirectories(clean.substringBeforeLast('/', ""))
                val existing = parent.findFile(name)
                val file = existing
                    ?: parent.createFile(mimeFor(name), name)
                    ?: throw WorkspaceException("Non riesco a creare \"$clean\"")
                // "wt" tronca il contenuto precedente invece di sovrascriverlo a metà.
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                    output.write(request.content.toByteArray())
                } ?: throw WorkspaceException("Non riesco a scrivere \"$clean\"")
            }
            "Scritti ${files.size} file in $label"
        }

    override suspend fun delete(path: String, message: String): String = withContext(Dispatchers.IO) {
        val clean = normalizePath(path)
        val file = resolve(clean) ?: throw WorkspaceException("Non esiste: \"$path\"")
        if (!file.delete()) throw WorkspaceException("Non riesco a cancellare \"$path\"")
        "Cancellato $clean"
    }

    override suspend fun search(
        query: String,
        path: String,
        inContent: Boolean,
    ): List<WorkspaceHit> = withContext(Dispatchers.IO) {
        val start = resolve(normalizePath(path)) ?: throw WorkspaceException("Non esiste: \"$path\"")
        val hits = mutableListOf<WorkspaceHit>()
        var visited = 0

        fun walk(directory: DocumentFile, prefix: String) {
            if (hits.size >= MAX_HITS || visited >= MAX_VISITS) return
            directory.listFiles().forEach { file ->
                if (hits.size >= MAX_HITS || visited >= MAX_VISITS) return
                visited++
                val name = file.name ?: return@forEach
                val full = join(prefix, name)
                when {
                    file.isDirectory -> walk(file, full)

                    name.contains(query, ignoreCase = true) -> hits += WorkspaceHit(full)

                    inContent && !looksBinary(name) && file.length() < MAX_GREP_BYTES -> {
                        val text = runCatching {
                            context.contentResolver.openInputStream(file.uri)?.use {
                                it.readBytes().decodeToString()
                            }
                        }.getOrNull() ?: return@forEach
                        text.lineSequence().forEachIndexed { index, line ->
                            if (hits.size < MAX_HITS && line.contains(query, ignoreCase = true)) {
                                hits += WorkspaceHit(full, index + 1, line.trim().take(200))
                            }
                        }
                    }
                }
            }
        }
        walk(start, normalizePath(path))
        hits
    }

    private fun resolve(path: String): DocumentFile? {
        val clean = normalizePath(path)
        if (clean.isEmpty()) return root
        var current: DocumentFile? = root
        clean.split('/').forEach { segment ->
            current = current?.findFile(segment) ?: return null
        }
        return current
    }

    private fun createDirectories(path: String): DocumentFile {
        if (path.isEmpty()) return root
        var current = root
        path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            val existing = current.findFile(segment)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing != null -> throw WorkspaceException("\"$segment\" esiste come file")
                else -> current.createDirectory(segment)
                    ?: throw WorkspaceException("Non riesco a creare la cartella \"$segment\"")
            }
        }
        return current
    }

    private fun join(prefix: String, name: String): String =
        if (prefix.isEmpty()) name else "$prefix/$name"

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "xml" -> "text/xml"
        else -> "text/plain"
    }

    private companion object {
        const val MAX_HITS = 80
        const val MAX_VISITS = 4_000
        const val MAX_GREP_BYTES = 512 * 1024L
    }
}

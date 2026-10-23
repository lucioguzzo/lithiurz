package com.lucio.guzzobot.data.workspace

/** Una voce dell'area di lavoro: file o cartella. */
data class WorkspaceEntry(
    val path: String,
    val directory: Boolean,
    val size: Long = 0,
)

/** Un riscontro di ricerca: il percorso e, se la ricerca era sul contenuto, la riga. */
data class WorkspaceHit(
    val path: String,
    val line: Int? = null,
    val text: String? = null,
)

data class WriteRequest(val path: String, val content: String)

/**
 * L'area di lavoro vista dal modello: elencare, leggere, scrivere, cancellare,
 * cercare. Due implementazioni, una cartella del telefono e un repository
 * GitHub, dietro la stessa interfaccia.
 */
interface Workspace {

    val label: String

    suspend fun list(path: String): List<WorkspaceEntry>

    suspend fun read(path: String): String

    /** Scrive più file in una volta: su GitHub diventano un solo commit. */
    suspend fun write(files: List<WriteRequest>, message: String): String

    suspend fun delete(path: String, message: String): String

    suspend fun search(query: String, path: String, inContent: Boolean): List<WorkspaceHit>
}

class WorkspaceException(message: String) : Exception(message)

/** Quanti caratteri di un file passano al modello in una lettura. */
const val MAX_READ_CHARS = 60_000

/**
 * Normalizza un percorso relativo alla radice e rifiuta le fughe: niente
 * percorsi assoluti, niente "..", il modello resta dentro il perimetro.
 */
fun normalizePath(raw: String): String {
    val cleaned = raw.trim().replace('\\', '/').trim('/')
    if (cleaned.isEmpty()) return ""
    val parts = cleaned.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.any { it == ".." }) {
        throw WorkspaceException("Percorso non ammesso: \"$raw\" esce dall'area di lavoro")
    }
    return parts.joinToString("/")
}

/** I file che non ha senso aprire come testo. */
fun looksBinary(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "pdf", "zip", "gz", "tar",
    "7z", "rar", "jar", "aar", "apk", "so", "dll", "exe", "bin", "dat", "class",
    "mp3", "mp4", "m4a", "wav", "ogg", "mov", "avi", "mkv", "ttf", "otf", "woff",
    "woff2", "jks", "keystore", "doc", "xls", "ppt", "docx", "xlsx", "pptx",
)

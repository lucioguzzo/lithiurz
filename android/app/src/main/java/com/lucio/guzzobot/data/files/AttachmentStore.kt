package com.lucio.guzzobot.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.data.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Copia gli allegati nella cartella privata dell'app e li prepara per l'invio:
 * testo estratto dai documenti, immagini ridimensionate in base64, pagine PDF
 * rese come immagini quando non c'è testo da leggere.
 */
class AttachmentStore(private val context: Context) {

    private val dir: File by lazy { File(context.filesDir, "attachments").apply { mkdirs() } }

    private val videoProcessor = VideoProcessor { bitmap, maxSide -> scaleDown(bitmap, maxSide) }

    /** Dimensione massima del lato lungo di un'immagine inviata al modello. */
    private val maxImageSide = 1152

    suspend fun importUri(uri: Uri, kind: AttachmentKind): Attachment = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val mime = context.contentResolver.getType(uri)
            ?: guessMime(name)
        val declaredSize = fileSize(uri)
        if (kind == AttachmentKind.VIDEO && declaredSize > MAX_VIDEO_BYTES) {
            error("Video troppo grande: il limite è ${MAX_VIDEO_BYTES / 1_000_000} MB")
        }
        val target = File(dir, "${UUID.randomUUID()}-${name.take(60).replace('/', '_')}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Non riesco a leggere il file")
        build(target, kind, name, mime)
    }

    suspend fun importFile(file: File, kind: AttachmentKind, name: String, mime: String): Attachment =
        withContext(Dispatchers.IO) { build(file, kind, name, mime) }

    private fun build(file: File, kind: AttachmentKind, name: String, mime: String): Attachment {
        val base = Attachment(
            id = UUID.randomUUID().toString(),
            kind = kind,
            name = name,
            mime = mime,
            path = file.absolutePath,
            size = file.length(),
        )
        return when (kind) {
            AttachmentKind.DOCUMENT -> {
                val text = TextExtractor.extract(file, mime, name)
                if (text.isBlank() && TextExtractor.isPdf(mime, name)) {
                    // PDF scansionato: nessun testo, lo rendiamo come immagini.
                    val pages = renderPdfPages(file)
                    base.copy(
                        pageImages = pages,
                        note = if (pages.isEmpty()) {
                            "Nessun testo estraibile"
                        } else {
                            "${pages.size} pagine come immagini"
                        },
                    )
                } else {
                    base.copy(
                        text = text.ifBlank { null },
                        note = if (text.isBlank()) "Nessun testo estraibile" else "${text.length} caratteri",
                    )
                }
            }

            AttachmentKind.VIDEO -> {
                val result = videoProcessor.process(
                    source = file,
                    frameTarget = { index ->
                        File(dir, "frame-${System.currentTimeMillis()}-$index.jpg")
                    },
                    audioTarget = { File(dir, "traccia-${System.currentTimeMillis()}.m4a") },
                )
                val seconds = result.durationMs / 1000
                base.copy(
                    pageImages = result.frames.map { it.absolutePath },
                    audioPath = result.audio?.absolutePath,
                    note = buildString {
                        if (seconds > 0) append("${seconds}s · ")
                        append("${result.frames.size} fotogrammi")
                        if (result.audio != null) append(" · audio estratto")
                    },
                )
            }

            else -> base
        }
    }

    fun delete(attachment: Attachment) {
        runCatching { File(attachment.path).delete() }
        attachment.pageImages.forEach { runCatching { File(it).delete() } }
        attachment.audioPath?.let { runCatching { File(it).delete() } }
    }

    fun cameraTarget(): Pair<File, Uri> {
        val file = File(dir, "foto-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return file to uri
    }

    fun recordingTarget(): File = File(dir, "nota-${System.currentTimeMillis()}.m4a")

    fun shareUri(attachment: Attachment): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(attachment.path))

    /** L'immagine pronta per l'API: ridimensionata, ruotata secondo l'EXIF, in base64. */
    suspend fun imageDataUrl(path: String): String? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(path) ?: return@withContext null
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        bitmap.recycle()
        "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun decodeThumbnail(path: String, maxSide: Int = 512): Bitmap? =
        withContext(Dispatchers.IO) { decodeScaled(path, maxSide) }

    private fun decodeScaled(path: String, maxSide: Int = maxImageSide): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val resized = scaleDown(decoded, maxSide)
        return applyExifRotation(path, resized)
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            when (
                ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Salva un'immagine ricevuta dal modello come allegato. */
    suspend fun saveGeneratedImage(bytes: ByteArray, prompt: String): Attachment =
        withContext(Dispatchers.IO) {
            val file = File(dir, "immagine-${System.currentTimeMillis()}.png")
            file.writeBytes(bytes)
            Attachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.GENERATED_IMAGE,
                name = prompt.take(60).ifBlank { "immagine" },
                mime = "image/png",
                path = file.absolutePath,
                size = file.length(),
            )
        }

    suspend fun saveGeneratedAudio(bytes: ByteArray, name: String): Attachment =
        withContext(Dispatchers.IO) {
            val file = File(dir, "voce-${System.currentTimeMillis()}.mp3")
            file.writeBytes(bytes)
            Attachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.GENERATED_AUDIO,
                name = name,
                mime = "audio/mpeg",
                path = file.absolutePath,
                size = file.length(),
            )
        }

    private fun renderPdfPages(file: File, maxPages: Int = 6): List<String> = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                (0 until minOf(renderer.pageCount, maxPages)).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = 1240f / max(page.width, 1)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * scale).toInt().coerceAtLeast(1),
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        android.graphics.Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val target = File(dir, "pagina-${System.currentTimeMillis()}-$index.jpg")
                        target.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bitmap.recycle()
                        target.absolutePath
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "allegato"
    }

    private fun fileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                return cursor.getLong(index)
            }
        }
        return 0
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        "txt", "md" -> "text/plain"
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }

    private companion object {
        /** Oltre questa dimensione un video non entra nella cartella privata senza problemi. */
        const val MAX_VIDEO_BYTES = 400_000_000L
    }
}

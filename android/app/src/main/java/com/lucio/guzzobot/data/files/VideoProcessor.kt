package com.lucio.guzzobot.data.files

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer

/**
 * Un video viene dato in pasto ai modelli in due pezzi: una manciata di
 * fotogrammi per chi sa guardare le immagini e la traccia audio per chi sa
 * trascrivere. Tutto il lavoro avviene sul telefono.
 */
class VideoProcessor(private val scale: (Bitmap, Int) -> Bitmap) {

    data class Result(
        val frames: List<File>,
        val audio: File?,
        val durationMs: Long,
    )

    fun process(
        source: File,
        frameTarget: (Int) -> File,
        audioTarget: () -> File,
        maxFrames: Int = 8,
    ): Result {
        val duration = duration(source)
        val frames = extractFrames(source, duration, maxFrames, frameTarget)
        val audio = runCatching { extractAudio(source, audioTarget()) }.getOrNull()
        return Result(frames = frames, audio = audio, durationMs = duration)
    }

    private fun duration(source: File): Long = runCatching {
        retriever(source).use { retriever ->
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    /** Fotogrammi distribuiti sulla durata: pochi e ben spaziati battono tanti e uguali. */
    private fun extractFrames(
        source: File,
        durationMs: Long,
        maxFrames: Int,
        frameTarget: (Int) -> File,
    ): List<File> = runCatching {
        retriever(source).use { retriever ->
            val count = when {
                durationMs <= 0L -> 1
                durationMs < 3_000 -> 2
                durationMs < 15_000 -> 4
                durationMs < 60_000 -> 6
                else -> maxFrames
            }
            (0 until count).mapNotNull { index ->
                val position = if (durationMs <= 0L) {
                    0L
                } else {
                    (durationMs * (index + 0.5) / count).toLong()
                }
                val bitmap = retriever.getFrameAtTime(
                    position * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: return@mapNotNull null
                val resized = scale(bitmap, 1152)
                val file = frameTarget(index)
                file.outputStream().use { out ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                resized.recycle()
                file
            }
        }
    }.getOrDefault(emptyList())

    /** Copia la sola traccia audio in un .m4a, senza ricodificarla. */
    private fun extractAudio(source: File, target: File): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val output = muxer.addTrack(format)
            muxer.start()
            val size = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(0)
                .takeIf { it > 0 } ?: (256 * 1024)
            val buffer = ByteBuffer.allocate(size)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                info.offset = 0
                info.size = read
                info.presentationTimeUs = extractor.sampleTime
                // I flag dell'estrattore non sono quelli del muxer: lo stesso
                // valore 2 vuol dire «cifrato» per l'uno e «configurazione
                // del codec» per l'altro. Dell'estrattore serve solo «chiave».
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(output, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            return target.takeIf { it.length() > 1024 }
        } catch (error: Throwable) {
            target.delete()
            return null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun retriever(source: File): MediaMetadataRetriever =
        MediaMetadataRetriever().apply { setDataSource(source.absolutePath) }
}

/** MediaMetadataRetriever è closeable solo da API 29: questo copre anche le versioni più vecchie. */
private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T = try {
    block(this)
} finally {
    runCatching { release() }
}

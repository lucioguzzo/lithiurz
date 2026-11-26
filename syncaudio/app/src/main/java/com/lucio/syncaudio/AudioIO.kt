package com.lucio.syncaudio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import com.lucio.syncaudio.core.PlayoutEngine
import com.lucio.syncaudio.core.SyncSource
import kotlin.math.abs
import kotlin.math.max

const val SAMPLE_RATE = 48000
const val CHANNELS = 2
/** 5 ms per packet: 960 bytes of PCM, fits a single Wi-Fi frame. */
const val CHUNK_FRAMES = 240

private const val TAG = "SyncAudio"

/**
 * Captures what other apps are playing (Android 10+ AudioPlaybackCapture) and hands it,
 * timestamped with this phone's monotonic clock, to [SyncSource].
 */
class AudioCapture(
    private val projection: MediaProjection,
    private val sink: SyncSource,
    private val onPeak: (Int) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @Volatile var error: String? = null; private set

    fun start() {
        running = true
        thread = Thread({ run() }, "sync-capture").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
    }

    @Suppress("MissingPermission") // RECORD_AUDIO is requested by MainActivity before starting.
    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(max(minBuf * 2, CHUNK_FRAMES * CHANNELS * 2 * 16))
                .setAudioPlaybackCaptureConfig(config)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            error = "Impossibile avviare la cattura audio: ${e.message}"
            return
        }
        record.startRecording()
        val buf = ShortArray(CHUNK_FRAMES * CHANNELS)
        val ts = AudioTimestamp()
        var tsValid = false
        var tsFrame = 0L
        var tsNano = 0L
        var framePos = 0L
        var chunks = 0
        var peak = 0
        var peakChunks = 0
        try {
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) { error = "Errore cattura ($n)"; break }
                val frames = n / CHANNELS
                if (frames == 0) continue
                val now = System.nanoTime()
                if (chunks % 20 == 0 &&
                    record.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS
                ) {
                    tsValid = true; tsFrame = ts.framePosition; tsNano = ts.nanoTime
                }
                chunks++
                // Capture time of the first frame of this chunk, on this phone's monotonic clock.
                val pts = if (tsValid) tsNano + (framePos - tsFrame) * 1_000_000_000L / SAMPLE_RATE
                else now - frames * 1_000_000_000L / SAMPLE_RATE
                sink.sendAudio(framePos, pts, buf, frames)
                framePos += frames

                for (i in 0 until frames * CHANNELS) { val v = abs(buf[i].toInt()); if (v > peak) peak = v }
                if (++peakChunks >= 200) { onPeak(peak); peak = 0; peakChunks = 0 } // every ~1 s
            }
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }
}

/**
 * Pulls audio from the [PlayoutEngine] and writes it to the local output (the Bluetooth
 * speaker connected to this phone). It tells the engine *when* each chunk will actually be
 * heard, using AudioTrack timestamps, so the engine can pick exactly the right samples.
 */
class AudioPlayer(private val engineProvider: () -> PlayoutEngine?) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread({ run() }, "sync-player").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, CHUNK_FRAMES * CHANNELS * 2 * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val buf = ShortArray(CHUNK_FRAMES * CHANNELS)
        val ts = AudioTimestamp()
        var tsValid = false
        var tsFrame = 0L
        var tsNano = 0L
        var lastQuery = 0L
        var written = 0L
        track.play()
        try {
            while (running) {
                val now = System.nanoTime()
                if (now - lastQuery > 100_000_000L) {
                    lastQuery = now
                    if (track.getTimestamp(ts) && ts.framePosition > 0) {
                        tsValid = true; tsFrame = ts.framePosition; tsNano = ts.nanoTime
                    }
                }
                // When will the first frame of this chunk come out of the output device?
                val present = if (tsValid) tsNano + (written - tsFrame) * 1_000_000_000L / SAMPLE_RATE
                else now + (written - track.playbackHeadPosition.toLong()) * 1_000_000_000L / SAMPLE_RATE

                val engine = engineProvider()
                if (engine == null || engine.sampleRate != SAMPLE_RATE || engine.channels != CHANNELS) {
                    buf.fill(0)
                } else {
                    engine.render(buf, CHUNK_FRAMES, present)
                }
                val w = track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (w < 0) { Log.w(TAG, "AudioTrack write error $w"); break }
                written += w / CHANNELS
            }
        } finally {
            try { track.stop() } catch (_: Exception) {}
            track.release()
        }
    }
}

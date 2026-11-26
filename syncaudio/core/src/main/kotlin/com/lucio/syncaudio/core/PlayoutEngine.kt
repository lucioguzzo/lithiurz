package com.lucio.syncaudio.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Circular store of received frames, addressed by absolute frame index. Missing frames read as absent. */
class FrameRing(val channels: Int, capacityFrames: Int = 1 shl 17) {
    init {
        require(capacityFrames > 0 && capacityFrames and (capacityFrames - 1) == 0) { "capacity must be a power of 2" }
    }

    private val mask = capacityFrames - 1
    private val data = ShortArray(capacityFrames * channels)
    private val tags = LongArray(capacityFrames) { Long.MIN_VALUE }

    /** One past the highest frame ever written. */
    var endFrame: Long = Long.MIN_VALUE; private set

    fun write(framePos: Long, pcm: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            val f = framePos + i
            val slot = (f and mask.toLong()).toInt()
            System.arraycopy(pcm, i * channels, data, slot * channels, channels)
            tags[slot] = f
        }
        if (framePos + frames > endFrame) endFrame = framePos + frames
    }

    fun has(frame: Long): Boolean = tags[(frame and mask.toLong()).toInt()] == frame

    /** Sample value, or [MISSING] if that frame never arrived (or was overwritten). */
    fun sample(frame: Long, channel: Int): Int {
        val slot = (frame and mask.toLong()).toInt()
        return if (tags[slot] == frame) data[slot * channels + channel].toInt() else MISSING
    }

    companion object {
        const val MISSING = Int.MIN_VALUE
    }
}

/**
 * Maps source frame index <-> source clock time. Source timestamps are jittery (thread
 * scheduling); this keeps a smoothed anchor that follows the real capture rate.
 */
class SourceTimeline(sampleRate: Int) {
    private val nsPerFrame = 1e9 / sampleRate
    private var anchorFrame = Long.MIN_VALUE
    private var anchorPts = 0.0

    val ready: Boolean get() = anchorFrame != Long.MIN_VALUE

    fun update(framePos: Long, ptsNs: Long) {
        if (!ready) {
            anchorFrame = framePos; anchorPts = ptsNs.toDouble(); return
        }
        val predicted = anchorPts + (framePos - anchorFrame) * nsPerFrame
        val err = ptsNs - predicted
        anchorFrame = framePos
        anchorPts = if (abs(err) > RESET_NS) ptsNs.toDouble() else predicted + err * ALPHA
    }

    fun frameAt(sourceNs: Long): Double = anchorFrame + (sourceNs - anchorPts) / nsPerFrame

    private companion object {
        const val ALPHA = 0.05
        const val RESET_NS = 20e6
    }
}

/**
 * The heart of the receiver. Decides, for every chunk the local audio output is about to
 * play, which source frames must go into it so that it comes out of the speaker exactly at
 *
 *     local_time(capture_time_of_frame) + delay
 *
 * It keeps a fractional read pointer into the received stream and nudges the playback rate
 * by a few hundred ppm (linear-interpolation resampling) to absorb crystal drift and clock
 * estimate updates without audible clicks. Large errors (> [hardResyncMs]) cause a jump.
 */
class PlayoutEngine(
    val sampleRate: Int,
    val channels: Int,
    private val clock: ClockSync,
    capacityFrames: Int = 1 shl 17,
    private val hardResyncMs: Double = 25.0,
) {
    private val lock = Any()
    private val ring = FrameRing(channels, capacityFrames)
    private val timeline = SourceTimeline(sampleRate)
    private var readPos = Double.NaN

    /** Delay requested by the source (network/jitter margin). */
    @Volatile var baseDelayMs: Int = 80
    /** Per-receiver correction set by the user/calibration, e.g. to compensate Bluetooth latency. */
    @Volatile var userOffsetMs: Int = 0

    // --- stats (read by UI, approximate) ---
    @Volatile var lastErrorMs: Double = 0.0; private set
    @Volatile var rate: Double = 1.0; private set
    @Volatile var bufferedMs: Double = 0.0; private set
    @Volatile var resyncs: Int = 0; private set
    @Volatile var missingFrames: Long = 0; private set
    @Volatile var renderedFrames: Long = 0; private set
    @Volatile var packets: Long = 0; private set
    @Volatile var playing: Boolean = false; private set

    val targetDelayMs: Int get() = max(MIN_DELAY_MS, baseDelayMs + userOffsetMs)

    fun onAudio(framePos: Long, ptsNs: Long, pcm: ShortArray, frames: Int, baseDelayMs: Int) {
        synchronized(lock) {
            ring.write(framePos, pcm, frames)
            timeline.update(framePos, ptsNs)
            this.baseDelayMs = baseDelayMs
            packets++
        }
    }

    /**
     * Fills [out] (interleaved, [frames] frames) with the audio whose first frame will be
     * presented by the local output at [presentLocalNs]. Returns true if real audio was produced.
     */
    fun render(out: ShortArray, frames: Int, presentLocalNs: Long): Boolean {
        synchronized(lock) {
            if (!clock.synced || !timeline.ready) {
                out.fill(0, 0, frames * channels)
                playing = false
                return false
            }
            val srcTime = clock.localToSource(presentLocalNs - targetDelayMs * 1_000_000L)
            val target = timeline.frameAt(srcTime)
            if (readPos.isNaN() || abs(target - readPos) > hardResyncMs * sampleRate / 1000.0) {
                readPos = target
                resyncs++
            }
            val err = target - readPos // > 0: we are late, consume faster
            val correction = (KP * err / sampleRate).coerceIn(-MAX_CORRECTION, MAX_CORRECTION)
            val step = 1.0 + correction

            var pos = readPos
            var missing = 0
            for (i in 0 until frames) {
                val i0 = floor(pos).toLong()
                val frac = pos - i0
                var anyReal = false
                for (c in 0 until channels) {
                    val a = ring.sample(i0, c)
                    val b = ring.sample(i0 + 1, c)
                    val v = when {
                        a != FrameRing.MISSING && b != FrameRing.MISSING -> {
                            anyReal = true; a + (b - a) * frac
                        }
                        a != FrameRing.MISSING -> { anyReal = true; a.toDouble() }
                        b != FrameRing.MISSING -> { anyReal = true; b.toDouble() }
                        else -> 0.0
                    }
                    out[i * channels + c] = v.toInt().coerceIn(-32768, 32767).toShort()
                }
                if (!anyReal) missing++
                pos += step
            }
            readPos = pos

            lastErrorMs = err * 1000.0 / sampleRate
            rate = step
            bufferedMs = if (ring.endFrame == Long.MIN_VALUE) 0.0 else (ring.endFrame - readPos) * 1000.0 / sampleRate
            missingFrames += missing
            renderedFrames += frames
            playing = missing < frames
            return playing
        }
    }

    companion object {
        /** Proportional gain, 1/s: a 5 ms error is corrected in roughly a second. */
        const val KP = 1.0
        /** Max speed change (0.5% ≈ 8.6 cents): inaudible for music in practice. */
        const val MAX_CORRECTION = 0.005
        const val MIN_DELAY_MS = 15
    }
}

package com.lucio.syncaudio.core

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Estimates the relation between the receiver's monotonic clock ("local") and the
 * source's monotonic clock ("source"), from NTP-style exchanges:
 *
 *   t1 = local send, t2 = source receive, t3 = source send, t4 = local receive
 *   offset = ((t2 - t1) + (t3 - t4)) / 2      (source - local)
 *   rtt    = (t4 - t1) - (t3 - t2)
 *
 * Wi-Fi delay is noisy and asymmetric, so only the exchanges with the lowest RTT are
 * trusted (they carry the least queuing error). Over a long enough window a linear fit
 * also gives the relative frequency error (skew) of the two crystals, typically 1..100 ppm,
 * which otherwise would make the phones drift apart by several ms per minute.
 */
class ClockSync(
    private val maxSamples: Int = 600,
    private val maxAgeNs: Long = 90_000_000_000L,
    private val bucketNs: Long = 2_000_000_000L,
    private val minSpanForSkewNs: Long = 10_000_000_000L,
) {
    private class Sample(val local: Long, val offset: Long, val rtt: Long)

    private data class Model(val refLocal: Long, val refOffset: Long, val skew: Double)

    private val samples = ArrayDeque<Sample>()

    @Volatile private var model: Model? = null

    @Volatile var lastRttNs: Long = 0L; private set
    @Volatile var bestRttNs: Long = 0L; private set
    @Volatile var sampleCount: Int = 0; private set

    val synced: Boolean get() = model != null && sampleCount >= 4
    val skewPpm: Double get() = (model?.skew ?: 0.0) * 1e6
    val offsetNs: Long get() = model?.refOffset ?: 0L

    @Synchronized
    fun reset() {
        samples.clear(); model = null; sampleCount = 0
    }

    /** Adds one exchange. Returns false if the sample was rejected as nonsense. */
    @Synchronized
    fun addExchange(t1: Long, t2: Long, t3: Long, t4: Long): Boolean {
        val rtt = (t4 - t1) - (t3 - t2)
        if (rtt < 0 || t4 < t1) return false
        val offset = (t2 - t1) / 2 + (t3 - t4) / 2
        val local = t1 + (t4 - t1) / 2
        lastRttNs = rtt
        samples.addLast(Sample(local, offset, rtt))
        while (samples.size > maxSamples) samples.removeFirst()
        while (samples.size > 1 && local - samples.first().local > maxAgeNs) samples.removeFirst()
        sampleCount = samples.size
        recompute()
        return true
    }

    private fun recompute() {
        bestRttNs = samples.minOf { it.rtt }
        // The exchange with the smallest RTT in each time bucket is the least distorted by
        // queuing; only those are used (classic NTP/chrony "minimum filter").
        val first = samples.first().local
        val best = LinkedHashMap<Long, Sample>()
        for (s in samples) {
            val key = (s.local - first) / bucketNs
            val cur = best[key]
            if (cur == null || s.rtt < cur.rtt) best[key] = s
        }
        var pts = best.values.toList()
        val span = pts.last().local - pts.first().local
        if (pts.size < 5 || span < minSpanForSkewNs) {
            val s = samples.minBy { it.rtt }
            model = Model(s.local, s.offset, model?.skew ?: 0.0)
            return
        }
        var fit = fit(pts)
        // One robust pass: drop points far from the line (bursts of Wi-Fi congestion), refit.
        val res = pts.map { abs(residual(fit, it)) }.sorted()
        val mad = res[res.size / 2]
        val kept = pts.filter { abs(residual(fit, it)) <= 3 * mad + 200_000 }
        if (kept.size >= 5 && kept.size < pts.size) { pts = kept; fit = fit(pts) }
        model = fit
    }

    private fun residual(m: Model, s: Sample): Double =
        (s.offset - m.refOffset) - m.skew * (s.local - m.refLocal)

    private fun fit(pts: List<Sample>): Model {
        val ref = pts.last()
        // Least squares on values relative to the reference sample (keeps doubles precise).
        var sx = 0.0; var sy = 0.0
        for (s in pts) { sx += (s.local - ref.local).toDouble(); sy += (s.offset - ref.offset).toDouble() }
        val mx = sx / pts.size; val my = sy / pts.size
        var cov = 0.0; var vx = 0.0
        for (s in pts) {
            val dx = (s.local - ref.local) - mx
            val dy = (s.offset - ref.offset) - my
            cov += dx * dy; vx += dx * dx
        }
        val slope = if (vx > 0) (cov / vx).coerceIn(-MAX_SKEW, MAX_SKEW) else 0.0
        val interceptAtRef = my - slope * mx
        return Model(ref.local, ref.offset + interceptAtRef.roundToLong(), slope)
    }

    /** source - local, at the given local time. */
    fun offsetAt(localNs: Long): Long {
        val m = model ?: return 0L
        return m.refOffset + (m.skew * (localNs - m.refLocal)).roundToLong()
    }

    fun localToSource(localNs: Long): Long = localNs + offsetAt(localNs)

    fun sourceToLocal(sourceNs: Long): Long {
        val m = model ?: return sourceNs
        var local = sourceNs - m.refOffset
        local = sourceNs - offsetAt(local) // one fixed-point iteration is plenty (skew << 1)
        return local
    }

    companion object {
        const val MAX_SKEW = 500e-6
    }
}

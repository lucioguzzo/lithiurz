package com.cardioresp.monitor.sensor

import com.cardioresp.monitor.domain.model.SensorKind
import com.cardioresp.monitor.domain.model.StreamDiagnostics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Statistiche online (O(1) per campione, nessuna allocazione dopo il warm-up) sulla regolarità
 * dei timestamp hardware di un flusso.
 *
 * - Il periodo nominale T NON è quello richiesto (Android lo tratta come suggerimento) ma la
 *   MEDIANA dei primi [warmupIntervals] intervalli: robusta ai buchi iniziali.
 * - Media e deviazione standard (algoritmo di Welford, numericamente stabile) sono calcolate
 *   solo sugli intervalli "regolari" (dt ≤ 1.5·T): un singolo buco non deve far esplodere il
 *   jitter. I buchi sono contati a parte come campioni persi: round(dt/T) − 1.
 */
class StreamStatsCalculator(
    private val kind: SensorKind,
    private val warmupIntervals: Int = 64
) {
    private var count = 0L
    private var firstT = 0L
    private var lastT = 0L

    private var nominalNs = 0.0
    private val warmup = LongArray(warmupIntervals)
    private var warmupSize = 0

    // Welford sugli intervalli regolari
    private var n = 0L
    private var mean = 0.0
    private var m2 = 0.0
    private var minDt = Long.MAX_VALUE
    private var maxDt = Long.MIN_VALUE
    private var peakJitter = 0.0

    private var lost = 0L
    private var gaps = 0L
    private var nonMonotonic = 0L

    fun add(tNs: Long) {
        if (count == 0L) {
            firstT = tNs
            lastT = tNs
            count = 1
            return
        }
        val dt = tNs - lastT
        if (dt <= 0) {
            nonMonotonic++
            return
        }
        lastT = tNs
        count++
        minDt = min(minDt, dt)
        maxDt = max(maxDt, dt)

        if (nominalNs == 0.0) {
            warmup[warmupSize++] = dt
            if (warmupSize == warmupIntervals) {
                val sorted = warmup.sortedArray()
                val mid = warmupIntervals / 2
                nominalNs = if (warmupIntervals % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid].toDouble()
                for (d in warmup) accumulate(d)
            }
        } else {
            accumulate(dt)
        }
    }

    private fun accumulate(dt: Long) {
        if (dt > GAP_FACTOR * nominalNs) {
            gaps++
            lost += max(0L, (dt / nominalNs).roundToLong() - 1)
            return
        }
        n++
        val d = dt - mean
        mean += d / n
        m2 += d * (dt - mean)
        peakJitter = max(peakJitter, abs(dt - nominalNs))
    }

    fun snapshot(): StreamDiagnostics {
        val spanNs = lastT - firstT
        val rate = if (count > 1 && spanNs > 0) (count - 1) * 1e9 / spanNs else 0.0
        val std = if (n > 1) sqrt(m2 / (n - 1)) else 0.0
        return StreamDiagnostics(
            sensor = kind,
            sampleCount = count,
            effectiveRateHz = rate,
            nominalPeriodMs = nominalNs / 1e6,
            meanIntervalMs = mean / 1e6,
            stdIntervalMs = std / 1e6,
            minIntervalMs = if (minDt == Long.MAX_VALUE) 0.0 else minDt / 1e6,
            maxIntervalMs = if (maxDt == Long.MIN_VALUE) 0.0 else maxDt / 1e6,
            peakJitterMs = peakJitter / 1e6,
            estimatedLostSamples = lost,
            gapCount = gaps,
            nonMonotonicCount = nonMonotonic
        )
    }

    val firstTimestampNs: Long get() = firstT
    val lastTimestampNs: Long get() = lastT
    val sampleCount: Long get() = count

    private companion object {
        const val GAP_FACTOR = 1.5
    }
}

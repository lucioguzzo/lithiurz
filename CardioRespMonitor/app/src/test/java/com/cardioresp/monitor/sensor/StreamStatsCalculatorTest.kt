package com.cardioresp.monitor.sensor

import com.cardioresp.monitor.domain.model.SensorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamStatsCalculatorTest {

    @Test
    fun perfectlyRegularStream() {
        val s = StreamStatsCalculator(SensorKind.ACCELEROMETER, warmupIntervals = 16)
        for (i in 0 until 1001) s.add(i * 5_000_000L) // 200 Hz
        val d = s.snapshot()
        assertEquals(1001L, d.sampleCount)
        assertEquals(200.0, d.effectiveRateHz, 1e-9)
        assertEquals(5.0, d.meanIntervalMs, 1e-9)
        assertEquals(0.0, d.stdIntervalMs, 1e-9)
        assertEquals(0L, d.estimatedLostSamples)
    }

    @Test
    fun detectsGapsAndEstimatesLostSamples() {
        val s = StreamStatsCalculator(SensorKind.GYROSCOPE, warmupIntervals = 16)
        var t = 0L
        for (i in 0 until 100) { s.add(t); t += 5_000_000L }
        t += 3 * 5_000_000L // mancano 3 campioni
        for (i in 0 until 100) { s.add(t); t += 5_000_000L }
        val d = s.snapshot()
        assertEquals(1L, d.gapCount)
        assertEquals(3L, d.estimatedLostSamples)
        assertEquals(5.0, d.meanIntervalMs, 1e-9) // il buco non sporca la media
    }

    @Test
    fun countsNonMonotonicTimestamps() {
        val s = StreamStatsCalculator(SensorKind.ACCELEROMETER)
        s.add(10); s.add(20); s.add(20); s.add(15); s.add(30)
        assertEquals(2L, s.snapshot().nonMonotonicCount)
        assertEquals(3L, s.snapshot().sampleCount)
    }

    @Test
    fun jitterMatchesStandardDeviation() {
        val s = StreamStatsCalculator(SensorKind.ACCELEROMETER, warmupIntervals = 4)
        // intervalli alternati 4 ms / 6 ms -> media 5, std ~1
        var t = 0L
        s.add(t)
        repeat(1000) { k -> t += if (k % 2 == 0) 4_000_000L else 6_000_000L; s.add(t) }
        val d = s.snapshot()
        assertEquals(5.0, d.meanIntervalMs, 1e-3)
        assertEquals(1.0, d.stdIntervalMs, 1e-2)
        assertEquals(1.0, d.peakJitterMs, 1e-9)
    }
}

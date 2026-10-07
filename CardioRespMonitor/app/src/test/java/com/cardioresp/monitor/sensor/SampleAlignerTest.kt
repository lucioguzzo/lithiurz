package com.cardioresp.monitor.sensor

import com.cardioresp.monitor.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleAlignerTest {

    private val ms = 1_000_000L

    @Test
    fun interpolatesGyroAtAccelerometerTimestamp() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(emit = { out += it })
        a.onGyroscope(0, 0f, 0f, 0f)
        a.onAccelerometer(2 * ms, 1f, 2f, 3f) // in attesa del gyro successivo
        assertTrue(out.isEmpty())
        a.onGyroscope(5 * ms, 10f, 20f, 30f)
        assertEquals(1, out.size)
        assertEquals(4f, out[0].gyroX, 1e-5f)
        assertEquals(8f, out[0].gyroY, 1e-5f)
        assertEquals(12f, out[0].gyroZ, 1e-5f)
        assertEquals(1f, out[0].accX)
    }

    @Test
    fun writesNaNAcrossLargeGyroGap() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(maxGyroGapNs = 50 * ms, emit = { out += it })
        a.onGyroscope(0, 0f, 0f, 0f)
        a.onAccelerometer(40 * ms, 0f, 0f, 0f)
        a.onGyroscope(100 * ms, 1f, 1f, 1f)
        assertTrue(out.single().gyroX.isNaN())
        assertEquals(1L, a.samplesWithoutGyro)
    }

    @Test
    fun emitsWithoutGyroWhenSensorMissing() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(gyroAvailable = false, emit = { out += it })
        a.onAccelerometer(1 * ms, 0f, 0f, 9.81f)
        assertEquals(1, out.size)
        assertTrue(out[0].gyroZ.isNaN())
    }

    @Test
    fun timeoutReleasesPendingSamplesWhenGyroStalls() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(pendingTimeoutNs = 100 * ms, emit = { out += it })
        a.onGyroscope(0, 0f, 0f, 0f)
        for (k in 1..30) a.onAccelerometer(k * 5 * ms, 0f, 0f, 0f) // 5..150 ms, nessun nuovo gyro
        assertTrue(out.isNotEmpty())
        // Il primo campione (5 ms) è vicino a g0 (0 ms): valore gyro tenuto.
        assertEquals(0f, out.first().gyroX)
        // Ordine preservato
        assertEquals(out.map { it.sensorTimestampNs }.sorted(), out.map { it.sensorTimestampNs })
    }

    @Test
    fun unixTimestampUsesOffset() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(gyroAvailable = false, emit = { out += it })
        a.unixOffsetNs = 1_700_000_000_000L * ms - 5 * ms
        a.onAccelerometer(5 * ms + 999_999, 0f, 0f, 0f)
        assertEquals(1_700_000_000_000L, out[0].unixTimestampMs)
    }

    @Test
    fun flushEmitsEverything() {
        val out = mutableListOf<SensorSample>()
        val a = SampleAligner(emit = { out += it })
        a.onGyroscope(0, 0f, 0f, 0f)
        a.onAccelerometer(1 * ms, 0f, 0f, 0f)
        a.onAccelerometer(2 * ms, 0f, 0f, 0f)
        a.flush()
        assertEquals(2, out.size)
    }
}

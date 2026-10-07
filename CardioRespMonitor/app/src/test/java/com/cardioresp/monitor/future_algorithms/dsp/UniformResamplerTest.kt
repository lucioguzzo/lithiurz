package com.cardioresp.monitor.future_algorithms.dsp

import com.cardioresp.monitor.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Test

class UniformResamplerTest {
    private fun s(tMs: Long, v: Float) = SensorSample(tMs * 1_000_000, 0, v, 0f, 0f, 0f, 0f, 0f)

    @Test
    fun resamplesIrregularInputOnUniformGrid() {
        val times = mutableListOf<Long>()
        val values = mutableListOf<Float>()
        val r = UniformResampler(100.0) { t, v -> times += t; values += v[0] }
        // rampa v = t(ms) campionata irregolarmente
        listOf(0L, 7L, 13L, 30L, 41L).forEach { r.push(s(it, it.toFloat())) }
        assertEquals(listOf(0L, 10L, 20L, 30L, 40L).map { it * 1_000_000 }, times)
        values.forEachIndexed { i, v -> assertEquals(i * 10f, v, 1e-4f) }
    }
}

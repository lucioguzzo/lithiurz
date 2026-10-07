package com.cardioresp.monitor.future_algorithms.dsp

import com.cardioresp.monitor.domain.model.SensorSample

/**
 * Infrastruttura DSP (non è un algoritmo cardiorespiratorio): converte il flusso a timestamp
 * IRREGOLARI in una griglia UNIFORME a frequenza [targetRateHz] con interpolazione lineare.
 *
 * Perché serve: FFT, filtri IIR/FIR e autocorrelazione assumono campioni equispaziati.
 * Applicarli direttamente a dati con jitter/buchi introduce errori spettrali; usare l'indice
 * del campione come tempo introduce un errore di scala se la frequenza reale ≠ nominale.
 *
 * Per downsampling forte (es. 200 -> 20 Hz) applicare PRIMA un filtro anti-aliasing passa-basso.
 * Buchi più lunghi di [maxGapNs] non vengono interpolati: la griglia riparte dopo il buco
 * e [onGap] viene notificato (l'algoritmo dovrebbe resettare la finestra).
 */
class UniformResampler(
    private val targetRateHz: Double,
    private val maxGapNs: Long = 100_000_000L,
    private val onGap: (fromNs: Long, toNs: Long) -> Unit = { _, _ -> },
    /** Riceve: istante griglia, e i 6 canali interpolati in ordine acc xyz, gyro xyz. */
    private val output: (tNs: Long, values: FloatArray) -> Unit
) {
    private val periodNs = 1e9 / targetRateHz
    private var prev: SensorSample? = null
    private var nextGridNs = Double.NaN
    private val out = FloatArray(6)

    fun push(s: SensorSample) {
        val p = prev
        prev = s
        if (p == null) {
            nextGridNs = s.sensorTimestampNs.toDouble()
            return
        }
        val dt = s.sensorTimestampNs - p.sensorTimestampNs
        if (dt <= 0) return
        if (dt > maxGapNs) {
            onGap(p.sensorTimestampNs, s.sensorTimestampNs)
            nextGridNs = s.sensorTimestampNs.toDouble()
            return
        }
        while (nextGridNs <= s.sensorTimestampNs) {
            val w = ((nextGridNs - p.sensorTimestampNs) / dt).toFloat()
            out[0] = lerp(p.accX, s.accX, w); out[1] = lerp(p.accY, s.accY, w); out[2] = lerp(p.accZ, s.accZ, w)
            out[3] = lerp(p.gyroX, s.gyroX, w); out[4] = lerp(p.gyroY, s.gyroY, w); out[5] = lerp(p.gyroZ, s.gyroZ, w)
            output(nextGridNs.toLong(), out)
            nextGridNs += periodNs
        }
    }

    fun reset() {
        prev = null
        nextGridNs = Double.NaN
    }

    private fun lerp(a: Float, b: Float, w: Float) = a + (b - a) * w
}

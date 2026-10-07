package com.cardioresp.monitor.sensor

import com.cardioresp.monitor.domain.model.SensorSample
import kotlin.math.abs

/**
 * Fonde i due flussi asincroni (accelerometro e giroscopio hanno timestamp DIVERSI anche
 * alla stessa frequenza nominale) in un unico flusso di [SensorSample].
 *
 * Strategia: l'accelerometro è il clock MASTER. Per ogni campione acc a(t) il giroscopio
 * viene interpolato LINEARMENTE all'istante t tra i due campioni gyro che lo racchiudono
 * (g0.t ≤ t ≤ g1.t). Rispetto al "sample & hold" (prendere l'ultimo gyro) elimina un ritardo
 * variabile fino a un periodo, che si tradurrebbe in jitter di fase tra i canali.
 *
 * Se il campione gyro successivo non è ancora arrivato, il campione acc resta in attesa
 * (latenza di pochi ms). Se il gyro tace per più di [pendingTimeoutNs] o l'intervallo tra
 * g0 e g1 supera [maxGyroGapNs], i canali gyro vengono scritti come NaN: meglio un dato
 * esplicitamente mancante che un valore inventato (fondamentale per la validazione).
 *
 * I flussi GREZZI non interpolati vengono comunque salvati in raw_events.csv.
 * Non thread-safe: usato solo dal thread consumatore.
 */
class SampleAligner(
    private val maxGyroGapNs: Long = 50_000_000L,      // 50 ms
    private val pendingTimeoutNs: Long = 200_000_000L, // 200 ms
    private val gyroAvailable: Boolean = true,
    private val emit: (SensorSample) -> Unit
) {
    /** Offset (ns) da sommare al timestamp sensore per ottenere unix ns. Impostato dalla calibrazione. */
    var unixOffsetNs: Long = 0L

    var samplesWithoutGyro: Long = 0L
        private set

    private class Point(val t: Long, val x: Float, val y: Float, val z: Float)

    private val pendingAcc = ArrayDeque<Point>()
    private val gyro = ArrayDeque<Point>()

    fun onAccelerometer(tNs: Long, x: Float, y: Float, z: Float) {
        pendingAcc.addLast(Point(tNs, x, y, z))
        drain()
    }

    fun onGyroscope(tNs: Long, x: Float, y: Float, z: Float) {
        if (gyro.isNotEmpty() && tNs <= gyro.last().t) return // non monotono: ignorato qui (contato nelle diagnostiche)
        gyro.addLast(Point(tNs, x, y, z))
        if (gyro.size > MAX_GYRO_HISTORY) gyro.removeFirst() // acc assente: evita crescita illimitata
        drain()
    }

    /** A fine registrazione: emette tutto ciò che è in attesa con il miglior dato disponibile. */
    fun flush() {
        while (pendingAcc.isNotEmpty()) emitOne(pendingAcc.removeFirst())
    }

    private fun drain() {
        while (pendingAcc.isNotEmpty()) {
            val a = pendingAcc.first()
            val newestGyro = gyro.lastOrNull()
            val gyroCovers = newestGyro != null && newestGyro.t >= a.t
            if (!gyroCovers) {
                val waited = pendingAcc.last().t - a.t
                if (gyroAvailable && waited < pendingTimeoutNs) return // aspetta il prossimo gyro
            }
            emitOne(pendingAcc.removeFirst())
        }
    }

    private fun emitOne(a: Point) {
        var gx = Float.NaN
        var gy = Float.NaN
        var gz = Float.NaN

        if (gyro.isNotEmpty()) {
            // Scarta i gyro che non servono più: teniamo g0 = ultimo campione con t ≤ a.t.
            while (gyro.size >= 2 && gyro[1].t <= a.t) gyro.removeFirst()
            val g0 = gyro[0]
            val g1 = if (gyro.size >= 2) gyro[1] else null
            val bracketed = g0.t <= a.t && g1 != null && g1.t >= a.t
            when {
                g0.t == a.t -> { gx = g0.x; gy = g0.y; gz = g0.z }
                bracketed -> if (g1!!.t - g0.t <= maxGyroGapNs) {
                    val w = ((a.t - g0.t).toDouble() / (g1.t - g0.t).toDouble()).toFloat()
                    gx = g0.x + (g1.x - g0.x) * w
                    gy = g0.y + (g1.y - g0.y) * w
                    gz = g0.z + (g1.z - g0.z) * w
                } // else: buco nel giroscopio -> NaN
                // Bordo (inizio registrazione: a.t precede tutti i gyro; oppure timeout/flush:
                // nessun gyro successivo). g0 è il campione più vicino: lo usiamo solo se entro
                // metà del gap massimo, altrimenti NaN.
                else -> if (abs(g0.t - a.t) <= maxGyroGapNs / 2) {
                    gx = g0.x; gy = g0.y; gz = g0.z
                }
            }
        }
        if (gx.isNaN()) samplesWithoutGyro++

        emit(
            SensorSample(
                sensorTimestampNs = a.t,
                unixTimestampMs = Math.floorDiv(a.t + unixOffsetNs, 1_000_000L),
                accX = a.x, accY = a.y, accZ = a.z,
                gyroX = gx, gyroY = gy, gyroZ = gz
            )
        )
    }

    private companion object {
        const val MAX_GYRO_HISTORY = 4096
    }
}

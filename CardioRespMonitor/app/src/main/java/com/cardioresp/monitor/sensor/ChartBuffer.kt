package com.cardioresp.monitor.sensor

import com.cardioresp.monitor.domain.model.ChartFrame

/**
 * Buffer circolare per la VISUALIZZAZIONE (separato dal percorso di registrazione).
 *
 * Disaccoppiamento rate acquisizione / rate rendering:
 * - il thread consumatore chiama [add] per OGNI campione (es. 200–500 Hz);
 * - la UI chiama [snapshot] al proprio ritmo (30 FPS) e ottiene una copia degli ultimi
 *   [windowNs] di segnale. Nessuno dei due aspetta l'altro più di qualche µs (lock breve).
 * La decimazione per il disegno (min/max per colonna di pixel) avviene nel componente grafico.
 */
class ChartBuffer(private val capacity: Int, private val windowNs: Long) {
    private val t = LongArray(capacity)
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val z = FloatArray(capacity)
    private var next = 0
    private var size = 0
    private val lock = Any()

    fun add(tNs: Long, vx: Float, vy: Float, vz: Float) = synchronized(lock) {
        t[next] = tNs; x[next] = vx; y[next] = vy; z[next] = vz
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    fun clear() = synchronized(lock) { next = 0; size = 0 }

    fun snapshot(): ChartFrame = synchronized(lock) {
        if (size == 0) return ChartFrame.EMPTY
        val newest = t[(next - 1 + capacity) % capacity]
        // Conta quanti campioni (a ritroso) cadono nella finestra temporale.
        var n = 0
        while (n < size) {
            val idx = (next - 1 - n + capacity) % capacity
            if (newest - t[idx] > windowNs) break
            n++
        }
        val ot = LongArray(n); val ox = FloatArray(n); val oy = FloatArray(n); val oz = FloatArray(n)
        val start = (next - n + capacity) % capacity
        for (k in 0 until n) {
            val idx = (start + k) % capacity
            ot[k] = t[idx]; ox[k] = x[idx]; oy[k] = y[idx]; oz[k] = z[idx]
        }
        ChartFrame(n, ot, ox, oy, oz)
    }
}

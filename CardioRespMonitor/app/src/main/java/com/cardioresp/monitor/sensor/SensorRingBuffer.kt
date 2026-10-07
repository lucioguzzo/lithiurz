package com.cardioresp.monitor.sensor

import java.util.concurrent.atomic.AtomicLong

/**
 * Ring buffer lock-free SPSC (Single Producer / Single Consumer) a struttura di array primitivi.
 *
 * PRODUTTORE: il thread del SensorEventListener (unico per ENTRAMBI i sensori, vedi
 * [SensorDataSource]); CONSUMATORE: il thread della pipeline ([com.cardioresp.monitor.data.AcquisitionEngine]).
 *
 * Perché un ring buffer e non un `Channel` in questo punto:
 * + zero allocazioni nel callback (niente oggetti -> niente GC -> niente pause sul thread sensore);
 * + `offer()` è O(1) con due letture atomiche e una `lazySet` (store-release), mai bloccante;
 * + capacità fissa e nota: 65 536 eventi = ~65 s di margine a 1 kHz complessivi;
 * - capacità fissa: se il consumatore si ferma più del margine, si perdono eventi (contati in [overflowCount]);
 * - non "sospende": il consumatore deve fare polling (qui ogni ~4 ms, costo trascurabile).
 *
 * Il `Channel` di Kotlin è invece usato più a valle (verso i processori futuri), dove
 * l'allocazione per oggetto è accettabile e la semantica suspend/backpressure è comoda.
 *
 * Politica di overflow: DROP-NEWEST (si scarta l'evento in arrivo). Così i dati già bufferizzati
 * restano contigui e il buco appare come un salto nei timestamp, rilevato dalle diagnostiche.
 */
class SensorRingBuffer(capacityPowerOfTwo: Int = 1 shl 16) {

    init {
        require(capacityPowerOfTwo > 0 && capacityPowerOfTwo and (capacityPowerOfTwo - 1) == 0) {
            "La capacità deve essere una potenza di 2"
        }
    }

    val capacity: Int = capacityPowerOfTwo
    private val mask: Long = (capacityPowerOfTwo - 1).toLong()

    private val sensorCode = IntArray(capacity)
    private val timestampNs = LongArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private val zs = FloatArray(capacity)

    /** Prossima posizione da scrivere (scritta solo dal produttore). */
    private val head = AtomicLong(0)
    /** Prossima posizione da leggere (scritta solo dal consumatore). */
    private val tail = AtomicLong(0)

    private val overflows = AtomicLong(0)
    @Volatile private var highWatermarkInternal = 0

    val overflowCount: Long get() = overflows.get()
    val highWatermark: Int get() = highWatermarkInternal

    /** Chiamato dal thread sensore. Non alloca, non blocca. */
    fun offer(sensor: Int, tNs: Long, x: Float, y: Float, z: Float): Boolean {
        val h = head.get()
        val used = h - tail.get()
        if (used >= capacity) {
            overflows.incrementAndGet()
            return false
        }
        val i = (h and mask).toInt()
        sensorCode[i] = sensor
        timestampNs[i] = tNs
        xs[i] = x
        ys[i] = y
        zs[i] = z
        // lazySet = store-release: i dati sopra diventano visibili al consumatore
        // prima (o insieme) al nuovo valore di head.
        head.lazySet(h + 1)
        val fill = (used + 1).toInt()
        if (fill > highWatermarkInternal) highWatermarkInternal = fill
        return true
    }

    fun interface Consumer {
        fun onEvent(sensor: Int, tNs: Long, x: Float, y: Float, z: Float)
    }

    /** Chiamato dal thread consumatore. Restituisce il numero di eventi letti. */
    fun drain(consumer: Consumer, maxEvents: Int = Int.MAX_VALUE): Int {
        val t = tail.get()
        val available = head.get() - t // get() = load-acquire
        val n = minOf(available, maxEvents.toLong()).toInt()
        for (k in 0 until n) {
            val i = ((t + k) and mask).toInt()
            consumer.onEvent(sensorCode[i], timestampNs[i], xs[i], ys[i], zs[i])
        }
        if (n > 0) tail.lazySet(t + n)
        return n
    }

    fun size(): Int = (head.get() - tail.get()).toInt()
}

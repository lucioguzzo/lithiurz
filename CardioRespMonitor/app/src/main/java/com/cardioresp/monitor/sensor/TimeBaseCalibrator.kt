package com.cardioresp.monitor.sensor

import android.os.SystemClock
import kotlin.math.abs

/**
 * Gestione dei clock. Riassunto (dettagli in docs/TIMESTAMPS.md):
 *
 * - `SensorEvent.timestamp` (ns): istante di campionamento fornito da HAL/sensor hub.
 *   Su Android moderno è nel time base di `SystemClock.elapsedRealtimeNanos()` (monotono,
 *   include il deep sleep, NON regolato da NTP). Su alcuni device vecchi era `System.nanoTime()`
 *   (= uptime, si ferma in deep sleep). Per sicurezza lo RILEVIAMO al primo evento.
 * - `System.currentTimeMillis()`: wall clock UTC. Può saltare avanti/indietro (NTP, cambio
 *   manuale, fuso). Risoluzione 1 ms. NON va mai usato per misurare intervalli tra campioni.
 *
 * Conversione: unix_ns = event.timestamp + offsetNs, con offsetNs misurato una volta sola
 * all'avvio come (currentTimeMillis·1e6 − elapsedRealtimeNanos). Usare un offset fisso
 * preserva l'uniformità della timeline; la deriva tra i due clock viene misurata di nuovo
 * allo stop e salvata nei metadati per poterla correggere offline se necessario.
 */
class TimeBaseCalibrator {

    enum class TimeBase { ELAPSED_REALTIME, UPTIME_NANOTIME }

    data class OffsetMeasurement(
        /** unix_ns − clock_ns. */
        val offsetNs: Long,
        /** Larghezza della finestra di misura = incertezza massima sull'offset. */
        val uncertaintyNs: Long,
        val measuredAtUnixMs: Long
    )

    /**
     * Misura l'offset tra wall clock e clock monotono prendendo, su N tentativi, la coppia
     * con la finestra (t1 − t0) più stretta (riduce l'effetto di preemption tra le due letture).
     */
    fun measureOffset(base: TimeBase, attempts: Int = 15): OffsetMeasurement {
        var bestSpan = Long.MAX_VALUE
        var bestOffset = 0L
        var bestWall = 0L
        repeat(attempts) {
            val t0 = now(base)
            val wallMs = System.currentTimeMillis()
            val t1 = now(base)
            val span = t1 - t0
            if (span < bestSpan) {
                bestSpan = span
                bestWall = wallMs
                // Il wall clock è stato letto tra t0 e t1: usiamo il punto medio.
                bestOffset = wallMs * 1_000_000L - (t0 + span / 2)
            }
        }
        // Aggiungiamo 1 ms di incertezza intrinseca della risoluzione di currentTimeMillis.
        return OffsetMeasurement(bestOffset, bestSpan + 1_000_000L, bestWall)
    }

    /**
     * Determina il time base confrontando il timestamp del primo evento con i due clock
     * letti subito dopo la sua ricezione. L'evento è nel passato di pochi ms (latenza di consegna),
     * quindi il clock "giusto" è quello con differenza minima. Se i due clock coincidono
     * (device mai andato in deep sleep dal boot) la scelta è indifferente: default ELAPSED_REALTIME.
     */
    fun detect(firstEventTimestampNs: Long): TimeBase {
        val dElapsed = abs(SystemClock.elapsedRealtimeNanos() - firstEventTimestampNs)
        val dUptime = abs(System.nanoTime() - firstEventTimestampNs)
        return if (dUptime + AMBIGUITY_NS < dElapsed) TimeBase.UPTIME_NANOTIME else TimeBase.ELAPSED_REALTIME
    }

    fun now(base: TimeBase): Long = when (base) {
        TimeBase.ELAPSED_REALTIME -> SystemClock.elapsedRealtimeNanos()
        TimeBase.UPTIME_NANOTIME -> System.nanoTime()
    }

    private companion object {
        const val AMBIGUITY_NS = 50_000_000L // 50 ms
    }
}

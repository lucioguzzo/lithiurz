package com.cardioresp.monitor.future_algorithms

import com.cardioresp.monitor.domain.model.SensorSample

/**
 * Punto di estensione per TUTTI gli algoritmi futuri.
 *
 * Contratto:
 * - [process] viene chiamato per ogni campione combinato, in ordine di timestamp, sempre dallo
 *   STESSO thread dedicato ("SignalProcessing"): l'implementazione può avere stato mutabile
 *   senza sincronizzazione.
 * - NON viene mai chiamato dal thread sensori né dal thread di scrittura CSV: un algoritmo lento
 *   non può causare perdita di campioni nella registrazione (al massimo perde campioni lui stesso,
 *   contati in `processorQueueDrops`).
 * - Usare SEMPRE `sample.sensorTimestampNs` per la base temporale (mai l'indice del campione):
 *   la frequenza reale non è esattamente quella nominale.
 */
interface SignalProcessor {
    fun process(sample: SensorSample)

    /** Nome leggibile (log, metadati, UI). */
    val name: String get() = javaClass.simpleName

    /** Chiamato a inizio sessione: resettare qui buffer e stato. */
    fun onSessionStart(context: ProcessingContext) {}

    /** Chiamato a fine sessione, dopo l'ultimo campione. */
    fun onSessionEnd() {}
}

/** Informazioni di sessione utili agli algoritmi. */
data class ProcessingContext(
    /** Frequenza richiesta (può differire dalla reale: stimarla dai timestamp o usare [com.cardioresp.monitor.future_algorithms.dsp.UniformResampler]). */
    val requestedRateHz: Double?,
    val sessionId: String
)

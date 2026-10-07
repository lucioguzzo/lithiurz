package com.cardioresp.monitor.domain.model

/**
 * Output dei futuri algoritmi cardiorespiratori (NON ancora implementati).
 * Ogni stima porta con sé l'istante di riferimento e un indice di qualità, indispensabili
 * per la validazione scientifica contro un gold standard (ECG / fascia respiratoria).
 */
data class HeartRateEstimate(
    /** Istante (time base sensore) al centro della finestra di analisi. */
    val sensorTimestampNs: Long,
    val bpm: Float,
    /** 0..1: qualità del segnale / confidenza (es. SNR spettrale, periodicità). */
    val confidence: Float,
    val windowSeconds: Float,
    val algorithm: String
)

data class RespirationRateEstimate(
    val sensorTimestampNs: Long,
    val breathsPerMinute: Float,
    val confidence: Float,
    val windowSeconds: Float,
    val algorithm: String
)

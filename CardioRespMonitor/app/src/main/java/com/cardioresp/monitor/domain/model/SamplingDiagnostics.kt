package com.cardioresp.monitor.domain.model

/**
 * Metriche di qualità temporale per UN flusso sensore (calcolate sui timestamp hardware,
 * quindi misurano la regolarità reale del campionamento e non il ritardo di consegna).
 */
data class StreamDiagnostics(
    val sensor: SensorKind,
    val sampleCount: Long = 0,
    /** (N-1) / (t_last - t_first). */
    val effectiveRateHz: Double = 0.0,
    /** Periodo nominale stimato (mediana dei primi intervalli), in ms. */
    val nominalPeriodMs: Double = 0.0,
    val meanIntervalMs: Double = 0.0,
    /** Deviazione standard dell'intervallo = jitter RMS. */
    val stdIntervalMs: Double = 0.0,
    val minIntervalMs: Double = 0.0,
    val maxIntervalMs: Double = 0.0,
    /** Jitter picco-picco rispetto al periodo nominale (max |dt - T|), escludendo i buchi. */
    val peakJitterMs: Double = 0.0,
    /** Campioni mancanti stimati dai buchi temporali (dt > 1.5 T). */
    val estimatedLostSamples: Long = 0,
    /** Numero di buchi rilevati. */
    val gapCount: Long = 0,
    /** Timestamp non crescenti (duplicati o all'indietro): deve restare 0. */
    val nonMonotonicCount: Long = 0
)

/** Snapshot complessivo della pipeline, pubblicato ~2 volte al secondo. */
data class SamplingDiagnostics(
    val accelerometer: StreamDiagnostics = StreamDiagnostics(SensorKind.ACCELEROMETER),
    val gyroscope: StreamDiagnostics = StreamDiagnostics(SensorKind.GYROSCOPE),
    /** Campioni combinati scritti su CSV. */
    val combinedSamples: Long = 0,
    /** Eventi persi lato app per overflow del ring buffer (deve restare 0). */
    val ringBufferOverflows: Long = 0,
    /** Massimo riempimento osservato del ring buffer (indicatore di margine). */
    val ringBufferHighWatermark: Int = 0,
    val ringBufferCapacity: Int = 0,
    /** Campioni combinati senza giroscopio valido (gyro = NaN). */
    val samplesWithoutGyro: Long = 0,
    /** Campioni scartati dalla coda dei processori futuri (non impatta la registrazione). */
    val processorQueueDrops: Long = 0,
    /** Durata della registrazione misurata sui timestamp sensore. */
    val durationMs: Long = 0,
    /** Time base rilevato per event.timestamp. */
    val timeBase: String = "-"
)

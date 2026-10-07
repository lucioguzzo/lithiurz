package com.cardioresp.monitor.future_algorithms.heart

import com.cardioresp.monitor.domain.model.HeartRateEstimate
import com.cardioresp.monitor.domain.model.SensorSample
import com.cardioresp.monitor.future_algorithms.HeartRateProcessor
import com.cardioresp.monitor.future_algorithms.ProcessingContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SEGNAPOSTO — nessun algoritmo implementato (requisito di questa fase).
 *
 * Dove e come inserire l'algoritmo reale (es. sismocardiografia SCG su acc_z +
 * giroscardiografia GCG su gyro_x/gyro_y, paziente supino, telefono sul torace):
 *  1. in [onSessionStart]: allocare una finestra scorrevole (es. 10 s) e i filtri
 *     (passa-banda 5–30 Hz per SCG, inviluppo, poi 0.7–3 Hz per il battito);
 *  2. in [process]: accodare il campione; ogni N secondi (es. hop 1 s)
 *     - ricampionare a griglia uniforme con `dsp.UniformResampler`,
 *     - filtrare, calcolare autocorrelazione/spettro, stimare i BPM e un indice di qualità,
 *     - pubblicare `_estimates.value = HeartRateEstimate(...)`.
 *  3. ATTENZIONE: [process] gira sul thread "SignalProcessing": calcoli pesanti ammessi,
 *     ma oltre ~30 s di ritardo accumulato la coda scarta campioni (vedi processorQueueDrops).
 */
class PlaceholderHeartRateProcessor : HeartRateProcessor {
    private val _estimates = MutableStateFlow<HeartRateEstimate?>(null)
    override val estimates: StateFlow<HeartRateEstimate?> = _estimates

    override fun onSessionStart(context: ProcessingContext) {
        _estimates.value = null
    }

    override fun process(sample: SensorSample) {
        // Intenzionalmente vuoto.
    }
}

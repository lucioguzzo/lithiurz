package com.cardioresp.monitor.future_algorithms.respiration

import com.cardioresp.monitor.domain.model.RespirationRateEstimate
import com.cardioresp.monitor.domain.model.SensorSample
import com.cardioresp.monitor.future_algorithms.ProcessingContext
import com.cardioresp.monitor.future_algorithms.RespirationRateProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * SEGNAPOSTO — nessun algoritmo implementato.
 *
 * Schema tipico per l'algoritmo reale:
 *  - decimare a ~10–25 Hz (anti-aliasing!) e filtrare passa-banda 0.1–0.7 Hz (6–42 atti/min);
 *  - asse: proiezione della gravità (acc) sull'asse di massima varianza (PCA) o rotazione
 *    toracica dal giroscopio integrato;
 *  - finestra 30–60 s, stima per picco spettrale / zero-crossing / autocorrelazione;
 *  - pubblicare `_estimates.value = RespirationRateEstimate(...)` con confidenza.
 * Preferire i sensori UNCALIBRATED: la calibrazione automatica del bias del giroscopio
 * può introdurre gradini proprio nella banda respiratoria.
 */
class PlaceholderRespirationRateProcessor : RespirationRateProcessor {
    private val _estimates = MutableStateFlow<RespirationRateEstimate?>(null)
    override val estimates: StateFlow<RespirationRateEstimate?> = _estimates

    override fun onSessionStart(context: ProcessingContext) {
        _estimates.value = null
    }

    override fun process(sample: SensorSample) {
        // Intenzionalmente vuoto.
    }
}

package com.cardioresp.monitor.domain.repository

import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.AcquisitionState
import com.cardioresp.monitor.domain.model.ChartSnapshot
import com.cardioresp.monitor.domain.model.HeartRateEstimate
import com.cardioresp.monitor.domain.model.RespirationRateEstimate
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import kotlinx.coroutines.flow.StateFlow

/**
 * Contratto del dominio verso la pipeline di acquisizione.
 * La presentation dipende SOLO da questa interfaccia, mai da SensorManager o dal Service.
 */
interface AcquisitionRepository {
    val state: StateFlow<AcquisitionState>
    val diagnostics: StateFlow<SamplingDiagnostics>

    /** Stime future: restano null finché non viene registrato un algoritmo reale. */
    val heartRate: StateFlow<HeartRateEstimate?>
    val respirationRate: StateFlow<RespirationRateEstimate?>

    /** Chiamato SOLO dal Foreground Service (che garantisce processo vivo e wake lock). */
    suspend fun start(config: AcquisitionConfig)
    suspend fun stop()

    /**
     * Copia (pull) degli ultimi secondi di segnale per il grafico. La UI la chiama
     * al proprio frame rate (20–30 FPS), indipendente dal rate di acquisizione.
     */
    fun chartSnapshot(): ChartSnapshot
}

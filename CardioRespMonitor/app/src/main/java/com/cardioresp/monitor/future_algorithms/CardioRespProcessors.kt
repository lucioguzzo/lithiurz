package com.cardioresp.monitor.future_algorithms

import com.cardioresp.monitor.domain.model.HeartRateEstimate
import com.cardioresp.monitor.domain.model.RespirationRateEstimate
import kotlinx.coroutines.flow.StateFlow

/**
 * Specializzazioni di [SignalProcessor] che producono stime cardiorespiratorie.
 * Il repository raccoglie automaticamente le [estimates] di qualunque processore registrato
 * che implementi una di queste interfacce e le espone alla UI: nessuna modifica necessaria
 * a ViewModel, Service o storage per aggiungere un nuovo algoritmo.
 */
interface HeartRateProcessor : SignalProcessor {
    val estimates: StateFlow<HeartRateEstimate?>
}

interface RespirationRateProcessor : SignalProcessor {
    val estimates: StateFlow<RespirationRateEstimate?>
}

package com.cardioresp.monitor.future_algorithms

import com.cardioresp.monitor.future_algorithms.heart.PlaceholderHeartRateProcessor
import com.cardioresp.monitor.future_algorithms.respiration.PlaceholderRespirationRateProcessor

/**
 * >>> UNICO PUNTO DA MODIFICARE PER ATTIVARE UN NUOVO ALGORITMO <<<
 *
 * Per aggiungere ad es. un algoritmo SCG per la frequenza cardiaca:
 *   1. creare `future_algorithms/heart/ScgHeartRateProcessor.kt` che implementa [HeartRateProcessor];
 *   2. sostituire qui `PlaceholderHeartRateProcessor()` con `ScgHeartRateProcessor()`.
 * La UI mostrerà automaticamente le stime; nulla nel resto dell'app va toccato.
 */
object ProcessorRegistry {
    fun createProcessors(): List<SignalProcessor> = listOf(
        PlaceholderHeartRateProcessor(),
        PlaceholderRespirationRateProcessor()
    )
}

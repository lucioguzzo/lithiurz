package com.cardioresp.monitor.domain.model

/**
 * Campione combinato accelerometro + giroscopio allineato sullo stesso istante.
 *
 * - [sensorTimestampNs]: timestamp hardware/HAL dell'evento ACCELEROMETRO (clock "master"),
 *   nel time base di `SystemClock.elapsedRealtimeNanos()`. È monotono, non salta con NTP,
 *   continua a contare in deep sleep. È l'unico riferimento valido per la DSP.
 * - [unixTimestampMs]: lo stesso istante convertito in tempo UTC (epoch ms) tramite un offset
 *   misurato UNA SOLA VOLTA all'avvio (vedi `TimeBaseCalibrator`). Serve per sincronizzarsi
 *   con dispositivi esterni (ECG di riferimento, video, ecc.), NON per calcolare intervalli.
 * - acc*: m/s² (include gravità).
 * - gyro*: rad/s, interpolati linearmente all'istante [sensorTimestampNs].
 *   Valgono `Float.NaN` se il giroscopio non era disponibile o aveva un buco > soglia.
 */
data class SensorSample(
    val sensorTimestampNs: Long,
    val unixTimestampMs: Long,

    val accX: Float,
    val accY: Float,
    val accZ: Float,

    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
)

package com.cardioresp.monitor.domain.model

/**
 * Configurazione di una sessione di acquisizione.
 *
 * @param samplingRate frequenza richiesta. NB: Android tratta il periodo come un *suggerimento*;
 *        la frequenza reale dipende dall'ODR del chip IMU (es. 200 Hz richiesti -> 208 Hz reali).
 *        Per questo la frequenza effettiva viene sempre misurata e salvata nei metadati.
 * @param maxReportLatencyUs 0 = nessun batching (consegna immediata, migliore per il real-time).
 *        Valori > 0 abilitano la FIFO hardware: i timestamp restano quelli hardware (nessun
 *        jitter aggiunto) e si riduce il rischio di perdita campioni se la CPU va in sleep.
 * @param useUncalibratedSensors usa TYPE_*_UNCALIBRATED: evita che la compensazione automatica
 *        del bias del giroscopio introduca gradini/derive nella banda respiratoria (0.1–0.7 Hz).
 */
data class AcquisitionConfig(
    val samplingRate: SamplingRate = SamplingRate.HZ_200,
    val maxReportLatencyUs: Int = 0,
    val useUncalibratedSensors: Boolean = false,
    val writeRawEvents: Boolean = true
)

enum class SamplingRate(val label: String, val periodUs: Int) {
    /** SENSOR_DELAY_FASTEST: periodo 0 -> massima ODR supportata (richiede HIGH_SAMPLING_RATE_SENSORS oltre 200 Hz su Android 12+). */
    FASTEST("Fastest", 0),
    HZ_500("500 Hz", 2_000),
    HZ_200("200 Hz", 5_000),
    HZ_100("100 Hz", 10_000),
    HZ_50("50 Hz", 20_000);

    /** Frequenza nominale richiesta (null per FASTEST, dove dipende dal device). */
    val nominalHz: Double? get() = if (periodUs == 0) null else 1_000_000.0 / periodUs
}

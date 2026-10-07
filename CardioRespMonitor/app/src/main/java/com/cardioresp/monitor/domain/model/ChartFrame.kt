package com.cardioresp.monitor.domain.model

/**
 * Finestra di dati per il rendering. Array primitivi copiati dal buffer di visualizzazione:
 * la UI lavora su una copia immutabile, mai sui buffer scritti dal thread di acquisizione.
 */
class ChartFrame(
    val size: Int,
    val tNs: LongArray,
    val x: FloatArray,
    val y: FloatArray,
    val z: FloatArray
) {
    companion object {
        val EMPTY = ChartFrame(0, LongArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
    }
}

data class ChartSnapshot(
    val accelerometer: ChartFrame = ChartFrame.EMPTY,
    val gyroscope: ChartFrame = ChartFrame.EMPTY
)

package com.cardioresp.monitor.domain.model

/** Sensori acquisiti. Codice intero compatto usato nel ring buffer (niente oggetti nel callback). */
enum class SensorKind(val code: Int, val label: String) {
    ACCELEROMETER(0, "acc"),
    GYROSCOPE(1, "gyro");

    companion object {
        fun fromCode(code: Int): SensorKind = if (code == 0) ACCELEROMETER else GYROSCOPE
    }
}

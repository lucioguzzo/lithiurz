package com.cardioresp.monitor.domain.model

import java.io.File

sealed interface AcquisitionState {
    data object Idle : AcquisitionState
    data class Recording(val session: RecordingSession) : AcquisitionState
    data object Stopping : AcquisitionState
    data class Error(val message: String) : AcquisitionState
}

/** Una sessione registrata: cartella con samples.csv, raw_events.csv, metadata.json. */
data class RecordingSession(
    val id: String,
    val directory: File,
    val startUnixMs: Long,
    val config: AcquisitionConfig
) {
    val samplesCsv: File get() = File(directory, SAMPLES_FILE)
    val rawEventsCsv: File get() = File(directory, RAW_FILE)
    val metadataJson: File get() = File(directory, METADATA_FILE)

    companion object {
        const val SAMPLES_FILE = "samples.csv"
        const val RAW_FILE = "raw_events.csv"
        const val METADATA_FILE = "metadata.json"
    }
}

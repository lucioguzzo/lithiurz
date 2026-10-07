package com.cardioresp.monitor.domain.usecase

import android.net.Uri
import com.cardioresp.monitor.domain.model.RecordingSession
import com.cardioresp.monitor.domain.repository.AcquisitionRepository
import com.cardioresp.monitor.domain.repository.RecordingRepository

/*
 * Use case sottili: oggi delegano quasi 1:1, ma isolano la presentation dai repository
 * e sono il punto dove aggiungere regole di business (es. "non esportare sessioni < 10 s",
 * "allega le stime HR/RR all'export") senza toccare ViewModel o storage.
 */

class ObserveAcquisitionUseCase(private val repo: AcquisitionRepository) {
    val state get() = repo.state
    val diagnostics get() = repo.diagnostics
    val heartRate get() = repo.heartRate
    val respirationRate get() = repo.respirationRate
    fun chartSnapshot() = repo.chartSnapshot()
}

class GetLastSessionUseCase(private val repo: RecordingRepository) {
    operator fun invoke(): RecordingSession? = repo.lastSession()
}

class ExportRecordingUseCase(private val repo: RecordingRepository) {
    suspend fun csv(session: RecordingSession, destination: Uri) = repo.exportCsv(session, destination)
    suspend fun zip(session: RecordingSession, destination: Uri) = repo.exportZip(session, destination)
}

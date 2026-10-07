package com.cardioresp.monitor.di

import android.content.Context
import com.cardioresp.monitor.data.AcquisitionRepositoryImpl
import com.cardioresp.monitor.data.RecordingRepositoryImpl
import com.cardioresp.monitor.domain.repository.AcquisitionRepository
import com.cardioresp.monitor.domain.repository.RecordingRepository
import com.cardioresp.monitor.domain.usecase.ExportRecordingUseCase
import com.cardioresp.monitor.domain.usecase.GetLastSessionUseCase
import com.cardioresp.monitor.domain.usecase.ObserveAcquisitionUseCase
import com.cardioresp.monitor.future_algorithms.ProcessorRegistry
import com.cardioresp.monitor.sensor.SensorDataSource
import com.cardioresp.monitor.storage.SafExporter
import com.cardioresp.monitor.storage.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Dependency injection manuale (nessun framework): il grafo è piccolo e così il progetto
 * compila senza annotation processing. Migrare a Hilt è banale: ogni `val` diventa un @Provides.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Scope dell'intero processo: sopravvive a Activity/ViewModel (la registrazione non dipende dalla UI). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessionStorage = SessionStorage(appContext)

    val acquisitionRepository: AcquisitionRepository = AcquisitionRepositoryImpl(
        sensorSource = SensorDataSource(appContext),
        storage = sessionStorage,
        processors = ProcessorRegistry.createProcessors(),
        appScope = applicationScope
    )

    val recordingRepository: RecordingRepository =
        RecordingRepositoryImpl(sessionStorage, SafExporter(appContext.contentResolver))

    val observeAcquisition = ObserveAcquisitionUseCase(acquisitionRepository)
    val getLastSession = GetLastSessionUseCase(recordingRepository)
    val exportRecording = ExportRecordingUseCase(recordingRepository)
}

package com.cardioresp.monitor.data

import android.os.Process
import android.util.Log
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.AcquisitionState
import com.cardioresp.monitor.domain.model.ChartSnapshot
import com.cardioresp.monitor.domain.model.HeartRateEstimate
import com.cardioresp.monitor.domain.model.RespirationRateEstimate
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import com.cardioresp.monitor.domain.repository.AcquisitionRepository
import com.cardioresp.monitor.future_algorithms.HeartRateProcessor
import com.cardioresp.monitor.future_algorithms.RespirationRateProcessor
import com.cardioresp.monitor.future_algorithms.SignalProcessingPipeline
import com.cardioresp.monitor.future_algorithms.SignalProcessor
import com.cardioresp.monitor.sensor.ChartBuffer
import com.cardioresp.monitor.sensor.SensorDataSource
import com.cardioresp.monitor.storage.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

/**
 * Implementazione singleton (vive nell'AppContainer, quindi sopravvive ad Activity e ViewModel).
 * Il Foreground Service la avvia/ferma; la UI la osserva.
 */
class AcquisitionRepositoryImpl(
    private val sensorSource: SensorDataSource,
    private val storage: SessionStorage,
    processors: List<SignalProcessor>,
    private val appScope: CoroutineScope
) : AcquisitionRepository {

    /**
     * Thread dedicato per drenare il ring buffer, allineare e scrivere su file.
     * Un Executor single-thread come CoroutineDispatcher garantisce che TUTTO il lavoro della
     * pipeline sia seriale sullo stesso thread (niente lock) e isolato da Dispatchers.Default/IO,
     * che sono condivisi con il resto dell'app e potrebbero essere saturati da altro lavoro.
     */
    private val pipelineDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
                } catch (e: Exception) {
                    Log.w(TAG, "Priorità pipeline non impostata", e)
                }
                r.run()
            }, "AcquisitionPipeline")
        }.asCoroutineDispatcher()

    private val processing = SignalProcessingPipeline(processors)

    // 5 s di finestra; 8192 slot coprono fino a ~1.6 kHz.
    private val accChart = ChartBuffer(capacity = 8192, windowNs = CHART_WINDOW_NS)
    private val gyroChart = ChartBuffer(capacity = 8192, windowNs = CHART_WINDOW_NS)

    private val _state = MutableStateFlow<AcquisitionState>(AcquisitionState.Idle)
    override val state: StateFlow<AcquisitionState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(SamplingDiagnostics())
    override val diagnostics: StateFlow<SamplingDiagnostics> = _diagnostics.asStateFlow()

    // Le stime arrivano dal primo processore registrato del tipo giusto; se nessuno, null per sempre.
    override val heartRate: StateFlow<HeartRateEstimate?> =
        processors.filterIsInstance<HeartRateProcessor>().firstOrNull()?.estimates ?: MutableStateFlow(null)
    override val respirationRate: StateFlow<RespirationRateEstimate?> =
        processors.filterIsInstance<RespirationRateProcessor>().firstOrNull()?.estimates ?: MutableStateFlow(null)

    private val mutex = Mutex()
    private var current: AcquisitionSession? = null

    override suspend fun start(config: AcquisitionConfig) = mutex.withLock {
        if (current != null) return@withLock
        try {
            val recording = storage.createSession(config)
            _diagnostics.value = SamplingDiagnostics()
            val s = AcquisitionSession(
                session = recording,
                config = config,
                sensorSource = sensorSource,
                processing = processing,
                accChart = accChart,
                gyroChart = gyroChart,
                dispatcher = pipelineDispatcher,
                publishDiagnostics = { _diagnostics.value = it }
            )
            s.start(appScope)
            current = s
            _state.value = AcquisitionState.Recording(recording)
        } catch (e: Exception) {
            Log.e(TAG, "Avvio acquisizione fallito", e)
            _state.value = AcquisitionState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun stop() = mutex.withLock {
        val s = current ?: return@withLock
        _state.value = AcquisitionState.Stopping
        try {
            s.stop()
            _state.value = AcquisitionState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Stop acquisizione fallito", e)
            _state.value = AcquisitionState.Error(e.message ?: e.javaClass.simpleName)
        } finally {
            current = null
        }
    }

    override fun chartSnapshot() = ChartSnapshot(accChart.snapshot(), gyroChart.snapshot())

    private companion object {
        const val TAG = "AcquisitionRepository"
        const val CHART_WINDOW_NS = 5_000_000_000L
    }
}

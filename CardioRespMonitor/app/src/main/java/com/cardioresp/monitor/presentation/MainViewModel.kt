package com.cardioresp.monitor.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cardioresp.monitor.CardioRespApp
import com.cardioresp.monitor.domain.model.AcquisitionConfig
import com.cardioresp.monitor.domain.model.AcquisitionState
import com.cardioresp.monitor.domain.model.ChartSnapshot
import com.cardioresp.monitor.domain.model.HeartRateEstimate
import com.cardioresp.monitor.domain.model.RecordingSession
import com.cardioresp.monitor.domain.model.RespirationRateEstimate
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import com.cardioresp.monitor.domain.model.SamplingRate
import com.cardioresp.monitor.domain.usecase.ExportRecordingUseCase
import com.cardioresp.monitor.domain.usecase.GetLastSessionUseCase
import com.cardioresp.monitor.domain.usecase.ObserveAcquisitionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Impostazioni scelte dall'utente prima dello START. */
data class SettingsState(
    val samplingRate: SamplingRate = SamplingRate.HZ_200,
    val batching: Boolean = false,
    val uncalibrated: Boolean = false
) {
    fun toConfig() = AcquisitionConfig(
        samplingRate = samplingRate,
        maxReportLatencyUs = if (batching) 100_000 else 0,
        useUncalibratedSensors = uncalibrated
    )
}

data class MainUiState(
    val acquisition: AcquisitionState = AcquisitionState.Idle,
    val diagnostics: SamplingDiagnostics = SamplingDiagnostics(),
    val settings: SettingsState = SettingsState(),
    val lastSession: RecordingSession? = null,
    val heartRate: HeartRateEstimate? = null,
    val respirationRate: RespirationRateEstimate? = null,
    val message: String? = null
) {
    val isRecording get() = acquisition is AcquisitionState.Recording
    val isBusy get() = acquisition is AcquisitionState.Stopping
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val observe: ObserveAcquisitionUseCase,
    private val getLastSession: GetLastSessionUseCase,
    private val export: ExportRecordingUseCase
) : ViewModel() {

    private val settings = MutableStateFlow(SettingsState())
    private val lastSession = MutableStateFlow<RecordingSession?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        observe.state, observe.diagnostics, settings, lastSession,
        combine(observe.heartRate, observe.respirationRate, message, ::Triple)
    ) { state, diag, set, last, (hr, rr, msg) ->
        MainUiState(state, diag, set, last, hr, rr, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /**
     * SEPARAZIONE RATE ACQUISIZIONE / RATE RENDERING.
     * Mentre si registra, un ticker a ~30 FPS "tira" uno snapshot dal buffer grafico; il thread
     * di acquisizione non sa nulla della UI e scrive a 200–500 Hz. Da fermo si emette un solo
     * snapshot (nessun consumo). Con WhileSubscribed il ticker si ferma se la UI non è visibile,
     * mentre l'acquisizione nel Service continua.
     */
    val chart: StateFlow<ChartSnapshot> = observe.state
        .map { it is AcquisitionState.Recording }
        .distinctUntilChanged()
        .flatMapLatest { recording ->
            if (recording) flow {
                while (true) {
                    emit(observe.chartSnapshot())
                    delay(FRAME_PERIOD_MS)
                }
            }.flowOn(Dispatchers.Default)
            else flowOf(observe.chartSnapshot())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartSnapshot())

    init {
        refreshLastSession()
        // Quando una registrazione termina, aggiorna la sessione esportabile.
        viewModelScope.launch {
            observe.state.collect { if (it is AcquisitionState.Idle) refreshLastSession() }
        }
    }

    fun onRateSelected(rate: SamplingRate) = settings.update { it.copy(samplingRate = rate) }
    fun onBatchingChanged(v: Boolean) = settings.update { it.copy(batching = v) }
    fun onUncalibratedChanged(v: Boolean) = settings.update { it.copy(uncalibrated = v) }
    fun currentConfig(): AcquisitionConfig = settings.value.toConfig()
    fun consumeMessage() { message.value = null }

    fun exportCsv(uri: Uri) = runExport { export.csv(it, uri) }
    fun exportZip(uri: Uri) = runExport { export.zip(it, uri) }

    fun suggestedCsvName() = "cardioresp_${lastSession.value?.id ?: "session"}.csv"
    fun suggestedZipName() = "cardioresp_${lastSession.value?.id ?: "session"}.zip"

    private fun runExport(block: suspend (RecordingSession) -> Unit) {
        val session = lastSession.value ?: return
        viewModelScope.launch {
            message.value = try {
                block(session)
                "Export completato"
            } catch (e: Exception) {
                "Export fallito: ${e.message}"
            }
        }
    }

    private fun refreshLastSession() {
        viewModelScope.launch {
            lastSession.value = withContext(Dispatchers.IO) { getLastSession() }
        }
    }

    companion object {
        private const val FRAME_PERIOD_MS = 33L // ~30 FPS

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CardioRespApp
                val c = app.container
                MainViewModel(c.observeAcquisition, c.getLastSession, c.exportRecording)
            }
        }
    }
}

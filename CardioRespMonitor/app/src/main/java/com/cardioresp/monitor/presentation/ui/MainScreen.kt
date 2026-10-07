package com.cardioresp.monitor.presentation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardioresp.monitor.domain.model.AcquisitionState
import com.cardioresp.monitor.domain.model.SamplingRate
import com.cardioresp.monitor.presentation.MainUiState
import com.cardioresp.monitor.presentation.MainViewModel
import com.cardioresp.monitor.presentation.ui.components.DiagnosticsPanel
import com.cardioresp.monitor.presentation.ui.components.RealtimeChart
import com.cardioresp.monitor.presentation.ui.theme.RecordingRed

private const val CHART_WINDOW_NS = 5_000_000_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    notificationPermission: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // POST_NOTIFICATIONS (Android 13+): richiesto allo START. Se negato l'acquisizione parte
    // comunque (il FGS funziona), ma la notifica non sarà visibile nel cassetto.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStart() }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportZip)
    }

    LaunchedEffect(ui.message) {
        ui.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CardioResp Monitor") }, actions = { RecordingIndicator(ui.isRecording) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Counters(ui)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (notificationPermission != null) permissionLauncher.launch(notificationPermission) else onStart() },
                    enabled = !ui.isRecording && !ui.isBusy,
                    modifier = Modifier.weight(1f)
                ) { Text("START") }
                Button(
                    onClick = onStop,
                    enabled = ui.isRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                    modifier = Modifier.weight(1f)
                ) { Text("STOP") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canExport = ui.lastSession != null && !ui.isRecording && !ui.isBusy
                OutlinedButton(onClick = { csvLauncher.launch(viewModel.suggestedCsvName()) }, enabled = canExport, modifier = Modifier.weight(1f)) {
                    Text("EXPORT CSV")
                }
                OutlinedButton(onClick = { zipLauncher.launch(viewModel.suggestedZipName()) }, enabled = canExport, modifier = Modifier.weight(1f)) {
                    Text("EXPORT ZIP")
                }
            }
            ui.lastSession?.let {
                Text("Ultima sessione: ${it.id}", style = MaterialTheme.typography.labelMedium)
            }
            (ui.acquisition as? AcquisitionState.Error)?.let {
                Text("Errore: ${it.message}", color = MaterialTheme.colorScheme.error)
            }

            Card { RealtimeChart("Accelerometro", "m/s²", chart.accelerometer, CHART_WINDOW_NS, minSpan = 0.5f, modifier = Modifier.padding(12.dp)) }
            Card { RealtimeChart("Giroscopio", "rad/s", chart.gyroscope, CHART_WINDOW_NS, minSpan = 0.1f, modifier = Modifier.padding(12.dp)) }
            Card { DiagnosticsPanel(ui.diagnostics, Modifier.padding(12.dp)) }
            Card { Settings(ui, viewModel, Modifier.padding(12.dp)) }
            Card { FutureEstimates(ui, Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun RecordingIndicator(recording: Boolean) {
    if (!recording) return
    val alpha by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recAlpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        Canvas(Modifier.size(12.dp).alpha(alpha)) { drawCircle(RecordingRed) }
        Text(" RECORDING", color = RecordingRed, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Counters(ui: MainUiState) {
    val d = ui.diagnostics
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Counter("Campioni", d.combinedSamples.toString())
        Counter("Freq. eff.", "%.1f Hz".format(d.accelerometer.effectiveRateHz))
        Counter("Durata", formatDuration(d.durationMs))
    }
}

@Composable
private fun Counter(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Settings(ui: MainUiState, vm: MainViewModel, modifier: Modifier) {
    val enabled = !ui.isRecording && !ui.isBusy
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Impostazioni acquisizione", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SamplingRate.entries.forEach { rate ->
                FilterChip(
                    selected = ui.settings.samplingRate == rate,
                    onClick = { vm.onRateSelected(rate) },
                    enabled = enabled,
                    label = { Text(rate.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        SwitchRow("Sensori UNCALIBRATED (no correzione bias)", ui.settings.uncalibrated, enabled, vm::onUncalibratedChanged)
        SwitchRow("Batching FIFO hardware (100 ms)", ui.settings.batching, enabled, vm::onBatchingChanged)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun FutureEstimates(ui: MainUiState, modifier: Modifier) {
    Column(modifier) {
        Text("Stime cardiorespiratorie (future)", style = MaterialTheme.typography.titleSmall)
        Text("Frequenza cardiaca: " + (ui.heartRate?.let { "%.0f bpm (q=%.2f)".format(it.bpm, it.confidence) } ?: "— nessun algoritmo attivo"))
        Text("Frequenza respiratoria: " + (ui.respirationRate?.let { "%.1f atti/min (q=%.2f)".format(it.breathsPerMinute, it.confidence) } ?: "— nessun algoritmo attivo"))
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

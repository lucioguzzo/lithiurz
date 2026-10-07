package com.cardioresp.monitor.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardioresp.monitor.domain.model.SamplingDiagnostics
import com.cardioresp.monitor.domain.model.StreamDiagnostics

/** Tabella delle metriche di qualità temporale: una colonna per sensore. */
@Composable
fun DiagnosticsPanel(d: SamplingDiagnostics, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Diagnostica campionamento", style = MaterialTheme.typography.titleSmall)
        HeaderRow()
        HorizontalDivider()
        Row3("Campioni", d.accelerometer, d.gyroscope) { it.sampleCount.toString() }
        Row3("Freq. effettiva (Hz)", d.accelerometer, d.gyroscope) { "%.2f".format(it.effectiveRateHz) }
        Row3("Periodo nominale (ms)", d.accelerometer, d.gyroscope) { "%.3f".format(it.nominalPeriodMs) }
        Row3("Δt medio (ms)", d.accelerometer, d.gyroscope) { "%.3f".format(it.meanIntervalMs) }
        Row3("Δt dev. std / jitter RMS (ms)", d.accelerometer, d.gyroscope) { "%.3f".format(it.stdIntervalMs) }
        Row3("Jitter picco (ms)", d.accelerometer, d.gyroscope) { "%.3f".format(it.peakJitterMs) }
        Row3("Δt min / max (ms)", d.accelerometer, d.gyroscope) { "%.2f / %.2f".format(it.minIntervalMs, it.maxIntervalMs) }
        Row3("Campioni persi (stima)", d.accelerometer, d.gyroscope, warn = { it.estimatedLostSamples > 0 }) { it.estimatedLostSamples.toString() }
        Row3("Buchi", d.accelerometer, d.gyroscope, warn = { it.gapCount > 0 }) { it.gapCount.toString() }
        Row3("Timestamp non monotoni", d.accelerometer, d.gyroscope, warn = { it.nonMonotonicCount > 0 }) { it.nonMonotonicCount.toString() }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Single("Overflow ring buffer (app)", d.ringBufferOverflows.toString(), d.ringBufferOverflows > 0)
        Single("Riempimento max ring buffer", "${d.ringBufferHighWatermark} / ${d.ringBufferCapacity}")
        Single("Campioni senza gyro (NaN)", d.samplesWithoutGyro.toString(), d.samplesWithoutGyro > 0)
        Single("Drop coda algoritmi", d.processorQueueDrops.toString(), d.processorQueueDrops > 0)
        Single("Time base event.timestamp", d.timeBase)
    }
}

private val mono = FontFamily.Monospace

@Composable
private fun HeaderRow() = Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
    Text("", Modifier.weight(1.6f))
    Text("ACC", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    Text("GYRO", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun Row3(
    label: String,
    a: StreamDiagnostics,
    g: StreamDiagnostics,
    warn: (StreamDiagnostics) -> Boolean = { false },
    value: (StreamDiagnostics) -> String
) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
    Text(value(a), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = mono, color = if (warn(a)) Color(0xFFE65100) else Color.Unspecified)
    Text(value(g), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = mono, color = if (warn(g)) Color(0xFFE65100) else Color.Unspecified)
}

@Composable
private fun Single(label: String, value: String, warn: Boolean = false) = Row(Modifier.fillMaxWidth()) {
    Text(label, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
    Text(value, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, fontFamily = mono, color = if (warn) Color(0xFFE65100) else Color.Unspecified)
}

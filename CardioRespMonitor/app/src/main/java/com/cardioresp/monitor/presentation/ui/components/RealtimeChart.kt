package com.cardioresp.monitor.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cardioresp.monitor.domain.model.ChartFrame
import com.cardioresp.monitor.presentation.ui.theme.ChannelColors
import kotlin.math.max
import kotlin.math.min

/**
 * Grafico real-time a 3 canali disegnato direttamente su Canvas (nessuna libreria esterna:
 * pieno controllo del costo per frame e nessuna allocazione nascosta per punto).
 *
 * Decimazione MIN/MAX per colonna di pixel: con 1000+ campioni su ~1000 px, disegnare ogni
 * punto è inutile; prendere un campione ogni k causerebbe aliasing visivo (picchi SCG che
 * appaiono e scompaiono). L'inviluppo min/max per colonna conserva tutti i picchi.
 */
@Composable
fun RealtimeChart(
    title: String,
    unit: String,
    frame: ChartFrame,
    windowNs: Long,
    minSpan: Float,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$title [$unit]", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Legend("X", ChannelColors.X); Legend("Y", ChannelColors.Y); Legend("Z", ChannelColors.Z)
        }
        val range = computeRange(frame, minSpan)
        Text(
            "%.2f … %.2f".format(range.first, range.second),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            drawGrid(gridColor, range)
            if (frame.size < 2) return@Canvas
            val newest = frame.tNs[frame.size - 1]
            drawChannel(frame, frame.x, newest, windowNs, range, ChannelColors.X)
            drawChannel(frame, frame.y, newest, windowNs, range, ChannelColors.Y)
            drawChannel(frame, frame.z, newest, windowNs, range, ChannelColors.Z)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(8.dp))
    }
}

/** Range Y automatico su tutti e tre i canali con margine del 10% e ampiezza minima. */
private fun computeRange(frame: ChartFrame, minSpan: Float): Pair<Float, Float> {
    var lo = Float.POSITIVE_INFINITY
    var hi = Float.NEGATIVE_INFINITY
    for (arr in arrayOf(frame.x, frame.y, frame.z)) {
        for (i in 0 until frame.size) {
            val v = arr[i]
            if (v.isNaN()) continue
            lo = min(lo, v); hi = max(hi, v)
        }
    }
    if (lo > hi) return -minSpan / 2 to minSpan / 2
    var span = hi - lo
    if (span < minSpan) {
        val c = (hi + lo) / 2
        lo = c - minSpan / 2; hi = c + minSpan / 2; span = minSpan
    }
    return (lo - span * 0.1f) to (hi + span * 0.1f)
}

private fun DrawScope.drawGrid(color: Color, range: Pair<Float, Float>) {
    val stroke = 1f
    for (k in 0..4) {
        val y = size.height * k / 4f
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }
    for (k in 0..5) { // una linea al secondo sulla finestra di 5 s
        val x = size.width * k / 5f
        drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
    }
    if (range.first < 0 && range.second > 0) {
        val y0 = size.height * (range.second / (range.second - range.first))
        drawLine(color.copy(alpha = 1f), Offset(0f, y0), Offset(size.width, y0), 2f)
    }
}

private fun DrawScope.drawChannel(
    frame: ChartFrame,
    values: FloatArray,
    newestNs: Long,
    windowNs: Long,
    range: Pair<Float, Float>,
    color: Color
) {
    val w = size.width
    val h = size.height
    val (lo, hi) = range
    val scaleY = h / (hi - lo)
    fun yOf(v: Float) = h - (v - lo) * scaleY
    fun xOf(t: Long) = w * (1f - (newestNs - t).toFloat() / windowNs)

    val path = Path()
    val columns = w.toInt().coerceAtLeast(1)
    if (frame.size <= columns * 2) {
        // Pochi punti: polilinea completa.
        var started = false
        for (i in 0 until frame.size) {
            val v = values[i]
            if (v.isNaN()) { started = false; continue }
            val x = xOf(frame.tNs[i]); val y = yOf(v)
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
    } else {
        // Molti punti: inviluppo min/max per colonna di pixel.
        var col = -1
        var cMin = 0f
        var cMax = 0f
        var started = false
        fun flush() {
            if (col < 0) return
            val x = col.toFloat()
            if (!started) { path.moveTo(x, yOf(cMin)); started = true } else path.lineTo(x, yOf(cMin))
            path.lineTo(x, yOf(cMax))
        }
        for (i in 0 until frame.size) {
            val v = values[i]
            if (v.isNaN()) continue
            val c = xOf(frame.tNs[i]).toInt()
            if (c != col) { flush(); col = c; cMin = v; cMax = v } else { cMin = min(cMin, v); cMax = max(cMax, v) }
        }
        flush()
    }
    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
}

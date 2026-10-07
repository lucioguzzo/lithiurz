package com.cardioresp.monitor.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colori dei canali X/Y/Z, coerenti in tutti i grafici. */
object ChannelColors {
    val X = Color(0xFFE53935)
    val Y = Color(0xFF43A047)
    val Z = Color(0xFF1E88E5)
}

val RecordingRed = Color(0xFFD32F2F)

private val Light = lightColorScheme(primary = Color(0xFF00695C), secondary = Color(0xFF455A64))
private val Dark = darkColorScheme(primary = Color(0xFF4DB6AC), secondary = Color(0xFF90A4AE))

@Composable
fun CardioRespTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

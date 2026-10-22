package com.lucio.guzzobot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lucio.guzzobot.data.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = Clay,
    onPrimary = CreamCard,
    primaryContainer = CreamBubble,
    onPrimaryContainer = ClayDeep,
    secondary = InkMuted,
    onSecondary = CreamCard,
    background = Cream,
    onBackground = InkStrong,
    surface = Cream,
    onSurface = InkStrong,
    surfaceVariant = CreamBubble,
    onSurfaceVariant = InkMuted,
    surfaceContainer = CreamCard,
    surfaceContainerHigh = CreamCard,
    surfaceContainerHighest = CreamCode,
    outline = InkMuted,
    outlineVariant = CreamBorder,
    error = Danger,
    onError = CreamCard,
)

private val DarkScheme = darkColorScheme(
    primary = Clay,
    onPrimary = Graphite,
    primaryContainer = GraphiteBubble,
    onPrimaryContainer = ClaySoft,
    secondary = PaperMuted,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = Paper,
    surface = Graphite,
    onSurface = Paper,
    surfaceVariant = GraphiteBubble,
    onSurfaceVariant = PaperMuted,
    surfaceContainer = GraphiteCard,
    surfaceContainerHigh = GraphiteCard,
    surfaceContainerHighest = GraphiteCode,
    outline = PaperMuted,
    outlineVariant = GraphiteBorder,
    error = DangerDark,
    onError = Graphite,
)

@Composable
fun GuzzoBotTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = GuzzoBotTypography,
        content = content,
    )
}

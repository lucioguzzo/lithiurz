package com.lucio.guzzobot.ui.voice

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.media.VoiceStage
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * La conversazione parlata, a tutto schermo.
 *
 * Qui non c'è niente da leggere e niente da premere: si parla. Lo schermo
 * dice solo a che punto è il turno — ti ascolto, ci penso, parlo — perché
 * durante una conversazione a voce l'unica domanda è «tocca a me?».
 *
 * Il microfono resta aperto anche mentre legge: per interromperla basta
 * parlarci sopra, come con una persona. Un tocco qualunque la zittisce
 * comunque, perché l'interruzione a voce su un altoparlante a volume alto
 * non è mai sicura al cento per cento e un modo infallibile ci vuole.
 */
@Composable
fun VoiceScreen(viewModel: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val heard by viewModel.heardSoFar.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val active by viewModel.voiceMode.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleVoiceMode() }

    // Entrando si comincia subito: una schermata che aspetta un pulsante non
    // sarebbe una conversazione.
    LaunchedEffect(Unit) {
        if (viewModel.voiceMode.value) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleVoiceMode() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun leave() {
        if (viewModel.voiceMode.value) viewModel.toggleVoiceMode()
        onClose()
    }

    BackHandler(enabled = true) { leave() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        // Scorrevole se lo schermo non basta: «Esci» non deve mai finire fuori.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .clickable(
                    // Un tocco qualunque zittisce la voce e ridà il turno.
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ) { viewModel.hush() }
                .navigationBarsPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.voice_conversation),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 28.dp),
            )

            Pulse(stage)

            Text(
                text = stringResource(
                    when (stage) {
                        VoiceStage.LISTENING -> R.string.voice_listening
                        VoiceStage.THINKING -> R.string.voice_thinking
                        VoiceStage.SPEAKING -> R.string.voice_speaking
                        VoiceStage.OFF -> R.string.voice_off
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 28.dp),
            )

            // Quello che sta capendo, o quello che sta dicendo: una cosa sola.
            val echo = when (stage) {
                VoiceStage.LISTENING -> heard
                VoiceStage.SPEAKING -> streaming?.content.orEmpty().takeLast(220)
                else -> ""
            }
            Text(
                text = echo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            )

            Text(
                text = stringResource(
                    if (settings.bargeIn) R.string.voice_barge_on else R.string.voice_barge_off,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
            )

            TextButton(onClick = { viewModel.setBargeIn(!settings.bargeIn) }) {
                Text(
                    stringResource(
                        if (settings.bargeIn) {
                            R.string.voice_barge_disable
                        } else {
                            R.string.voice_barge_enable
                        },
                    ),
                )
            }

            TextButton(onClick = ::leave, modifier = Modifier.padding(top = 8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text("  " + stringResource(R.string.voice_leave))
            }

            if (!active && stage == VoiceStage.OFF) {
                Text(
                    text = stringResource(R.string.voice_needs_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        }
    }
}

/** Il cerchio che respira: dice a colpo d'occhio di chi è il turno. */
@Composable
private fun Pulse(stage: VoiceStage) {
    val transition = rememberInfiniteTransition(label = "voce")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (stage == VoiceStage.OFF) 1f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (stage == VoiceStage.SPEAKING) 700 else 1_200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "respiro",
    )
    val tint = when (stage) {
        VoiceStage.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceStage.SPEAKING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(132.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = when (stage) {
                VoiceStage.LISTENING -> Icons.Outlined.Mic
                VoiceStage.SPEAKING -> Icons.Outlined.GraphicEq
                else -> Icons.Outlined.MoreHoriz
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(52.dp),
        )
    }
}

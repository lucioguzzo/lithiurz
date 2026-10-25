package com.lucio.guzzobot.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lucio.guzzobot.R
import androidx.compose.material.icons.outlined.AutoAwesome
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.ui.components.SparkPulse

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    streaming: Boolean,
    ready: Boolean,
    onOpenSettings: () -> Unit,
    pending: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onAttach: () -> Unit,
    onRecord: () -> Unit,
    onVoiceMode: () -> Unit,
    voiceMode: Boolean,
    listening: Boolean,
    heard: String,
    recording: Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    busy: String?,
    agentAvailable: Boolean,
    agentMode: Boolean,
    onAgentModeChange: (Boolean) -> Unit,
    agentProgress: AgentProgress?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!ready) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenSettings)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.no_key_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                TextButton(onClick = onOpenSettings) {
                    Text(
                        text = stringResource(R.string.open_settings),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        agentProgress?.let { progress ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SparkPulse(size = 14.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.agent_working),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            R.string.agent_progress,
                            progress.steps,
                            progress.maxSteps,
                            progress.minutes,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (agentAvailable && agentProgress == null) {
            Row(
                modifier = Modifier
                    .padding(bottom = 8.dp, start = 2.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (agentMode) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .clickable { onAgentModeChange(!agentMode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = if (agentMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = stringResource(
                        if (agentMode) R.string.agent_on else R.string.agent_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (agentMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        busy?.let { label ->
            Row(
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SparkPulse(size = 14.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (recording) {
            RecordingBar(onStop = onStopRecording, onCancel = onCancelRecording)
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
                .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            PendingAttachments(
                items = pending,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RoundAction(
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.attach),
                    onClick = onAttach,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 2.dp),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 38.dp, max = 180.dp)
                            .padding(vertical = 9.dp),
                    )
                    if (value.isEmpty()) {
                        Text(
                            text = if (voiceMode && listening) {
                                heard.ifBlank { stringResource(R.string.voice_listening) }
                            } else if (voiceMode) {
                                stringResource(R.string.voice_speaking)
                            } else {
                                stringResource(R.string.composer_hint)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (voiceMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 9.dp),
                        )
                    }
                }
                if (!streaming && value.isBlank() && pending.isEmpty()) {
                    // Tocco breve: detta un messaggio. Pressione lunga: entra
                    // nella conversazione parlata e ci resta.
                    RoundAction(
                        icon = if (voiceMode) {
                            Icons.Outlined.RecordVoiceOver
                        } else {
                            Icons.Outlined.Mic
                        },
                        label = stringResource(
                            if (voiceMode) R.string.voice_mode_stop else R.string.record_audio,
                        ),
                        onClick = if (voiceMode) onVoiceMode else onRecord,
                        onLongClick = onVoiceMode,
                        accent = voiceMode,
                    )
                } else {
                    SendButton(
                        streaming = streaming,
                        onClick = { if (streaming) onStop() else onSend() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accent: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (accent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SendButton(streaming: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (streaming) Icons.Filled.Stop else Icons.Filled.ArrowUpward,
            contentDescription = stringResource(if (streaming) R.string.stop else R.string.send),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

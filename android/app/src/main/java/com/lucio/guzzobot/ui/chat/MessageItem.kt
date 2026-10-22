package com.lucio.guzzobot.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.local.MessageEntity
import com.lucio.guzzobot.data.model.Attachment
import com.lucio.guzzobot.ui.components.SparkPulse
import com.lucio.guzzobot.ui.markdown.MarkdownText

@Composable
fun UserMessage(
    message: MessageEntity,
    playingPath: String?,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPlay: (Attachment) -> Unit,
    onShare: (Attachment) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        if (message.attachments.isNotEmpty()) {
            MessageAttachments(
                items = message.attachments,
                playingPath = playingPath,
                onPlay = onPlay,
                onShare = onShare,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (message.content.isNotBlank()) {
            Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MessageAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy), onCopy)
            MessageAction(Icons.Outlined.Edit, stringResource(R.string.edit), onEdit)
        }
    }
}

@Composable
fun AssistantMessage(
    content: String,
    reasoning: String?,
    isError: Boolean,
    showReasoning: Boolean,
    streaming: Boolean,
    showActions: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    attachments: List<Attachment> = emptyList(),
    playingPath: String? = null,
    onPlay: (Attachment) -> Unit = {},
    onShare: (Attachment) -> Unit = {},
    onSpeak: () -> Unit = {},
    speaking: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!reasoning.isNullOrBlank() && showReasoning) {
            ReasoningBlock(reasoning = reasoning, streamingEmptyAnswer = streaming && content.isBlank())
        }
        when {
            isError -> ErrorBubble(content)
            content.isNotBlank() -> MarkdownText(markdown = content)
            streaming && reasoning.isNullOrBlank() -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SparkPulse()
                Text(
                    text = stringResource(R.string.thinking_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (attachments.isNotEmpty()) {
            MessageAttachments(
                items = attachments,
                playingPath = playingPath,
                onPlay = onPlay,
                onShare = onShare,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (showActions) {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MessageAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy), onCopy)
                MessageAction(Icons.Outlined.Refresh, stringResource(R.string.retry), onRetry)
                if (content.isNotBlank() && !isError) {
                    MessageAction(
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        label = stringResource(if (speaking) R.string.listen_stop else R.string.listen),
                        onClick = onSpeak,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ReasoningBlock(reasoning: String, streamingEmptyAnswer: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (streamingEmptyAnswer) {
                SparkPulse(size = 14.dp)
            }
            Text(
                text = stringResource(R.string.thinking),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = if (streamingEmptyAnswer) 8.dp else 0.dp)
                    .weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MessageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

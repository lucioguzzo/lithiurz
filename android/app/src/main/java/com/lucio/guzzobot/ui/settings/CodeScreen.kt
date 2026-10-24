package com.lucio.guzzobot.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.workspace.Workflow
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * La sezione per chi scrive codice: si sfoglia l'area di lavoro, si legge un
 * file, si avvia la CI e si manda l'agente a lavorare su un punto preciso.
 * Non è una chat: la chat è altrove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onAgentStarted: () -> Unit,
) {
    val state by viewModel.code.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var asking by remember { mutableStateOf<String?>(null) }
    var workflows by remember { mutableStateOf<List<Workflow>>(emptyList()) }

    LaunchedEffect(Unit) { viewModel.browse("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                state.file != null -> viewModel.closeCodeFile()
                                state.path.isNotEmpty() -> viewModel.codeUp()
                                else -> onBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.file?.path?.substringAfterLast('/')
                                ?: stringResource(R.string.code),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.file?.path ?: state.label.ifBlank {
                                stringResource(R.string.agent_needs_workspace)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    state.file?.let { file ->
                        IconButton(onClick = { clipboard.setText(AnnotatedString(file.content)) }) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = stringResource(R.string.copy),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.browse(state.path) }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(16.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenWorkspace)
                        .padding(20.dp),
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val file = state.file
            if (file == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionChip(
                        icon = Icons.Outlined.AutoAwesome,
                        label = stringResource(R.string.code_ask_agent),
                        onClick = { asking = state.path },
                    )
                    if (state.github) {
                        ActionChip(
                            icon = Icons.Outlined.PlayArrow,
                            label = stringResource(R.string.code_run_ci),
                            onClick = { viewModel.workflows { workflows = it } },
                        )
                        ActionChip(
                            icon = Icons.Outlined.Refresh,
                            label = stringResource(R.string.code_ci_status),
                            onClick = viewModel::ciStatus,
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.path }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (entry.directory) {
                                        viewModel.browse(entry.path)
                                    } else {
                                        viewModel.openCodeFile(entry.path)
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = if (entry.directory) {
                                    Icons.Outlined.Folder
                                } else {
                                    Icons.Outlined.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = entry.path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!entry.directory && entry.size > 0) {
                                Text(
                                    text = "${entry.size / 1024} kB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionChip(
                        icon = Icons.Outlined.AutoAwesome,
                        label = stringResource(R.string.code_ask_agent),
                        onClick = { asking = file.path },
                    )
                }
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = file.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(16.dp),
                    )
                }
            }
        }
    }

    asking?.let { path ->
        var request by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.code_ask_agent)) },
            text = {
                Column {
                    Text(
                        text = path.ifBlank { stringResource(R.string.code_whole_workspace) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = request,
                        onValueChange = { request = it },
                        placeholder = { Text(stringResource(R.string.code_ask_hint)) },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.askAgentAbout(path, request)
                        asking = null
                        onAgentStarted()
                    },
                    enabled = request.isNotBlank(),
                ) {
                    Text(stringResource(R.string.agent_start))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (workflows.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { workflows = emptyList() },
            title = { Text(stringResource(R.string.code_run_ci)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    workflows.forEach { workflow ->
                        Text(
                            text = workflow.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.runWorkflow(workflow)
                                    workflows = emptyList()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { workflows = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

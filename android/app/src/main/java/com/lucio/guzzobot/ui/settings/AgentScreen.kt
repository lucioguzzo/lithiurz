package com.lucio.guzzobot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * Il posto da cui si comanda l'agente: stato dell'area di lavoro, lancio di un
 * obiettivo, tetto di sicurezza, autonomia e compiti pianificati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenTasks: () -> Unit,
    onStarted: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var goal by remember { mutableStateOf("") }
    var steps by remember(settings.agentMaxSteps) { mutableStateOf(settings.agentMaxSteps.toString()) }
    var minutes by remember(settings.agentMaxMinutes) {
        mutableStateOf(settings.agentMaxMinutes.toString())
    }
    val ready = settings.workspace.isConfigured

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.agent_bot),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = stringResource(R.string.agent_hub_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            LinkRow(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.workspace),
                detail = settings.workspace.label.ifBlank {
                    stringResource(R.string.agent_needs_workspace)
                },
                onClick = onOpenWorkspace,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text(stringResource(R.string.agent_goal)) },
                placeholder = { Text(stringResource(R.string.agent_goal_hint)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
            Button(
                onClick = {
                    viewModel.startAgent(goal.trim())
                    goal = ""
                    onStarted()
                },
                enabled = ready && goal.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.agent_start))
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SectionTitle(stringResource(R.string.agent_budget))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = steps,
                    onValueChange = { value ->
                        steps = value.filter(Char::isDigit).take(3)
                        viewModel.setAgentBudget(
                            steps.toIntOrNull() ?: 60,
                            minutes.toIntOrNull() ?: 25,
                        )
                    },
                    label = { Text(stringResource(R.string.agent_steps)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { value ->
                        minutes = value.filter(Char::isDigit).take(3)
                        viewModel.setAgentBudget(
                            steps.toIntOrNull() ?: 60,
                            minutes.toIntOrNull() ?: 25,
                        )
                    },
                    label = { Text(stringResource(R.string.agent_minutes)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }

            SwitchRow(
                title = stringResource(R.string.autonomous_tools),
                detail = stringResource(R.string.autonomous_tools_hint),
                checked = settings.autonomousTools,
                onChange = viewModel::setAutonomousTools,
            )
            SwitchRow(
                title = stringResource(R.string.background_work),
                detail = stringResource(R.string.background_work_hint),
                checked = settings.backgroundWork,
                onChange = viewModel::setBackgroundWork,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LinkRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.tasks),
                detail = if (tasks.isEmpty()) {
                    stringResource(R.string.task_none)
                } else {
                    tasks.joinToString(" · ") { it.title }.take(80)
                },
                onClick = onOpenTasks,
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

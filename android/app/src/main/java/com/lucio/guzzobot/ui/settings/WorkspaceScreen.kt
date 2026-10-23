package com.lucio.guzzobot.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.model.WorkspaceConfig
import com.lucio.guzzobot.data.model.WorkspaceMode
import com.lucio.guzzobot.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val workspace = settings.workspace
    val context = LocalContext.current

    var token by remember { mutableStateOf(viewModel.apiKey(WorkspaceConfig.GITHUB_SECRET)) }
    var owner by remember(workspace.owner) { mutableStateOf(workspace.owner.orEmpty()) }
    var repo by remember(workspace.repo) { mutableStateOf(workspace.repo.orEmpty()) }
    var branch by remember(workspace.branch) { mutableStateOf(workspace.branch) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Permesso persistente: la cartella resta accessibile dopo il riavvio.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setWorkspace(
                workspace.copy(
                    mode = WorkspaceMode.LOCAL,
                    localTreeUri = uri.toString(),
                    localLabel = DocumentFile.fromTreeUri(context, uri)?.name ?: "Cartella",
                ),
            )
        }
    }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setBackgroundWork(granted) }

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
                        text = stringResource(R.string.workspace),
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
                text = stringResource(R.string.workspace_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            ModeRow(
                label = stringResource(R.string.workspace_none),
                selected = workspace.mode == WorkspaceMode.NONE,
                onClick = { viewModel.setWorkspace(workspace.copy(mode = WorkspaceMode.NONE)) },
            )
            ModeRow(
                label = stringResource(R.string.workspace_local),
                detail = workspace.localLabel,
                selected = workspace.mode == WorkspaceMode.LOCAL,
                onClick = {
                    if (workspace.localTreeUri.isNullOrBlank()) {
                        folderPicker.launch(null)
                    } else {
                        viewModel.setWorkspace(workspace.copy(mode = WorkspaceMode.LOCAL))
                    }
                },
            )
            if (workspace.mode == WorkspaceMode.LOCAL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPicker.launch(null) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.workspace_pick_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            ModeRow(
                label = stringResource(R.string.workspace_github),
                detail = workspace.takeIf { it.mode == WorkspaceMode.GITHUB }?.label,
                selected = workspace.mode == WorkspaceMode.GITHUB,
                onClick = { viewModel.setWorkspace(workspace.copy(mode = WorkspaceMode.GITHUB)) },
            )
            if (workspace.mode == WorkspaceMode.GITHUB) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            viewModel.setApiKey(WorkspaceConfig.GITHUB_SECRET, it)
                        },
                        label = { Text(stringResource(R.string.github_token)) },
                        supportingText = { Text(stringResource(R.string.api_key_saved)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = owner,
                        onValueChange = { owner = it },
                        label = { Text(stringResource(R.string.github_owner)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text(stringResource(R.string.github_repo)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text(stringResource(R.string.github_branch)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.setWorkspace(
                                workspace.copy(
                                    mode = WorkspaceMode.GITHUB,
                                    owner = owner.trim(),
                                    repo = repo.trim(),
                                    branch = branch.trim().ifBlank { "main" },
                                ),
                            )
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 20.dp),
            )

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
                onChange = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setBackgroundWork(enabled)
                    }
                },
            )
        }
    }
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
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

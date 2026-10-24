package com.lucio.guzzobot.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.model.WorkspaceMode
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * Cosa esce dal telefono, cosa resta, chi ha il permesso di fare cosa: in un
 * posto solo, con i pulsanti per revocare invece che una pagina di promesse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenConnectors: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmKeys by remember { mutableStateOf(false) }
    val endpoints = remember(settings) { viewModel.endpoints() }
    val attachmentsMb = remember(conversations) { viewModel.attachmentsBytes() / 1_000_000.0 }

    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

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
                        text = stringResource(R.string.privacy),
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
                text = stringResource(R.string.privacy_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            SectionTitle(stringResource(R.string.privacy_where))
            if (endpoints.isEmpty()) {
                Detail(stringResource(R.string.privacy_nowhere))
            }
            endpoints.forEach { (host, reason) ->
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Detail(stringResource(R.string.privacy_what_travels))

            Divider()
            SectionTitle(stringResource(R.string.privacy_stays))
            Detail(
                stringResource(
                    R.string.privacy_stays_detail,
                    conversations.size,
                    String.format("%.1f", attachmentsMb),
                ),
            )

            Divider()
            SectionTitle(stringResource(R.string.privacy_permissions))
            PermissionRow(stringResource(R.string.privacy_mic), micGranted)
            PermissionRow(stringResource(R.string.privacy_notifications), notificationsGranted)
            PermissionRow(
                label = stringResource(R.string.privacy_folder),
                granted = settings.workspace.mode == WorkspaceMode.LOCAL,
                detail = settings.workspace.localLabel,
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.privacy_system_settings))
                }
                if (settings.workspace.mode == WorkspaceMode.LOCAL) {
                    TextButton(onClick = viewModel::revokeWorkspaceFolder) {
                        Text(
                            text = stringResource(R.string.privacy_revoke_folder),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Divider()
            SectionTitle(stringResource(R.string.privacy_approvals))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.autonomous_tools),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.privacy_approvals_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.autonomousTools,
                    onCheckedChange = viewModel::setAutonomousTools,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenConnectors)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.connectors),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            R.string.privacy_connectors_detail,
                            settings.mcpServers.count { it.enabled },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Divider()
            SectionTitle(stringResource(R.string.privacy_forget))
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { confirmWipe = true }) {
                    Text(
                        text = stringResource(R.string.delete_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = { confirmKeys = true }) {
                    Text(
                        text = stringResource(R.string.privacy_forget_keys),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(stringResource(R.string.delete_all)) },
            text = { Text(stringResource(R.string.delete_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllConversations()
                    confirmWipe = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (confirmKeys) {
        AlertDialog(
            onDismissRequest = { confirmKeys = false },
            title = { Text(stringResource(R.string.privacy_forget_keys)) },
            text = { Text(stringResource(R.string.privacy_forget_keys_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetAllKeys()
                    confirmKeys = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmKeys = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

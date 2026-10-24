package com.lucio.guzzobot.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.mcp.McpAuth
import com.lucio.guzzobot.data.mcp.McpCatalog
import com.lucio.guzzobot.data.mcp.McpCategory
import com.lucio.guzzobot.data.mcp.McpEntry
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * Il catalogo dei connettori: indirizzi verificati, pronti da aggiungere.
 *
 * Quelli che richiedono OAuth sono elencati e segnati, non nascosti e non
 * finti: l'app non sa ancora fare quel giro di autorizzazione, e un pulsante
 * che non funziona sarebbe peggio di un lucchetto che spiega perché.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenConnectors: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    val installed = remember(settings) { settings.mcpServers.map { it.id }.toSet() }

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
                        text = stringResource(R.string.catalog),
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
                text = stringResource(R.string.catalog_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            McpCategory.entries.forEach { category ->
                val entries = McpCatalog.entries.filter { it.category == category }
                if (entries.isEmpty()) return@forEach
                SectionTitle(stringResource(label(category)))
                entries.forEach { entry ->
                    EntryRow(
                        entry = entry,
                        installed = entry.id in installed,
                        editing = editing == entry.id,
                        token = token,
                        onToken = { token = it },
                        onOpen = { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        onStart = {
                            editing = entry.id
                            // Il token GitHub dell'area di lavoro vale anche qui.
                            token = if (entry.id == "github") {
                                viewModel.workspaceGitHubToken()
                            } else {
                                ""
                            }
                        },
                        onAdd = {
                            viewModel.addFromCatalog(entry, token)
                            editing = null
                            token = ""
                        },
                        onCancel = { editing = null; token = "" },
                        onOpenConnectors = onOpenConnectors,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Text(
                text = stringResource(R.string.catalog_oauth_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: McpEntry,
    installed: Boolean,
    editing: Boolean,
    token: String,
    onToken: (String) -> Unit,
    onOpen: (String) -> Unit,
    onStart: () -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
    onOpenConnectors: () -> Unit,
) {
    val locked = entry.auth == McpAuth.OAUTH
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (locked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
            )
            if (installed) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            if (locked) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = entry.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                when (entry.auth) {
                    McpAuth.NONE -> R.string.catalog_auth_none
                    McpAuth.TOKEN -> R.string.catalog_auth_token
                    McpAuth.OAUTH -> R.string.catalog_auth_oauth
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (locked) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 2.dp),
        )

        when {
            locked -> entry.docsUrl?.let { url ->
                TextButton(onClick = { onOpen(url) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text("  " + stringResource(R.string.catalog_docs))
                }
            }

            installed -> TextButton(onClick = onOpenConnectors) {
                Text(stringResource(R.string.catalog_installed))
            }

            editing -> {
                if (entry.auth == McpAuth.TOKEN) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = onToken,
                        label = { Text(stringResource(R.string.connector_token)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onAdd,
                        enabled = entry.auth == McpAuth.NONE || token.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.catalog_add))
                    }
                    entry.tokenUrl?.let { url ->
                        TextButton(onClick = { onOpen(url) }) {
                            Text(stringResource(R.string.catalog_get_token))
                        }
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onStart) { Text(stringResource(R.string.catalog_add)) }
                entry.docsUrl?.let { url ->
                    TextButton(onClick = { onOpen(url) }) {
                        Text(stringResource(R.string.catalog_docs))
                    }
                }
            }
        }
    }
}

private fun label(category: McpCategory): Int = when (category) {
    McpCategory.AUTOMAZIONE -> R.string.catalog_automation
    McpCategory.SVILUPPO -> R.string.catalog_dev
    McpCategory.CONOSCENZA -> R.string.catalog_knowledge
    McpCategory.PRODUTTIVITA -> R.string.catalog_productivity
    McpCategory.AFFARI -> R.string.catalog_business
}

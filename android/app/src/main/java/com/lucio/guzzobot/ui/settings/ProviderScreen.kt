package com.lucio.guzzobot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderScreen(
    viewModel: ChatViewModel,
    providerId: String,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val existing = remember(providerId, settings.providers) {
        settings.providers.firstOrNull { it.id == providerId }
    }
    val isNew = existing == null

    var name by remember(providerId) { mutableStateOf(existing?.name.orEmpty()) }
    var baseUrl by remember(providerId) {
        mutableStateOf(existing?.baseUrl ?: "https://")
    }
    var models by remember(providerId) {
        mutableStateOf(existing?.models?.joinToString("\n").orEmpty())
    }
    var apiKey by remember(providerId) { mutableStateOf(viewModel.apiKey(providerId)) }
    var revealKey by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    fun currentProvider(): Provider {
        val id = existing?.id ?: slug(name).ifBlank { "custom-" + System.currentTimeMillis() }
        return Provider(
            id = id,
            name = name.trim().ifBlank { "Provider" },
            baseUrl = baseUrl.trim(),
            models = models.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            keyUrl = existing?.keyUrl,
            builtIn = existing?.builtIn ?: false,
        )
    }

    fun save(activate: Boolean) {
        val provider = currentProvider()
        viewModel.saveProvider(provider)
        viewModel.setApiKey(provider.id, apiKey)
        if (activate) {
            provider.models.firstOrNull()?.let { viewModel.selectModel(provider.id, it) }
        }
        onBack()
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
                        text = existing?.name ?: stringResource(R.string.add_provider),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = {
                            viewModel.deleteProvider(existing.id)
                            onBack()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.provider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.base_url)) },
                supportingText = { Text("…/v1 — endpoint compatibile OpenAI") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.api_key)) },
                supportingText = { Text(stringResource(R.string.api_key_saved)) },
                singleLine = true,
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            existing?.keyUrl?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Ottieni una chiave")
                }
            }
            OutlinedTextField(
                value = models,
                onValueChange = { models = it },
                label = { Text(stringResource(R.string.models)) },
                minLines = 3,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    val provider = currentProvider()
                    viewModel.setApiKey(provider.id, apiKey)
                    viewModel.saveProvider(provider)
                    viewModel.fetchModels(provider) { fetched ->
                        if (fetched.isNotEmpty()) models = fetched.joinToString("\n")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.fetch_models))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { save(activate = true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isNew) stringResource(R.string.save) else "Salva e usa")
                }
                OutlinedButton(
                    onClick = { save(activate = false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

private fun slug(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

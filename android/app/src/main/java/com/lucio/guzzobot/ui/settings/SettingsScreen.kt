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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.foundation.rememberScrollState as rememberDialogScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.model.ModelCapabilities
import com.lucio.guzzobot.data.model.ModelRef
import com.lucio.guzzobot.data.model.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: com.lucio.guzzobot.ui.chat.ChatViewModel,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmWipe by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<ModelSlot?>(null) }
    var promptDraft by remember(settings.systemPrompt) { mutableStateOf(settings.systemPrompt) }
    var tokensDraft by remember(settings.maxTokens) {
        mutableStateOf(if (settings.maxTokens > 0) settings.maxTokens.toString() else "")
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
                        text = stringResource(R.string.settings),
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
            SectionTitle(stringResource(R.string.providers))
            settings.providers.forEach { provider ->
                val selected = provider.id == settings.activeProviderId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProvider(provider.id) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        Text(
                            text = provider.baseUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (viewModel.apiKey(provider.id).isNotBlank()) {
                                "•••• " + viewModel.apiKey(provider.id).takeLast(4)
                            } else {
                                stringResource(R.string.api_key) + ": —"
                            },
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProvider("new") }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.add_provider),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Divider()
            SectionTitle(stringResource(R.string.system_prompt))
            OutlinedTextField(
                value = promptDraft,
                onValueChange = {
                    promptDraft = it
                    viewModel.setSystemPrompt(it)
                },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            SettingRow(
                label = stringResource(R.string.temperature),
                value = String.format("%.1f", settings.temperature),
            )
            Slider(
                value = settings.temperature,
                onValueChange = { viewModel.setTemperature((it * 10).roundToInt() / 10f) },
                valueRange = 0f..2f,
                steps = 19,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            SettingRow(
                label = stringResource(R.string.history_limit),
                value = settings.historyLimit.toString(),
            )
            Slider(
                value = settings.historyLimit.toFloat(),
                onValueChange = { viewModel.setHistoryLimit(it.roundToInt()) },
                valueRange = 2f..100f,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            OutlinedTextField(
                value = tokensDraft,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(6)
                    tokensDraft = digits
                    viewModel.setMaxTokens(digits.toIntOrNull() ?: 0)
                },
                label = { Text(stringResource(R.string.max_tokens)) },
                placeholder = { Text("auto") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )

            Divider()
            SectionTitle(stringResource(R.string.workspace))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenWorkspace)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.workspace.label.ifBlank {
                            stringResource(R.string.workspace_none)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.workspace_hint),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenTasks)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tasks),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.tasks_hint),
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

            Divider()
            SectionTitle(stringResource(R.string.multimodal))
            Text(
                text = stringResource(R.string.multimodal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            SlotRow(
                label = stringResource(R.string.vision_model),
                value = settings.visionRef?.model,
                onClick = { picking = ModelSlot.VISION },
            )
            SlotRow(
                label = stringResource(R.string.transcribe_model),
                value = settings.transcribeRef?.model,
                onClick = { picking = ModelSlot.TRANSCRIBE },
            )
            SlotRow(
                label = stringResource(R.string.image_model),
                value = settings.imageRef?.model,
                onClick = { picking = ModelSlot.IMAGE },
            )
            SlotRow(
                label = stringResource(R.string.speech_model),
                value = settings.speechRef?.model,
                onClick = { picking = ModelSlot.SPEECH },
            )
            if (settings.speechRef != null) {
                OutlinedTextField(
                    value = settings.speechVoice,
                    onValueChange = viewModel::setSpeechVoice,
                    label = { Text(stringResource(R.string.speech_voice)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (settings.imageRef != null) {
                OutlinedTextField(
                    value = settings.imageSize,
                    onValueChange = viewModel::setImageSize,
                    label = { Text(stringResource(R.string.image_size)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.force_native_vision),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (ModelCapabilities.seesImages(settings.activeModel)) {
                            stringResource(R.string.native_vision_on)
                        } else {
                            stringResource(R.string.force_native_vision_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.forceNativeVision ||
                        ModelCapabilities.seesImages(settings.activeModel),
                    enabled = !ModelCapabilities.seesImages(settings.activeModel),
                    onCheckedChange = viewModel::setForceNativeVision,
                )
            }

            Divider()
            SectionTitle(stringResource(R.string.theme))
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                val modes = listOf(
                    ThemeMode.SYSTEM to R.string.theme_system,
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.show_reasoning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.showReasoning,
                    onCheckedChange = viewModel::setShowReasoning,
                )
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { confirmWipe = true }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.delete_all),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Divider()
            SectionTitle(stringResource(R.string.about))
            Text(
                text = stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text = stringResource(
                    R.string.version_label,
                    com.lucio.guzzobot.BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
            Text(
                text = stringResource(R.string.copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }
    }

    picking?.let { slot ->
        val current = when (slot) {
            ModelSlot.VISION -> settings.visionRef
            ModelSlot.TRANSCRIBE -> settings.transcribeRef
            ModelSlot.IMAGE -> settings.imageRef
            ModelSlot.SPEECH -> settings.speechRef
        }
        ModelPickerDialog(
            providers = settings.providers,
            current = current,
            onPick = { ref ->
                when (slot) {
                    ModelSlot.VISION -> viewModel.setVisionRef(ref)
                    ModelSlot.TRANSCRIBE -> viewModel.setTranscribeRef(ref)
                    ModelSlot.IMAGE -> viewModel.setImageRef(ref)
                    ModelSlot.SPEECH -> viewModel.setSpeechRef(ref)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
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
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 20.dp),
    )
}

internal enum class ModelSlot { VISION, TRANSCRIBE, IMAGE, SPEECH }

@Composable
private fun SlotRow(label: String, value: String?, onClick: () -> Unit) {
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
            Text(
                text = value ?: stringResource(R.string.none_selected),
                style = MaterialTheme.typography.bodySmall,
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
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
private fun ModelPickerDialog(
    providers: List<com.lucio.guzzobot.data.model.Provider>,
    current: ModelRef?,
    onPick: (ModelRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_model)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberDialogScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.none_selected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null) }
                        .padding(vertical = 12.dp),
                )
                providers.forEach { provider ->
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    provider.models.forEach { model ->
                        val selected = current?.providerId == provider.id && current.model == model
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(ModelRef(provider.id, model)) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

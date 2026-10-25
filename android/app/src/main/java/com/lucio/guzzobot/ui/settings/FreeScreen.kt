package com.lucio.guzzobot.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.model.FreeNote
import com.lucio.guzzobot.data.model.Provider
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * Usare GUZZOBOT senza spendere niente.
 *
 * Non è una vetrina: ogni riga dice cosa il piano gratuito dà, cosa si prende
 * in cambio e quali funzioni dell'app restano spente. Il gratuito *senza
 * chiave* nel cloud non esiste — chi regala calcolo senza identificare nessuno
 * viene svuotato in ore — quindi quello che si abbrevia qui è l'iscrizione,
 * non la credenziale: un pulsante che apre la pagina giusta e un campo in cui
 * incollare.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FreeScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val providers = remember(settings) { viewModel.freeProviders() }

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
                        text = stringResource(R.string.free_models),
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
            Disclaimer()

            providers.forEach { provider ->
                val key = viewModel.apiKey(provider.id)
                val tier = provider.freeTier ?: return@forEach
                // Ollama non chiede una chiave: chiede un computer acceso.
                val local = tier.notes.contains(FreeNote.NEEDS_LOCAL_MACHINE)
                val ready = key.isNotBlank() || !provider.requiresKey

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (key.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    if (tier.limits.isNotBlank()) {
                        Text(
                            text = tier.limits,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tier.notes.forEach { note -> NoteRow(note) }

                    if (editing == provider.id) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text(stringResource(R.string.api_key)) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    viewModel.activateFreeProvider(provider.id, draft)
                                    draft = ""
                                    editing = null
                                },
                                enabled = draft.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.free_activate))
                            }
                            TextButton(onClick = { editing = null; draft = "" }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(tier.signupUrl)),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = " " + stringResource(
                                        when {
                                            local -> R.string.free_install
                                            !provider.requiresKey -> R.string.free_site
                                            else -> R.string.free_get_key
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (ready) {
                                TextButton(
                                    onClick = { viewModel.activateFreeProvider(provider.id, "") },
                                ) {
                                    Text(stringResource(R.string.free_use_now))
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        editing = provider.id
                                        // Se la chiave è già negli appunti,
                                        // il campo arriva compilato.
                                        draft = viewModel.keyFromClipboard(
                                            provider.id,
                                            clipboardText(context),
                                        ).orEmpty()
                                    },
                                ) {
                                    Text(stringResource(R.string.free_paste_key))
                                }
                            }
                            TextButton(onClick = { onOpenProvider(provider.id) }) {
                                Text(stringResource(R.string.free_details))
                            }
                        }
                    }
                }
                Divider()
            }
        }
    }
}

/** Il testo negli appunti, per non far trascrivere a mano una chiave. */
private fun clipboardText(context: android.content.Context): String? = runCatching {
    val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as? android.content.ClipboardManager
    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}.getOrNull()

/** L'avviso sta in cima e non si chiude: è il prezzo del gratuito. */
@Composable
private fun Disclaimer() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.free_disclaimer_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.free_disclaimer_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun NoteRow(note: FreeNote) {
    val label = stringResource(
        when (note) {
            FreeNote.SEES_IMAGES -> R.string.free_note_images
            FreeNote.TRANSCRIBES -> R.string.free_note_transcribe
            FreeNote.NO_CARD -> R.string.free_note_no_card
            FreeNote.TRAINS_ON_DATA -> R.string.free_note_training
            FreeNote.NEEDS_LOCAL_MACHINE -> R.string.free_note_local
            FreeNote.PARTIAL_CATALOG -> R.string.free_note_partial
            FreeNote.CREATES_IMAGES -> R.string.free_note_creates
            FreeNote.NO_KEY -> R.string.free_note_no_key
            FreeNote.PUBLIC_RESULTS -> R.string.free_note_public
            FreeNote.WATERMARK -> R.string.free_note_watermark
            FreeNote.IMAGES_PAID -> R.string.free_note_images_paid
        },
    )
    Text(
        text = (if (note.warning) "⚠ " else "· ") + label,
        style = MaterialTheme.typography.bodySmall,
        color = if (note.warning) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

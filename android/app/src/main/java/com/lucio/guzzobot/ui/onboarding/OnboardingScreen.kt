package com.lucio.guzzobot.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lucio.guzzobot.R
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.components.GuzzoBotLogo
import kotlinx.coroutines.launch

/**
 * Il primo avvio.
 *
 * GUZZOBOT non ha server propri, quindi senza un modello configurato non può
 * dire niente: è il muro su cui si ferma chi non ha voglia di smanettare. Qui
 * si prova a smontarlo, senza raccontare bugie — la chiave serve, e nessuna
 * app può ottenerla al posto dell'utente: Google non espone nessun modo per
 * farsela rilasciare da fuori, e l'accesso all'account non servirebbe a
 * niente. Quello che si può togliere è tutto il resto: trovare la pagina
 * giusta, capire cosa premere, trascrivere a mano quaranta caratteri.
 *
 * Ogni passo si salta. Un'app che non parte finché non ottiene i permessi è
 * peggio di un'app senza permessi.
 */
@Composable
fun OnboardingScreen(
    viewModel: ChatViewModel,
    onOpenFree: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    // Il tasto indietro torna al passo prima; dal primo non fa niente, perché
    // sotto c'è la chat e uscire dall'app a metà configurazione è peggio.
    BackHandler(enabled = true) { if (step > 0) step-- }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
        ) {
            when (step) {
                0 -> Welcome(onNext = { step = 1 })
                1 -> KeyStep(
                    viewModel = viewModel,
                    onOpenFree = onOpenFree,
                    onNext = { step = 2 },
                )
                // Appena il flag è salvato questa schermata si ritira da sé.
                else -> PermissionStep(onDone = viewModel::completeOnboarding)
            }
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GuzzoBotLogo(size = 96.dp)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_welcome),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text(stringResource(R.string.onboarding_start))
        }
    }
}

/**
 * Il passo che decide se l'utente resta o se disinstalla.
 *
 * Il tasto apre la pagina della chiave nel browser del telefono, dove l'account
 * Google è già connesso: nessun accesso da rifare. Al ritorno gli appunti
 * vengono guardati una volta e, se contengono qualcosa con la forma giusta,
 * la chiave si attiva con un tocco.
 */
@Composable
private fun KeyStep(
    viewModel: ChatViewModel,
    onOpenFree: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    // Gli appunti si guardano solo al ritorno nell'app, e solo per vedere se
    // contengono una chiave: qualunque altra cosa viene scartata subito.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        // Una volta subito, perché l'osservatore scatta solo sui ritorni e la
        // chiave può essere già stata copiata prima di arrivare qui.
        found = viewModel.keyFromClipboard(GOOGLE, clipboardText(context))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !done) {
                found = viewModel.keyFromClipboard(GOOGLE, clipboardText(context))
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun save(key: String) {
        checking = true
        outcome = null
        scope.launch {
            val result = viewModel.verifyKey(GOOGLE, key)
            checking = false
            result.onSuccess { count ->
                viewModel.activateFreeProvider(GOOGLE, key)
                done = true
                outcome = context.getString(R.string.onboarding_key_ok, count)
            }.onFailure { error ->
                outcome = error.message ?: context.getString(R.string.onboarding_key_bad)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_key_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_key_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        Button(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")),
                )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(text = "  " + stringResource(R.string.onboarding_open_studio))
        }
        listOf(
            R.string.onboarding_step_1,
            R.string.onboarding_step_2,
            R.string.onboarding_step_3,
        ).forEachIndexed { index, resource ->
            Text(
                text = "${index + 1}.  " + stringResource(resource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        // La chiave trovata negli appunti: un tocco e si è pronti.
        AnimatedVisibility(visible = found != null && !done) {
            val key = found.orEmpty()
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_found, masked(key)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { save(key) },
                        enabled = !checking,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_use_found))
                    }
                }
            }
        }

        if (!done) {
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(stringResource(R.string.onboarding_paste_here)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )
            Button(
                onClick = { save(pasted) },
                enabled = pasted.isNotBlank() && !checking,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(stringResource(R.string.onboarding_verify))
            }
        }

        if (checking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        outcome?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (done) {
                Button(onClick = onNext, shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.onboarding_continue))
                }
            } else {
                TextButton(onClick = onNext) { Text(stringResource(R.string.onboarding_later)) }
                TextButton(onClick = onOpenFree) {
                    Text(stringResource(R.string.onboarding_other_free))
                }
            }
        }
    }
}

/**
 * I permessi, spiegati per quello che fanno e chiesti uno alla volta.
 *
 * Android concede una sola richiesta dopo un rifiuto: dopo quella l'unica via
 * sono le impostazioni di sistema, e allora è lì che si manda, invece di
 * ripresentare una finestra che non comparirà più.
 */
@Composable
private fun PermissionStep(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var micState by remember { mutableIntStateOf(state(context, Manifest.permission.RECORD_AUDIO)) }
    var notifyState by remember {
        mutableIntStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                state(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                GRANTED
            },
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micState = when {
            granted -> GRANTED
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false ->
                BLOCKED
            else -> MISSING
        }
    }
    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifyState = if (granted) GRANTED else BLOCKED }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_permissions_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = stringResource(R.string.privacy_notifications),
                detail = stringResource(R.string.onboarding_notifications_why),
                state = notifyState,
                onAsk = { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSettings = { openSettings(context) },
            )
        }
        PermissionCard(
            title = stringResource(R.string.privacy_mic),
            detail = stringResource(R.string.onboarding_mic_why),
            state = micState,
            onAsk = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onSettings = { openSettings(context) },
        )

        Text(
            text = stringResource(R.string.onboarding_permissions_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.onboarding_done))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    detail: String,
    state: Int,
    onAsk: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state == GRANTED) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            when (state) {
                GRANTED -> Unit
                BLOCKED -> TextButton(onClick = onSettings) {
                    Text(stringResource(R.string.privacy_system_settings))
                }
                else -> TextButton(onClick = onAsk) {
                    Text(stringResource(R.string.onboarding_allow))
                }
            }
        }
    }
}

private const val GOOGLE = "google"
private const val MISSING = 0
private const val GRANTED = 1
private const val BLOCKED = 2

private fun state(context: Context, permission: String): Int =
    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
        GRANTED
    } else {
        MISSING
    }

private fun openSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

/** Il testo negli appunti, se c'è e se l'app è in primo piano. */
private fun clipboardText(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}.getOrNull()

/** Di una chiave si mostra solo quanto basta a riconoscerla. */
private fun masked(key: String): String =
    if (key.length <= 10) key else key.take(6) + "…" + key.takeLast(4)

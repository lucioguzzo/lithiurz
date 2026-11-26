package com.lucio.syncaudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && res.data != null) {
            val i = Intent(this, SyncService::class.java)
                .setAction(SyncService.ACTION_SOURCE)
                .putExtra(SyncService.EXTRA_CODE, res.resultCode)
                .putExtra(SyncService.EXTRA_DATA, res.data)
            ContextCompat.startForegroundService(this, i)
        } else {
            SyncController.warning = "Serve il permesso di cattura per trasmettere."
        }
    }

    private var afterPermissions: (() -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == false) {
            SyncController.warning = "Senza il permesso \"Registra audio\" Android non consente di catturare l'audio delle altre app."
        } else afterPermissions?.invoke()
        afterPermissions = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    private fun withPermissions(needMic: Boolean, then: () -> Unit) {
        val wanted = buildList {
            if (needMic) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) then() else { afterPermissions = then; permissionLauncher.launch(wanted.toTypedArray()) }
    }

    private fun startTransmitting() = withPermissions(needMic = true) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else mpm.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    private fun startReceiving() = withPermissions(needMic = false) {
        ContextCompat.startForegroundService(this, Intent(this, SyncService::class.java).setAction(SyncService.ACTION_RECEIVER))
    }

    private fun connectTo(host: String) = withPermissions(needMic = false) {
        ContextCompat.startForegroundService(
            this, Intent(this, SyncService::class.java).setAction(SyncService.ACTION_CONNECT).putExtra(SyncService.EXTRA_HOST, host.trim())
        )
    }

    private fun stopAll() {
        startService(Intent(this, SyncService::class.java).setAction(SyncService.ACTION_STOP))
    }

    @Composable
    private fun Screen() {
        val prefs = remember { SyncController.prefs(this) }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(300); tick++ } }
        @Suppress("UNUSED_EXPRESSION") tick // recompose periodically: state lives in the service

        val role = SyncController.role
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SyncAudio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Due telefoni, due casse Bluetooth, la stessa musica a tempo. Avvia la musica in qualsiasi app " +
                    "sul telefono che trasmette; l'altro telefono la riproduce sincronizzata.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SyncController.warning?.let { w ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(w, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            when (role) {
                SyncController.Role.IDLE -> {
                    Button(onClick = { startTransmitting() }, Modifier.fillMaxWidth()) { Text("Trasmetti l'audio di questo telefono") }
                    OutlinedButton(onClick = { startReceiving() }, Modifier.fillMaxWidth()) { Text("Ricevi e riproduci") }
                }
                SyncController.Role.SOURCE -> SourcePanel(prefs)
                SyncController.Role.RECEIVER -> ReceiverPanel(prefs)
            }
            if (role != SyncController.Role.IDLE) {
                OutlinedButton(onClick = { stopAll() }, Modifier.fillMaxWidth()) { Text("Ferma") }
            }

            ManualConnect()

            DiagnosticsCard()

            Text(
                "Questo telefono: " + NetUtil.localIPv4().joinToString().ifEmpty { "nessuna rete" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun SourcePanel(prefs: android.content.SharedPreferences) {
        val src = SyncController.source
        var base by remember { mutableIntStateOf(prefs.getInt("baseDelayMs", 150)) }
        InfoCard("Trasmettitore") {
            val peers = src?.peerList.orEmpty()
            Line("Ricevitori", if (peers.isEmpty()) "nessuno (apri l'app sull'altro telefono e tocca \"Ricevi\")" else peers.joinToString { it.name })
            Line("Livello audio catturato", "${(SyncController.lastPeak * 100 / 32767)} %")
        }
        InfoCard("Ritardo di riproduzione") {
            Text(
                "Margine per la rete. Più è basso, più i ricevitori sono vicini a questo telefono; troppo basso causa buchi.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("$base ms", fontWeight = FontWeight.Bold)
            Slider(
                value = base.toFloat(), valueRange = 20f..600f,
                onValueChange = { base = it.roundToInt(); src?.baseDelayMs = base },
                onValueChangeFinished = { prefs.edit().putInt("baseDelayMs", base).apply() },
            )
        }
    }

    @Composable
    private fun ReceiverPanel(prefs: android.content.SharedPreferences) {
        val rcv = SyncController.receiver
        val engine = rcv?.engine
        val cs = rcv?.clockSync
        var offset by remember { mutableIntStateOf(prefs.getInt("userOffsetMs", 0)) }
        InfoCard("Ricevitore") {
            Line("Sorgente", rcv?.source?.name ?: "cerco sulla rete…")
            if (engine != null && cs != null) {
                Line("Orologio", if (cs.synced) "agganciato (RTT %.1f ms, deriva %.0f ppm)".format(cs.bestRttNs / 1e6, cs.skewPpm) else "aggancio…")
                Line("Ritardo totale", "${engine.targetDelayMs} ms")
                Line("Margine dati", "%.0f ms".format(engine.bufferedMs))
                Line("Correzione velocità", "%+.0f ppm".format((engine.rate - 1.0) * 1e6))
                val lossPct = if (engine.renderedFrames > 0) engine.missingFrames * 100.0 / engine.renderedFrames else 0.0
                Line("Audio mancante", "%.1f %%".format(lossPct))
                if (engine.playing && engine.bufferedMs < 0) {
                    Text("Ritardo troppo basso: i dati arrivano dopo il momento di riproduzione. Aumenta la regolazione.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        InfoCard("Regola sincronia") {
            Text(
                "Sposta finché le due casse suonano insieme. Valori positivi ritardano questa cassa, negativi la anticipano. " +
                    "Il valore resta salvato per questo telefono.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("%+d ms".format(offset), fontWeight = FontWeight.Bold)
            Slider(
                value = offset.toFloat(), valueRange = -400f..600f,
                onValueChange = { offset = it.roundToInt(); rcv?.userOffsetMs = offset },
                onValueChangeFinished = { prefs.edit().putInt("userOffsetMs", offset).apply() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in listOf(-10, -1, +1, +10)) {
                    TextButton(onClick = {
                        offset += d; rcv?.userOffsetMs = offset; prefs.edit().putInt("userOffsetMs", offset).apply()
                    }) { Text("%+d".format(d)) }
                }
            }
        }
        Button(onClick = { startTransmitting() }, Modifier.fillMaxWidth()) { Text("Trasmetti da questo telefono (scambia i ruoli)") }
    }

    @Composable
    private fun ManualConnect() {
        var host by remember { mutableStateOf("") }
        InfoCard("Connessione manuale") {
            Text("Se il telefono trasmettitore non viene trovato automaticamente, inserisci il suo indirizzo IP.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = host, onValueChange = { host = it }, singleLine = true,
                label = { Text("es. 192.168.1.23") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { if (host.isNotBlank()) connectTo(host) }) { Text("Connetti") }
        }
    }

    @Composable
    private fun DiagnosticsCard() {
        var open by remember { mutableStateOf(false) }
        InfoCard("Diagnostica") {
            val hints = Diagnostics.hints(this@MainActivity)
            if (hints.isNotEmpty()) hints.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open = !open }) { Text(if (open) "Nascondi dettagli" else "Mostra dettagli") }
                OutlinedButton(onClick = {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("SyncAudio", Diagnostics.report(this@MainActivity)))
                    android.widget.Toast.makeText(this@MainActivity, "Report copiato", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copia report") }
            }
            if (open) Text(Diagnostics.report(this@MainActivity), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }

    @Composable
    private fun InfoCard(title: String, content: @Composable () -> Unit) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }

    @Composable
    private fun Line(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
    }
}

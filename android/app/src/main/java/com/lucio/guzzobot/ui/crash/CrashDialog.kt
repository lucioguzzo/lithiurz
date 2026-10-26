package com.lucio.guzzobot.ui.crash

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.crash.CrashReport

/**
 * Al lancio dopo un crash: cosa è successo, e il modo di farlo sapere.
 *
 * Si condivide con le app che il telefono già ha — mail, WhatsApp,
 * Telegram — perché GUZZOBOT non ha un server a cui mandarlo da sé.
 */
@Composable
fun CrashDialog(report: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val subject = stringResource(R.string.crash_subject)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crash_body))
                Text(
                    text = CrashReport.headline(report),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                runCatching { context.startActivity(Intent.createChooser(send, subject)) }
                onDone()
            }) { Text(stringResource(R.string.crash_share)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.crash_dismiss)) }
        },
    )
}

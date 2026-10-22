package com.lucio.guzzobot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucio.guzzobot.ui.GuzzoBotNavHost
import com.lucio.guzzobot.ui.chat.ChatViewModel
import com.lucio.guzzobot.ui.theme.GuzzoBotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GuzzoBotApp).container
        val shared = sharedText(intent)
        val sharedFile = sharedStream(intent)

        setContent {
            val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            GuzzoBotTheme(mode = settings.themeMode) {
                GuzzoBotNavHost(viewModel = viewModel, sharedText = shared)
            }
        }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    /** Un file condiviso da un'altra app: immagine, documento o audio. */
    private fun sharedStream(intent: Intent?): Pair<Uri, String>? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (type == "text/plain") return null
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        return uri to type
    }
}
